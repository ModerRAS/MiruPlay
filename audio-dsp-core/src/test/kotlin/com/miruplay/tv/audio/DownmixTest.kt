package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownmixTest {
    @Test
    fun `channel count fallback recognizes common surround layouts without an android mask`() {
        assertEquals(ChannelLayoutId.SURROUND_5_1, ChannelLayout.from(6, null).id)
        assertEquals(ChannelLayoutId.SURROUND_7_1, ChannelLayout.from(8, null).id)
    }

    @Test
    fun `mono downmix does not upmix the source`() {
        val plan = AudioDspPlanCompiler.compile(
            AudioDspPreset("mono", "Mono", outputMode = AudioDspOutputMode.STEREO_DOWNMIX),
            ChannelLayout.from(1, null),
            48_000,
        )

        assertEquals(1, plan.outputChannelCount)
        assertEquals(1, StreamingDspProcessor(plan).process(floatArrayOf(0.5f), 1).size)
    }

    @Test
    fun `standard downmix reduces 5 point 1 to stereo with bounded lfe`() {
        val plan = AudioDspPlanCompiler.compile(
            AudioDspPreset("downmix", "Downmix", outputMode = AudioDspOutputMode.STEREO_DOWNMIX),
            ChannelLayout.from(6, ChannelLayout.ANDROID_5_1_MASK),
            48_000,
        )
        val output = StreamingDspProcessor(plan).process(
            floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f),
            frameCount = 1,
        )

        assertEquals(2, output.size)
        assertTrue(output[0] > 0.9f)
        assertTrue(output[0] < 1.1f)
    }

    @Test
    fun `hrtf route always exposes two output channels`() {
        val plan = AudioDspPlanCompiler.compile(
            AudioDspPreset("hrtf", "HRTF", outputMode = AudioDspOutputMode.HRTF_BINAURAL),
            ChannelLayout.from(8, ChannelLayout.ANDROID_7_1_MASK),
            48_000,
        )

        assertEquals(2, plan.outputChannelCount)
        assertEquals(2, StreamingDspProcessor(plan).process(FloatArray(8), 1).size)
    }
}
