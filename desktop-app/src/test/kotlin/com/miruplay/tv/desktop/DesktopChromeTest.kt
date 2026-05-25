package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.model.desktopPosterPlaceholderSubtitleLabel
import com.miruplay.tv.model.desktopWindowTitleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopChromeTest {
    @Test
    fun `window title uses TV-facing desktop copy`() {
        assertEquals("MiruPlay 桌面版", desktopWindowTitleLabel())
    }

    @Test
    fun `poster placeholder subtitle uses TV-facing runtime copy`() {
        assertEquals("内置播放运行时", desktopPosterPlaceholderSubtitleLabel())
    }

    @Test
    fun `desktop confirm keys include keyboard enter and TV DPAD center`() {
        assertEquals(MiruPlayInputIntent.Activate, Key.Enter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, Key.NumPadEnter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, Key.DirectionCenter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, Key.Spacebar.toMiruPlayInputIntent())
        assertTrue(isDesktopConfirmKey(Key.Enter))
        assertTrue(isDesktopConfirmKey(Key.NumPadEnter))
        assertTrue(isDesktopConfirmKey(Key.DirectionCenter))
        assertTrue(isDesktopConfirmKey(Key.Spacebar))
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
    fun `desktop confirm intent event exposes shared navigation intents`() {
        var clicks = 0
        var navigatedIntent: MiruPlayInputIntent? = null

        assertTrue(
            desktopConfirmOrNavigationIntentEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onClick = { clicks += 1 },
                onNavigationIntent = { intent ->
                    navigatedIntent = intent
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertEquals(null, navigatedIntent)

        assertTrue(
            desktopConfirmOrNavigationIntentEvent(
                key = Key.DirectionRight,
                type = KeyEventType.KeyDown,
                onClick = { clicks += 1 },
                onNavigationIntent = { intent ->
                    navigatedIntent = intent
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertEquals(MiruPlayInputIntent.DirectionRight, navigatedIntent)

        assertFalse(
            desktopConfirmOrNavigationIntentEvent(
                key = Key.DirectionRight,
                type = KeyEventType.KeyUp,
                onClick = { clicks += 1 },
                onNavigationIntent = { intent ->
                    navigatedIntent = intent
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertEquals(MiruPlayInputIntent.DirectionRight, navigatedIntent)
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

    @Test
    fun `desktop toggle key event flips state on TV confirm keys`() {
        var nextValue: Boolean? = null

        assertTrue(
            desktopToggleKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                checked = true,
                onCheckedChange = { nextValue = it },
            ),
        )
        assertEquals(false, nextValue)

        assertTrue(
            desktopToggleKeyEvent(
                key = Key.Spacebar,
                type = KeyEventType.KeyDown,
                checked = false,
                onCheckedChange = { nextValue = it },
            ),
        )
        assertEquals(true, nextValue)

        assertFalse(
            desktopToggleKeyEvent(
                key = Key.DirectionLeft,
                type = KeyEventType.KeyDown,
                checked = false,
                onCheckedChange = { nextValue = it },
            ),
        )
        assertEquals(true, nextValue)
    }

    @Test
    fun `desktop picker key event opens on TV confirm keys`() {
        var opens = 0

        assertTrue(
            desktopOpenPickerKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onOpen = { opens += 1 },
            ),
        )
        assertEquals(1, opens)

        assertFalse(
            desktopOpenPickerKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyUp,
                onOpen = { opens += 1 },
            ),
        )
        assertFalse(
            desktopOpenPickerKeyEvent(
                key = Key.DirectionRight,
                type = KeyEventType.KeyDown,
                onOpen = { opens += 1 },
            ),
        )
        assertEquals(1, opens)
    }

    @Test
    fun `desktop selectable row key event shares confirm and navigation semantics`() {
        var clicks = 0
        var navigatedKey: Key? = null

        assertTrue(
            desktopSelectableRowKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onClick = { clicks += 1 },
                onNavigationKey = { key ->
                    navigatedKey = key
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertEquals(null, navigatedKey)

        assertTrue(
            desktopSelectableRowKeyEvent(
                key = Key.DirectionDown,
                type = KeyEventType.KeyDown,
                onClick = { clicks += 1 },
                onNavigationKey = { key ->
                    navigatedKey = key
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertEquals(Key.DirectionDown, navigatedKey)

        assertFalse(
            desktopSelectableRowKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyUp,
                onClick = { clicks += 1 },
                onNavigationKey = { key ->
                    navigatedKey = key
                    true
                },
            ),
        )
        assertEquals(1, clicks)
        assertEquals(Key.DirectionDown, navigatedKey)
    }

    @Test
    fun `desktop navigation key event handles only non-confirm key down`() {
        var navigatedKey: Key? = null

        assertTrue(
            desktopNavigationKeyEvent(
                key = Key.DirectionDown,
                type = KeyEventType.KeyDown,
                onNavigationKey = { key ->
                    navigatedKey = key
                    true
                },
            ),
        )
        assertEquals(Key.DirectionDown, navigatedKey)

        assertFalse(
            desktopNavigationKeyEvent(
                key = Key.DirectionDown,
                type = KeyEventType.KeyUp,
                onNavigationKey = { key ->
                    navigatedKey = key
                    true
                },
            ),
        )
        assertEquals(Key.DirectionDown, navigatedKey)

        assertFalse(
            desktopNavigationKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onNavigationKey = { key ->
                    navigatedKey = key
                    true
                },
            ),
        )
        assertEquals(Key.DirectionDown, navigatedKey)
    }

    @Test
    fun `desktop navigation intent event handles non-confirm mapped intents`() {
        var navigatedIntent: MiruPlayInputIntent? = null

        assertTrue(
            desktopNavigationIntentEvent(
                key = Key.DirectionDown,
                type = KeyEventType.KeyDown,
                onNavigationIntent = { intent ->
                    navigatedIntent = intent
                    true
                },
            ),
        )
        assertEquals(MiruPlayInputIntent.DirectionDown, navigatedIntent)

        assertFalse(
            desktopNavigationIntentEvent(
                key = Key.DirectionDown,
                type = KeyEventType.KeyUp,
                onNavigationIntent = { intent ->
                    navigatedIntent = intent
                    true
                },
            ),
        )
        assertEquals(MiruPlayInputIntent.DirectionDown, navigatedIntent)

        assertFalse(
            desktopNavigationIntentEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onNavigationIntent = { intent ->
                    navigatedIntent = intent
                    true
                },
            ),
        )
        assertEquals(MiruPlayInputIntent.DirectionDown, navigatedIntent)
    }
}
