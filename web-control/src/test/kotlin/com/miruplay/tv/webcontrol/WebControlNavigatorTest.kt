package com.miruplay.tv.webcontrol

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlNavigatorTest {
    @Test
    fun `open player command survives until first collector attaches`() = runBlocking {
        val navigator = WebControlNavigator()
        val source = WebPlaybackSource(
            uri = "content://episode",
            mediaSourceId = "anime-1",
            startPositionMs = 0L,
            episodeId = "anime-1:episode-1",
        )

        assertTrue(navigator.openPlayer(source))

        val command = withTimeout(1_000) {
            navigator.commands.first()
        }

        assertEquals(WebControlNavigator.TYPE_OPEN_PLAYER, command.type)
        val payload = command.payload?.jsonObject ?: error("missing payload")
        assertEquals("content://episode", payload.getValue("uri").jsonPrimitive.content)
        assertEquals("anime-1", payload.getValue("mediaSourceId").jsonPrimitive.content)
        assertEquals("anime-1:episode-1", payload.getValue("episodeId").jsonPrimitive.content)
    }

    @Test
    fun `close player command survives until first collector attaches`() = runBlocking {
        val navigator = WebControlNavigator()

        assertTrue(navigator.closePlayer())

        val command = withTimeout(1_000) {
            navigator.commands.first()
        }

        assertEquals(WebControlNavigator.TYPE_CLOSE_PLAYER, command.type)
    }

    @Test
    fun `app restart command survives until first collector attaches`() = runBlocking {
        val navigator = WebControlNavigator()

        assertTrue(navigator.requestAppRestart())

        val command = withTimeout(1_000) {
            navigator.commands.first()
        }

        assertEquals(WebControlNavigator.TYPE_APP_RESTART, command.type)
    }
}
