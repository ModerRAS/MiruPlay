package com.miruplay.tv.ui.components

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
}
