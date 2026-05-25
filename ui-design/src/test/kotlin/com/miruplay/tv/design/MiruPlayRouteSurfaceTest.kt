package com.miruplay.tv.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiruPlayRouteSurfaceTest {
    @Test
    fun `desktop route order keeps TV facing sections stable`() {
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
    fun `section lookup accepts automation ids defensively`() {
        assertEquals(null, MiruPlayRouteSurface.sectionForId(null))
        assertEquals(null, MiruPlayRouteSurface.sectionForId(""))
        assertEquals(null, MiruPlayRouteSurface.sectionForId("missing"))
        assertEquals(MiruPlayRouteSurface.player, MiruPlayRouteSurface.sectionForId("player"))
        assertEquals(MiruPlayRouteSurface.settings, MiruPlayRouteSurface.sectionForId(" SETTINGS "))
    }

    @Test
    fun `route rail stepping stops at TV list edges`() {
        assertNull(MiruPlayRouteSurface.desktopSectionStep(MiruPlayRouteSurface.library, -1))
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.desktopSectionStep(MiruPlayRouteSurface.library, 1))
        assertEquals(MiruPlayRouteSurface.player, MiruPlayRouteSurface.desktopSectionStep(MiruPlayRouteSurface.details, 1))
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.desktopSectionStep(MiruPlayRouteSurface.player, -1))
        assertEquals(MiruPlayRouteSurface.settings, MiruPlayRouteSurface.desktopSectionStep(MiruPlayRouteSurface.player, 1))
        assertNull(MiruPlayRouteSurface.desktopSectionStep(MiruPlayRouteSurface.settings, 1))
    }

    @Test
    fun `back targets encode shared player details library hierarchy`() {
        assertEquals(MiruPlayRouteSurface.details, MiruPlayRouteSurface.backTarget(MiruPlayRouteSurface.player))
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.backTarget(MiruPlayRouteSurface.details))
        assertEquals(MiruPlayRouteSurface.library, MiruPlayRouteSurface.backTarget(MiruPlayRouteSurface.settings))
        assertNull(MiruPlayRouteSurface.backTarget(MiruPlayRouteSurface.library))
    }
}
