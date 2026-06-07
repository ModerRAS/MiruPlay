package com.miruplay.tv.navigation

import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModeSelectionState
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

    @Test
    fun `app mode routes expose mode selection and drama home destinations`() {
        assertEquals("mode-selection", NavRoutes.MODE_SELECTION)
        assertEquals("drama-home", NavRoutes.DRAMA_HOME)
    }

    @Test
    fun `dramaDetail encodes punctuation and slash characters`() {
        val rawSeriesId = "剧集/Season 01?# +plus"

        val route = NavRoutes.dramaDetail(rawSeriesId)

        assertTrue(route.startsWith("drama/"))
        assertEquals(rawSeriesId, 1, route.count { it == '/' })
        assertFalse(route.contains('#'))
        assertFalse(route.contains('?'))
        assertEquals(rawSeriesId, MediaPathConventions.decodePath(route.removePrefix("drama/")))
    }

    @Test
    fun `home route returns anime library for anime mode and drama home for drama mode`() {
        assertEquals(NavRoutes.LIBRARY, NavRoutes.homeFor(AppMode.ANIME))
        assertEquals(NavRoutes.DRAMA_HOME, NavRoutes.homeFor(AppMode.DRAMA))
    }

    @Test
    fun `launch destination falls back to mode selection until onboarding is complete`() {
        val incompleteAnimeState = AppModeSelectionState(
            currentAppMode = AppMode.ANIME,
            hasCompletedModeSelection = false,
        )
        val missingModeState = AppModeSelectionState(
            currentAppMode = null,
            hasCompletedModeSelection = false,
        )

        assertEquals(NavRoutes.MODE_SELECTION, NavRoutes.launchDestinationFor(incompleteAnimeState))
        assertEquals(NavRoutes.MODE_SELECTION, NavRoutes.launchDestinationFor(missingModeState))
    }

    @Test
    fun `launch destination uses current mode home after onboarding completes`() {
        val animeState = AppModeSelectionState(
            currentAppMode = AppMode.ANIME,
            hasCompletedModeSelection = true,
        )
        val dramaState = AppModeSelectionState(
            currentAppMode = AppMode.DRAMA,
            hasCompletedModeSelection = true,
        )

        assertEquals(NavRoutes.LIBRARY, NavRoutes.launchDestinationFor(animeState))
        assertEquals(NavRoutes.DRAMA_HOME, NavRoutes.launchDestinationFor(dramaState))
    }
}
