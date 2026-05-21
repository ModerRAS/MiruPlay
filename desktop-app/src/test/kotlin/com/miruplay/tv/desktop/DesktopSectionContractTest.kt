package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
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
    fun `desktop route rail chrome uses TV facing copy`() {
        assertEquals("电视式导航", desktopRouteRailSubtitle())
    }

    @Test
    fun `desktop library header keeps Android TV scan and settings actions only`() {
        assertEquals(
            listOf("扫描", "设置"),
            desktopLibraryHeaderActions().map { it.label },
        )
    }

    @Test
    fun `desktop library header focus moves like a TV action row`() {
        assertEquals(
            DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Settings),
            desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionRight),
        )
        assertEquals(
            DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Scan),
            desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Settings, Key.DirectionLeft),
        )
        assertEquals(
            DesktopLibraryHeaderFocusTarget.NextPanel,
            desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionDown),
        )
        assertNull(desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionLeft))
        assertNull(desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Settings, Key.DirectionRight))
        assertNull(desktopLibraryHeaderFocusTarget(DesktopLibraryHeaderAction.Scan, Key.DirectionUp))
    }

    @Test
    fun `desktop escape back follows Android TV route hierarchy`() {
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.player.desktopBackTarget())
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.details.desktopBackTarget())
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.settings.desktopBackTarget())
        assertNull(MiruPlayRouteSurface.library.desktopBackTarget())
    }

    @Test
    fun `desktop route rail navigation stops at TV list edges`() {
        assertNull(MiruPlayRouteSurface.library.stepDesktopSection(-1))
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.library.stepDesktopSection(1))
        assertEquals(MiruPlayRouteSurface.player, MiruPlayRouteSurface.details.stepDesktopSection(1))
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.player.stepDesktopSection(-1))
        assertEquals(MiruPlayRouteSurface.settings, MiruPlayRouteSurface.player.stepDesktopSection(1))
        assertNull(MiruPlayRouteSurface.settings.stepDesktopSection(1))
    }
}
