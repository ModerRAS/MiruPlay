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
        assertEquals(MiruPlayKeyInput.MediaNext, miruPlayKeyInputFromComposeKeyCode(87L))
        assertEquals(MiruPlayKeyInput.MediaPrevious, miruPlayKeyInputFromComposeKeyCode(88L))
        assertEquals(MiruPlayKeyInput.MediaRewind, miruPlayKeyInputFromComposeKeyCode(89L))
        assertEquals(MiruPlayKeyInput.MediaFastForward, miruPlayKeyInputFromComposeKeyCode(90L))
        assertEquals(MiruPlayKeyInput.Info, miruPlayKeyInputFromComposeKeyCode(165L))
        assertEquals(MiruPlayKeyInput.Captions, miruPlayKeyInputFromComposeKeyCode(175L))
        assertEquals(MiruPlayKeyInput.Menu, miruPlayKeyInputFromComposeKeyCode(82L))
        assertNull(miruPlayKeyInputFromComposeKeyCode(176L))
        assertNull(miruPlayKeyInputFromComposeKeyCode(67L))
    }

    @Test
    fun `compose key codes map to shared intents`() {
        assertEquals(MiruPlayInputIntent.Activate, miruPlayInputIntentFromComposeKeyCode(23L))
        assertEquals(MiruPlayInputIntent.Back, miruPlayInputIntentFromComposeKeyCode(4L))
        assertEquals(MiruPlayInputIntent.MediaFastForward, miruPlayInputIntentFromComposeKeyCode(90L))
        assertEquals(MiruPlayInputIntent.Captions, miruPlayInputIntentFromComposeKeyCode(175L))
        assertEquals(MiruPlayInputIntent.Info, miruPlayInputIntentFromComposeKeyCode(165L))
        assertNull(miruPlayInputIntentFromComposeKeyCode(111L))
    }
}
