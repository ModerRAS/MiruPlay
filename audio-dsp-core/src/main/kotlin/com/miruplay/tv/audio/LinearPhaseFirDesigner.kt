package com.miruplay.tv.audio

import kotlin.math.cos
import kotlin.math.pow

object LinearPhaseFirDesigner {
    fun design(targetMagnitudeDb: FloatArray, sampleRateHz: Int, taps: Int): FloatArray {
        require(taps > 1 && taps and (taps - 1) == 0) { "FIR taps must be a power of two" }
        require(targetMagnitudeDb.isNotEmpty()) { "FIR target cannot be empty" }
        val bins = targetMagnitudeDb.size
        val center = (taps - 1) / 2.0
        val coefficients = FloatArray(taps)
        for (n in 0 until taps) {
            var sum = 0.0
            for (k in 0 until bins) {
                val magnitude = 10.0.pow(targetMagnitudeDb[k].toDouble() / 20.0)
                val frequency = Math.PI * k / (bins - 1).coerceAtLeast(1)
                sum += magnitude * cos(frequency * (n - center))
            }
            val window = 0.5 - 0.5 * cos(2.0 * Math.PI * n / (taps - 1))
            coefficients[n] = (sum / bins * window).toFloat()
        }
        val dcSum = coefficients.sum().toDouble()
        val dc = if (kotlin.math.abs(dcSum) > 1e-8) dcSum else 1.0
        return coefficients.map { (it.toDouble() / dc).toFloat() }.toFloatArray()
    }
}
