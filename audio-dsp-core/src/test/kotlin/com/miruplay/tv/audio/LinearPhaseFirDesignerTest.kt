package com.miruplay.tv.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspPhaseMode

class LinearPhaseFirDesignerTest {
    @Test
    fun `fir is symmetric and has a centered impulse`() {
        val taps = LinearPhaseFirDesigner.design(
            targetMagnitudeDb = FloatArray(256),
            sampleRateHz = 48_000,
            taps = 256,
        )

        for (i in taps.indices) assertEquals(taps[i], taps[taps.lastIndex - i], 1e-6f)
        val peak = taps.indices.maxBy { kotlin.math.abs(taps[it]) }
        assertTrue(peak == 127 || peak == 128)
    }

    @Test
    fun `compiler uses one common tap count for every channel`() {
        val preset = com.miruplay.tv.model.AudioDspPreset(
            id = "linear",
            name = "Linear",
            phaseMode = com.miruplay.tv.model.AudioDspPhaseMode.LINEAR,
        )
        val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(2, null), 48_000)

        assertEquals(2, plan.firTapsByChannel.size)
        assertEquals(plan.firTapsByChannel[0].toList(), plan.firTapsByChannel[1].toList())
    }

    @Test
    fun `linear phase plan does not retain the minimum phase biquad chain`() {
        val preset = com.miruplay.tv.model.AudioDspPreset(
            id = "linear-peq",
            name = "Linear PEQ",
            phaseMode = AudioDspPhaseMode.LINEAR,
            rules = listOf(
                com.miruplay.tv.model.AudioDspChannelRule(
                    bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1_000f, 6f, 1f)),
                ),
            ),
        )

        val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(2, null), 48_000)

        assertTrue(plan.biquadsByChannel.all { it.isEmpty() })
    }

    @Test
    fun `linear phase preview reports the baked fir response`() {
        val preset = com.miruplay.tv.model.AudioDspPreset(
            id = "linear-peq",
            name = "Linear PEQ",
            phaseMode = AudioDspPhaseMode.LINEAR,
            rules = listOf(
                com.miruplay.tv.model.AudioDspChannelRule(
                    bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1_000f, 6f, 1f)),
                ),
            ),
        )

        val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(2, null), 48_000)
        val curve = FrequencyResponse.sample(plan, floatArrayOf(1_000f))

        assertTrue("magnitude=${curve.magnitudeDb.single()}", curve.magnitudeDb.single() > 1f)
        assertTrue(kotlin.math.abs(curve.phaseRadians.single()) > 0.01f)
    }
}
