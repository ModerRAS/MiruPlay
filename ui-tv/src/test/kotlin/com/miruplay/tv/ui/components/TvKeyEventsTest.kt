package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvKeyEventsTest {
    @Test
    fun `TV activate keys accept remote center and keyboard confirm keys`() {
        assertTrue(Key.DirectionCenter.isTvActivateKey())
        assertTrue(Key.Enter.isTvActivateKey())
        assertTrue(Key.NumPadEnter.isTvActivateKey())
        assertTrue(Key.Spacebar.isTvActivateKey())
    }

    @Test
    fun `TV activate keys do not swallow directional navigation or back`() {
        assertFalse(Key.DirectionLeft.isTvActivateKey())
        assertFalse(Key.DirectionRight.isTvActivateKey())
        assertFalse(Key.DirectionUp.isTvActivateKey())
        assertFalse(Key.DirectionDown.isTvActivateKey())
        assertFalse(Key.Back.isTvActivateKey())
    }
}
