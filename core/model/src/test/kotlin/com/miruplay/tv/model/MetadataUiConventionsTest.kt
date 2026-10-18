package com.miruplay.tv.model

import com.miruplay.tv.core.common.rawUiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataUiConventionsTest {
    @Test
    fun `metadata panel labels are shared`() {
        assertEquals("外部元数据", metadataPanelTitleLabel().fallback)
        assertEquals("Bangumi 搜索词", metadataQueryFieldLabel().fallback)
        assertEquals("使用当前条目", metadataUseSelectedEntryActionLabel().fallback)
        assertEquals("搜索", metadataSearchActionLabel().fallback)
        assertEquals("应用匹配", metadataApplyMatchActionLabel().fallback)
        assertEquals("清除元数据", metadataClearActionLabel().fallback)
        assertEquals("批量预览", metadataBatchPreviewActionLabel().fallback)
        assertEquals("应用批量", metadataApplyBatchActionLabel().fallback)
        assertEquals("撤销批量", metadataUndoBatchActionLabel().fallback)
        assertEquals("接受复核", metadataAcceptReviewActionLabel().fallback)
        assertEquals("当前索引", metadataSelectedIndexSectionTitle().fallback)
        assertEquals("Bangumi 匹配", metadataMatchesSectionTitle().fallback)
        assertEquals("候选条目", metadataBatchCandidatesSectionTitle().fallback)
        assertEquals("批量：3 个查询已预览", metadataBatchPreviewedCountLabel(3).fallback)
        assertEquals("批量", metadataBatchPageLabel().fallback)
        assertEquals("候选", metadataCandidatePageLabel().fallback)
        assertEquals("搜索结果", metadataSearchResultsPageLabel().fallback)
        assertEquals("个条目", metadataPageUnitLabel().fallback)
        assertEquals("尚未选择索引视频。", metadataNoSelectedIndexMessage().fallback)
        assertEquals("Bangumi：葬送的芙莉莲", metadataBangumiLinkedLabel("葬送的芙莉莲").fallback)
        assertEquals("Bangumi：未关联", metadataBangumiLinkedLabel(null).fallback)
        assertEquals("未选择条目", metadataNoSelectedEntryLabel().fallback)
        assertEquals("待匹配", metadataPendingMatchLabel().fallback)
        assertEquals("已匹配：葬送的芙莉莲", metadataMatchedSummaryLabel(" 葬送的芙莉莲 ").fallback)
        assertEquals("待匹配", metadataMatchedSummaryLabel(" ").fallback)
        assertEquals("已匹配：葬送的芙莉莲", metadataMatchSummaryLabel("葬送的芙莉莲").fallback)
        assertEquals("待匹配", metadataMatchSummaryLabel(null).fallback)
        assertEquals("请先应用 Bangumi 匹配", metadataApplyBangumiRequiredMessage().fallback)
        assertEquals("无匹配", metadataNoMatchLabel().fallback)
        assertEquals("候选 2/3", metadataSelectedCandidateLabel(selectedIndex = 1, count = 3).fallback)
        assertEquals("3 个候选", metadataCandidateCountLabel(3).fallback)
        assertEquals("搜索后在这里显示 Bangumi 匹配。", metadataEmptyResultsMessage().fallback)
    }

    @Test
    fun `bangumi pagination helpers are shared`() {
        assertEquals(4, BANGUMI_BATCH_MATCH_LIMIT)
        assertEquals(4, BANGUMI_CANDIDATE_LIMIT)
        assertEquals(6, BANGUMI_RESULT_LIMIT)
        assertEquals(12, bangumiPageStartForIndex(index = 30, itemCount = 13, pageSize = 4))
        assertEquals(6, bangumiPageStartForIndex(index = 6, itemCount = 12, pageSize = 6))
        assertEquals(8, bangumiCoercedPageStart(pageStart = 10, itemCount = 13, pageSize = 4))
        assertEquals(12, bangumiCoercedPageStart(pageStart = 40, itemCount = 13, pageSize = 4))
        assertEquals(0, bangumiCoercedPageStart(pageStart = -4, itemCount = 13, pageSize = 4))
        assertEquals(
            "候选：显示 5-8 / 13 个条目，按上/下继续翻页。",
            bangumiPageSummary(label = rawUiText("候选"), pageStart = 4, visibleCount = 4, itemCount = 13, pageSize = 4)?.fallback,
        )
        assertNull(bangumiPageSummary(label = rawUiText("批量"), pageStart = 0, visibleCount = 4, itemCount = 4, pageSize = 4))
    }

    @Test
    fun `metadata status helpers provide shared TV text`() {
        assertEquals("选择索引视频后可搜索 Bangumi。", metadataInitialTvStatus("Bangumi").fallback)
        assertEquals("请先选择一个索引视频。", metadataIndexedVideoRequiredTvStatus().fallback)
        assertEquals("已从当前索引条目填入搜索词。", metadataQuerySetFromIndexTvStatus().fallback)
        assertEquals("请输入 Bangumi 搜索词，或先选择索引视频。", metadataQueryRequiredTvStatus("Bangumi").fallback)
        assertEquals("正在搜索 Bangumi：\"Frieren\"...", metadataSearchStartedTvStatus("Frieren", "Bangumi").fallback)
        assertEquals("没有匹配 \"Frieren\" 的 Bangumi 元数据。", metadataSearchResultTvStatus("Frieren", 0, "Bangumi").fallback)
        assertEquals("找到 2 个 Bangumi 匹配。", metadataSearchResultTvStatus("Frieren", 2, "Bangumi").fallback)
        assertEquals("请先打开或扫描媒体源。", metadataSourceRequiredTvStatus().fallback)
        assertEquals("已选择批量候选：Frieren -> 葬送的芙莉莲", metadataSelectedBatchCandidateTvStatus("Frieren", "葬送的芙莉莲").fallback)
        assertEquals("已选择批量复核：Frieren", metadataSelectedBatchReviewTvStatus("Frieren").fallback)
        assertEquals("请先选择带 Bangumi 结果的批量匹配。", metadataBatchResultRequiredTvStatus("Bangumi").fallback)
        assertEquals("当前复核项有 2 个元数据冲突，未覆盖任何内容。", metadataReviewConflictTvStatus(2).fallback)
        assertEquals("当前复核项没有匹配的索引条目。", metadataReviewNoMatchTvStatus().fallback)
        assertEquals("已选择：葬送的芙莉莲", metadataSelectedResultTvStatus("葬送的芙莉莲").fallback)
        assertEquals("请先选择索引视频，再应用 Bangumi 元数据。", metadataApplyEntryRequiredTvStatus("Bangumi").fallback)
        assertEquals("请先搜索 Bangumi 并选择一个匹配。", metadataSearchSelectionRequiredTvStatus("Bangumi").fallback)
        assertEquals("已将 Bangumi 元数据应用到 D:/Anime/Frieren/01.mkv。", metadataAppliedTvStatus("Bangumi", "D:/Anime/Frieren/01.mkv").fallback)
        assertEquals("请先选择索引视频，再清除元数据。", metadataClearEntryRequiredTvStatus().fallback)
        assertEquals("已清除 D:/Anime/Frieren/01.mkv 的外部元数据。", metadataClearedTvStatus("D:/Anime/Frieren/01.mkv").fallback)
        assertEquals("正在用 Bangumi 搜索 2 个索引标题...", metadataBatchSearchingTvStatus(2, "Bangumi").fallback)
        assertEquals("没有可用于 Bangumi 批量匹配的索引条目。", metadataNoBatchEntriesTvStatus("Bangumi").fallback)
        assertEquals("2 个可应用，1 个需复核，0 个冲突", metadataPlanSummaryTvStatus(2, 1, 0).fallback)
        assertEquals("已将 Bangumi 批量元数据应用到 1 个索引条目，跳过 2 个冲突。", metadataBatchAppliedTvStatus(1, 2).fallback)
        assertEquals("已接受复核的 Bangumi 匹配，更新 1 个索引条目。", metadataReviewAcceptedTvStatus(1).fallback)
        assertEquals("已从上一次 Bangumi 批量更改中恢复 2 个索引条目。", metadataBatchRestoredTvStatus(2).fallback)
        assertEquals("请先运行批量预览；当前没有可直接应用的高置信匹配。", metadataNoBatchPreviewTvStatus().fallback)
        assertEquals("没有可撤销的 Bangumi 批量更改。", metadataNoBatchUndoTvStatus().fallback)
    }

    @Test
    fun `metadata status text accepts stable shared TV statuses`() {
        assertEquals("请先选择一个索引视频。", localizedMetadataStatusText(metadataIndexedVideoRequiredTvStatus().fallback))
        assertEquals("已从当前索引条目填入搜索词。", localizedMetadataStatusText(metadataQuerySetFromIndexTvStatus().fallback))
        assertEquals("请先打开或扫描媒体源。", localizedMetadataStatusText(metadataSourceRequiredTvStatus().fallback))
        assertEquals("当前复核项没有匹配的索引条目。", localizedMetadataStatusText(metadataReviewNoMatchTvStatus().fallback))
        assertEquals("请先选择索引视频，再清除元数据。", localizedMetadataStatusText(metadataClearEntryRequiredTvStatus().fallback))
        assertEquals("请先运行批量预览；当前没有可直接应用的高置信匹配。", localizedMetadataStatusText(metadataNoBatchPreviewTvStatus().fallback))
        assertEquals("没有可撤销的 Bangumi 批量更改。", localizedMetadataStatusText(metadataNoBatchUndoTvStatus().fallback))
        assertEquals("选择索引视频后可搜索 Bangumi。", localizedMetadataStatusText(metadataInitialTvStatus("Bangumi").fallback))
        assertEquals("请输入 Bangumi 搜索词，或先选择索引视频。", localizedMetadataStatusText(metadataQueryRequiredTvStatus("Bangumi").fallback))
        assertEquals("正在搜索 Bangumi：\"Frieren\"...", localizedMetadataStatusText(metadataSearchStartedTvStatus("Frieren", "Bangumi").fallback))
        assertEquals("没有匹配 \"Frieren\" 的 Bangumi 元数据。", localizedMetadataStatusText(metadataSearchResultTvStatus("Frieren", 0, "Bangumi").fallback))
        assertEquals("找到 2 个 Bangumi 匹配。", localizedMetadataStatusText(metadataSearchResultTvStatus("Frieren", 2, "Bangumi").fallback))
        assertEquals("已选择批量候选：Frieren -> 葬送的芙莉莲", localizedMetadataStatusText(metadataSelectedBatchCandidateTvStatus("Frieren", "葬送的芙莉莲").fallback))
        assertEquals("已选择批量复核：Frieren", localizedMetadataStatusText(metadataSelectedBatchReviewTvStatus("Frieren").fallback))
        assertEquals("请先选择带 Bangumi 结果的批量匹配。", localizedMetadataStatusText(metadataBatchResultRequiredTvStatus("Bangumi").fallback))
        assertEquals("当前复核项有 2 个元数据冲突，未覆盖任何内容。", localizedMetadataStatusText(metadataReviewConflictTvStatus(2).fallback))
        assertEquals("已选择：葬送的芙莉莲", localizedMetadataStatusText(metadataSelectedResultTvStatus("葬送的芙莉莲").fallback))
        assertEquals("请先选择索引视频，再应用 Bangumi 元数据。", localizedMetadataStatusText(metadataApplyEntryRequiredTvStatus("Bangumi").fallback))
        assertEquals("请先搜索 Bangumi 并选择一个匹配。", localizedMetadataStatusText(metadataSearchSelectionRequiredTvStatus("Bangumi").fallback))
        assertEquals("已将 Bangumi 元数据应用到 D:/Anime/Frieren/01.mkv。", localizedMetadataStatusText(metadataAppliedTvStatus("Bangumi", "D:/Anime/Frieren/01.mkv").fallback))
        assertEquals("已清除 D:/Anime/Frieren/01.mkv 的外部元数据。", localizedMetadataStatusText(metadataClearedTvStatus("D:/Anime/Frieren/01.mkv").fallback))
        assertEquals("正在用 Bangumi 搜索 2 个索引标题...", localizedMetadataStatusText(metadataBatchSearchingTvStatus(2, "Bangumi").fallback))
        assertEquals("没有可用于 Bangumi 批量匹配的索引条目。", localizedMetadataStatusText(metadataNoBatchEntriesTvStatus("Bangumi").fallback))
        assertEquals("2 个可应用，1 个需复核，0 个冲突", localizedMetadataStatusText(metadataPlanSummaryTvStatus(2, 1, 0).fallback))
        assertEquals("已将 Bangumi 批量元数据应用到 1 个索引条目，跳过 2 个冲突。", localizedMetadataStatusText(metadataBatchAppliedTvStatus(1, 2).fallback))
        assertEquals("已接受复核的 Bangumi 匹配，更新 1 个索引条目。", localizedMetadataStatusText(metadataReviewAcceptedTvStatus(1).fallback))
        assertEquals("已从上一次 Bangumi 批量更改中恢复 2 个索引条目。", localizedMetadataStatusText(metadataBatchRestoredTvStatus(2).fallback))
    }

    @Test
    fun `metadata status text localizes legacy repository wire statuses`() {
        assertEquals("请先选择一个索引视频。", localizedMetadataStatusText("Select an indexed video first."))
        assertEquals("已从当前索引条目填入搜索词。", localizedMetadataStatusText("Query set from selected index entry."))
        assertEquals("请先打开或扫描媒体源。", localizedMetadataStatusText("Open or scan a source first."))
        assertEquals("当前复核项没有匹配的索引条目。", localizedMetadataStatusText("Selected review has no matching indexed entries."))
        assertEquals("请先选择索引视频，再清除元数据。", localizedMetadataStatusText("Select an indexed video before clearing metadata."))
        assertEquals(
            "请先运行批量预览；当前没有可直接应用的高置信匹配。",
            localizedMetadataStatusText("Run Batch preview first; no high-confidence matches are ready."),
        )
        assertEquals("没有可撤销的 Bangumi 批量更改。", localizedMetadataStatusText("No batch Bangumi changes are available to undo."))
        assertNull(localizedMetadataStatusText("custom status"))
        assertEquals("custom status", metadataStatusText("custom status"))
    }

    @Test
    fun `metadata status text localizes dynamic repository wire statuses`() {
        assertEquals("选择索引视频后可搜索 Bangumi。", localizedMetadataStatusText("Select an indexed video, then search Bangumi."))
        assertEquals(
            "请输入 Bangumi 搜索词，或先选择索引视频。",
            localizedMetadataStatusText("Enter a Bangumi query or select an indexed video."),
        )
        assertEquals("正在搜索 Bangumi：\"Frieren\"...", localizedMetadataStatusText("Searching Bangumi for \"Frieren\"..."))
        assertEquals("没有匹配 \"Frieren\" 的 Bangumi 元数据。", localizedMetadataStatusText("No Bangumi metadata matched \"Frieren\"."))
        assertEquals("找到 2 个 Bangumi 匹配。", localizedMetadataStatusText("Found 2 Bangumi match(es)."))
        assertEquals("已选择批量复核：Frieren", localizedMetadataStatusText("Selected batch review: Frieren."))
        assertEquals(
            "请先选择带 Bangumi 结果的批量匹配。",
            localizedMetadataStatusText("Select a batch match with a Bangumi result first."),
        )
        assertEquals(
            "当前复核项有 2 个元数据冲突，未覆盖任何内容。",
            localizedMetadataStatusText("Selected review has 2 metadata conflicts; nothing was overwritten."),
        )
        assertEquals("已选择：葬送的芙莉莲", localizedMetadataStatusText("Selected 葬送的芙莉莲."))
        assertEquals(
            "请先选择索引视频，再应用 Bangumi 元数据。",
            localizedMetadataStatusText("Select an indexed video before applying Bangumi metadata."),
        )
        assertEquals(
            "请先搜索 Bangumi 并选择一个匹配。",
            localizedMetadataStatusText("Search Bangumi and select a match first."),
        )
        assertEquals(
            "已将 Bangumi 元数据应用到 D:/Anime/Frieren/01.mkv。",
            localizedMetadataStatusText("Applied Bangumi metadata to D:/Anime/Frieren/01.mkv."),
        )
        assertEquals(
            "已清除 D:/Anime/Frieren/01.mkv 的外部元数据。",
            localizedMetadataStatusText("Cleared external metadata for D:/Anime/Frieren/01.mkv."),
        )
        assertEquals("正在用 Bangumi 搜索 2 个索引标题...", localizedMetadataStatusText("Searching Bangumi for 2 indexed title(s)..."))
        assertEquals(
            "没有可用于 Bangumi 批量匹配的索引条目。",
            localizedMetadataStatusText("No indexed entries are available for Bangumi batch matching."),
        )
        assertEquals("2 个可应用，1 个需复核，0 个冲突", localizedMetadataStatusText("2 ready, 1 review, 0 conflicts"))
        assertEquals(
            "已选择批量候选：Frieren -> 葬送的芙莉莲",
            localizedMetadataStatusText("Selected batch candidate for Frieren: 葬送的芙莉莲."),
        )
        assertEquals(
            "已将 Bangumi 批量元数据应用到 1 个索引条目，跳过 2 个冲突。",
            localizedMetadataStatusText("Applied Bangumi batch metadata to 1 index entry; 2 conflicts skipped."),
        )
        assertEquals(
            "已接受复核的 Bangumi 匹配，更新 1 个索引条目。",
            localizedMetadataStatusText("Accepted reviewed Bangumi match for 1 index entry."),
        )
        assertEquals(
            "已从上一次 Bangumi 批量更改中恢复 2 个索引条目。",
            localizedMetadataStatusText("Restored 2 index entries from the previous Bangumi batch."),
        )
    }

    @Test
    fun `metadata batch status labels are shared`() {
        assertEquals("预览", metadataBatchStatusLabel("preview").fallback)
        assertEquals("可用", metadataBatchStatusLabel("ready").fallback)
        assertEquals("复核", metadataBatchStatusLabel("review").fallback)
        assertEquals("冲突", metadataBatchStatusLabel("conflict").fallback)
        assertEquals("custom", metadataBatchStatusLabel("custom").fallback)
    }
}
