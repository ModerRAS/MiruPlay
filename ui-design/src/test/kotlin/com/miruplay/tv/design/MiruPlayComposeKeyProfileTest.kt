package com.miruplay.tv.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiruPlayComposeKeyProfileTest {
    @Test
    fun `android compose key codes map to shared key profile`() {
        assertEquals(MiruPlayKeyInput.DirectionCenter, miruPlayKeyInputFromComposeKeyCode(23L))
        assertEquals(MiruPlayKeyInput.Enter, miruPlayKeyInputFromComposeKeyCode(66L))
        assertEquals(MiruPlayKeyInput.NumPadEnter, miruPlayKeyInputFromComposeKeyCode(160L))
        assertEquals(MiruPlayKeyInput.Spacebar, miruPlayKeyInputFromComposeKeyCode(62L))
        assertEquals(MiruPlayKeyInput.DirectionLeft, miruPlayKeyInputFromComposeKeyCode(21L))
        assertEquals(MiruPlayKeyInput.DirectionRight, miruPlayKeyInputFromComposeKeyCode(22L))
        assertEquals(MiruPlayKeyInput.DirectionUp, miruPlayKeyInputFromComposeKeyCode(19L))
        assertEquals(MiruPlayKeyInput.DirectionDown, miruPlayKeyInputFromComposeKeyCode(20L))
        assertEquals(MiruPlayKeyInput.MediaPlayPause, miruPlayKeyInputFromComposeKeyCode(85L))
        assertEquals(MiruPlayKeyInput.MediaPlay, miruPlayKeyInputFromComposeKeyCode(126L))
        assertEquals(MiruPlayKeyInput.MediaPause, miruPlayKeyInputFromComposeKeyCode(127L))
        assertEquals(MiruPlayKeyInput.MediaStop, miruPlayKeyInputFromComposeKeyCode(86L))
        assertNull(miruPlayKeyInputFromComposeKeyCode(67L))
    }

    @Test
    fun `compose key codes map to shared intents`() {
        assertEquals(MiruPlayInputIntent.Activate, miruPlayInputIntentFromComposeKeyCode(23L))
        assertEquals(MiruPlayInputIntent.Back, miruPlayInputIntentFromComposeKeyCode(4L))
        assertNull(miruPlayInputIntentFromComposeKeyCode(111L))
    }
}
