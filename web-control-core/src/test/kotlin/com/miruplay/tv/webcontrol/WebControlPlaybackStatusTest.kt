package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlPlaybackStatusTest {
    @Test
    fun `idle playback status uses WebUI idle defaults`() {
        val status = idleWebControlPlaybackStatus()

        assertEquals("Idle", status.state)
        assertNull(status.uri)
        assertNull(status.mediaSourceId)
        assertEquals(0L, status.positionMs)
        assertEquals(0L, status.durationMs)
        assertFalse(status.isPlaying)
        assertNull(status.error)
    }

    @Test
    fun `playback status normalizes blank fields and negative positions`() {
        val status = webControlPlaybackStatus(
            state = "",
            uri = "",
            mediaSourceId = " ",
            positionMs = -1L,
            durationMs = -2L,
            isPlaying = true,
            error = "",
        )

        assertEquals("Idle", status.state)
        assertNull(status.uri)
        assertNull(status.mediaSourceId)
        assertEquals(0L, status.positionMs)
        assertEquals(0L, status.durationMs)
        assertTrue(status.isPlaying)
        assertNull(status.error)
    }

    @Test
    fun `episode id source prefix maps to media source id`() {
        assertEquals("7", "7:/Anime/Episode.mkv".webControlMediaSourceIdFromEpisodeId())
        assertNull("standalone-episode".webControlMediaSourceIdFromEpisodeId())
        assertNull(":missing-source".webControlMediaSourceIdFromEpisodeId())
    }

    @Test
    fun `playback state maps source position duration and playing flag`() {
        val source = PlaybackSource(uri = "content://episode", mediaSourceId = "anime-1")

        val status = PlaybackState.Playing(source = source, position = 42_000L)
            .toWebControlPlaybackStatus(currentPositionMs = 1_000L, durationMs = 120_000L)

        assertEquals("Playing", status.state)
        assertEquals("content://episode", status.uri)
        assertEquals("anime-1", status.mediaSourceId)
        assertEquals(42_000L, status.positionMs)
        assertEquals(120_000L, status.durationMs)
        assertTrue(status.isPlaying)
        assertNull(status.error)
    }

    @Test
    fun `playback state uses queried position for states without embedded position`() {
        val source = PlaybackSource(uri = "content://episode", mediaSourceId = "anime-1")

        val loading = PlaybackState.Loading(source)
            .toWebControlPlaybackStatus(currentPositionMs = 7_000L, durationMs = 120_000L)
        val error = PlaybackState.Error(source = source, error = "boom")
            .toWebControlPlaybackStatus(currentPositionMs = 9_000L, durationMs = 120_000L)

        assertEquals("Loading", loading.state)
        assertEquals(7_000L, loading.positionMs)
        assertFalse(loading.isPlaying)
        assertEquals("Error", error.state)
        assertEquals(9_000L, error.positionMs)
        assertEquals("boom", error.error)
    }
}
