package com.miruplay.tv

import com.miruplay.tv.navigation.NavRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRouteNavigationTest {
    @Test
    fun `player route should be replaced when already on player destination`() {
        assertTrue(shouldReplaceExistingPlayerRoute(NavRoutes.PLAYER_WITH_OPTIONS))
    }

    @Test
    fun `non-player route should not be replaced`() {
        assertFalse(shouldReplaceExistingPlayerRoute(NavRoutes.LIBRARY))
    }
}
