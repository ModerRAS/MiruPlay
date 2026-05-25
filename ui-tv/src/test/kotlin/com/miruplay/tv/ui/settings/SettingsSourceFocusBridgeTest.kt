package com.miruplay.tv.ui.settings

import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.design.MiruPlayInputIntent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSourceFocusBridgeTest {
    @Test
    fun `source list left key requests menu focus`() {
        var focusedMenu = false

        val handled = settingsSourceListMenuBridgeIntent(
            intent = MiruPlayInputIntent.DirectionLeft,
            type = KeyEventType.KeyDown,
            onFocusMenu = { focusedMenu = true },
        )

        assertTrue(handled)
        assertTrue(focusedMenu)
    }

    @Test
    fun `source list bridge ignores key up and non-left keys`() {
        var focusedMenu = false

        assertFalse(
            settingsSourceListMenuBridgeIntent(
                intent = MiruPlayInputIntent.DirectionLeft,
                type = KeyEventType.KeyUp,
                onFocusMenu = { focusedMenu = true },
            ),
        )
        assertFalse(
            settingsSourceListMenuBridgeIntent(
                intent = MiruPlayInputIntent.DirectionRight,
                type = KeyEventType.KeyDown,
                onFocusMenu = { focusedMenu = true },
            ),
        )
        assertFalse(
            settingsSourceListMenuBridgeIntent(
                intent = MiruPlayInputIntent.Activate,
                type = KeyEventType.KeyDown,
                onFocusMenu = { focusedMenu = true },
            ),
        )
        assertFalse(focusedMenu)
    }
}
