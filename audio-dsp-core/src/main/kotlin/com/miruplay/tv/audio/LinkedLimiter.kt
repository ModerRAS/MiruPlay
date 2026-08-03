package com.miruplay.tv.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

class LinkedLimiter(
    ceilingDb: Float = -1f,
    private val releaseMs: Float = 100f,
    private val sampleRateHz: Int = 48_000,
) {
    private val ceiling = 10.0.pow(ceilingDb.toDouble() / 20.0).toFloat().coerceIn(0.01f, 1f)
    private var gain = 1f

    fun process(interleaved: FloatArray, channels: Int): FloatArray {
        require(channels > 0) { "channels must be positive" }
        if (interleaved.isEmpty()) return interleaved.copyOf()
        val output = FloatArray(interleaved.size)
        val frames = interleaved.size / channels
        val releaseCoefficient = 1f - exp(
            -1f / (releaseMs.coerceAtLeast(1f) * 0.001f * sampleRateHz.coerceAtLeast(1)),
        )
        for (frame in 0 until frames) {
            val offset = frame * channels
            val peak = (0 until channels).maxOf { index -> abs(interleaved[offset + index]) }
            val target = if (peak > ceiling) ceiling / peak else 1f
            gain = if (target < gain) target else min(1f, gain + (target - gain) * releaseCoefficient)
            for (channel in 0 until channels) output[offset + channel] = interleaved[offset + channel] * gain
        }
        return output
    }
}
