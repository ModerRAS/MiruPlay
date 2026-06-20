package com.miruplay.tv.player

import com.miruplay.tv.model.PeakDetectionStrategy
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.defaultToneMappingRuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMpvSessionOptionsTest {
    @Test
    fun `disabled rule keeps tone mapping options unset`() {
        val options = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.SDR),
            shaderPaths = emptyList(),
        )

        assertEquals("gpu-next", options.vo)
        assertNull(options.toneMapping)
        assertNull(options.toneMappingParam)
        assertNull(options.hdrComputePeak)
        assertNull(options.hdrContrastRecovery)
        assertNull(options.saturation)
        assertNull(options.gamutMappingMode)
    }

    @Test
    fun `dynamic hdr10 rule maps to libplacebo dynamic controls`() {
        val options = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
                enabled = true,
                curvePreset = ToneMappingCurvePreset.MOBIUS,
                targetSdrNits = 120,
                peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC,
                contrastRecovery = 8,
                saturationRecovery = 10,
                highlightCompression = 18,
            ),
            shaderPaths = listOf("/tmp/test.glsl"),
            speed = 1.25f,
        )

        assertEquals("bt.709", options.targetPrim)
        assertEquals("bt.1886", options.targetTrc)
        assertEquals(120, options.hdrReferenceWhite)
        assertEquals("mobius", options.toneMapping)
        assertTrue((options.toneMappingParam ?: 0f) > 0.2f)
        assertEquals(true, options.hdrComputePeak)
        assertEquals(100f, options.hdrPeakPercentile)
        assertEquals(20f, options.hdrPeakDecayRate)
        assertEquals(1f, options.hdrSceneThresholdLow)
        assertEquals(3f, options.hdrSceneThresholdHigh)
        assertTrue((options.hdrContrastRecovery ?: 0f) > 0f)
        assertEquals(20f, options.saturation)
        assertEquals("perceptual", options.gamutMappingMode)
        assertEquals(listOf("/tmp/test.glsl"), options.shaderPaths)
        assertEquals("1.25", options.extraOptions.getValue("speed"))
    }

    @Test
    fun `aggressive dynamic rule lowers percentile and decay for faster adaptation`() {
        val options = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10_PLUS).copy(
                peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC_AGGRESSIVE,
            ),
            shaderPaths = emptyList(),
        )

        assertEquals(true, options.hdrComputePeak)
        assertEquals(99.9f, options.hdrPeakPercentile)
        assertEquals(12f, options.hdrPeakDecayRate)
        assertEquals(0.5f, options.hdrSceneThresholdLow)
        assertEquals(2f, options.hdrSceneThresholdHigh)
    }

    @Test
    fun `reinhard curve uses contrast style tone mapping param`() {
        val options = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.UNKNOWN_HDR).copy(
                curvePreset = ToneMappingCurvePreset.REINHARD,
                highlightCompression = 24,
            ),
            shaderPaths = emptyList(),
        )

        assertEquals("reinhard", options.toneMapping)
        assertTrue((options.toneMappingParam ?: 0f) in 0.25f..0.8f)
    }

    @Test
    fun `punchier saturation recovery still stays inside mpv equalizer range`() {
        val options = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
                saturationRecovery = 80,
            ),
            shaderPaths = emptyList(),
        )

        assertEquals(100f, options.saturation)
    }
}
