package com.miruplay.tv.audio

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object LinearPhaseFirDesigner {
    fun design(targetMagnitudeDb: FloatArray, sampleRateHz: Int, taps: Int): FloatArray {
        require(taps > 1 && taps and (taps - 1) == 0) { "FIR taps must be a power of two" }
        require(targetMagnitudeDb.isNotEmpty()) { "FIR target cannot be empty" }
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

    private fun interpolatedMagnitude(targetMagnitudeDb: FloatArray, normalizedFrequency: Double): Double {
        val position = normalizedFrequency.coerceIn(0.0, 1.0) * (targetMagnitudeDb.lastIndex)
        val lower = position.toInt().coerceIn(0, targetMagnitudeDb.lastIndex)
        val upper = (lower + 1).coerceAtMost(targetMagnitudeDb.lastIndex)
        val fraction = position - lower
        val db = targetMagnitudeDb[lower] + (targetMagnitudeDb[upper] - targetMagnitudeDb[lower]) * fraction
        return 10.0.pow(db.toDouble() / 20.0)
    }
}
