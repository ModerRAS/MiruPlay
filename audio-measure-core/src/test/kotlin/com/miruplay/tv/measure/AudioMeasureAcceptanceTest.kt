package com.miruplay.tv.measure

import com.miruplay.tv.audio.BiquadDesigner
import com.miruplay.tv.model.AudioDspBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Acceptance tests for the sweep-measured PEQ core. They replicate the Python
 * reference prototype's T1–T7 scenario (synthetic 6-peaking room, 5 ms direct
 * delay, −45 dB noise floor, deterministic seed) and assert the same bounds:
 * deconvolution RMS ≤ 0.1 dB, smoothed RMS ≤ 0.6 dB, auto-fit peak ≤ 1.1 dB,
 * all constraints PASS, drift compensation direction proven.
 */
class AudioMeasureAcceptanceTest {

    companion object {
        private const val FS = 48_000
        private const val DIRECT_DELAY_S = 0.005
        private const val NOISE_DB = -45.0

        private val roomBands = listOf(
            Triple(42.0, +8.5, 7.0),
            Triple(68.0, +6.0, 5.5),
            Triple(90.0, -13.0, 9.0),   // non-minimum-phase null: must never be boosted
            Triple(115.0, +5.0, 4.5),
            Triple(160.0, -4.0, 2.5),
            Triple(8000.0, -4.5, 0.8),  // HF roll-off
        )

        private val fitConfig = PeqFitConfig(
            matchLoHz = 20.0,
            matchHiHz = 300.0,
            flatnessTargetDb = 1.0,
            maxFilters = 10,
            individualMaxBoostDb = 0.0,
            overallMaxBoostDb = 0.0,
            allowNarrowBelow200 = true,
            varyMaxQAbove200 = true,
            dropSmallFilters = true,
            trimToCrossings = true,
        )

        private val pipeline by lazy { runPipeline() }

        private fun biquadChain(x: DoubleArray, bands: List<Triple<Double, Double, Double>>, fs: Int): DoubleArray {
            var y = x
            for ((f0, gainDb, q) in bands) {
                val c = BiquadDesigner.design(
                    AudioDspBand(frequencyHz = f0.toFloat(), gainDb = gainDb.toFloat(), q = q.toFloat()),
                    fs,
                )
                val out = DoubleArray(y.size)
                var z1 = 0.0
                var z2 = 0.0
                for (i in y.indices) {
                    val o = c.b0 * y[i] + z1
                    z1 = c.b1 * y[i] - c.a1 * o + z2
                    z2 = c.b2 * y[i] - c.a2 * o
                    out[i] = o
                }
                y = out
            }
            return y
        }

        private fun rmsError(a: DoubleArray, b: DoubleArray, mask: BooleanArray): Double {
            var sq = 0.0
            var n = 0
            for (i in a.indices) {
                if (mask[i]) {
                    val d = a[i] - b[i]
                    sq += d * d
                    n++
                }
            }
            return sqrt(sq / n)
        }

        private fun DoubleArray.meanWhere(mask: BooleanArray): Double {
            var sum = 0.0
            var n = 0
            for (i in indices) if (mask[i]) {
                sum += this[i]; n++
            }
            return sum / n
        }

        private class PipelineResult(
            val cosSim: Double,
            val peakSampleTrue: Int,
            val peakSampleEst: Int,
            val rawRmsDb: Double,
            val smoothedRmsDb: Double,
            val fit: PeqFitResult,
            val peakBeforeDb: Double,
            val peakAfterDb: Double,
            val nullResidualDb: Double,
            /** ppm → (uncompensated similarity, ideal-compensated similarity) */
            val driftTable: Map<Double, Pair<Double, Double>>,
        )

        private fun runPipeline(): PipelineResult {
            val freqs = ResponseAnalysis.logFreqGrid(10.0, 24_000.0, 48)

            // T1: synthetic room truth
            val truthDb = DoubleArray(freqs.size)
            for ((f0, g, q) in roomBands) {
                val resp = AutoEqFitter.bandResponseDb(
                    AudioDspBand(frequencyHz = f0.toFloat(), gainDb = g.toFloat(), q = q.toFloat()),
                    freqs, FS,
                )
                for (i in freqs.indices) truthDb[i] += resp[i]
            }

            // T2: sweep → deconvolution → response vs truth
            val irLen = FS // 1 s room tail
            val delta = DoubleArray(irLen)
            delta[(DIRECT_DELAY_S * FS).toInt()] = 1.0
            val roomIr = biquadChain(delta, roomBands, FS)
            val irMax = roomIr.max()
            for (i in roomIr.indices) roomIr[i] /= irMax

            val sweep = LogSweepGenerator.generate(10.0, 20_000.0, 4.0, FS)
            val recording = Fft.fftConvolve(sweep.sweep, roomIr)
            val recMax = recording.max()
            val rng = Random(20260904)
            val noiseAmp = recMax * 10.0.pow(NOISE_DB / 20.0)
            for (i in recording.indices) recording[i] += rng.nextGaussian() * noiseAmp
            val rec = recording.copyOf(sweep.sweep.size + irLen)

            val irEst = Deconvolver.deconvolve(rec, sweep.sweep).copyOf(irLen)

            val cosSim = MeasurementOps.cosineSimilarity(irEst, roomIr.copyOf(irEst.size))
            val peakSampleEst = irEst.indices.maxBy { abs(irEst[it]) }
            val peakSampleTrue = (0 until irEst.size).maxBy { abs(roomIr[it]) }

            val measRaw = ResponseAnalysis.irToResponseDb(irEst, freqs, FS)
            // center both on the 200 Hz–2 kHz mean, same as the prototype
            val centerMask = BooleanArray(freqs.size) { freqs[it] in 200.0..2000.0 }
            val bandMask = BooleanArray(freqs.size) { freqs[it] in 20.0..20_000.0 }
            val measCenter = measRaw.meanWhere(centerMask)
            val truthCenter = truthDb.meanWhere(centerMask)
            for (i in freqs.indices) {
                measRaw[i] -= measCenter
                truthDb[i] -= truthCenter
            }
            val rawRmsDb = rmsError(measRaw, truthDb, bandMask)

            // T3: REW variable smoothing
            val widths = ResponseAnalysis.smoothingWidthVariable(freqs)
            val measSmooth = ResponseAnalysis.smoothGaussianLogf(freqs, measRaw, widths)
            val smoothedRmsDb = rmsError(measSmooth, truthDb, bandMask)

            // T4/T5: target + auto-fit
            val level = ResponseAnalysis.autoTargetLevel(measSmooth, freqs)
            val target = ResponseAnalysis.buildTarget(
                freqs, level,
                lfCutoffHz = 15.0, lfSlopeDbOct = 24.0,
                hfFallStartHz = 10_000.0, hfFallDbOct = 1.5,
            )
            val fit = AutoEqFitter.fitPeq(measSmooth, target, freqs, fitConfig, FS)
            val lo = fit.matchLoHz
            val hi = fit.matchHiHz
            var peakBefore = 0.0
            for (i in freqs.indices) {
                if (freqs[i] in lo..hi) peakBefore = max(peakBefore, measSmooth[i] - target[i])
            }
            val (peakAfter, nullResidual) = AutoEqFitter.finalMetrics(measSmooth, target, fit.bands, freqs, FS, lo, hi)

            // T7: clock drift — uncompensated vs ideal compensation (true-ppm resample)
            val driftTable = mutableMapOf<Double, Pair<Double, Double>>()
            for (ppm in listOf(0.0, 2.0, 5.0, 10.0, 20.0)) {
                val recD = MeasurementOps.applyDrift(recording, ppm).copyOf(sweep.sweep.size + irLen)
                val irD = Deconvolver.deconvolve(recD, sweep.sweep).copyOf(irLen)
                val csRaw = MeasurementOps.cosineSimilarity(irD, roomIr.copyOf(irD.size))
                val irIdeal = Deconvolver.deconvolve(recD, MeasurementOps.applyDrift(sweep.sweep, ppm)).copyOf(irLen)
                val csIdeal = MeasurementOps.cosineSimilarity(irIdeal, roomIr.copyOf(irIdeal.size))
                driftTable[ppm] = csRaw to csIdeal
            }

            return PipelineResult(
                cosSim, peakSampleTrue, peakSampleEst,
                rawRmsDb, smoothedRmsDb, fit, peakBefore, peakAfter, nullResidual, driftTable,
            )
        }
    }

    @Test
    fun `T2 deconvolution recovers room IR with high cosine similarity and exact peak`() {
        assertTrue("cosine similarity ${pipeline.cosSim}", pipeline.cosSim >= 0.90)
        assertEquals(
            "IR peak location off by more than 1 sample",
            pipeline.peakSampleTrue.toLong(), pipeline.peakSampleEst.toLong(),
        )
    }

    @Test
    fun `T2 raw frequency response matches truth within 0_1 dB RMS`() {
        assertTrue("raw RMS ${pipeline.rawRmsDb}", pipeline.rawRmsDb <= 0.1)
    }

    @Test
    fun `T3 smoothed response matches truth within 0_6 dB RMS`() {
        assertTrue("smoothed RMS ${pipeline.smoothedRmsDb}", pipeline.smoothedRmsDb <= 0.6)
    }

    @Test
    fun `T5 auto fit presses in-band peak below 1_1 dB while preserving the 90 Hz null`() {
        assertTrue("peak before fit was only ${pipeline.peakBeforeDb}", pipeline.peakBeforeDb >= 5.0)
        assertTrue("peak after fit ${pipeline.peakAfterDb}", pipeline.peakAfterDb <= 1.15)
        assertTrue("null residual ${pipeline.nullResidualDb}", pipeline.nullResidualDb >= 10.0)
        assertTrue("filter count ${pipeline.fit.bands.size}", pipeline.fit.bands.size in 2..fitConfig.maxFilters)
    }

    @Test
    fun `T6 fitted bands satisfy all constraint checks`() {
        val bands = pipeline.fit.bands
        val boosts = bands.filter { it.gainDb > 0f }
        assertTrue(
            "no boost filters allowed, got ${boosts.map { it.gainDb }}",
            boosts.all { it.gainDb <= fitConfig.individualMaxBoostDb.toFloat() + 1e-6f },
        )
        assertTrue(
            bands.all { it.q.toDouble() <= AutoEqFitter.maxQAt(it.frequencyHz.toDouble(), it.gainDb > 0f, fitConfig) + 1e-6 },
        )
        assertTrue("filter count ${bands.size}", bands.size <= fitConfig.maxFilters)
        assertTrue(
            bands.all { AutoEqFitter.t60OfFilter(it.frequencyHz.toDouble(), it.q.toDouble()) <= 0.5 + 1e-9 },
        )
        val nullBoosts = bands.filter { it.frequencyHz in 75f..105f && it.gainDb > 0.5f }
        assertTrue("boosted into the 90 Hz null: $nullBoosts", nullBoosts.isEmpty())
        val small = bands.filter { abs(it.gainDb.toDouble()) < fitConfig.flatnessTargetDb / 2 - 1e-9 }
        assertTrue("small filters not dropped: $small", small.isEmpty())
    }

    @Test
    fun `T7 uncompensated drift collapses IR and ideal compensation recovers it`() {
        val t = pipeline.driftTable
        // Uncompensated: fine at 0 ppm, collapses by 5–20 ppm.
        assertTrue("cs(0) = ${t[0.0]!!.first}", t[0.0]!!.first > 0.85)
        assertTrue("cs(5) = ${t[5.0]!!.first}", t[5.0]!!.first < 0.5)
        assertTrue("cs(20) = ${t[20.0]!!.first}", t[20.0]!!.first < 0.1)
        // Ideal compensation (true-ppm resample of the reference) recovers similarity.
        assertTrue("cs(5, comp) = ${t[5.0]!!.second}", t[5.0]!!.second > 0.85)
        assertTrue("cs(20, comp) = ${t[20.0]!!.second}", t[20.0]!!.second > 0.8)
    }

    @Test
    fun `sweep inverse filter is peak-normalized and sweep is bounded`() {
        val sweep = LogSweepGenerator.generate(10.0, 20_000.0, 1.0, FS)
        assertEquals(1.0, sweep.inverse.maxOf { abs(it) }, 1e-12)
        assertTrue(sweep.sweep.all { abs(it) <= 1.0 + 1e-12 })
        assertEquals(sweep.sweep.size, sweep.inverse.size)
    }

    @Test
    fun `deconvolution of clean sweep through delay-only IR locates the delay exactly`() {
        val sweep = LogSweepGenerator.generate(10.0, 20_000.0, 2.0, FS)
        val ir = DoubleArray(FS)
        ir[(0.005 * FS).toInt()] = 1.0
        val rec = Fft.fftConvolve(sweep.sweep, ir).copyOf(sweep.sweep.size + FS)
        val est = Deconvolver.deconvolve(rec, sweep.sweep).copyOf(FS)
        val peak = est.indices.maxBy { abs(est[it]) }
        assertEquals((0.005 * FS).toInt().toLong(), peak.toLong())
    }

    @Test
    fun `fitter refuses to boost into deep nulls even when the whole match range dips`() {
        val freqs = ResponseAnalysis.logFreqGrid(10.0, 24_000.0, 48)
        val measured = DoubleArray(freqs.size)
        val dip = AutoEqFitter.bandResponseDb(
            AudioDspBand(frequencyHz = 100f, gainDb = -12f, q = 8f), freqs, FS,
        )
        for (i in freqs.indices) measured[i] += dip[i]
        val target = DoubleArray(freqs.size) // flat 0 dB would require boosting the dip
        val result = AutoEqFitter.fitPeq(measured, target, freqs, fitConfig, FS)
        assertTrue(result.bands.isEmpty())
    }
}
