package com.miruplay.tv.webcontrol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlPlaybackStopTest {
    @Test
    fun `stop happens before close without a pause-only step and returns idle`() = runBlocking {
        val events = mutableListOf<String>()

        val status = stopWebControlPlayback(
            stopPlayback = { events += "stop" },
            closePlayer = {
                events += "close"
                true
            },
        )

        assertEquals(listOf("stop", "close"), events)
        assertFalse("pause" in events)
        assertEquals("Idle", status.state)
        assertFalse(status.isPlaying)
    }

    @Test
    fun `stop fails when player close cannot be queued`() = runBlocking {
        var stopped = false

        val error = runCatching {
            stopWebControlPlayback(
                stopPlayback = { stopped = true },
                closePlayer = { false },
            )
        }.exceptionOrNull()

        assertTrue(stopped)
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `repeated stop closes twice but releases native playback once`() = runBlocking {
        val events = mutableListOf<String>()
        var nativeReleased = false
        var hostDetached = false
        val stopPlayback = {
            if (!nativeReleased) {
                nativeReleased = true
                events += "native-release"
            }
        }
        val closePlayer = {
            if (!hostDetached) {
                hostDetached = true
                events += "host-detach"
            }
            true
        }

        repeat(2) {
            stopWebControlPlayback(stopPlayback, closePlayer)
        }

        assertEquals(listOf("native-release", "host-detach"), events)
    }
}
