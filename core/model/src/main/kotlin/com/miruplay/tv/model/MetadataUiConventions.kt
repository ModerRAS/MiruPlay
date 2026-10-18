package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.uiText

const val BANGUMI_BATCH_MATCH_LIMIT = 4
const val BANGUMI_CANDIDATE_LIMIT = 4
const val BANGUMI_RESULT_LIMIT = 6

fun metadataPanelTitleLabel(): UiText = uiText("metadata_panel_title", "外部元数据")

fun metadataQueryFieldLabel(): UiText = uiText("metadata_query_field", "Bangumi 搜索词")

fun metadataUseSelectedEntryActionLabel(): UiText = uiText("metadata_use_selected", "使用当前条目")

fun metadataSearchActionLabel(): UiText = uiText("metadata_search", "搜索")

fun metadataApplyMatchActionLabel(): UiText = uiText("metadata_apply_match", "应用匹配")

fun metadataClearActionLabel(): UiText = uiText("metadata_clear", "清除元数据")

fun metadataBatchPreviewActionLabel(): UiText = uiText("metadata_batch_preview", "批量预览")

fun metadataApplyBatchActionLabel(): UiText = uiText("metadata_apply_batch", "应用批量")

fun metadataUndoBatchActionLabel(): UiText = uiText("metadata_undo_batch", "撤销批量")

fun metadataAcceptReviewActionLabel(): UiText = uiText("metadata_accept_review", "接受复核")

fun metadataSelectedIndexSectionTitle(): UiText = uiText("metadata_selected_index", "当前索引")

fun metadataMatchesSectionTitle(): UiText = uiText("metadata_matches", "Bangumi 匹配")

fun metadataBatchCandidatesSectionTitle(): UiText = uiText("metadata_batch_candidates", "候选条目")

fun metadataBatchPreviewedCountLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("metadata_batch_previewed_count", listOf(safe), "批量：$safe 个查询已预览")
}

fun metadataBatchPageLabel(): UiText = uiText("metadata_page_batch", "批量")

fun metadataCandidatePageLabel(): UiText = uiText("metadata_page_candidate", "候选")

fun metadataSearchResultsPageLabel(): UiText = uiText("metadata_page_results", "搜索结果")

fun metadataPageUnitLabel(): UiText = uiText("metadata_page_unit", "个条目")

fun bangumiPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

fun bangumiCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

fun bangumiPageSummary(
    label: UiText,
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
    pageSize: Int,
): UiText? =
    pagedListPageSummary(
        pageStart = bangumiCoercedPageStart(pageStart, itemCount, pageSize),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = pageSize,
        unitLabel = metadataPageUnitLabel(),
        prefix = label,
    )

fun metadataNoSelectedIndexMessage(): UiText =
    uiText("metadata_no_selected_index", "尚未选择索引视频。")

fun metadataBangumiLinkedLabel(title: String?): UiText {
    val name = title?.takeIf { it.isNotBlank() }
        ?: return uiText("metadata_bangumi_unlinked", "Bangumi：未关联")
    return UiText("metadata_bangumi_linked", listOf(name), "Bangumi：$name")
}

fun metadataNoSelectedEntryLabel(): UiText = uiText("metadata_no_entry", "未选择条目")

fun metadataPendingMatchLabel(): UiText = uiText("metadata_pending_match", "待匹配")

fun metadataMatchedSummaryLabel(title: String): UiText =
    title.trim().takeIf { it.isNotBlank() }?.let {
        UiText("metadata_matched_summary", listOf(it), "已匹配：$it")
    }
        ?: metadataPendingMatchLabel()

fun metadataMatchSummaryLabel(title: String?): UiText =
    title?.takeIf { it.isNotBlank() }?.let(::metadataMatchedSummaryLabel)
        ?: metadataPendingMatchLabel()

fun metadataApplyBangumiRequiredMessage(): UiText =
    uiText("metadata_apply_required", "请先应用 Bangumi 匹配")

fun metadataNoMatchLabel(): UiText = uiText("metadata_no_match", "无匹配")

fun metadataSelectedCandidateLabel(selectedIndex: Int, count: Int): UiText {
    val index = selectedIndex.coerceAtLeast(0) + 1
    val total = count.coerceAtLeast(0)
    return UiText("metadata_selected_candidate", listOf(index, total), "候选 $index/$total")
}

fun metadataCandidateCountLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("metadata_candidate_count", listOf(safe), "$safe 个候选")
}

fun metadataEmptyResultsMessage(): UiText =
    uiText("metadata_empty_results", "搜索后在这里显示 Bangumi 匹配。")

fun metadataInitialTvStatus(sourceName: String = "metadata"): UiText =
    UiText("metadata_initial_status", listOf(sourceName), "选择索引视频后可搜索 $sourceName。")

fun metadataIndexedVideoRequiredTvStatus(): UiText =
    uiText("metadata_index_required", "请先选择一个索引视频。")

fun metadataQuerySetFromIndexTvStatus(): UiText =
    uiText("metadata_query_from_index", "已从当前索引条目填入搜索词。")

fun metadataQueryRequiredTvStatus(sourceName: String = "metadata"): UiText =
    UiText(
        "metadata_query_required",
        listOf(sourceName),
        "请输入 $sourceName 搜索词，或先选择索引视频。",
    )

fun metadataSearchStartedTvStatus(query: String, sourceName: String = "metadata"): UiText =
    UiText("metadata_search_started", listOf(sourceName, query), "正在搜索 $sourceName：\"$query\"...")

fun metadataSearchResultTvStatus(
    query: String,
    resultCount: Int,
    sourceName: String = "metadata",
): UiText =
    if (resultCount == 0) {
        UiText("metadata_search_no_match", listOf(query, sourceName), "没有匹配 \"$query\" 的 $sourceName 元数据。")
    } else {
        val safe = resultCount.coerceAtLeast(0)
        UiText("metadata_search_found", listOf(safe, sourceName), "找到 $safe 个 $sourceName 匹配。")
    }

fun metadataSourceRequiredTvStatus(): UiText =
    uiText("metadata_source_required", "请先打开或扫描媒体源。")

fun metadataSelectedBatchCandidateTvStatus(query: String, title: String): UiText =
    UiText("metadata_batch_candidate_selected", listOf(query, title), "已选择批量候选：$query -> $title")

fun metadataSelectedBatchReviewTvStatus(query: String): UiText =
    UiText("metadata_batch_review_selected", listOf(query), "已选择批量复核：$query")

fun metadataBatchResultRequiredTvStatus(sourceName: String = "metadata"): UiText =
    UiText(
        "metadata_batch_result_required",
        listOf(sourceName),
        "请先选择带 $sourceName 结果的批量匹配。",
    )

fun metadataReviewConflictTvStatus(conflictCount: Int): UiText {
    val safe = conflictCount.coerceAtLeast(0)
    return UiText(
        "metadata_review_conflict",
        listOf(safe),
        "当前复核项有 $safe 个元数据冲突，未覆盖任何内容。",
    )
}

fun metadataReviewNoMatchTvStatus(): UiText =
    uiText("metadata_review_no_match", "当前复核项没有匹配的索引条目。")

fun metadataSelectedResultTvStatus(title: String): UiText =
    UiText("metadata_result_selected", listOf(title), "已选择：$title")

fun metadataApplyEntryRequiredTvStatus(sourceName: String = "metadata"): UiText =
    UiText(
        "metadata_apply_entry_required",
        listOf(sourceName),
        "请先选择索引视频，再应用 $sourceName 元数据。",
    )

fun metadataSearchSelectionRequiredTvStatus(sourceName: String = "metadata"): UiText =
    UiText(
        "metadata_search_selection_required",
        listOf(sourceName),
        "请先搜索 $sourceName 并选择一个匹配。",
    )

fun metadataAppliedTvStatus(sourceName: String = "metadata", path: String): UiText =
    UiText("metadata_applied", listOf(sourceName, path), "已将 $sourceName 元数据应用到 $path。")

fun metadataClearEntryRequiredTvStatus(): UiText =
    uiText("metadata_clear_required", "请先选择索引视频，再清除元数据。")

fun metadataClearedTvStatus(path: String): UiText =
    UiText("metadata_cleared", listOf(path), "已清除 $path 的外部元数据。")

fun metadataBatchSearchingTvStatus(
    queryCount: Int,
    sourceName: String = "metadata",
): UiText {
    val safe = queryCount.coerceAtLeast(0)
    return UiText(
        "metadata_batch_searching",
        listOf(sourceName, safe),
        "正在用 $sourceName 搜索 $safe 个索引标题...",
    )
}

fun metadataNoBatchEntriesTvStatus(sourceName: String = "metadata"): UiText =
    UiText(
        "metadata_no_batch_entries",
        listOf(sourceName),
        "没有可用于 $sourceName 批量匹配的索引条目。",
    )

fun metadataPlanSummaryTvStatus(
    readyCount: Int,
    reviewCount: Int,
    conflictCount: Int,
): UiText {
    val ready = readyCount.coerceAtLeast(0)
    val review = reviewCount.coerceAtLeast(0)
    val conflict = conflictCount.coerceAtLeast(0)
    return UiText(
        "metadata_plan_summary",
        listOf(ready, review, conflict),
        "$ready 个可应用，$review 个需复核，$conflict 个冲突",
    )
}

fun metadataBatchAppliedTvStatus(updatedCount: Int, conflictCount: Int): UiText {
    val updated = updatedCount.coerceAtLeast(0)
    val conflict = conflictCount.coerceAtLeast(0)
    return UiText(
        "metadata_batch_applied",
        listOf(updated, conflict),
        "已将 Bangumi 批量元数据应用到 $updated 个索引条目，跳过 $conflict 个冲突。",
    )
}

fun metadataReviewAcceptedTvStatus(updatedCount: Int): UiText {
    val updated = updatedCount.coerceAtLeast(0)
    return UiText(
        "metadata_review_accepted",
        listOf(updated),
        "已接受复核的 Bangumi 匹配，更新 $updated 个索引条目。",
    )
}

fun metadataBatchRestoredTvStatus(restoredCount: Int): UiText {
    val restored = restoredCount.coerceAtLeast(0)
    return UiText(
        "metadata_batch_restored",
        listOf(restored),
        "已从上一次 Bangumi 批量更改中恢复 $restored 个索引条目。",
    )
}

fun metadataNoBatchPreviewTvStatus(): UiText =
    uiText("metadata_no_batch_preview", "请先运行批量预览；当前没有可直接应用的高置信匹配。")

fun metadataNoBatchUndoTvStatus(): UiText =
    uiText("metadata_no_batch_undo", "没有可撤销的 Bangumi 批量更改。")

fun localizedMetadataStatusText(status: String): String? =
    when (val trimmed = status.trim()) {
        metadataIndexedVideoRequiredTvStatus().fallback,
        metadataQuerySetFromIndexTvStatus().fallback,
        metadataSourceRequiredTvStatus().fallback,
        metadataReviewNoMatchTvStatus().fallback,
        metadataClearEntryRequiredTvStatus().fallback,
        metadataNoBatchPreviewTvStatus().fallback,
        metadataNoBatchUndoTvStatus().fallback -> trimmed
        "Select an indexed video first." ->
            metadataIndexedVideoRequiredTvStatus().fallback
        "Query set from selected index entry." ->
            metadataQuerySetFromIndexTvStatus().fallback
        "Open or scan a source first." ->
            metadataSourceRequiredTvStatus().fallback
        "Selected review has no matching indexed entries." ->
            metadataReviewNoMatchTvStatus().fallback
        "Select an indexed video before clearing metadata." ->
            metadataClearEntryRequiredTvStatus().fallback
        "Run Batch preview first; no high-confidence matches are ready." ->
            metadataNoBatchPreviewTvStatus().fallback
        "No batch Bangumi changes are available to undo." ->
            metadataNoBatchUndoTvStatus().fallback
        else -> localizedDynamicMetadataStatusText(trimmed)?.fallback
    }

fun metadataStatusText(status: String): String =
    localizedMetadataStatusText(status) ?: status

fun metadataBatchStatusLabel(status: String): UiText =
    when (status) {
        "preview" -> uiText("metadata_batch_status_preview", "预览")
        "ready" -> uiText("metadata_batch_status_ready", "可用")
        "review" -> uiText("metadata_batch_status_review", "复核")
        "conflict" -> uiText("metadata_batch_status_conflict", "冲突")
        else -> UiText("metadata_batch_status_other", listOf(status), status)
    }

data class BangumiUiLabels(
    val title: UiText,
    val query: UiText,
    val useSelected: UiText,
    val search: UiText,
    val applyMatch: UiText,
    val clearMetadata: UiText,
    val batchPreview: UiText,
    val applyBatch: UiText,
    val undoBatch: UiText,
    val acceptReview: UiText,
    val syncProgress: UiText,
    val selectedIndex: UiText,
    val matches: UiText,
    val batchCandidates: UiText,
    val emptyResults: UiText,
)

fun bangumiUiLabels(isSyncingProgress: Boolean = false): BangumiUiLabels =
    BangumiUiLabels(
        title = metadataPanelTitleLabel(),
        query = metadataQueryFieldLabel(),
        useSelected = metadataUseSelectedEntryActionLabel(),
        search = metadataSearchActionLabel(),
        applyMatch = metadataApplyMatchActionLabel(),
        clearMetadata = metadataClearActionLabel(),
        batchPreview = metadataBatchPreviewActionLabel(),
        applyBatch = metadataApplyBatchActionLabel(),
        undoBatch = metadataUndoBatchActionLabel(),
        acceptReview = metadataAcceptReviewActionLabel(),
        syncProgress = detailSyncProgressActionLabel(isSyncing = isSyncingProgress),
        selectedIndex = metadataSelectedIndexSectionTitle(),
        matches = metadataMatchesSectionTitle(),
        batchCandidates = metadataBatchCandidatesSectionTitle(),
        emptyResults = metadataEmptyResultsMessage(),
    )

private fun localizedDynamicMetadataStatusText(status: String): UiText? {
    if (status.isTvMetadataStatus()) {
        return UiText("metadata_status_passthrough", listOf(status), status)
    }
    metadataInitialStatusRegex.matchEntire(status)?.let { match ->
        return metadataInitialTvStatus(match.groupValues[1])
    }
    metadataQueryRequiredRegex.matchEntire(status)?.let { match ->
        return metadataQueryRequiredTvStatus(match.groupValues[1])
    }
    metadataSearchStartedRegex.matchEntire(status)?.let { match ->
        return metadataSearchStartedTvStatus(
            query = match.groupValues[2],
            sourceName = match.groupValues[1],
        )
    }
    metadataNoMatchRegex.matchEntire(status)?.let { match ->
        return metadataSearchResultTvStatus(
            query = match.groupValues[2],
            resultCount = 0,
            sourceName = match.groupValues[1],
        )
    }
    metadataFoundRegex.matchEntire(status)?.let { match ->
        return metadataSearchResultTvStatus(
            query = "",
            resultCount = match.groupValues[1].toInt(),
            sourceName = match.groupValues[2],
        )
    }
    selectedBatchCandidateRegex.matchEntire(status)?.let { match ->
        return metadataSelectedBatchCandidateTvStatus(match.groupValues[1], match.groupValues[2])
    }
    selectedBatchReviewRegex.matchEntire(status)?.let { match ->
        return metadataSelectedBatchReviewTvStatus(match.groupValues[1])
    }
    metadataBatchResultRequiredRegex.matchEntire(status)?.let { match ->
        return metadataBatchResultRequiredTvStatus(match.groupValues[1])
    }
    metadataReviewConflictRegex.matchEntire(status)?.let { match ->
        return metadataReviewConflictTvStatus(match.groupValues[1].toInt())
    }
    selectedMetadataRegex.matchEntire(status)?.let { match ->
        return metadataSelectedResultTvStatus(match.groupValues[1])
    }
    metadataApplyRequiredRegex.matchEntire(status)?.let { match ->
        return metadataApplyEntryRequiredTvStatus(match.groupValues[1])
    }
    metadataSearchSelectionRequiredRegex.matchEntire(status)?.let { match ->
        return metadataSearchSelectionRequiredTvStatus(match.groupValues[1])
    }
    metadataBatchAppliedRegex.matchEntire(status)?.let { match ->
        return metadataBatchAppliedTvStatus(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }
    metadataAppliedRegex.matchEntire(status)?.let { match ->
        return metadataAppliedTvStatus(
            sourceName = match.groupValues[1],
            path = match.groupValues[2],
        )
    }
    metadataClearedRegex.matchEntire(status)?.let { match ->
        return metadataClearedTvStatus(match.groupValues[1])
    }
    metadataBatchSearchingRegex.matchEntire(status)?.let { match ->
        return metadataBatchSearchingTvStatus(
            queryCount = match.groupValues[2].toInt(),
            sourceName = match.groupValues[1],
        )
    }
    noMetadataBatchEntriesRegex.matchEntire(status)?.let { match ->
        return metadataNoBatchEntriesTvStatus(match.groupValues[1])
    }
    metadataPlanSummaryRegex.matchEntire(status)?.let { match ->
        return metadataPlanSummaryTvStatus(
            readyCount = match.groupValues[1].toInt(),
            reviewCount = match.groupValues[2].toInt(),
            conflictCount = match.groupValues[3].toInt(),
        )
    }
    metadataReviewAcceptedRegex.matchEntire(status)?.let { match ->
        return metadataReviewAcceptedTvStatus(match.groupValues[1].toInt())
    }
    metadataBatchRestoredRegex.matchEntire(status)?.let { match ->
        return metadataBatchRestoredTvStatus(match.groupValues[1].toInt())
    }
    return null
}

private fun String.isTvMetadataStatus(): Boolean =
    localizedTvMetadataStatusRegexes.any { it.matches(this) }

private val localizedTvMetadataStatusRegexes = listOf(
    Regex("""^选择索引视频后可搜索 .+。$"""),
    Regex("""^请输入 .+ 搜索词，或先选择索引视频。$"""),
    Regex("""^正在搜索 .+：".*"\.\.\.$"""),
    Regex("""^没有匹配 ".*" 的 .+ 元数据。$"""),
    Regex("""^找到 \d+ 个 .+ 匹配。$"""),
    Regex("""^已选择批量候选：.* -> .*$"""),
    Regex("""^已选择批量复核：.*$"""),
    Regex("""^请先选择带 .+ 结果的批量匹配。$"""),
    Regex("""^当前复核项有 \d+ 个元数据冲突，未覆盖任何内容。$"""),
    Regex("""^已选择：.*$"""),
    Regex("""^请先选择索引视频，再应用 .+ 元数据。$"""),
    Regex("""^请先搜索 .+ 并选择一个匹配。$"""),
    Regex("""^已将 .+ 元数据应用到 .*。$"""),
    Regex("""^已清除 .* 的外部元数据。$"""),
    Regex("""^正在用 .+ 搜索 \d+ 个索引标题\.\.\.$"""),
    Regex("""^没有可用于 .+ 批量匹配的索引条目。$"""),
    Regex("""^\d+ 个可应用，\d+ 个需复核，\d+ 个冲突$"""),
    Regex("""^已将 Bangumi 批量元数据应用到 \d+ 个索引条目，跳过 \d+ 个冲突。$"""),
    Regex("""^已接受复核的 Bangumi 匹配，更新 \d+ 个索引条目。$"""),
    Regex("""^已从上一次 Bangumi 批量更改中恢复 \d+ 个索引条目。$"""),
)

private val metadataInitialStatusRegex = Regex("""^Select an indexed video, then search (.+)\.$""")
private val metadataQueryRequiredRegex = Regex("""^Enter a (.+) query or select an indexed video\.$""")
private val metadataSearchStartedRegex = Regex("""^Searching (.+) for "(.*)"\.\.\.$""")
private val metadataNoMatchRegex = Regex("""^No (.+) metadata matched "(.*)"\.$""")
private val metadataFoundRegex = Regex("""^Found (\d+) (.+) match\(es\)\.$""")
private val selectedBatchCandidateRegex = Regex("""^Selected batch candidate for (.+): (.*)\.$""")
private val selectedBatchReviewRegex = Regex("""^Selected batch review: (.+)\.$""")
private val metadataBatchResultRequiredRegex = Regex("""^Select a batch match with a (.+) result first\.$""")
private val metadataReviewConflictRegex = Regex("""^Selected review has (\d+) metadata conflict(s?); nothing was overwritten\.$""")
private val selectedMetadataRegex = Regex("""^Selected (.+)\.$""")
private val metadataApplyRequiredRegex = Regex("""^Select an indexed video before applying (.+) metadata\.$""")
private val metadataSearchSelectionRequiredRegex = Regex("""^Search (.+) and select a match first\.$""")
private val metadataAppliedRegex = Regex("""^Applied (.+) metadata to (.+)\.$""")
private val metadataClearedRegex = Regex("""^Cleared external metadata for (.+)\.$""")
private val metadataBatchSearchingRegex = Regex("""^Searching (.+) for (\d+) indexed title\(s\)\.\.\.$""")
private val noMetadataBatchEntriesRegex = Regex("""^No indexed entries are available for (.+) batch matching\.$""")
private val metadataPlanSummaryRegex = Regex("""^(\d+) ready, (\d+) review, (\d+) conflicts$""")
private val metadataBatchAppliedRegex = Regex("""^Applied Bangumi batch metadata to (\d+) index entr(?:y|ies); (\d+) conflict(s?) skipped\.$""")
private val metadataReviewAcceptedRegex = Regex("""^Accepted reviewed Bangumi match for (\d+) index entr(?:y|ies)\.$""")
private val metadataBatchRestoredRegex = Regex("""^Restored (\d+) index entr(?:y|ies) from the previous Bangumi batch\.$""")
