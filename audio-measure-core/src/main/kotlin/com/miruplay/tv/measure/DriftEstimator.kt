package com.miruplay.tv.measure

import kotlin.math.abs

/**
 * Clock-drift estimation and measurement-quality gates.
 *
 * The estimator is the reference prototype's baseline: re-deconvolve the
 * recording against the reference sweep resampled by each candidate drift and
 * take the drift with the sharpest IR (max|IR|/rms(IR)), coarse 1 ppm grid →
 * fine 0.1 ppm grid → sub-sample parabolic refinement.
 *
 * KNOWN LIMITATION (measured, matching the prototype's T7): the score has a
 * ~1/(T·fs) ≈ 5 ppm sample-quantization lattice, so estimates at ≥ 5 ppm
 * drift can land on spurious peaks. This is why [RoomMeasurer] requires a
 * dual sweep and gates the COMPENSATED result through [tailSimilarity]
 * before any EQ is applied: at correct compensation both sweeps recover the
 * same room tail (similarity ≈ 0.9+), at wrong compensation the tails
 * decorrelate (≈ 0.6–0.75) — cleanly separating valid from invalid
 * measurements where sharpness alone cannot.
 */
object DriftEstimator {

    data class Estimate(
        val estimatedPpm: Double,
        /** IR sharpness after resampling the reference sweep by [estimatedPpm]. */
        val compensatedSharpness: Double,
    )

    fun estimate(
        recorded: DoubleArray,
        sweep: DoubleArray,
        irSampleCount: Int,
        searchRangePpm: Double = 15.0,
        coarseStepPpm: Double = 1.0,
        fineStepPpm: Double = 0.1,
    ): Estimate {
        // The recording's spectrum is invariant across candidates: compute it
        // once instead of once per candidate (one 512k-point FFT + ~8 MB alloc
        // per candidate otherwise — the dominant analysis-phase cost).
        val nFft = Fft.nextPow2(recorded.size + sweep.size)
        val (yRe, yIm) = Fft.rfft(recorded, nFft)
        val outLen = recorded.size - sweep.size + 1

        fun sharpnessAt(ppm: Double): Double {
            val sw = if (ppm == 0.0) sweep else MeasurementOps.applyDrift(sweep, ppm)
            val ir = Deconvolver.deconvolve(yRe, yIm, nFft, sw, outputLength = outLen)
            val n = minOf(irSampleCount, ir.size)
            return MeasurementOps.irSharpness(ir.copyOf(n))
        }

        var coarseBest = -searchRangePpm
        var coarseScore = sharpnessAt(coarseBest)
        var p = -searchRangePpm
        while (p <= searchRangePpm + 1e-9) {
            val s = sharpnessAt(p)
            if (s > coarseScore) {
                coarseScore = s
                coarseBest = p
            }
            p += coarseStepPpm
        }

        var fineBest = coarseBest
        var fineScore = coarseScore
        var q = coarseBest - coarseStepPpm
        while (q <= coarseBest + coarseStepPpm + 1e-9) {
            val s = sharpnessAt(q)
            if (s > fineScore) {
                fineScore = s
                fineBest = q
            }
            q += fineStepPpm
        }

        val ppm = refineParabolic(fineBest, fineScore, fineStepPpm, ::sharpnessAt)
        return Estimate(ppm, sharpnessAt(ppm))
    }

    /**
     * Cosine similarity between the room tails of two independently
     * deconvolved IRs, after each IR's direct peak (plus [skipAfterPeak]
     * samples). The direct δ peak dominates whole-IR similarity at any
     * compensation, so the tail — which carries the room's actual character —
     * is the discriminative part.
     */
    fun tailSimilarity(irA: DoubleArray, irB: DoubleArray, skipAfterPeak: Int = 60): Double {
        val startA = peakIndex(irA) + skipAfterPeak
        val startB = peakIndex(irB) + skipAfterPeak
        val start = maxOf(startA, startB)
        val n = minOf(irA.size, irB.size)
        if (start >= n) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in start until n) {
            dot += irA[i] * irB[i]
            na += irA[i] * irA[i]
            nb += irB[i] * irB[i]
        }
        return dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb) + 1e-30)
    }

    private fun peakIndex(ir: DoubleArray): Int {
        var best = 0
        for (i in ir.indices) if (abs(ir[i]) > abs(ir[best])) best = i
        return best
    }

    /** 3-point parabolic peak interpolation; clamped to ±[step] of the grid peak. */
    private fun refineParabolic(
        x0: Double,
        y0: Double,
        step: Double,
        score: (Double) -> Double,
    ): Double {
        val yMinus = score(x0 - step)
        val yPlus = score(x0 + step)
        val denom = yMinus - 2.0 * y0 + yPlus
        if (abs(denom) < 1e-12) return x0
        val delta = 0.5 * (yMinus - yPlus) / denom * step
        return x0 + delta.coerceIn(-step, step)
    }
}
