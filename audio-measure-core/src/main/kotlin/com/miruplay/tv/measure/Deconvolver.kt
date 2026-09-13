package com.miruplay.tv.measure

/**
 * Regularized frequency-domain deconvolution H = Y·conj(X) / (|X|² + ε).
 *
 * Never divide nakedly by |X|: the sweep has almost no energy outside
 * [f1, f2], so Y/X amplifies out-of-band noise by tens of dB.
 * The returned impulse response starts at index 0 with valid length
 * recorded.size - sweep.size + 1 (the time-reversed-inverse convolution
 * path instead puts the correlation peak at index N−1).
 */
object Deconvolver {
    fun deconvolve(recorded: DoubleArray, sweep: DoubleArray, regularization: Double = 1e-6): DoubleArray {
        val nFft = Fft.nextPow2(recorded.size + sweep.size)
        val (yRe, yIm) = Fft.rfft(recorded, nFft)
        return deconvolve(yRe, yIm, nFft, sweep, recorded.size - sweep.size + 1, regularization)
    }

    /**
     * Cached-spectrum variant: [yRe]/[yIm] is rfft(recorded) at [nFft].
     * Drift search re-deconvolves the SAME recording against many candidate
     * references; caching the recording's spectrum saves one 512k-point FFT
     * and ~8 MB of allocation per candidate (the dominant GC churn source).
     */
    fun deconvolve(
        yRe: DoubleArray,
        yIm: DoubleArray,
        nFft: Int,
        sweep: DoubleArray,
        outputLength: Int,
        regularization: Double = 1e-6,
    ): DoubleArray {
        val m = nFft / 2 + 1
        val (xRe, xIm) = Fft.rfft(sweep, nFft)

        var maxX2 = 0.0
        for (i in 0 until m) {
            val p = xRe[i] * xRe[i] + xIm[i] * xIm[i]
            if (p > maxX2) maxX2 = p
        }
        val eps = regularization * maxX2

        val hRe = DoubleArray(m)
        val hIm = DoubleArray(m)
        for (i in 0 until m) {
            val denom = xRe[i] * xRe[i] + xIm[i] * xIm[i] + eps
            val numRe = yRe[i] * xRe[i] + yIm[i] * xIm[i]
            val numIm = yIm[i] * xRe[i] - yRe[i] * xIm[i]
            hRe[i] = numRe / denom
            hIm[i] = numIm / denom
        }
        val full = Fft.irfft(hRe, hIm, nFft)
        return full.copyOf(minOf(outputLength, full.size))
    }
}
