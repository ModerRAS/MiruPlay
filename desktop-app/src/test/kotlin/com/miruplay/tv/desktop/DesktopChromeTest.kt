package com.miruplay.tv.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopChromeTest {
    @Test
    fun `window title uses TV-facing desktop copy`() {
        assertEquals("MiruPlay 桌面版", desktopWindowTitle())
    }

    @Test
    fun `poster placeholder subtitle uses TV-facing runtime copy`() {
        assertEquals("内置播放运行时", desktopPosterPlaceholderSubtitle())
    }
}
