package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAwareToneMappingPreferencesTest {
    @Test
    fun `default format-aware preferences include per-format rule sets`() {
        val preferences = FormatAwareToneMappingPreferences()

        assertEquals(PlaybackRenderBackend.STANDARD_EXO, preferences.defaultBackend)
        assertEquals(VideoRenderRuleKey.SDR, preferences.rules[VideoRenderRuleKey.SDR]?.ruleKey)
        assertEquals(VideoRenderRuleKey.HDR10, preferences.rules[VideoRenderRuleKey.HDR10]?.ruleKey)
        assertEquals(VideoRenderRuleKey.HDR10_PLUS, preferences.rules[VideoRenderRuleKey.HDR10_PLUS]?.ruleKey)
        assertEquals(VideoRenderRuleKey.DOLBY_VISION, preferences.rules[VideoRenderRuleKey.DOLBY_VISION]?.ruleKey)
        assertEquals(VideoRenderRuleKey.UNKNOWN_HDR, preferences.rules[VideoRenderRuleKey.UNKNOWN_HDR]?.ruleKey)
        assertFalse(preferences.rules.getValue(VideoRenderRuleKey.SDR).enabled)
        assertEquals(ToneMappingCurvePreset.MOBIUS, preferences.rules.getValue(VideoRenderRuleKey.HDR10).curvePreset)
        assertEquals(PeakDetectionStrategy.DYNAMIC, preferences.rules.getValue(VideoRenderRuleKey.HDR10).peakDetectionStrategy)
        assertEquals(PeakDetectionStrategy.DYNAMIC_AGGRESSIVE, preferences.rules.getValue(VideoRenderRuleKey.HDR10_PLUS).peakDetectionStrategy)
        assertEquals(FallbackBackendPolicy.FALLBACK_TO_STANDARD, preferences.rules.getValue(VideoRenderRuleKey.DOLBY_VISION).fallbackBackendPolicy)
    }

    @Test
    fun `video signal descriptor resolves rule key by format family`() {
        assertEquals(
            VideoRenderRuleKey.SDR,
            VideoSignalDescriptor(
                signalKind = VideoSignalKind.SDR,
                codecId = "avc1.640028",
            ).toRenderRuleKey(),
        )
        assertEquals(
            VideoRenderRuleKey.HDR10,
            VideoSignalDescriptor(
                signalKind = VideoSignalKind.HDR10,
                codecId = "hev1.2.4.L153",
            ).toRenderRuleKey(),
        )
        assertEquals(
            VideoRenderRuleKey.HDR10_PLUS,
            VideoSignalDescriptor(
                signalKind = VideoSignalKind.HDR10_PLUS,
                codecId = "hev1.2.4.L153",
            ).toRenderRuleKey(),
        )
        assertEquals(
            VideoRenderRuleKey.DOLBY_VISION,
            VideoSignalDescriptor(
                signalKind = VideoSignalKind.DOLBY_VISION,
                codecId = "dvhe.08.04",
                dolbyVisionProfile = DolbyVisionProfile.PROFILE_8_1,
                dolbyVisionLevel = "04",
            ).toRenderRuleKey(),
        )
        assertEquals(
            VideoRenderRuleKey.UNKNOWN_HDR,
            VideoSignalDescriptor(
                signalKind = VideoSignalKind.UNKNOWN_HDR,
                codecId = "unknown",
            ).toRenderRuleKey(),
        )
    }

    @Test
    fun `dolby vision display label prefers parsed profile variant`() {
        assertEquals(
            "Dolby Vision P8.1",
            VideoSignalDescriptor(
                signalKind = VideoSignalKind.DOLBY_VISION,
                codecId = "dvhe.08.04",
                dolbyVisionProfile = DolbyVisionProfile.PROFILE_8_1,
                dolbyVisionLevel = "04",
            ).displayLabel(),
        )
        assertEquals(
            "Dolby Vision P5",
            VideoSignalDescriptor(
                signalKind = VideoSignalKind.DOLBY_VISION,
                codecId = "dvh1.05.06",
                dolbyVisionProfile = DolbyVisionProfile.PROFILE_5,
                dolbyVisionLevel = "06",
            ).displayLabel(),
        )
    }

    @Test
    fun `dolby vision profile parser recognizes common profile strings`() {
        assertEquals(DolbyVisionProfile.PROFILE_5, DolbyVisionProfile.fromCodecString("dvh1.05.06"))
        assertEquals(DolbyVisionProfile.PROFILE_7, DolbyVisionProfile.fromCodecString("dvhe.07.06"))
        assertEquals(DolbyVisionProfile.PROFILE_8_1, DolbyVisionProfile.fromCodecString("dvhe.08.04"))
        assertEquals(DolbyVisionProfile.PROFILE_8_4, DolbyVisionProfile.fromCodecString("dvh1.08.07"))
        assertNull(DolbyVisionProfile.fromCodecString("hev1.2.4.L153"))
    }

    @Test
    fun `format-aware preferences fill missing rule entries with defaults`() {
        val preferences = FormatAwareToneMappingPreferences(
            rules = mapOf(
                VideoRenderRuleKey.SDR to ToneMappingRuleSet(
                    ruleKey = VideoRenderRuleKey.SDR,
                    enabled = true,
                    curvePreset = ToneMappingCurvePreset.PASSTHROUGH,
                    targetSdrNits = 100,
                    peakDetectionStrategy = PeakDetectionStrategy.DISABLED,
                    saturationRecovery = 0,
                    contrastRecovery = 0,
                    highlightCompression = 0,
                    fallbackBackendPolicy = FallbackBackendPolicy.KEEP_CURRENT,
                )
            )
        )

        val normalized = preferences.normalized()

        assertTrue(normalized.rules.containsKey(VideoRenderRuleKey.HDR10))
        assertTrue(normalized.rules.containsKey(VideoRenderRuleKey.HDR10_PLUS))
        assertTrue(normalized.rules.containsKey(VideoRenderRuleKey.DOLBY_VISION))
        assertTrue(normalized.rules.containsKey(VideoRenderRuleKey.UNKNOWN_HDR))
    }
}
