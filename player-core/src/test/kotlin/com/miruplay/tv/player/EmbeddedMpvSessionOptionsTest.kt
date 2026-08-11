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
        assertEquals("mediacodec,mediacodec-copy", options.hwdec)
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
            runtimeAbiIs32Bit = false,
        )

        assertEquals("bt.709", options.targetPrim)
        assertEquals("bt.1886", options.targetTrc)
        assertEquals(120, options.targetPeak)
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
        assertEquals("no", options.extraOptions.getValue("sub-auto"))
    }

    @Test
    fun `aggressive dynamic rule lowers percentile and decay for faster adaptation`() {
        val options = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10_PLUS).copy(
                peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC_AGGRESSIVE,
            ),
            shaderPaths = emptyList(),
            runtimeAbiIs32Bit = false,
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
            runtimeAbiIs32Bit = false,
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
            runtimeAbiIs32Bit = false,
        )

        assertEquals(100f, options.saturation)
    }

    @Test
    fun `32-bit runtime downgrades dynamic peak detection for embedded mpv`() {
        val dynamic = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
                peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC,
            ),
            shaderPaths = emptyList(),
            runtimeAbiIs32Bit = true,
        )
        val aggressive = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10_PLUS).copy(
                peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC_AGGRESSIVE,
            ),
            shaderPaths = emptyList(),
            runtimeAbiIs32Bit = true,
        )

        assertEquals("gpu-hq", dynamic.vo)
        assertEquals(false, dynamic.hdrComputePeak)
        assertEquals("clip", dynamic.gamutMappingMode)
        assertEquals(false, dynamic.deband)
        assertEquals("gpu-hq", aggressive.vo)
        assertEquals(true, aggressive.hdrComputePeak)
        assertEquals("clip", aggressive.gamutMappingMode)
        assertEquals(false, aggressive.deband)
        assertEquals(100f, aggressive.hdrPeakPercentile)
        assertEquals(20f, aggressive.hdrPeakDecayRate)
        assertEquals(1f, aggressive.hdrSceneThresholdLow)
        assertEquals(3f, aggressive.hdrSceneThresholdHigh)
    }

    @Test
    fun `debug config can override embedded mpv baseline vo and hwdec`() {
        val options = buildEmbeddedMpvSessionOptions(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            shaderPaths = emptyList(),
            runtimeAbiIs32Bit = true,
            debugConfig = EmbeddedMpvDebugConfig(
                vo = "gpu-hq",
                hwdec = "no",
            ),
        )

        assertEquals("gpu-hq", options.vo)
        assertEquals("no", options.hwdec)
    }

    @Test
    fun `gles version parser falls back to es2 for missing capability`() {
        assertEquals(2, resolveGlEsMajorVersion(0))
        assertEquals(2, resolveGlEsMajorVersion(-1))
        assertEquals(3, resolveGlEsMajorVersion(0x00030000))
    }

    @Test
    fun `gles2 devices use direct mediacodec output regardless of runtime abi`() {
        assertEquals(
            "mediacodec_embed",
            effectiveEmbeddedMpvVideoOutput(
                runtimeAbiIs32Bit = false,
                glEsMajorVersion = 2,
            ),
        )
        assertEquals(
            "mediacodec_embed",
            effectiveEmbeddedMpvVideoOutput(
                runtimeAbiIs32Bit = true,
                glEsMajorVersion = 2,
            ),
        )
    }
}
