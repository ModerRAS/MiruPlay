package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `desktop confirm keys include keyboard enter and TV DPAD center`() {
        assertTrue(isDesktopConfirmKey(Key.Enter))
        assertTrue(isDesktopConfirmKey(Key.NumPadEnter))
        assertTrue(isDesktopConfirmKey(Key.DirectionCenter))
        assertFalse(isDesktopConfirmKey(Key.DirectionLeft))
        assertFalse(isDesktopConfirmKey(Key.Back))
    }

    @Test
    fun `desktop confirm key event activates only on enabled key down`() {
        var clicks = 0
        var navigated = false

        assertTrue(
            desktopConfirmOrNavigationKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onClick = { clicks += 1 },
                onNavigationKey = {
                    navigated = true
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertFalse(navigated)

        assertFalse(
            desktopConfirmOrNavigationKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyUp,
                onClick = { clicks += 1 },
            ),
        )
        assertFalse(
            desktopConfirmOrNavigationKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                enabled = false,
                onClick = { clicks += 1 },
            ),
        )
        assertTrue(
            desktopConfirmOrNavigationKeyEvent(
                key = Key.DirectionRight,
                type = KeyEventType.KeyDown,
                onClick = { clicks += 1 },
                onNavigationKey = {
                    navigated = true
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertTrue(navigated)
    }

    @Test
    fun `desktop confirm key event ignores non-confirm keys without navigation fallback`() {
        var clicks = 0

        assertFalse(
            desktopConfirmOrNavigationKeyEvent(
                key = Key.DirectionRight,
                type = KeyEventType.KeyDown,
                onClick = { clicks += 1 },
            ),
        )
        assertEquals(0, clicks)
    }
}
