package com.miruplay.tv.webcontrol

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
}
