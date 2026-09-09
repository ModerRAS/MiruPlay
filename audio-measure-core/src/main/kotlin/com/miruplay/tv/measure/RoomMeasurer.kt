package com.miruplay.tv.measure

/**
 * Measurement pipeline facade: deconvolve → response → REW smoothing →
 * target → auto-fit, plus the fail-closed clock-drift gate.
 *
 * Clock drift between the playback clock (HDMI/AVR) and the capture clock
 * (USB mic) destroys the deconvolution: ~5 ppm already collapses IR
 * similarity from 0.92 to ~0.14. Compensation direction is proven (resample
 * the reference sweep by the estimated drift before deconvolving) and small
 * drift (≤ ~2 ppm) is estimable, but a single sweep cannot verify its own
 * drift estimate (sample-quantization lattice ~1/(T·fs)), so a trustworthy
 * measurement REQUIRES a dual sweep:
 *
 *  1. both sweeps must estimate the same drift within [ppmTolerance].
 *
 * Known residual risk (measured): estimates at ≥ 5 ppm drift can land on
 * spurious ~1/(T·fs)-lattice peaks; dual-sweep consistency catches most such
 * cases but is not a proof. A rejected measurement reports
 * [Measurement.valid] = false and the fitted bands MUST NOT be applied.
 * Guidance: drift error scales with sweep length — use shorter sweeps, or fix
 * the clock domain (shared-clock capture / timing reference, Phase 3).
 */
object RoomMeasurer {

    data class DriftCheck(
        /** Mean per-sweep estimate; 0 when unverifiable (single sweep). */
        val estimatedPpm: Double,
        /** IR sharpness after compensating by [estimatedPpm]. */
        val compensatedSharpness: Double,
        /** False when estimates disagree across sweeps, or a single sweep can't verify. */
        val consistent: Boolean,
        /** Max |estimate_i − estimate_0| across sweeps; 0 for a single sweep. */
        val ppmSpread: Double,
        /** Per-sweep estimates, for diagnostics. */
        val estimates: List<Double>,
        /** Tail similarity of the compensated dual IRs; NaN when unverifiable. */
        val tailSimilarity: Double,
    )

    data class Measurement(
        val ir: DoubleArray,
        val responseDb: DoubleArray,
        val smoothedDb: DoubleArray,
        val freqs: DoubleArray,
        val targetDb: DoubleArray,
        val fit: PeqFitResult,
        val drift: DriftCheck,
        /** False ⇒ do NOT apply the fitted bands (fail-closed). */
        val valid: Boolean,
        val invalidReason: String?,
    )

    /**
     * @param recordings one captured recording per sweep (same drift affects all)
     * @param sweeps the reference sweeps as played, in the same order
     */
    fun measure(
        recordings: List<DoubleArray>,
        sweeps: List<LogSweep>,
        fs: Int,
        irLengthS: Double = 1.0,
        gridPerOctave: Int = 48,
        gridLoHz: Double = 10.0,
        gridHiHz: Double = 24_000.0,
        fitConfig: PeqFitConfig = PeqFitConfig(),
        ppmTolerance: Double = 1.0,
        /**
         * Tail-similarity quality gate. Disabled (0) by default: in the synthetic
         * reference scenario the deconvolution noise floor exceeds the room tail
         * energy, so the gate cannot be calibrated offline — set it from on-device
         * measurements (Phase 3) where real room tails have far higher SNR.
         */
        minTailSimilarity: Double = 0.0,
        /** Optional absolute sharpness gate; 0 disables it until calibrated on device. */
        minSharpness: Double = 0.0,
    ): Measurement {
        require(recordings.size == sweeps.size) { "one recording per sweep required" }
        require(recordings.isNotEmpty()) { "at least one sweep required" }

        val irLen = (irLengthS * fs).toInt()
        val freqs = ResponseAnalysis.logFreqGrid(gridLoHz, gridHiHz, gridPerOctave)

        // Per-sweep drift estimates (same physical drift must be seen by all).
        val estimates = recordings.indices.map { i ->
            DriftEstimator.estimate(recordings[i], sweeps[i].sweep, irLen)
        }
        var spread = 0.0
        for (i in 1 until estimates.size) {
            spread = maxOf(spread, kotlin.math.abs(estimates[i].estimatedPpm - estimates[0].estimatedPpm))
        }
        val meanPpm = estimates.map { it.estimatedPpm }.average()
        val consistent = estimates.size >= 2 && spread <= ppmTolerance

        // Compensation direction proven in the prototype: resample the reference
        // sweep by the estimated drift, then deconvolve.
        val ir = if (kotlin.math.abs(meanPpm) > 0.05) {
            Deconvolver.deconvolve(recordings[0], MeasurementOps.applyDrift(sweeps[0].sweep, meanPpm))
        } else {
            Deconvolver.deconvolve(recordings[0], sweeps[0].sweep)
        }.copyOf(irLen)

        var tailSim = Double.NaN
        if (estimates.size >= 2) {
            val irB = if (kotlin.math.abs(estimates[1].estimatedPpm) > 0.05) {
                Deconvolver.deconvolve(recordings[1], MeasurementOps.applyDrift(sweeps[1].sweep, estimates[1].estimatedPpm))
            } else {
                Deconvolver.deconvolve(recordings[1], sweeps[1].sweep)
            }.copyOf(irLen)
            tailSim = DriftEstimator.tailSimilarity(ir, irB)
        }

        val drift = DriftCheck(
            estimatedPpm = meanPpm,
            compensatedSharpness = estimates[0].compensatedSharpness,
            consistent = consistent,
            ppmSpread = spread,
            tailSimilarity = tailSim,
            estimates = estimates.map { it.estimatedPpm },
        )

        val responseDb = ResponseAnalysis.irToResponseDb(ir, freqs, fs)
        val center = responseDb.meanWhere(BooleanArray(freqs.size) { freqs[it] in 200.0..2_000.0 })
        for (i in freqs.indices) responseDb[i] -= center

        val smoothed = ResponseAnalysis.smoothGaussianLogf(freqs, responseDb, ResponseAnalysis.smoothingWidthVariable(freqs))
        val level = ResponseAnalysis.autoTargetLevel(smoothed, freqs)
        val target = ResponseAnalysis.buildTarget(
            freqs, level,
            lfCutoffHz = 15.0, lfSlopeDbOct = 24.0,
            hfFallStartHz = 10_000.0, hfFallDbOct = 1.5,
        )
        val fit = AutoEqFitter.fitPeq(smoothed, target, freqs, fitConfig, fs)

        var invalidReason: String? = null
        when {
            estimates.size < 2 ->
                invalidReason = "single sweep cannot verify clock drift; a dual sweep is required"
            !consistent ->
                invalidReason = "clock-drift estimates disagree across sweeps (spread ${"%.2f".format(spread)} ppm > $ppmTolerance)"
            minTailSimilarity > 0.0 && tailSim < minTailSimilarity ->
                invalidReason = "compensated room tails disagree (similarity ${"%.3f".format(tailSim)} < $minTailSimilarity)"
            drift.compensatedSharpness < minSharpness ->
                invalidReason = "compensated IR sharpness ${"%.1f".format(drift.compensatedSharpness)} below gate $minSharpness"
        }

        return Measurement(
            ir = ir,
            responseDb = responseDb,
            smoothedDb = smoothed,
            freqs = freqs,
            targetDb = target,
            fit = fit,
            drift = drift,
            valid = invalidReason == null,
            invalidReason = invalidReason,
        )
    }

    private fun DoubleArray.meanWhere(mask: BooleanArray): Double {
        var sum = 0.0
        var n = 0
        for (i in indices) if (mask[i]) {
            sum += this[i]; n++
        }
        return sum / n
    }
}
