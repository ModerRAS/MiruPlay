package com.miruplay.tv.audio

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

data class ResponseCurve(
    val frequenciesHz: FloatArray,
    val magnitudeDb: FloatArray,
    val phaseRadians: FloatArray,
)

object FrequencyResponse {
    fun sample(plan: CompiledDspPlan, frequenciesHz: FloatArray): ResponseCurve {
        val magnitude = FloatArray(frequenciesHz.size)
        val phase = FloatArray(frequenciesHz.size)
        val channel = 0
        val chains = plan.biquadsByChannel.getOrNull(channel) ?: emptyList()
        val fir = plan.firTapsByChannel.getOrNull(channel) ?: FloatArray(0)
        val staticGain = plan.preampLinear * plan.channelGainLinear.getOrElse(channel) { 1f }
        for (index in frequenciesHz.indices) {
            val frequency = frequenciesHz[index].toDouble()
            val omega = 2.0 * Math.PI * frequency / plan.sampleRateHz
            var gain = staticGain.toDouble()
            var phaseRadians = 0.0
            if (fir.isNotEmpty()) {
                var real = 0.0
                var imag = 0.0
                fir.forEachIndexed { tap, coefficient ->
                    real += coefficient * cos(omega * tap)
                    imag -= coefficient * sin(omega * tap)
                }
                gain *= sqrt(real * real + imag * imag)
                phaseRadians = atan2(imag, real)
            } else {
                chains.forEach { biquad ->
                    gain *= biquad.magnitudeAt(frequency.coerceAtLeast(1.0), plan.sampleRateHz.toDouble())
                }
            }
            magnitude[index] = (20.0 * log10(gain.coerceAtLeast(1e-12))).toFloat()
            phase[index] = phaseRadians.toFloat()
        }
        return ResponseCurve(frequenciesHz.copyOf(), magnitude, phase)
    }
}
