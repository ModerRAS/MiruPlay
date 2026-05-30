package com.miruplay.tv.navigation

import com.miruplay.tv.model.MediaPathConventions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavRoutesTest {
    @Test
    fun `animeDetail encodes punctuation and slash characters`() {
        val rawAnimeIds = listOf(
            "/ 我推的孩子",
            "#?/Season 01+v2",
            "%2F literal + plus"
        )

        rawAnimeIds.forEach { rawAnimeId ->
            val route = NavRoutes.animeDetail(rawAnimeId)

            assertTrue(route.startsWith("anime/"))
            assertEquals(rawAnimeId, 1, route.count { it == '/' })
            assertFalse(route.contains('#'))
            assertFalse(route.contains('?'))
            assertEquals(rawAnimeId, MediaPathConventions.decodePath(route.removePrefix("anime/")))
        }
    }
}
