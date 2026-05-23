package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.model.detailSyncProgressActionLabel
import com.miruplay.tv.model.metadataAcceptReviewActionLabel
import com.miruplay.tv.model.metadataApplyBatchActionLabel
import com.miruplay.tv.model.metadataApplyMatchActionLabel
import com.miruplay.tv.model.metadataBatchCandidatesSectionTitle
import com.miruplay.tv.model.metadataBatchPreviewActionLabel
import com.miruplay.tv.model.localizedMetadataStatusText
import com.miruplay.tv.model.metadataCandidateCountLabel
import com.miruplay.tv.model.metadataClearActionLabel
import com.miruplay.tv.model.metadataEmptyResultsMessage
import com.miruplay.tv.model.metadataMatchesSectionTitle
import com.miruplay.tv.model.metadataPageUnitLabel
import com.miruplay.tv.model.metadataPanelTitleLabel
import com.miruplay.tv.model.metadataQueryFieldLabel
import com.miruplay.tv.model.metadataBatchStatusLabel
import com.miruplay.tv.model.metadataSearchActionLabel
import com.miruplay.tv.model.metadataSelectedCandidateLabel
import com.miruplay.tv.model.metadataSelectedIndexSectionTitle
import com.miruplay.tv.model.metadataStatusText
import com.miruplay.tv.model.metadataUndoBatchActionLabel
import com.miruplay.tv.model.metadataUseSelectedEntryActionLabel
import com.miruplay.tv.repository.MetadataBatchMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBangumiNavigationTest {
    @Test
    fun `bangumi panel labels use TV facing Chinese text`() {
        val labels = desktopBangumiUiLabels()

        assertEquals(metadataPanelTitleLabel(), labels.title)
        assertEquals(metadataQueryFieldLabel(), labels.query)
        assertEquals(metadataUseSelectedEntryActionLabel(), labels.useSelected)
        assertEquals(metadataSearchActionLabel(), labels.search)
        assertEquals(metadataApplyMatchActionLabel(), labels.applyMatch)
        assertEquals(metadataClearActionLabel(), labels.clearMetadata)
        assertEquals(metadataBatchPreviewActionLabel(), labels.batchPreview)
        assertEquals(metadataApplyBatchActionLabel(), labels.applyBatch)
        assertEquals(metadataUndoBatchActionLabel(), labels.undoBatch)
        assertEquals(metadataAcceptReviewActionLabel(), labels.acceptReview)
        assertEquals(detailSyncProgressActionLabel(isSyncing = false), labels.syncProgress)
        assertEquals(detailSyncProgressActionLabel(isSyncing = true), desktopBangumiUiLabels(isSyncingProgress = true).syncProgress)
        assertEquals(metadataSelectedIndexSectionTitle(), labels.selectedIndex)
        assertEquals(metadataMatchesSectionTitle(), labels.matches)
        assertEquals(metadataBatchCandidatesSectionTitle(), labels.batchCandidates)
        assertEquals(metadataEmptyResultsMessage(), labels.emptyResults)
    }

    @Test
    fun `bangumi status messages use TV facing Chinese text`() {
        assertEquals(localizedMetadataStatusText("Select an indexed video first."), metadataStatusText("Select an indexed video first."))
        assertEquals(
            localizedMetadataStatusText("Select an indexed video, then search Bangumi."),
            metadataStatusText("Select an indexed video, then search Bangumi."),
        )
        assertEquals(
            localizedMetadataStatusText("Query set from selected index entry."),
            metadataStatusText("Query set from selected index entry."),
        )
        assertEquals(
            localizedMetadataStatusText("Enter a Bangumi query or select an indexed video."),
            metadataStatusText("Enter a Bangumi query or select an indexed video."),
        )
        assertEquals(localizedMetadataStatusText("Searching Bangumi for \"Frieren\"..."), metadataStatusText("Searching Bangumi for \"Frieren\"..."))
        assertEquals(
            localizedMetadataStatusText("No Bangumi metadata matched \"Frieren\"."),
            metadataStatusText("No Bangumi metadata matched \"Frieren\"."),
        )
        assertEquals(localizedMetadataStatusText("Found 2 Bangumi match(es)."), metadataStatusText("Found 2 Bangumi match(es)."))
        assertEquals(localizedMetadataStatusText("Open or scan a source first."), metadataStatusText("Open or scan a source first."))
        assertEquals(localizedMetadataStatusText("Selected batch review: Frieren."), metadataStatusText("Selected batch review: Frieren."))
        assertEquals(
            localizedMetadataStatusText("Select a batch match with a Bangumi result first."),
            metadataStatusText("Select a batch match with a Bangumi result first."),
        )
        assertEquals(
            localizedMetadataStatusText("Selected review has 2 metadata conflicts; nothing was overwritten."),
            metadataStatusText("Selected review has 2 metadata conflicts; nothing was overwritten."),
        )
        assertEquals(
            localizedMetadataStatusText("Selected review has no matching indexed entries."),
            metadataStatusText("Selected review has no matching indexed entries."),
        )
        assertEquals(localizedMetadataStatusText("Selected 葬送的芙莉莲."), metadataStatusText("Selected 葬送的芙莉莲."))
        assertEquals(
            localizedMetadataStatusText("Select an indexed video before applying Bangumi metadata."),
            metadataStatusText("Select an indexed video before applying Bangumi metadata."),
        )
        assertEquals(
            localizedMetadataStatusText("Search Bangumi and select a match first."),
            metadataStatusText("Search Bangumi and select a match first."),
        )
        assertEquals(
            localizedMetadataStatusText("Applied Bangumi metadata to D:/Anime/Frieren/01.mkv."),
            metadataStatusText("Applied Bangumi metadata to D:/Anime/Frieren/01.mkv."),
        )
        assertEquals(
            localizedMetadataStatusText("Select an indexed video before clearing metadata."),
            metadataStatusText("Select an indexed video before clearing metadata."),
        )
        assertEquals(
            localizedMetadataStatusText("Cleared external metadata for D:/Anime/Frieren/01.mkv."),
            metadataStatusText("Cleared external metadata for D:/Anime/Frieren/01.mkv."),
        )
        assertEquals(
            localizedMetadataStatusText("Searching Bangumi for 2 indexed title(s)..."),
            metadataStatusText("Searching Bangumi for 2 indexed title(s)..."),
        )
        assertEquals(
            localizedMetadataStatusText("No indexed entries are available for Bangumi batch matching."),
            metadataStatusText("No indexed entries are available for Bangumi batch matching."),
        )
        assertEquals(localizedMetadataStatusText("2 ready, 1 review, 0 conflicts"), metadataStatusText("2 ready, 1 review, 0 conflicts"))
        assertEquals(
            localizedMetadataStatusText("Selected batch candidate for Frieren: 葬送的芙莉莲."),
            metadataStatusText("Selected batch candidate for Frieren: 葬送的芙莉莲."),
        )
        assertEquals(
            localizedMetadataStatusText("Applied Bangumi batch metadata to 1 index entry; 2 conflicts skipped."),
            metadataStatusText("Applied Bangumi batch metadata to 1 index entry; 2 conflicts skipped."),
        )
        assertEquals(
            localizedMetadataStatusText("Accepted reviewed Bangumi match for 1 index entry."),
            metadataStatusText("Accepted reviewed Bangumi match for 1 index entry."),
        )
        assertEquals(
            localizedMetadataStatusText("Restored 2 index entries from the previous Bangumi batch."),
            metadataStatusText("Restored 2 index entries from the previous Bangumi batch."),
        )
        assertEquals(
            localizedMetadataStatusText("Run Batch preview first; no high-confidence matches are ready."),
            metadataStatusText("Run Batch preview first; no high-confidence matches are ready."),
        )
        assertEquals(
            localizedMetadataStatusText("No batch Bangumi changes are available to undo."),
            metadataStatusText("No batch Bangumi changes are available to undo."),
        )
        assertEquals("custom status", metadataStatusText("custom status"))
    }

    @Test
    fun `bangumi batch status chips use TV facing Chinese labels`() {
        assertEquals(metadataBatchStatusLabel("preview"), desktopBangumiBatchStatusLabel("preview"))
        assertEquals(metadataBatchStatusLabel("ready"), desktopBangumiBatchStatusLabel("ready"))
        assertEquals(metadataBatchStatusLabel("review"), desktopBangumiBatchStatusLabel("review"))
        assertEquals(metadataBatchStatusLabel("conflict"), desktopBangumiBatchStatusLabel("conflict"))
        assertEquals(metadataBatchStatusLabel("custom"), desktopBangumiBatchStatusLabel("custom"))
    }

    @Test
    fun `bangumi selected candidate label uses TV facing Chinese text`() {
        val first = ScraperResult(
            animeId = "1",
            title = "Frieren",
            matchedTitle = "Frieren",
            confidence = 0.7f,
            source = ScraperSource.BANGUMI,
        )
        val second = ScraperResult(
            animeId = "2",
            title = "Sousou no Frieren",
            matchedTitle = "Frieren",
            confidence = 0.9f,
            source = ScraperSource.BANGUMI,
        )

        assertEquals(
            metadataSelectedCandidateLabel(selectedIndex = 1, count = 2),
            MetadataBatchMatch(query = "Frieren", result = second, candidates = listOf(first, second))
                .desktopSelectedCandidateLabel(),
        )
        assertEquals(
            metadataCandidateCountLabel(2),
            MetadataBatchMatch(query = "Frieren", result = null, candidates = listOf(first, second))
                .desktopSelectedCandidateLabel(),
        )
    }

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
    fun `bangumi navigation keeps rows beyond the first page reachable`() {
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchMatches, 9),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 9),
                key = Key.DirectionLeft,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchMatches, 4),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 3),
                key = Key.DirectionDown,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchCandidates, 4),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 3),
                key = Key.DirectionDown,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchCandidates, 0),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 9),
                key = Key.DirectionDown,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.SearchResults, 6),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 5),
                key = Key.DirectionDown,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.SearchResults, 9),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 8),
                key = Key.DirectionDown,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
    }

    @Test
    fun `bangumi navigation stops at full list edges`() {
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchMatches, 3),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 9),
                key = Key.DirectionLeft,
                batchMatchCount = 4,
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
                current = BangumiListPosition(BangumiListSection.SearchResults, 9),
                key = Key.DirectionDown,
                batchMatchCount = 10,
                candidateCount = 10,
                resultCount = 10,
            ),
        )
    }

    @Test
    fun `bangumi list navigation follows shared input intents`() {
        assertEquals(
            BangumiListPosition(BangumiListSection.BatchCandidates, 1),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 1),
                intent = MiruPlayInputIntent.DirectionRight,
                batchMatchCount = 3,
                candidateCount = 2,
                resultCount = 2,
            ),
        )
        assertEquals(
            BangumiListPosition(BangumiListSection.SearchResults, 0),
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 1),
                intent = MiruPlayInputIntent.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 2,
                resultCount = 2,
            ),
        )
        assertNull(
            bangumiListNavigationTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 0),
                intent = MiruPlayInputIntent.Activate,
                batchMatchCount = 2,
                candidateCount = 2,
                resultCount = 2,
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

    @Test
    fun `bangumi action grid moves across metadata command buttons`() {
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.Search),
            bangumiActionFocusTarget(BangumiAction.UseSelected, Key.DirectionRight),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.ApplyMatch),
            bangumiActionFocusTarget(BangumiAction.UseSelected, Key.DirectionDown),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.ClearMetadata),
            bangumiActionFocusTarget(BangumiAction.ApplyMatch, Key.DirectionRight),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.ClearMetadata),
            bangumiActionFocusTarget(BangumiAction.Search, Key.DirectionDown),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.Search),
            bangumiActionFocusTarget(BangumiAction.ClearMetadata, Key.DirectionUp),
        )
        assertEquals(
            BangumiActionFocusTarget.PreviousPanel,
            bangumiActionFocusTarget(BangumiAction.UseSelected, Key.DirectionUp),
        )
        assertEquals(
            BangumiActionFocusTarget.NextPanel,
            bangumiActionFocusTarget(BangumiAction.AcceptReview, Key.DirectionDown),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.AcceptReview),
            bangumiActionFocusTarget(BangumiAction.SyncProgress, Key.DirectionDown),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.UndoBatch),
            bangumiActionFocusTarget(BangumiAction.AcceptReview, Key.DirectionUp),
        )
        assertEquals(
            BangumiActionFocusTarget.EmptyResults,
            bangumiActionFocusTarget(BangumiAction.Search, Key.DirectionRight),
        )
    }

    @Test
    fun `bangumi action grid can enter and leave match lists horizontally`() {
        assertEquals(
            BangumiActionFocusTarget.ListPosition(BangumiListPosition(BangumiListSection.SearchResults, 0)),
            bangumiActionFocusTarget(
                current = BangumiAction.Search,
                key = Key.DirectionRight,
                batchMatchCount = 0,
                candidateCount = 0,
                resultCount = 2,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.EmptyResults,
            bangumiActionFocusTarget(
                current = BangumiAction.Search,
                key = Key.DirectionRight,
                batchMatchCount = 0,
                candidateCount = 0,
                resultCount = 0,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.ListPosition(BangumiListPosition(BangumiListSection.BatchMatches, 0)),
            bangumiActionFocusTarget(
                current = BangumiAction.ApplyBatch,
                key = Key.DirectionRight,
                batchMatchCount = 2,
                candidateCount = 0,
                resultCount = 2,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.EmptyResults,
            bangumiActionFocusTarget(
                current = BangumiAction.AcceptReview,
                key = Key.DirectionRight,
                batchMatchCount = 0,
                candidateCount = 0,
                resultCount = 0,
            ),
        )
        assertEquals(
            BangumiAction.ApplyMatch,
            bangumiListExitActionTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 0),
                key = Key.DirectionLeft,
            ),
        )
        assertEquals(
            BangumiAction.BatchPreview,
            bangumiListExitActionTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 0),
                key = Key.DirectionLeft,
            ),
        )
        assertNull(
            bangumiListExitActionTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 0),
                key = Key.DirectionLeft,
            ),
        )
    }

    @Test
    fun `bangumi action and empty states follow shared input intents`() {
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.Search),
            bangumiActionFocusTarget(
                current = BangumiAction.UseSelected,
                intent = MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.ListPosition(BangumiListPosition(BangumiListSection.SearchResults, 0)),
            bangumiActionFocusTarget(
                current = BangumiAction.Search,
                intent = MiruPlayInputIntent.DirectionRight,
                resultCount = 2,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.ApplyMatch),
            bangumiListExitFocusTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 0),
                intent = MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.Search),
            bangumiEmptyResultsFocusTarget(MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(
            BangumiActionFocusTarget.NextPanel,
            bangumiEmptyResultsFocusTarget(MiruPlayInputIntent.DirectionDown),
        )
        assertNull(bangumiEmptyResultsFocusTarget(MiruPlayInputIntent.DirectionRight))
    }

    @Test
    fun `bangumi list bottom exits to next details panel`() {
        assertNull(
            bangumiListExitFocusTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 0),
                key = Key.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 0,
                resultCount = 0,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.EmptyResults,
            bangumiListExitFocusTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 1),
                key = Key.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 0,
                resultCount = 0,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.NextPanel,
            bangumiListExitFocusTarget(
                current = BangumiListPosition(BangumiListSection.SearchResults, 1),
                key = Key.DirectionDown,
                batchMatchCount = 0,
                candidateCount = 0,
                resultCount = 2,
            ),
        )
    }

    @Test
    fun `bangumi empty result state bridges between actions lists and next details panel`() {
        assertEquals(
            BangumiActionFocusTarget.EmptyResults,
            bangumiListExitFocusTarget(
                current = BangumiListPosition(BangumiListSection.BatchMatches, 1),
                key = Key.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 0,
                resultCount = 0,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.EmptyResults,
            bangumiListExitFocusTarget(
                current = BangumiListPosition(BangumiListSection.BatchCandidates, 1),
                key = Key.DirectionDown,
                batchMatchCount = 2,
                candidateCount = 2,
                resultCount = 0,
            ),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.ApplyMatch),
            bangumiEmptyResultsFocusTarget(Key.DirectionLeft),
        )
        assertEquals(
            BangumiActionFocusTarget.Action(BangumiAction.Search),
            bangumiEmptyResultsFocusTarget(Key.DirectionUp),
        )
        assertEquals(
            BangumiActionFocusTarget.ListPosition(BangumiListPosition(BangumiListSection.BatchMatches, 9)),
            bangumiEmptyResultsFocusTarget(Key.DirectionUp, batchMatchCount = 10, candidateCount = 0),
        )
        assertEquals(
            BangumiActionFocusTarget.ListPosition(BangumiListPosition(BangumiListSection.BatchCandidates, 9)),
            bangumiEmptyResultsFocusTarget(Key.DirectionUp, batchMatchCount = 10, candidateCount = 10),
        )
        assertEquals(
            BangumiActionFocusTarget.NextPanel,
            bangumiEmptyResultsFocusTarget(Key.DirectionDown),
        )
        assertNull(bangumiEmptyResultsFocusTarget(Key.DirectionRight))
    }

    @Test
    fun `bangumi page helpers keep every metadata row reachable`() {
        assertEquals(0, bangumiPageStartForIndex(index = 0, itemCount = 13, pageSize = 4))
        assertEquals(0, bangumiPageStartForIndex(index = 3, itemCount = 13, pageSize = 4))
        assertEquals(4, bangumiPageStartForIndex(index = 4, itemCount = 13, pageSize = 4))
        assertEquals(12, bangumiPageStartForIndex(index = 12, itemCount = 13, pageSize = 4))
        assertEquals(12, bangumiPageStartForIndex(index = 30, itemCount = 13, pageSize = 4))
        assertEquals(6, bangumiPageStartForIndex(index = 6, itemCount = 12, pageSize = 6))
        assertEquals(8, bangumiCoercedPageStart(pageStart = 10, itemCount = 13, pageSize = 4))
        assertEquals(12, bangumiCoercedPageStart(pageStart = 40, itemCount = 13, pageSize = 4))
        assertEquals(0, bangumiCoercedPageStart(pageStart = -4, itemCount = 13, pageSize = 4))

        assertEquals(
            "候选：显示 5-8 / 13 ${metadataPageUnitLabel()}，按上/下继续翻页。",
            bangumiPageSummary(label = "候选", pageStart = 4, visibleCount = 4, itemCount = 13, pageSize = 4),
        )
        assertEquals(
            "搜索结果：显示 13-13 / 13 ${metadataPageUnitLabel()}，按上/下继续翻页。",
            bangumiPageSummary(label = "搜索结果", pageStart = 12, visibleCount = 1, itemCount = 13, pageSize = 4),
        )
        assertNull(bangumiPageSummary(label = "批量", pageStart = 0, visibleCount = 4, itemCount = 4, pageSize = 4))
    }
}
