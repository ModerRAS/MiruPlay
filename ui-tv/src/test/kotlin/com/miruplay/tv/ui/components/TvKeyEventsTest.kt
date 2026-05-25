package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.design.MiruPlayInputIntent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvKeyEventsTest {
    @Test
    fun `TV activate helper follows shared activation intent semantics`() {
        assertTrue(isTvActivateIntent(MiruPlayInputIntent.Activate))
        assertFalse(isTvActivateIntent(MiruPlayInputIntent.DirectionLeft))
        assertFalse(isTvActivateIntent(MiruPlayInputIntent.DirectionRight))
        assertFalse(isTvActivateIntent(MiruPlayInputIntent.DirectionUp))
        assertFalse(isTvActivateIntent(MiruPlayInputIntent.DirectionDown))
        assertFalse(isTvActivateIntent(MiruPlayInputIntent.Back))
        assertFalse(isTvActivateIntent(null))
    }

    @Test
    fun `TV activate event helper only triggers on key down with activation intent`() {
        var activated = false
        assertTrue(
            tvActivateIntentEvent(
                intent = MiruPlayInputIntent.Activate,
                type = KeyEventType.KeyDown,
                onActivate = { activated = true },
            ),
        )
        assertTrue(activated)

        activated = false
        assertFalse(
            tvActivateIntentEvent(
                intent = MiruPlayInputIntent.Activate,
                type = KeyEventType.KeyUp,
                onActivate = { activated = true },
            ),
        )
        assertFalse(activated)

        assertFalse(
            tvActivateIntentEvent(
                intent = MiruPlayInputIntent.DirectionRight,
                type = KeyEventType.KeyDown,
                onActivate = { activated = true },
            ),
        )
        assertFalse(
            tvActivateIntentEvent(
                intent = MiruPlayInputIntent.Activate,
                type = KeyEventType.KeyDown,
                enabled = false,
                onActivate = { activated = true },
            ),
        )
    }
}
