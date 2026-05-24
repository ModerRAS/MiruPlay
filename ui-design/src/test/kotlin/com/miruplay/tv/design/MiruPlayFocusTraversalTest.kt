package com.miruplay.tv.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiruPlayFocusTraversalTest {
    private enum class Target {
        First,
        Second,
        Third,
    }

    @Test
    fun `list traversal moves by shared direction intents with edge stops`() {
        val targets = Target.entries

        assertEquals(
            Target.First,
            targets.focusTargetAfter(
                current = Target.Second,
                intent = MiruPlayInputIntent.DirectionLeft,
                axis = MiruPlayFocusAxis.Horizontal,
            ),
        )
        assertEquals(
            Target.Third,
            targets.focusTargetAfter(
                current = Target.Second,
                intent = MiruPlayInputIntent.DirectionDown,
                axis = MiruPlayFocusAxis.Vertical,
            ),
        )
        assertNull(
            targets.focusTargetAfter(
                current = Target.First,
                intent = MiruPlayInputIntent.DirectionLeft,
                axis = MiruPlayFocusAxis.Horizontal,
            ),
        )
        assertNull(
            targets.focusTargetAfter(
                current = Target.Second,
                intent = MiruPlayInputIntent.Activate,
                axis = MiruPlayFocusAxis.Linear,
            ),
        )
    }

    @Test
    fun `enabled-index traversal skips disabled items without wrapping`() {
        val enabledItems = listOf(true, false, true, false, true)

        assertEquals(
            2,
            nextEnabledFocusIndex(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionRight,
                axis = MiruPlayFocusAxis.Horizontal,
                itemCount = enabledItems.size,
                enabledItems = enabledItems,
            ),
        )
        assertEquals(
            0,
            nextEnabledFocusIndex(
                currentIndex = 2,
                delta = -1,
                itemCount = enabledItems.size,
                enabledItems = enabledItems,
            ),
        )
        assertNull(
            nextEnabledFocusIndex(
                currentIndex = 4,
                intent = MiruPlayInputIntent.DirectionRight,
                axis = MiruPlayFocusAxis.Horizontal,
                itemCount = enabledItems.size,
                enabledItems = enabledItems,
            ),
        )
        assertNull(
            nextEnabledFocusIndex(
                currentIndex = 2,
                intent = MiruPlayInputIntent.DirectionDown,
                axis = MiruPlayFocusAxis.Horizontal,
                itemCount = enabledItems.size,
                enabledItems = enabledItems,
            ),
        )
    }

    @Test
    fun `first enabled focus index respects disabled leading actions`() {
        assertEquals(2, firstEnabledFocusIndex(itemCount = 4, enabledItems = listOf(false, false, true, true)))
        assertNull(firstEnabledFocusIndex(itemCount = 3, enabledItems = listOf(false, false, false)))
        assertNull(firstEnabledFocusIndex(itemCount = 0))
    }
}
