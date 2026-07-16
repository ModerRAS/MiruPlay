package com.miruplay.tv.webcontrol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlPlaybackStopTest {
    @Test
    fun `stop pauses before requesting player close and returns idle`() = runBlocking {
        val events = mutableListOf<String>()

        val status = stopWebControlPlayback(
            pausePlayback = { events += "pause" },
            closePlayer = {
                events += "close"
                true
            },
        )

        assertEquals(listOf("pause", "close"), events)
        assertEquals("Idle", status.state)
        assertFalse(status.isPlaying)
    }

    @Test
    fun `stop fails when player close cannot be queued`() = runBlocking {
        var paused = false

        val error = runCatching {
            stopWebControlPlayback(
                pausePlayback = { paused = true },
                closePlayer = { false },
            )
        }.exceptionOrNull()

        assertTrue(paused)
        assertTrue(error is IllegalStateException)
    }
}
