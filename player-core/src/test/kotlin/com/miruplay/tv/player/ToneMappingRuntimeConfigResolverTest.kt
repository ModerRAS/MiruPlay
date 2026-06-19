package com.miruplay.tv.player

import com.miruplay.tv.model.FallbackBackendPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToneMappingRuntimeConfigResolverTest {
    @Test
    fun `resolver uses preference backend and default rule when no session override exists`() {
        val preferences = FormatAwareToneMappingPreferences(
            defaultBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
        )

        val config = resolveToneMappingRuntimeConfig(
            preferences = preferences,
            sessionRuleOverrides = emptyMap(),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
            requestedBackendOverride = null,
        )

        assertEquals(VideoRenderRuleKey.HDR10, config.ruleKey)
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, config.activeBackend)
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, config.requestedBackend)
        assertEquals(
            preferences.rules.getValue(VideoRenderRuleKey.HDR10),
            config.appliedRuleSet,
        )
        assertNull(config.fallbackReason)
    }

    @Test
    fun `resolver prefers session override over stored default`() {
        val preferences = FormatAwareToneMappingPreferences()
        val overrideRule = preferences.rules.getValue(VideoRenderRuleKey.HDR10).copy(
            targetSdrNits = 140,
        )

        val config = resolveToneMappingRuntimeConfig(
            preferences = preferences,
            sessionRuleOverrides = mapOf(VideoRenderRuleKey.HDR10 to overrideRule),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
            requestedBackendOverride = null,
        )

        assertEquals(140, config.appliedRuleSet.targetSdrNits)
    }

    @Test
    fun `resolver keeps libvlc backend active for dolby vision and only reports status`() {
        val preferences = FormatAwareToneMappingPreferences(
            defaultBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            rules = FormatAwareToneMappingPreferences().rules + (
                VideoRenderRuleKey.DOLBY_VISION to FormatAwareToneMappingPreferences()
                    .rules
                    .getValue(VideoRenderRuleKey.DOLBY_VISION)
                    .copy(fallbackBackendPolicy = FallbackBackendPolicy.FALLBACK_TO_STANDARD)
            ),
        )

        val config = resolveToneMappingRuntimeConfig(
            preferences = preferences,
            sessionRuleOverrides = emptyMap(),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.DOLBY_VISION),
            requestedBackendOverride = null,
        )

        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, config.requestedBackend)
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, config.activeBackend)
        assertEquals("Dolby Vision 正在使用 VLC 新后端播放。", config.fallbackReason)
    }

    @Test
    fun `resolver respects explicit backend override for current session`() {
        val preferences = FormatAwareToneMappingPreferences(
            defaultBackend = PlaybackRenderBackend.STANDARD_EXO,
        )

        val config = resolveToneMappingRuntimeConfig(
            preferences = preferences,
            sessionRuleOverrides = emptyMap(),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10_PLUS),
            requestedBackendOverride = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
        )

        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, config.requestedBackend)
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, config.activeBackend)
        assertEquals(VideoRenderRuleKey.HDR10_PLUS, config.ruleKey)
    }
}
