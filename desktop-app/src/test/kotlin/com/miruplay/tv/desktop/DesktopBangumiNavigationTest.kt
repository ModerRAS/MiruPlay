package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBangumiNavigationTest {
    @Test
    fun `bangumi navigation moves through batch candidates and search results`() {
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchMatches, 1),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 0),
                key = Key.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 2,
                resultCount = 2,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchCandidates, 0),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 1),
                key = Key.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 2,
                resultCount = 2,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.SearchResults, 0),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 1),
                key = Key.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 2,
                resultCount = 2,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchCandidates, 1),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 0),
                key = Key.DirectionUp,
                batchMatchCount = 2,
                candidateCount = 2,
                resultCount = 2,
            ),
        )
    }

    @Test
    fun `bangumi navigation enters and exits candidate review with horizontal keys`() {
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchCandidates, 1),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 1),
                key = Key.DirectionRight,
                batchMatchCount = 3,
                candidateCount = 2,
                resultCount = 0,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchMatches, 1),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 1),
                key = Key.DirectionLeft,
                batchMatchCount = 3,
                candidateCount = 2,
                resultCount = 0,
            ),
        )
    }

    @Test
    fun `bangumi navigation clamps to visible rows and stops at edges`() {
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchMatches, 3),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 3),
                key = Key.DirectionLeft,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
        assertNull(
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 0),
                key = Key.DirectionUp,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
        assertNull(
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 5),
                key = Key.DirectionDown,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
    }

    @Test
    fun `bangumi top action up key exits to previous details panel`() {
        var requestedPreviousPanel = false

        assertTrue(
            bangumiTopActionKeyEvent(
                key = Key.DirectionUp,
                type = KeyEventType.KeyDown,
                onFocusPreviousPanel = {
                    requestedPreviousPanel = true
                    true
                },
            ),
        )
        assertTrue(requestedPreviousPanel)
        assertFalse(
            bangumiTopActionKeyEvent(
                key = Key.DirectionDown,
                type = KeyEventType.KeyDown,
                onFocusPreviousPanel = { true },
            ),
        )
        assertFalse(
            bangumiTopActionKeyEvent(
                key = Key.DirectionUp,
                type = KeyEventType.KeyUp,
                onFocusPreviousPanel = { true },
            ),
        )
    }
}
