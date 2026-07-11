package com.miruplay.tv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExitConfirmationTest {

    @Test
    fun `second back confirms exit only inside the window`() {
        assertFalse(isConfirmedPlayerExit(lastBackAtMs = 0L, nowMs = 1_000L))
        assertTrue(isConfirmedPlayerExit(lastBackAtMs = 1_000L, nowMs = 3_000L))
        assertFalse(isConfirmedPlayerExit(lastBackAtMs = 1_000L, nowMs = 3_001L))
        assertFalse(isConfirmedPlayerExit(lastBackAtMs = 2_000L, nowMs = 1_999L))
    }
}
