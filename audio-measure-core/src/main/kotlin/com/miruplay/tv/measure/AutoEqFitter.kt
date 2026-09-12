package com.miruplay.tv.measure

import com.miruplay.tv.audio.BiquadDesigner
import com.miruplay.tv.model.AudioDspBand
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Parameter semantics mirror the REW EQ window help documentation. */
data class PeqFitConfig(
    val matchLoHz: Double = 20.0,
    val matchHiHz: Double = 300.0,
    val flatnessTargetDb: Double = 1.0,
    val maxFilters: Int = 10,
    /** Room correction is cut-only by default. */
    val individualMaxBoostDb: Double = 0.0,
    val overallMaxBoostDb: Double = 0.0,
    val allowNarrowBelow200: Boolean = true,
    val varyMaxQAbove200: Boolean = true,
    val qMin: Double = 0.5,
    /** REW "Drop filters if gain is small": drop |gain| < flatnessTarget/2. */
    val dropSmallFilters: Boolean = true,
    /** Don't place filters below the first measured-above-target / above the last below. */
    val trimToCrossings: Boolean = true,
    val qGrid: List<Double> = listOf(0.7, 1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0),
)

data class PeqFitResult(
    val bands: List<AudioDspBand>,
    val matchLoHz: Double,
    val matchHiHz: Double,
    val maxDeviationDb: Double,
    val rmsDeviationDb: Double,
    val overallMaxBoostDb: Double,
)

/**
 * REW "Match Response to Target" behaviour:
 *  1. trim the match range (default: exclude natural roll-off regions)
 *  2. loop: seed a peaking filter at the worst |predicted − target| point with
 *     gain = −deviation (RBJ peaking response at center frequency is exactly
 *     gain dB), grid-search Q minimizing in-range RMS, stop when flatness
 *     target is met or no improvement
 *  3. drop filters with |gain| < flatnessTarget/2
 *
 * Bands are emitted as [AudioDspBand] PEAKING and computed through the existing
 * [BiquadDesigner], so fitted values flow into the playback DSP with no
 * coefficient translation.
 */
object AutoEqFitter {

    /** 60 dB decay time of a second-order resonator: t60 = 2.1986·Q/f0 (seconds). */
    fun t60OfFilter(f0Hz: Double, q: Double): Double = 2.1986 * q / f0Hz

    /**
     * REW Q-cap rules: below 200 Hz up to 10.0 when narrow filters are allowed;
     * above, 10.0@200Hz → 3.0@10kHz when varying; boost filters additionally
     * limited (min(7.5, f/6.22) — the conservative reading of the 500 ms t60 rule).
     */
    fun maxQAt(fHz: Double, isBoost: Boolean, cfg: PeqFitConfig): Double {
        val qCap: Double = if (fHz < 200.0) {
            if (cfg.allowNarrowBelow200) 10.0 else if (!cfg.varyMaxQAbove200) 5.0 else 10.0
        } else if (cfg.varyMaxQAbove200) {
            val t = min(log2(fHz / 200.0) / log2(10_000.0 / 200.0), 1.0)
            10.0 * (3.0 / 10.0).pow(t)
        } else {
            5.0
        }
        var cap = qCap
        if (isBoost) {
            val boostCap = if (cfg.varyMaxQAbove200 && fHz >= 200.0) {
                val t = min(log2(fHz / 200.0) / log2(10_000.0 / 200.0), 1.0)
                7.5 * (3.0 / 7.5).pow(t)
            } else {
                min(7.5, fHz / 6.22)
            }
            cap = min(cap, boostCap)
        }
        return maxOf(cap, cfg.qMin)
    }

    /** Magnitude response (dB) of one band over [freqs], via the shared BiquadDesigner. */
    fun bandResponseDb(band: AudioDspBand, freqs: DoubleArray, fs: Int): DoubleArray {
        val coeffs = BiquadDesigner.design(band, fs)
        return DoubleArray(freqs.size) { i ->
            val mag = coeffs.magnitudeAt(freqs[i], fs.toDouble())
            20.0 * kotlin.math.log10(maxOf(mag, 1e-12))
        }
    }

    fun trimMatchRange(
        measuredDb: DoubleArray,
        targetDb: DoubleArray,
        freqs: DoubleArray,
        cfg: PeqFitConfig,
    ): Pair<Double, Double> {
        if (!cfg.trimToCrossings) return cfg.matchLoHz to cfg.matchHiHz
        var firstAbove = -1
        var lastBelow = -1
        for (i in freqs.indices) {
            val f = freqs[i]
            if (f < cfg.matchLoHz || f > cfg.matchHiHz) continue
            if (measuredDb[i] - targetDb[i] > 0.0) {
                if (firstAbove < 0) firstAbove = i
                lastBelow = i
            }
        }
        if (firstAbove < 0) return cfg.matchLoHz to cfg.matchHiHz
        return freqs[firstAbove] to freqs[lastBelow]
    }

    fun fitPeq(
        measuredDb: DoubleArray,
        targetDb: DoubleArray,
        freqs: DoubleArray,
        cfg: PeqFitConfig,
        fs: Int,
    ): PeqFitResult {
        val (lo, hi) = trimMatchRange(measuredDb, targetDb, freqs, cfg)
        val inRange = BooleanArray(freqs.size) { freqs[it] >= lo && freqs[it] <= hi }
        var rangeCount = 0
        for (v in inRange) if (v) rangeCount++
        if (rangeCount == 0) {
            return PeqFitResult(emptyList(), lo, hi, 0.0, 0.0, 0.0)
        }

        val correction = DoubleArray(freqs.size)
        val bands = mutableListOf<AudioDspBand>()
        val active = inRange.copyOf()

        while (bands.size < cfg.maxFilters && active.any()) {
            val err = DoubleArray(freqs.size) { measuredDb[it] + correction[it] - targetDb[it] }
            var rmsBeforeSq = 0.0
            for (i in freqs.indices) {
                if (inRange[i]) rmsBeforeSq += err[i] * err[i]
            }
            val rmsBefore = sqrt(rmsBeforeSq / rangeCount)

            var worstLocal = -1
            var worstAbs = 0.0
            var worstDb = 0.0
            for (i in freqs.indices) {
                if (!active[i]) continue
                val a = abs(err[i])
                if (a > worstAbs) {
                    worstAbs = a
                    worstDb = err[i]
                    worstLocal = i
                }
            }
            if (worstLocal < 0 || worstAbs < cfg.flatnessTargetDb) break

            val fSeed = freqs[worstLocal]
            val gainSeed = clampGain(-worstDb, cfg)

            // Boost needed but cut-only constraint forbids it → deactivate and move on.
            if (worstDb < 0 && gainSeed >= -1e-9) {
                active[worstLocal] = false
                continue
            }

            val qCap = maxQAt(fSeed, gainSeed > 0, cfg)
            var qCandidates = cfg.qGrid.filter { it >= cfg.qMin && it <= qCap }
            if (qCandidates.isEmpty()) qCandidates = listOf(maxOf(cfg.qMin, min(1.0, qCap)))

            var bestRms = Double.MAX_VALUE
            var bestBand: AudioDspBand? = null
            var bestResp: DoubleArray? = null
            for (q0 in qCandidates) {
                val band = AudioDspBand(
                    type = com.miruplay.tv.model.AudioDspFilterType.PEAKING,
                    frequencyHz = fSeed.toFloat(),
                    gainDb = gainSeed.toFloat(),
                    q = q0.toFloat(),
                )
                val resp = bandResponseDb(band, freqs, fs)
                var sq = 0.0
                for (i in freqs.indices) {
                    if (inRange[i]) {
                        val e = err[i] + resp[i]
                        sq += e * e
                    }
                }
                val rms = sqrt(sq / rangeCount)
                if (rms < bestRms) {
                    bestRms = rms
                    bestBand = band
                    bestResp = resp
                }
            }

            if (bestRms > rmsBefore - 0.02) {
                active[worstLocal] = false
                continue
            }

            bands += bestBand!!
            for (i in freqs.indices) correction[i] += bestResp!![i]
            active[worstLocal] = false
        }

        // Drop small-gain filters; final correction is the sum of kept responses either way.
        val kept = if (cfg.dropSmallFilters) {
            val threshold = cfg.flatnessTargetDb / 2.0
            bands.filter { abs(it.gainDb.toDouble()) >= threshold }
        } else {
            bands
        }

        val total = DoubleArray(freqs.size) { measuredDb[it] - targetDb[it] }
        for (b in kept) {
            val resp = bandResponseDb(b, freqs, fs)
            for (i in freqs.indices) total[i] += resp[i]
        }
        var maxDev = 0.0
        var rmsSq = 0.0
        for (i in freqs.indices) {
            if (inRange[i]) {
                val a = abs(total[i])
                if (a > maxDev) maxDev = a
                rmsSq += total[i] * total[i]
            }
        }
        return PeqFitResult(
            bands = kept,
            matchLoHz = lo,
            matchHiHz = hi,
            maxDeviationDb = maxDev,
            rmsDeviationDb = sqrt(rmsSq / rangeCount),
            overallMaxBoostDb = kept.maxOfOrNull { it.gainDb.toDouble() } ?: 0.0,
        )
    }

    /**
     * Residual peak height above target and null depth below target, reported
     * separately: with cut-only fitting max|residual| would forever sit at the
     * null depth, which is expected to be preserved, not a fit failure.
     */
    fun finalMetrics(
        measuredDb: DoubleArray,
        targetDb: DoubleArray,
        bands: List<AudioDspBand>,
        freqs: DoubleArray,
        fs: Int,
        loHz: Double,
        hiHz: Double,
    ): Pair<Double, Double> {
        val resid = DoubleArray(freqs.size) { measuredDb[it] - targetDb[it] }
        for (b in bands) {
            val resp = bandResponseDb(b, freqs, fs)
            for (i in freqs.indices) resid[i] += resp[i]
        }
        var peak = 0.0
        var nullDepth = 0.0
        for (i in freqs.indices) {
            if (freqs[i] in loHz..hiHz) {
                peak = maxOf(peak, maxOf(resid[i], 0.0))
                nullDepth = maxOf(nullDepth, abs(minOf(resid[i], 0.0)))
            }
        }
        return peak to nullDepth
    }

    private fun clampGain(g: Double, cfg: PeqFitConfig): Double {
        var v = g
        val hi = maxOf(0.0, cfg.individualMaxBoostDb)
        if (v > 0) v = min(v, hi)
        return v.coerceIn(-24.0, hi)
    }
}
