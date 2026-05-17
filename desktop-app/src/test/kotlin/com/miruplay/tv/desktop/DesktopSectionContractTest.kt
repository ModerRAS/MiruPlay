package com.miruplay.tv.desktop

import com.miruplay.tv.design.MiruPlayRouteSurface
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopSectionContractTest {
    @Test
    fun `desktop rail follows shared MiruPlay route surface`() {
        assertEquals(
            MiruPlayRouteSurface.desktopSectionOrder.map { it.id },
            DesktopSection.entries.map { it.surface.id },
        )
        assertEquals(
            MiruPlayRouteSurface.desktopSectionOrder.map { it.menuLabel },
            DesktopSection.entries.map { it.menuLabel },
        )
    }
}
