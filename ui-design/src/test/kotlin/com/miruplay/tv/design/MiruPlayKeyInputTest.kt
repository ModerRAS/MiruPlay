package com.miruplay.tv.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiruPlayKeyInputTest {
    @Test
    fun `confirm key inputs map to shared activation intent`() {
        assertEquals(MiruPlayInputIntent.Activate, MiruPlayKeyInput.DirectionCenter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, MiruPlayKeyInput.Enter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, MiruPlayKeyInput.NumPadEnter.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.Activate, MiruPlayKeyInput.Spacebar.toMiruPlayInputIntent())
        assertTrue(MiruPlayKeyInput.DirectionCenter.isMiruPlayActivationKey())
        assertTrue(MiruPlayKeyInput.Enter.isMiruPlayActivationKey())
        assertFalse(MiruPlayKeyInput.DirectionLeft.isMiruPlayActivationKey())
    }

    @Test
    fun `navigation and media key inputs map to shared intents`() {
        assertEquals(MiruPlayInputIntent.DirectionLeft, MiruPlayKeyInput.DirectionLeft.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.DirectionRight, MiruPlayKeyInput.DirectionRight.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.DirectionUp, MiruPlayKeyInput.DirectionUp.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.DirectionDown, MiruPlayKeyInput.DirectionDown.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.MediaPlayPause, MiruPlayKeyInput.MediaPlayPause.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.MediaPlay, MiruPlayKeyInput.MediaPlay.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.MediaPause, MiruPlayKeyInput.MediaPause.toMiruPlayInputIntent())
        assertEquals(MiruPlayInputIntent.MediaStop, MiruPlayKeyInput.MediaStop.toMiruPlayInputIntent())
    }

    @Test
    fun `TV key profile keeps unsupported aliases out`() {
        assertEquals(MiruPlayInputIntent.Back, MiruPlayKeyInput.Back.toMiruPlayInputIntent())
        assertNull(MiruPlayKeyInput.Escape.toMiruPlayInputIntent())
        assertNull(MiruPlayKeyInput.NavigatePrevious.toMiruPlayInputIntent())
        assertNull(MiruPlayKeyInput.NavigateOut.toMiruPlayInputIntent())
    }
}
