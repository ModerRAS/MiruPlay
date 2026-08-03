package com.miruplay.tv.audio

import kotlin.math.log10

data class ResponseCurve(
    val frequenciesHz: FloatArray,
    val magnitudeDb: FloatArray,
    val phaseRadians: FloatArray,
)

object FrequencyResponse {
    fun sample(plan: CompiledDspPlan, frequenciesHz: FloatArray): ResponseCurve {
        val magnitude = FloatArray(frequenciesHz.size)
        val phase = FloatArray(frequenciesHz.size)
        val chains = plan.biquadsByChannel.firstOrNull().orEmpty()
        for (index in frequenciesHz.indices) {
            var gain = 1.0
            chains.forEach { gain *= it.magnitudeAt(frequenciesHz[index].toDouble(), plan.sampleRateHz.toDouble()) }
            magnitude[index] = (20.0 * log10(gain.coerceAtLeast(1e-12))).toFloat()
        }
        return ResponseCurve(frequenciesHz.copyOf(), magnitude, phase)
    }
}
