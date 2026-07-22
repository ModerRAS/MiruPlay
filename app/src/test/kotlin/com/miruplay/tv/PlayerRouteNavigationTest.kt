package com.miruplay.tv

import com.miruplay.tv.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRouteNavigationTest {
    @Test
    fun `player route arguments keep canonical WebDAV escapes`() {
        val canonicalUri = "http://127.0.0.1:19798/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/Season%201/%5BANi%5D%2003.mp4"

        val source = playbackSourceFromPlayerRouteArguments(
            uri = canonicalUri,
            mediaSourceId = "anime",
            startPosition = 0L,
            episodeId = "1:/Show%20Name/03.mp4",
            progressId = "show#S1E3",
        )

        assertEquals(canonicalUri, source.uri)
        assertEquals("1:/Show%20Name/03.mp4", source.episodeId)
        assertEquals("show#S1E3", source.progressId)
    }

    @Test
    fun `player route should be replaced when already on player destination`() {
        assertTrue(shouldReplaceExistingPlayerRoute(NavRoutes.PLAYER_WITH_OPTIONS))
    }

    @Test
    fun `non-player route should not be replaced`() {
        assertFalse(shouldReplaceExistingPlayerRoute(NavRoutes.LIBRARY))
    }
}
