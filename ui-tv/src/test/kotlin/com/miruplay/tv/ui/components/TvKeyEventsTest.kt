package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.design.MiruPlayInputIntent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvKeyEventsTest {
    @Test
    fun `TV activate keys accept remote center and keyboard confirm keys`() {
        assertEquals(MiruPlayInputIntent.Activate, Key.DirectionCenter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, Key.Enter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, Key.NumPadEnter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, Key.Spacebar.toMiruPlayInputIntent())
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
