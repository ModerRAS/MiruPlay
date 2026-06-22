package com.miruplay.tv.player

import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import `is`.xyz.mpv.MiruMpvSurfaceView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMpvStartupStateTest {
    private val source = PlaybackSource(uri = "content://episode", mediaSourceId = "anime-1")

    @Test
    fun `startup snapshot at zero stays buffering before real playback begins`() {
        val resolved = resolveEmbeddedMpvStartupState(
            source = source,
            snapshot = MiruMpvSurfaceView.StateSnapshot(positionMs = 0L, durationMs = 0L),
            wasPlaying = false,
        )

        assertFalse(resolved.isPlaying)
        assertEquals(PlaybackState.Buffering(source, 0L), resolved.playbackState)
    }

    @Test
    fun `positive position snapshot promotes embedded mpv to playing`() {
        val resolved = resolveEmbeddedMpvStartupState(
            source = source,
            snapshot = MiruMpvSurfaceView.StateSnapshot(positionMs = 1L, durationMs = 0L),
            wasPlaying = false,
        )

        assertTrue(resolved.isPlaying)
        assertEquals(PlaybackState.Playing(source, 1L), resolved.playbackState)
    }

    @Test
    fun `once playback has restarted zero position remains playing`() {
        val resolved = resolveEmbeddedMpvStartupState(
            source = source,
            snapshot = MiruMpvSurfaceView.StateSnapshot(positionMs = 0L, durationMs = 0L),
            wasPlaying = true,
        )

        assertTrue(resolved.isPlaying)
        assertEquals(PlaybackState.Playing(source, 0L), resolved.playbackState)
    }

    @Test
    fun `position-only embedded mpv changes do not publish playback state`() {
        assertFalse(
            shouldPublishEmbeddedMpvStateChange(
                current = PlaybackState.Playing(source, 1_000L),
                next = PlaybackState.Playing(source, 2_000L),
            ),
        )
    }

    @Test
    fun `embedded mpv publishes state kind changes`() {
        assertTrue(
            shouldPublishEmbeddedMpvStateChange(
                current = PlaybackState.Buffering(source, 0L),
                next = PlaybackState.Playing(source, 1L),
            ),
        )
    }
}
