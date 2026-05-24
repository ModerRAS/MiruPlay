package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvKeyEventsTest {
    @Test
    fun `TV activate key helper delegates to shared key mapping`() {
        assertTrue(Key.DirectionCenter.isTvActivateKey())
        assertTrue(Key.Enter.isTvActivateKey())
        assertTrue(Key.NumPadEnter.isTvActivateKey())
        assertTrue(Key.Spacebar.isTvActivateKey())
        assertFalse(Key.DirectionLeft.isTvActivateKey())
        assertFalse(Key.DirectionRight.isTvActivateKey())
        assertFalse(Key.DirectionUp.isTvActivateKey())
        assertFalse(Key.DirectionDown.isTvActivateKey())
        assertFalse(Key.Back.isTvActivateKey())
    }
}
