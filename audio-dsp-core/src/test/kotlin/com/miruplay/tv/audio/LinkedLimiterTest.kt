package com.miruplay.tv.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LinkedLimiterTest {
    @Test
    fun `linked limiter keeps every channel below ceiling`() {
        val limiter = LinkedLimiter(ceilingDb = -1f)
        val output = limiter.process(floatArrayOf(2f, 0.5f, -1.5f, 0.25f), channels = 2)
        val ceiling = 10.0.pow(-1.0 / 20.0).toFloat()

        assertTrue(output.maxOf { kotlin.math.abs(it) } <= ceiling + 1e-5f)
    }
}
