package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownmixTest {
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
