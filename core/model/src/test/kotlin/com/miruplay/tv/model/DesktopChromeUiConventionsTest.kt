package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopChromeUiConventionsTest {
    @Test
    fun `desktop shell labels are shared`() {
        assertEquals("MiruPlay 桌面版", desktopWindowTitleLabel())
        assertEquals("电视式导航", desktopRouteRailSubtitleLabel())
        assertEquals("内置播放运行时", desktopPosterPlaceholderSubtitleLabel())
    }
}
