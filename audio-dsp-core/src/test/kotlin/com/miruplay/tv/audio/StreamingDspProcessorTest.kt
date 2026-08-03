package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspLimiter
import com.miruplay.tv.model.AudioDspPhaseMode
import com.miruplay.tv.model.AudioDspPreset
import com.miruplay.tv.model.AudioDspChannelRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingDspProcessorTest {
    @Test
    fun `identity processing preserves interleaved channel count`() {
        val layout = ChannelLayout.from(2, null)
        val plan = AudioDspPlanCompiler.compile(AudioDspPreset("flat", "Flat"), layout, 48_000)
        val input = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f)

        val output = StreamingDspProcessor(plan).process(input, frameCount = 2)

        assertEquals(input.size, output.size)
        assertTrue(output.zip(input.toList()).all { (a, b) -> kotlin.math.abs(a - b) < 1e-6f })
    }

    @Test
    fun `linear phase processing flushes a complete common tail`() {
        val layout = ChannelLayout.from(2, null)
        val plan = AudioDspPlanCompiler.compile(
            AudioDspPreset("linear", "Linear", phaseMode = AudioDspPhaseMode.LINEAR),
            layout,
            48_000,
        )
        val processor = StreamingDspProcessor(plan)
        val output = processor.process(floatArrayOf(1f, 1f), frameCount = 1)
        val tail = processor.endOfStream()

        assertEquals(2, output.size)
        assertEquals((plan.firTapsByChannel.first().size - 1) * 2, tail.size)
    }

    @Test
    fun `plan replacement crossfades without an oversized sample step`() {
        val layout = ChannelLayout.from(2, null)
        val flat = AudioDspPlanCompiler.compile(AudioDspPreset("flat", "Flat"), layout, 48_000)
        val boosted = AudioDspPlanCompiler.compile(
            AudioDspPreset(
                "boosted",
                "Boosted",
                rules = listOf(
                    com.miruplay.tv.model.AudioDspChannelRule(
                        bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1_000f, 6f, 1f)),
                    ),
                ),
            ),
            layout,
            48_000,
        )
        val processor = StreamingDspProcessor(flat, crossfadeFrames = 8)
        processor.process(FloatArray(16), 8)
        processor.queuePlan(boosted)
        val output = processor.process(FloatArray(32) { 0.2f }, 16)

        assertTrue(output.toList().zipWithNext().maxOf { kotlin.math.abs(it.second - it.first) } < 0.2f)
    }

    @Test
    fun `preamp channel gain and limiter are applied to output`() {
        val plan = AudioDspPlanCompiler.compile(
            AudioDspPreset(
                "gain",
                "Gain",
                preampDb = 6f,
                rules = listOf(AudioDspChannelRule(outputGainDb = 6f)),
                limiter = AudioDspLimiter(enabled = true, ceilingDb = -6f),
            ),
            ChannelLayout.from(1, null),
            48_000,
        )

        val output = StreamingDspProcessor(plan).process(floatArrayOf(1f), 1)

        assertEquals(1, output.size)
        assertTrue(output[0] <= 0.51f)
        assertTrue(output[0] > 0.49f)
    }

    @Test
    fun `linear fir flush includes the complete convolution tail`() {
        val plan = AudioDspPlanCompiler.compile(
            AudioDspPreset("linear", "Linear", phaseMode = AudioDspPhaseMode.LINEAR),
            ChannelLayout.from(2, null),
            48_000,
        )
        val processor = StreamingDspProcessor(plan)
        processor.process(floatArrayOf(1f, 1f), 1)

        assertEquals((plan.firTapsByChannel.first().size - 1) * 2, processor.endOfStream().size)
    }
}
