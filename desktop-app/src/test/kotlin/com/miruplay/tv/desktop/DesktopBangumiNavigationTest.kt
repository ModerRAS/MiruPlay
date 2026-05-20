package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
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

        assertEquals("Bangumi 元数据", labels.title)
        assertEquals("Bangumi 搜索词", labels.query)
        assertEquals("使用当前条目", labels.useSelected)
        assertEquals("搜索", labels.search)
        assertEquals("应用匹配", labels.applyMatch)
        assertEquals("清除元数据", labels.clearMetadata)
        assertEquals("批量预览", labels.batchPreview)
        assertEquals("应用批量", labels.applyBatch)
        assertEquals("撤销批量", labels.undoBatch)
        assertEquals("接受复核", labels.acceptReview)
        assertEquals("当前索引", labels.selectedIndex)
        assertEquals("Bangumi 匹配", labels.matches)
        assertEquals("候选条目", labels.batchCandidates)
        assertEquals("搜索后在这里显示 Bangumi 匹配。", labels.emptyResults)
    }

    @Test
    fun `bangumi status messages use TV facing Chinese text`() {
        assertEquals("请先选择一个索引视频。", desktopBangumiStatusText("Select an indexed video first."))
        assertEquals("选择索引视频后可搜索 Bangumi。", desktopBangumiStatusText("Select an indexed video, then search Bangumi."))
        assertEquals("已从当前索引条目填入搜索词。", desktopBangumiStatusText("Query set from selected index entry."))
        assertEquals("请输入 Bangumi 搜索词，或先选择索引视频。", desktopBangumiStatusText("Enter a Bangumi query or select an indexed video."))
        assertEquals("正在搜索 Bangumi：\"Frieren\"...", desktopBangumiStatusText("Searching Bangumi for \"Frieren\"..."))
        assertEquals("没有匹配 \"Frieren\" 的 Bangumi 元数据。", desktopBangumiStatusText("No Bangumi metadata matched \"Frieren\"."))
        assertEquals("找到 2 个 Bangumi 匹配。", desktopBangumiStatusText("Found 2 Bangumi match(es)."))
        assertEquals("请先打开或扫描媒体源。", desktopBangumiStatusText("Open or scan a source first."))
        assertEquals("已选择批量复核：Frieren", desktopBangumiStatusText("Selected batch review: Frieren."))
        assertEquals("请先选择带 Bangumi 结果的批量匹配。", desktopBangumiStatusText("Select a batch match with a Bangumi result first."))
        assertEquals("当前复核项有 2 个元数据冲突，未覆盖任何内容。", desktopBangumiStatusText("Selected review has 2 metadata conflicts; nothing was overwritten."))
        assertEquals("当前复核项没有匹配的索引条目。", desktopBangumiStatusText("Selected review has no matching indexed entries."))
        assertEquals("已选择：葬送的芙莉莲", desktopBangumiStatusText("Selected 葬送的芙莉莲."))
        assertEquals("请先选择索引视频，再应用 Bangumi 元数据。", desktopBangumiStatusText("Select an indexed video before applying Bangumi metadata."))
        assertEquals("请先搜索 Bangumi 并选择一个匹配。", desktopBangumiStatusText("Search Bangumi and select a match first."))
        assertEquals("已将 Bangumi 元数据应用到 D:/Anime/Frieren/01.mkv。", desktopBangumiStatusText("Applied Bangumi metadata to D:/Anime/Frieren/01.mkv."))
        assertEquals("请先选择索引视频，再清除元数据。", desktopBangumiStatusText("Select an indexed video before clearing metadata."))
        assertEquals("已清除 D:/Anime/Frieren/01.mkv 的外部元数据。", desktopBangumiStatusText("Cleared external metadata for D:/Anime/Frieren/01.mkv."))
        assertEquals("正在用 Bangumi 搜索 2 个索引标题...", desktopBangumiStatusText("Searching Bangumi for 2 indexed title(s)..."))
        assertEquals("没有可用于 Bangumi 批量匹配的索引条目。", desktopBangumiStatusText("No indexed entries are available for Bangumi batch matching."))
        assertEquals("2 个可应用，1 个需复核，0 个冲突", desktopBangumiStatusText("2 ready, 1 review, 0 conflicts"))
        assertEquals("已选择批量候选：Frieren -> 葬送的芙莉莲", desktopBangumiStatusText("Selected batch candidate for Frieren: 葬送的芙莉莲."))
        assertEquals("已将 Bangumi 批量元数据应用到 1 个索引条目，跳过 2 个冲突。", desktopBangumiStatusText("Applied Bangumi batch metadata to 1 index entry; 2 conflicts skipped."))
        assertEquals("已接受复核的 Bangumi 匹配，更新 1 个索引条目。", desktopBangumiStatusText("Accepted reviewed Bangumi match for 1 index entry."))
        assertEquals("已从上一次 Bangumi 批量更改中恢复 2 个索引条目。", desktopBangumiStatusText("Restored 2 index entries from the previous Bangumi batch."))
        assertEquals("请先运行批量预览；当前没有可直接应用的高置信匹配。", desktopBangumiStatusText("Run Batch preview first; no high-confidence matches are ready."))
        assertEquals("没有可撤销的 Bangumi 批量更改。", desktopBangumiStatusText("No batch Bangumi changes are available to undo."))
        assertEquals("custom status", desktopBangumiStatusText("custom status"))
    }

    @Test
    fun `bangumi batch status chips use TV facing Chinese labels`() {
        assertEquals("预览", desktopBangumiBatchStatusLabel("preview"))
        assertEquals("可用", desktopBangumiBatchStatusLabel("ready"))
        assertEquals("复核", desktopBangumiBatchStatusLabel("review"))
        assertEquals("冲突", desktopBangumiBatchStatusLabel("conflict"))
        assertEquals("custom", desktopBangumiBatchStatusLabel("custom"))
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
            "候选 2/2",
            MetadataBatchMatch(query = "Frieren", result = second, candidates = listOf(first, second))
                .desktopSelectedCandidateLabel(),
        )
        assertEquals(
            "2 个候选",
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
        assertNull(bangumiActionFocusTarget(BangumiAction.Search, Key.DirectionRight))
        assertNull(bangumiActionFocusTarget(BangumiAction.AcceptReview, Key.DirectionDown))
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
}
