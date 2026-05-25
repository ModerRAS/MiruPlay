package com.miruplay.tv.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeyHandlingTest {

    @Test
    fun `direction keys stay with focus when controls are visible`() {
        listOf(Key.DirectionLeft, Key.DirectionRight).forEach { key ->
            val calls = mutableListOf<String>()

            val consumed = handlePlayerKey(
                key = key,
                type = KeyEventType.KeyDown,
                controlsVisible = true,
                hasOpenMenu = false,
                actions = testActions(calls)
            )

            assertFalse(consumed)
            assertTrue(calls.isEmpty())
        }
    }

    @Test
    fun `direction keys still skip when controls are hidden`() {
        val calls = mutableListOf<String>()

        val consumed = handlePlayerKey(
            key = Key.DirectionRight,
            type = KeyEventType.KeyDown,
            controlsVisible = false,
            hasOpenMenu = false,
            actions = testActions(calls)
        )

        assertTrue(consumed)
        assertEquals(listOf("showControls", "skipForward"), calls)
    }

    @Test
    fun `back closes the open menu before hiding controls`() {
        val calls = mutableListOf<String>()

        val consumed = handlePlayerKey(
            key = Key.Back,
            type = KeyEventType.KeyDown,
            controlsVisible = true,
            hasOpenMenu = true,
            actions = testActions(calls)
        )

        assertTrue(consumed)
        assertEquals(listOf("closeMenu"), calls)
    }

    @Test
    fun `back navigates away when controls are hidden`() {
        val calls = mutableListOf<String>()

        val consumed = handlePlayerKey(
            key = Key.Back,
            type = KeyEventType.KeyDown,
            controlsVisible = false,
            hasOpenMenu = false,
            actions = testActions(calls)
        )

        assertTrue(consumed)
        assertEquals(listOf("navigateBack"), calls)
    }

    private fun testActions(calls: MutableList<String>): PlayerKeyActions =
        PlayerKeyActions(
            skipBackward = { calls.add("skipBackward") },
            skipForward = { calls.add("skipForward") },
            togglePlayback = { calls.add("togglePlayback") },
            resume = { calls.add("resume") },
            pause = { calls.add("pause") },
            showControls = { calls.add("showControls") },
            hideControls = { calls.add("hideControls") },
            closeMenu = { calls.add("closeMenu") },
            navigateBack = { calls.add("navigateBack") }
        )
}
