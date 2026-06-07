package com.miruplay.tv.navigation

import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModeSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModeSelectionStateTest {
    @Test
    fun `default selection state starts unselected`() {
        val state = AppModeSelectionState()

        assertNull(state.currentAppMode)
        assertFalse(state.hasCompletedModeSelection)
    }

    @Test
    fun `completed selection state preserves chosen mode`() {
        val state = AppModeSelectionState(
            currentAppMode = AppMode.DRAMA,
            hasCompletedModeSelection = true,
        )

        assertEquals(AppMode.DRAMA, state.currentAppMode)
        assertTrue(state.hasCompletedModeSelection)
    }
}
