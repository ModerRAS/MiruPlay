package com.miruplay.tv.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeyHandlingTest {
    @Test
    fun `focused timeline left and right seek on initial and repeated key down`() {
        val calls = mutableListOf<String>()

        val first = handlePlaybackTimelineKey(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyDown,
            onSkipBackward = { calls.add("skipBackward") },
            onSkipForward = { calls.add("skipForward") },
        )
        val repeated = handlePlaybackTimelineKey(
            key = Key.DirectionLeft,
            type = KeyEventType.KeyDown,
            onSkipBackward = { calls.add("skipBackward") },
            onSkipForward = { calls.add("skipForward") },
        )

        assertTrue(first)
        assertTrue(repeated)
        assertEquals(listOf("skipBackward", "skipBackward"), calls)
    }

    @Test
    fun `timeline key up does not seek`() {
        val calls = mutableListOf<String>()

        val consumed = handlePlaybackTimelineKey(
            key = Key.DirectionRight,
            type = KeyEventType.KeyUp,
            onSkipBackward = { calls.add("skipBackward") },
            onSkipForward = { calls.add("skipForward") },
        )

        assertFalse(consumed)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `timeline leaves non horizontal keys to focus traversal`() {
        val calls = mutableListOf<String>()

        val consumed = handlePlaybackTimelineKey(
            key = Key.DirectionDown,
            type = KeyEventType.KeyDown,
            onSkipBackward = { calls.add("skipBackward") },
            onSkipForward = { calls.add("skipForward") },
        )

        assertFalse(consumed)
        assertTrue(calls.isEmpty())
    }
}
