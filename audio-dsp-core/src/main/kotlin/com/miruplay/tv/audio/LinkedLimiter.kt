package com.miruplay.tv.audio

import kotlin.math.abs
import kotlin.math.pow

class LinkedLimiter(
    ceilingDb: Float = -1f,
    private val releaseMs: Float = 100f,
) {
    private val ceiling = 10.0.pow(ceilingDb.toDouble() / 20.0).toFloat().coerceIn(0.01f, 1f)

    fun process(interleaved: FloatArray, channels: Int): FloatArray {
        require(channels > 0) { "channels must be positive" }
        if (interleaved.isEmpty()) return interleaved.copyOf()
        val peak = interleaved.maxOf { abs(it) }
        val gain = if (peak > ceiling) ceiling / peak else 1f
        return if (gain == 1f) interleaved.copyOf() else FloatArray(interleaved.size) { interleaved[it] * gain }
    }
}
