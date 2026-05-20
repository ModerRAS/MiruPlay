package com.miruplay.tv.desktop

import com.miruplay.tv.design.MiruPlayRouteSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopSectionContractTest {
    @Test
    fun `desktop rail follows shared TV route surface`() {
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
            listOf("探索", "详情", "播放", "设置"),
            MiruPlayRouteSurface.desktopSectionOrder.map { it.menuLabel },
        )
        assertEquals(
            listOf("探索", "详情", "播放", "设置"),
            MiruPlayRouteSurface.desktopSectionOrder.map { it.title },
        )
    }

    @Test
    fun `desktop library header keeps Android TV scan and settings actions only`() {
        assertEquals(
            listOf("扫描", "设置"),
            desktopLibraryHeaderActions().map { it.label },
        )
    }

    @Test
    fun `desktop escape back follows Android TV route hierarchy`() {
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.player.desktopBackTarget())
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.details.desktopBackTarget())
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.settings.desktopBackTarget())
        assertNull(MiruPlayRouteSurface.library.desktopBackTarget())
    }
}
