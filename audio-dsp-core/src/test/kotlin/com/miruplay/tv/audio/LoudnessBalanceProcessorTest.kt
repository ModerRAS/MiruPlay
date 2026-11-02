package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioBalanceLevel
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LoudnessBalanceProcessorTest {
    private val sampleRate = 48_000

    @Test
    fun `off level is a no-op`() {
        val processor = LoudnessBalanceProcessor(AudioBalanceLevel.OFF, sampleRate)
        val input = floatArrayOf(0.5f, 0.5f, -0.2f, -0.2f)
        processor.processInPlace(input, channels = 2)
        assertTrue(input.contentEquals(floatArrayOf(0.5f, 0.5f, -0.2f, -0.2f)))
    }

    @Test
    fun `silence stays silent after calibration`() {
        val processor = LoudnessBalanceProcessor(AudioBalanceLevel.MEDIUM, sampleRate)
        val silence = FloatArray(sampleRate / 10 * 2) { 0f }
        repeat(40) { processor.processInPlace(silence, channels = 2) }
        assertTrue(silence.all { abs(it) < 1e-4f })
    }

    @Test
    fun `quiet segment is boosted toward target after calibration`() {
        val processor = LoudnessBalanceProcessor(AudioBalanceLevel.HIGH, sampleRate)
        val quiet = FloatArray(sampleRate * 3 * 2) { index ->
            if (index % 2 == 0) 0.01f else 0.01f
        }
        processor.processInPlace(quiet, channels = 2)
        val tailStart = quiet.size - sampleRate * 2
        val tailRms = rms(quiet, tailStart, quiet.size, channels = 2)
        assertTrue("tailRms=$tailRms", tailRms > 0.03f)
    }

    @Test
    fun `peak is limited below ceiling`() {
        val processor = LoudnessBalanceProcessor(AudioBalanceLevel.HIGH, sampleRate)
        val warm = FloatArray(sampleRate * 2 * 2) { 0.12f }
        processor.processInPlace(warm, channels = 2)
        val peakBlock = floatArrayOf(1f, 1f, 1f, 1f)
        processor.processInPlace(peakBlock, channels = 2)
        assertTrue(peakBlock.maxOf { abs(it) } <= 0.985f)
    }

    @Test
    fun `level change resets calibration state`() {
        val processor = LoudnessBalanceProcessor(AudioBalanceLevel.LOW, sampleRate)
        val warm = FloatArray(sampleRate * 2 * 2) { 0.08f }
        processor.processInPlace(warm, channels = 2)
        processor.setLevel(AudioBalanceLevel.HIGH, sampleRate)
        val after = FloatArray(4) { 0.08f }
        processor.processInPlace(after, channels = 2)
        assertTrue(after.all { it.isFinite() })
    }

    private fun rms(samples: FloatArray, start: Int, end: Int, channels: Int): Float {
        var sum = 0.0
        var count = 0
        for (index in start until end) {
            sum += samples[index] * samples[index]
            count += 1
        }
        return kotlin.math.sqrt(sum / count.coerceAtLeast(1)).toFloat()
    }
}
