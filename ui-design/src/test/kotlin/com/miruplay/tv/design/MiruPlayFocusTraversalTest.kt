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
    fun `index traversal moves by shared direction intents with edge stops`() {
        assertEquals(
            1,
            focusIndexAfter(
                currentIndex = 2,
                intent = MiruPlayInputIntent.DirectionLeft,
                axis = MiruPlayFocusAxis.Horizontal,
                itemCount = 4,
            ),
        )
        assertEquals(
            3,
            focusIndexAfter(
                currentIndex = 2,
                delta = 1,
                itemCount = 4,
            ),
        )
        assertNull(
            focusIndexAfter(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionLeft,
                axis = MiruPlayFocusAxis.Horizontal,
                itemCount = 4,
            ),
        )
        assertNull(
            focusIndexAfter(
                currentIndex = -1,
                delta = 1,
                itemCount = 4,
            ),
        )
    }

    @Test
    fun `first enabled focus index respects disabled leading actions`() {
        assertEquals(2, firstEnabledFocusIndex(itemCount = 4, enabledItems = listOf(false, false, true, true)))
        assertEquals(0, firstEnabledFocusIndex(itemCount = 3))
        assertNull(firstEnabledFocusIndex(itemCount = 3, enabledItems = listOf(false, false, false)))
        assertNull(firstEnabledFocusIndex(itemCount = 0))
    }

    @Test
    fun `grid traversal moves inside rows and clamps to short next rows`() {
        assertEquals(
            1,
            gridFocusIndexAfter(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionRight,
                columns = 6,
                itemCount = 8,
            ),
        )
        assertNull(
            gridFocusIndexAfter(
                currentIndex = 5,
                intent = MiruPlayInputIntent.DirectionRight,
                columns = 6,
                itemCount = 8,
            ),
        )
        assertNull(
            gridFocusIndexAfter(
                currentIndex = 6,
                intent = MiruPlayInputIntent.DirectionLeft,
                columns = 6,
                itemCount = 8,
            ),
        )
        assertEquals(
            7,
            gridFocusIndexAfter(
                currentIndex = 4,
                intent = MiruPlayInputIntent.DirectionDown,
                columns = 6,
                itemCount = 8,
            ),
        )
        assertEquals(
            0,
            gridFocusIndexAfter(
                currentIndex = 6,
                intent = MiruPlayInputIntent.DirectionUp,
                columns = 6,
                itemCount = 8,
            ),
        )
        assertNull(
            gridFocusIndexAfter(
                currentIndex = 7,
                intent = MiruPlayInputIntent.DirectionDown,
                columns = 6,
                itemCount = 8,
            ),
        )
    }

    @Test
    fun `grid traversal handles invalid input defensively`() {
        assertNull(
            gridFocusIndexAfter(
                currentIndex = 0,
                intent = MiruPlayInputIntent.Activate,
                columns = 6,
                itemCount = 8,
            ),
        )
        assertNull(
            gridFocusIndexAfter(
                currentIndex = -1,
                intent = MiruPlayInputIntent.DirectionDown,
                columns = 6,
                itemCount = 8,
            ),
        )
        assertNull(
            gridFocusIndexAfter(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionDown,
                columns = 6,
                itemCount = 0,
            ),
        )
        assertEquals(
            1,
            gridFocusIndexAfter(
                currentIndex = 0,
                intent = MiruPlayInputIntent.DirectionDown,
                columns = 0,
                itemCount = 3,
            ),
        )
    }
}
