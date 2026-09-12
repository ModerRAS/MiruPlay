package com.miruplay.tv.measure

import com.miruplay.tv.audio.AudioDspPlanCompiler
import com.miruplay.tv.audio.ChannelLayout
import com.miruplay.tv.audio.FrequencyResponse
import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Closes the Phase 4 loop in JVM: a measured preset must survive
 * AudioDspPlanCompiler + FrequencyResponse with the same curve the fitter
 * predicted — the same numbers the WebUI preview shows before applying.
 */
class MeasuredPresetPlanTest {

    @Test
    fun `measured preset compiles to a plan whose response matches the fitted curve`() {
        val bands = listOf(
            AudioDspBand(frequencyHz = 41.8f, gainDb = -8.75f, q = 5.6f),
            AudioDspBand(frequencyHz = 68.3f, gainDb = -5.48f, q = 8f),
            AudioDspBand(frequencyHz = 114.8f, gainDb = -3.42f, q = 8f),
        )
        val measured = MeasuredPresetFactory.MeasuredBands(bands, valid = true)
        val config = MeasuredPresetFactory.applyToConfig(
            AudioDspConfig.neutral(),
            measured,
            timestampMs = 1_000L,
            target = AudioDspChannelTarget.ALL,
        )
        val preset = config.presets.first { it.id == "measured-1000" }

        val fs = 48_000
        val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(2, null), fs)
        val probeFrequencies = floatArrayOf(41.8f, 68.3f, 114.8f, 1_000f)
        val curve = FrequencyResponse.sample(plan, probeFrequencies)

        // Expected = the fitted correction curve itself: the sum of the three
        // band responses (what the fitter optimized and the WebUI preview shows).
        val freqsDouble = probeFrequencies.map { it.toDouble() }.toDoubleArray()
        for (i in freqsDouble.indices) {
            var expectedDb = 0.0
            for (band in preset.rules.first().bands) {
                expectedDb += 20.0 * kotlin.math.log10(
                    com.miruplay.tv.audio.BiquadDesigner.design(band, fs)
                        .magnitudeAt(freqsDouble[i], fs.toDouble()),
                )
            }
            assertEquals(expectedDb.toFloat(), curve.magnitudeDb[i], 0.02f)
        }
        // Cut-only: the plan's channel gain must stay neutral.
        assertEquals(1.0f, plan.channelGainLinear[0], 1e-6f)
        assertTrue(plan.biquadsByChannel.first().isNotEmpty())
    }
}
