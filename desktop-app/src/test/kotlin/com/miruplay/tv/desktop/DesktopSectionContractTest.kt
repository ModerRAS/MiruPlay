package com.miruplay.tv.desktop

import com.miruplay.tv.design.MiruPlayRouteSurface
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopSectionContractTest {
    @Test
    fun `desktop rail follows shared MiruPlay route surface`() {
        assertEquals(
            listOf(
                MiruPlayRouteSurface.LIBRARY_ID,
                MiruPlayRouteSurface.DETAILS_ID,
                MiruPlayRouteSurface.PLAYER_ID,
                MiruPlayRouteSurface.SETTINGS_ID,
            ),
            MiruPlayRouteSurface.desktopSectionOrder.map { it.id },
        )
        assertEquals(
            listOf("Library", "Details", "Player", "Settings"),
            MiruPlayRouteSurface.desktopSectionOrder.map { it.menuLabel },
        )
    }
}
