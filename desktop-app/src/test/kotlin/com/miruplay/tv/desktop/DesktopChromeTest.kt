package com.miruplay.tv.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopChromeTest {
    @Test
    fun `poster placeholder subtitle uses TV-facing runtime copy`() {
        assertEquals("内置播放运行时", desktopPosterPlaceholderSubtitle())
    }
}
