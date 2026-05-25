package com.miruplay.tv.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiruPlayComposeKeyProfileTest {
    @Test
    fun `android compose key codes map to shared key profile`() {
        assertEquals(MiruPlayKeyInput.DirectionCenter, miruPlayKeyInputFromComposeKeyCode(23L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.Enter, miruPlayKeyInputFromComposeKeyCode(66L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.NumPadEnter, miruPlayKeyInputFromComposeKeyCode(160L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.Spacebar, miruPlayKeyInputFromComposeKeyCode(62L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.DirectionLeft, miruPlayKeyInputFromComposeKeyCode(21L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.DirectionRight, miruPlayKeyInputFromComposeKeyCode(22L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.DirectionUp, miruPlayKeyInputFromComposeKeyCode(19L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.DirectionDown, miruPlayKeyInputFromComposeKeyCode(20L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.MediaPlayPause, miruPlayKeyInputFromComposeKeyCode(85L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.MediaPlay, miruPlayKeyInputFromComposeKeyCode(126L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.MediaPause, miruPlayKeyInputFromComposeKeyCode(127L, MiruPlayComposeKeyProfile.Android))
        assertEquals(MiruPlayKeyInput.MediaStop, miruPlayKeyInputFromComposeKeyCode(86L, MiruPlayComposeKeyProfile.Android))
        assertNull(miruPlayKeyInputFromComposeKeyCode(67L, MiruPlayComposeKeyProfile.Android))
    }

    @Test
    fun `desktop compose key codes map to shared key profile`() {
        assertEquals(MiruPlayKeyInput.DirectionCenter, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(-1000000014), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.Enter, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(10), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.NumPadEnter, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(10, location = 4), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.Spacebar, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(32), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.DirectionLeft, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(37), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.DirectionRight, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(39), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.DirectionUp, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(38), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.DirectionDown, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(40), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.MediaPlayPause, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(-1000000073), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.MediaPlay, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(-1000000071), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.MediaPause, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(-1000000072), MiruPlayComposeKeyProfile.Desktop))
        assertEquals(MiruPlayKeyInput.MediaStop, miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(-1000000074), MiruPlayComposeKeyProfile.Desktop))
        assertNull(miruPlayKeyInputFromComposeKeyCode(desktopKeyCode(8), MiruPlayComposeKeyProfile.Desktop))
    }

    @Test
    fun `compose key intents keep desktop back aliases explicit`() {
        assertEquals(
            MiruPlayInputIntent.Activate,
            miruPlayInputIntentFromComposeKeyCode(23L, MiruPlayComposeKeyProfile.Android),
        )
        assertEquals(
            MiruPlayInputIntent.Back,
            miruPlayInputIntentFromComposeKeyCode(4L, MiruPlayComposeKeyProfile.Android),
        )
        assertNull(miruPlayInputIntentFromComposeKeyCode(111L, MiruPlayComposeKeyProfile.Android))

        assertNull(
            miruPlayInputIntentFromComposeKeyCode(
                desktopKeyCode(27),
                MiruPlayComposeKeyProfile.Desktop,
            ),
        )
        assertEquals(
            MiruPlayInputIntent.Back,
            miruPlayInputIntentFromComposeKeyCode(
                desktopKeyCode(27),
                MiruPlayComposeKeyProfile.Desktop,
                includeDesktopBackAliases = true,
            ),
        )
        assertEquals(
            MiruPlayInputIntent.NavigatePrevious,
            miruPlayInputIntentFromComposeKeyCode(
                desktopKeyCode(-1000000004),
                MiruPlayComposeKeyProfile.Desktop,
                includeDesktopBackAliases = true,
            ),
        )
        assertEquals(
            MiruPlayInputIntent.NavigateOut,
            miruPlayInputIntentFromComposeKeyCode(
                desktopKeyCode(-1000000007),
                MiruPlayComposeKeyProfile.Desktop,
                includeDesktopBackAliases = true,
            ),
        )
    }

    private fun desktopKeyCode(
        nativeKeyCode: Int,
        location: Int = 1,
    ): Long =
        (location.toLong() shl 32) or (nativeKeyCode.toLong() and 0xffffffffL)
}
