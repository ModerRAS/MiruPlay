package com.miruplay.tv.audio

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object LinearPhaseFirDesigner {
    fun design(targetMagnitudeDb: FloatArray, sampleRateHz: Int, taps: Int): FloatArray {
        require(taps > 1 && taps and (taps - 1) == 0) { "FIR taps must be a power of two" }
        require(targetMagnitudeDb.isNotEmpty()) { "FIR target cannot be empty" }
        // ponytail: try NEON FFT path (offline, one alloc), fallback to Kotlin FFT
        try {
            if (NativeDspBridge.isAvailable()) {
                NativeDspBridge.designFir(targetMagnitudeDb, taps)?.let { return it }
            }
        } catch (_: Throwable) {}
        // Kotlin FFT fallback (O(n log n), same result as native, no window)
        try {
            return designKotlinFft(targetMagnitudeDb, taps)
        } catch (_: Throwable) {}
        // legacy O(n²) as last resort
        return designLegacy(targetMagnitudeDb, taps)
    }

    // ponytail: O(n log n) Kotlin FFT, used when native not available (host tests)
    fun designKotlinFft(targetMagnitudeDb: FloatArray, taps: Int): FloatArray {
        val n = taps
        val center = (n - 1) / 2.0
        val half = n / 2
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        for (k in 0..half) {
            val mag = interpolatedMagnitude(targetMagnitudeDb, k.toDouble() / half)
            val phase = -2.0 * Math.PI * k * center / n
            val r = mag * cos(phase)
            val im = mag * sin(phase)
            real[k] = r; imag[k] = im
            if (k in 1 until half) {
                val mir = n - k
                real[mir] = r; imag[mir] = -im
            }
        }
        fftRadix2(real, imag, n, true)
        return FloatArray(n) { real[it].toFloat() }
    }

    fun designLegacy(targetMagnitudeDb: FloatArray, taps: Int): FloatArray {
        val center = (taps - 1) / 2.0
        val half = taps / 2
        val spectrumReal = DoubleArray(taps)
        val spectrumImag = DoubleArray(taps)
        for (k in 0..half) {
            val magnitude = interpolatedMagnitude(targetMagnitudeDb, k.toDouble() / half)
            val phase = -2.0 * Math.PI * k * center / taps
            spectrumReal[k] = magnitude * cos(phase)
            spectrumImag[k] = magnitude * sin(phase)
            if (k in 1 until half) {
                val mirror = taps - k
                spectrumReal[mirror] = spectrumReal[k]
                spectrumImag[mirror] = -spectrumImag[k]
            }
        }
        return FloatArray(taps) { n ->
            var value = 0.0
            for (k in 0 until taps) {
                val phase = 2.0 * Math.PI * k * n / taps
                value += spectrumReal[k] * cos(phase) - spectrumImag[k] * sin(phase)
            }
            (value / taps).toFloat()
        }
    }

    private fun fftRadix2(real: DoubleArray, imag: DoubleArray, n: Int, inverse: Boolean) {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * Math.PI / len * if (inverse) 1 else -1
            val wlenR = cos(ang); val wlenI = sin(ang)
            var i = 0
            while (i < n) {
                var wr = 1.0; var wi = 0.0
                for (k in 0 until len/2) {
                    val ur = real[i+k]; val ui = imag[i+k]
                    val vr = real[i+k+len/2] * wr - imag[i+k+len/2] * wi
                    val vi = real[i+k+len/2] * wi + imag[i+k+len/2] * wr
                    real[i+k] = ur + vr; imag[i+k] = ui + vi
                    real[i+k+len/2] = ur - vr; imag[i+k+len/2] = ui - vi
                    val nr = wr * wlenR - wi * wlenI
                    val ni = wr * wlenI + wi * wlenR
                    wr = nr; wi = ni
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { real[i] /= n; imag[i] /= n }
    }

    private fun interpolatedMagnitude(targetMagnitudeDb: FloatArray, normalizedFrequency: Double): Double {
        val position = normalizedFrequency.coerceIn(0.0, 1.0) * (targetMagnitudeDb.lastIndex)
        val lower = position.toInt().coerceIn(0, targetMagnitudeDb.lastIndex)
        val upper = (lower + 1).coerceAtMost(targetMagnitudeDb.lastIndex)
        val fraction = position - lower
        val db = targetMagnitudeDb[lower] + (targetMagnitudeDb[upper] - targetMagnitudeDb[lower]) * fraction
        return 10.0.pow(db.toDouble() / 20.0)
    }
}
