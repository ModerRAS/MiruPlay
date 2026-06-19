package com.miruplay.tv.player

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.defaultToneMappingRuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionStateTest {
    @Test
    fun `internal rebuild keeps backend override and format rule override for current session`() {
        val hdr10Override = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
            targetSdrNits = 140,
        )

        val sessionState = PlaybackSessionState()
            .withRequestedBackendOverride(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC)
            .withRuleOverride(VideoRenderRuleKey.HDR10, hdr10Override)

        assertEquals(
            PlaybackRenderBackend.STANDARD_EXO,
            sessionState.effectiveRequestedBackend(PlaybackRenderBackend.STANDARD_EXO),
        )
        assertEquals(
            hdr10Override,
            sessionState.ruleOverrides[VideoRenderRuleKey.HDR10],
        )
    }

    @Test
    fun `clearRuleOverrides keeps backend override for the same playback session`() {
        val sessionState = PlaybackSessionState()
            .withRequestedBackendOverride(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC)
            .withRuleOverride(
                VideoRenderRuleKey.HDR10,
                defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            )
            .clearRuleOverrides()

        assertEquals(
            PlaybackRenderBackend.STANDARD_EXO,
            sessionState.effectiveRequestedBackend(PlaybackRenderBackend.STANDARD_EXO),
        )
        assertEquals(emptyMap<VideoRenderRuleKey, Nothing>(), sessionState.ruleOverrides)
    }

    @Test
    fun `clearForStop resets session-only backend and rule overrides`() {
        val sessionState = PlaybackSessionState()
            .withRequestedBackendOverride(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC)
            .withRuleOverride(
                VideoRenderRuleKey.HDR10,
                defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            )
            .afterPlaybackReset(clearSessionState = true)

        assertEquals(
            PlaybackRenderBackend.STANDARD_EXO,
            sessionState.effectiveRequestedBackend(PlaybackRenderBackend.STANDARD_EXO),
        )
        assertNull(sessionState.requestedBackendOverride)
        assertEquals(emptyMap<VideoRenderRuleKey, Nothing>(), sessionState.ruleOverrides)
    }

    @Test
    fun `internal playback rebuild keeps session-only overrides`() {
        val hdr10Override = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
            saturationRecovery = 20,
        )

        val sessionState = PlaybackSessionState()
            .withRequestedBackendOverride(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC)
            .withRuleOverride(VideoRenderRuleKey.HDR10, hdr10Override)
            .afterPlaybackReset(clearSessionState = false)

        assertEquals(
            PlaybackRenderBackend.STANDARD_EXO,
            sessionState.effectiveRequestedBackend(PlaybackRenderBackend.STANDARD_EXO),
        )
        assertEquals(hdr10Override, sessionState.ruleOverrides[VideoRenderRuleKey.HDR10])
    }
}
