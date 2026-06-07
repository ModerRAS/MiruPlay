package com.miruplay.tv.model

const val BANGUMI_BATCH_MATCH_LIMIT = 4
const val BANGUMI_CANDIDATE_LIMIT = 4
const val BANGUMI_RESULT_LIMIT = 6

fun metadataPanelTitleLabel(): String = "外部元数据"

fun metadataQueryFieldLabel(): String = "Bangumi 搜索词"

fun metadataUseSelectedEntryActionLabel(): String = "使用当前条目"

fun metadataSearchActionLabel(): String = "搜索"

fun metadataApplyMatchActionLabel(): String = "应用匹配"

fun metadataClearActionLabel(): String = "清除元数据"

fun metadataBatchPreviewActionLabel(): String = "批量预览"

fun metadataApplyBatchActionLabel(): String = "应用批量"

fun metadataUndoBatchActionLabel(): String = "撤销批量"

fun metadataAcceptReviewActionLabel(): String = "接受复核"

fun metadataSelectedIndexSectionTitle(): String = "当前索引"

fun metadataMatchesSectionTitle(): String = "Bangumi 匹配"

fun metadataBatchCandidatesSectionTitle(): String = "候选条目"

fun metadataBatchPreviewedCountLabel(count: Int): String =
    "批量：${count.coerceAtLeast(0)} 个查询已预览"

fun metadataBatchPageLabel(): String = "批量"

fun metadataCandidatePageLabel(): String = "候选"

fun metadataSearchResultsPageLabel(): String = "搜索结果"

fun metadataPageUnitLabel(): String = "个条目"

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
    label: String,
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
    pageSize: Int,
): String? =
    pagedListPageSummary(
        pageStart = bangumiCoercedPageStart(pageStart, itemCount, pageSize),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = pageSize,
        unitLabel = metadataPageUnitLabel(),
        prefix = label,
    )

fun metadataNoSelectedIndexMessage(): String = "尚未选择索引视频。"

fun metadataBangumiLinkedLabel(title: String?): String =
    "Bangumi：${title?.takeIf { it.isNotBlank() } ?: "未关联"}"

fun metadataNoSelectedEntryLabel(): String = "未选择条目"

fun metadataPendingMatchLabel(): String = "待匹配"

fun metadataMatchedSummaryLabel(title: String): String =
    title.trim().takeIf { it.isNotBlank() }?.let { "已匹配：$it" }
        ?: metadataPendingMatchLabel()

fun metadataMatchSummaryLabel(title: String?): String =
    title?.takeIf { it.isNotBlank() }?.let(::metadataMatchedSummaryLabel)
        ?: metadataPendingMatchLabel()

fun metadataApplyBangumiRequiredMessage(): String = "请先应用 Bangumi 匹配"

fun metadataNoMatchLabel(): String = "无匹配"

fun metadataSelectedCandidateLabel(selectedIndex: Int, count: Int): String =
    "候选 ${selectedIndex.coerceAtLeast(0) + 1}/${count.coerceAtLeast(0)}"

fun metadataCandidateCountLabel(count: Int): String =
    "${count.coerceAtLeast(0)} 个候选"

fun metadataEmptyResultsMessage(): String = "搜索后在这里显示 Bangumi 匹配。"

fun metadataInitialTvStatus(sourceName: String = "metadata"): String =
    "选择索引视频后可搜索 $sourceName。"

fun metadataIndexedVideoRequiredTvStatus(): String =
    "请先选择一个索引视频。"

fun metadataQuerySetFromIndexTvStatus(): String =
    "已从当前索引条目填入搜索词。"

fun metadataQueryRequiredTvStatus(sourceName: String = "metadata"): String =
    "请输入 $sourceName 搜索词，或先选择索引视频。"

fun metadataSearchStartedTvStatus(query: String, sourceName: String = "metadata"): String =
    "正在搜索 $sourceName：\"$query\"..."

fun metadataSearchResultTvStatus(
    query: String,
    resultCount: Int,
    sourceName: String = "metadata",
): String =
    if (resultCount == 0) {
        "没有匹配 \"$query\" 的 $sourceName 元数据。"
    } else {
        "找到 ${resultCount.coerceAtLeast(0)} 个 $sourceName 匹配。"
    }

fun metadataSourceRequiredTvStatus(): String =
    "请先打开或扫描媒体源。"

fun metadataSelectedBatchCandidateTvStatus(query: String, title: String): String =
    "已选择批量候选：$query -> $title"

fun metadataSelectedBatchReviewTvStatus(query: String): String =
    "已选择批量复核：$query"

fun metadataBatchResultRequiredTvStatus(sourceName: String = "metadata"): String =
    "请先选择带 $sourceName 结果的批量匹配。"

fun metadataReviewConflictTvStatus(conflictCount: Int): String =
    "当前复核项有 ${conflictCount.coerceAtLeast(0)} 个元数据冲突，未覆盖任何内容。"

fun metadataReviewNoMatchTvStatus(): String =
    "当前复核项没有匹配的索引条目。"

fun metadataSelectedResultTvStatus(title: String): String =
    "已选择：$title"

fun metadataApplyEntryRequiredTvStatus(sourceName: String = "metadata"): String =
    "请先选择索引视频，再应用 $sourceName 元数据。"

fun metadataSearchSelectionRequiredTvStatus(sourceName: String = "metadata"): String =
    "请先搜索 $sourceName 并选择一个匹配。"

fun metadataAppliedTvStatus(sourceName: String = "metadata", path: String): String =
    "已将 $sourceName 元数据应用到 $path。"

fun metadataClearEntryRequiredTvStatus(): String =
    "请先选择索引视频，再清除元数据。"

fun metadataClearedTvStatus(path: String): String =
    "已清除 $path 的外部元数据。"

fun metadataBatchSearchingTvStatus(
    queryCount: Int,
    sourceName: String = "metadata",
): String =
    "正在用 $sourceName 搜索 ${queryCount.coerceAtLeast(0)} 个索引标题..."

fun metadataNoBatchEntriesTvStatus(sourceName: String = "metadata"): String =
    "没有可用于 $sourceName 批量匹配的索引条目。"

fun metadataPlanSummaryTvStatus(
    readyCount: Int,
    reviewCount: Int,
    conflictCount: Int,
): String =
    "${readyCount.coerceAtLeast(0)} 个可应用，" +
        "${reviewCount.coerceAtLeast(0)} 个需复核，" +
        "${conflictCount.coerceAtLeast(0)} 个冲突"

fun metadataBatchAppliedTvStatus(updatedCount: Int, conflictCount: Int): String =
    "已将 Bangumi 批量元数据应用到 ${updatedCount.coerceAtLeast(0)} 个索引条目，" +
        "跳过 ${conflictCount.coerceAtLeast(0)} 个冲突。"

fun metadataReviewAcceptedTvStatus(updatedCount: Int): String =
    "已接受复核的 Bangumi 匹配，更新 ${updatedCount.coerceAtLeast(0)} 个索引条目。"

fun metadataBatchRestoredTvStatus(restoredCount: Int): String =
    "已从上一次 Bangumi 批量更改中恢复 ${restoredCount.coerceAtLeast(0)} 个索引条目。"

fun metadataNoBatchPreviewTvStatus(): String =
    "请先运行批量预览；当前没有可直接应用的高置信匹配。"

fun metadataNoBatchUndoTvStatus(): String =
    "没有可撤销的 Bangumi 批量更改。"

fun localizedMetadataStatusText(status: String): String? =
    when (val trimmed = status.trim()) {
        metadataIndexedVideoRequiredTvStatus(),
        metadataQuerySetFromIndexTvStatus(),
        metadataSourceRequiredTvStatus(),
        metadataReviewNoMatchTvStatus(),
        metadataClearEntryRequiredTvStatus(),
        metadataNoBatchPreviewTvStatus(),
        metadataNoBatchUndoTvStatus() -> trimmed
        "Select an indexed video first." ->
            metadataIndexedVideoRequiredTvStatus()
        "Query set from selected index entry." ->
            metadataQuerySetFromIndexTvStatus()
        "Open or scan a source first." ->
            metadataSourceRequiredTvStatus()
        "Selected review has no matching indexed entries." ->
            metadataReviewNoMatchTvStatus()
        "Select an indexed video before clearing metadata." ->
            metadataClearEntryRequiredTvStatus()
        "Run Batch preview first; no high-confidence matches are ready." ->
            metadataNoBatchPreviewTvStatus()
        "No batch Bangumi changes are available to undo." ->
            metadataNoBatchUndoTvStatus()
        else -> localizedDynamicMetadataStatusText(trimmed)
    }

fun metadataStatusText(status: String): String =
    localizedMetadataStatusText(status) ?: status

fun metadataBatchStatusLabel(status: String): String =
    when (status) {
        "preview" -> "预览"
        "ready" -> "可用"
        "review" -> "复核"
        "conflict" -> "冲突"
        else -> status
    }

data class BangumiUiLabels(
    val title: String,
    val query: String,
    val useSelected: String,
    val search: String,
    val applyMatch: String,
    val clearMetadata: String,
    val batchPreview: String,
    val applyBatch: String,
    val undoBatch: String,
    val acceptReview: String,
    val syncProgress: String,
    val selectedIndex: String,
    val matches: String,
    val batchCandidates: String,
    val emptyResults: String,
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

private fun localizedDynamicMetadataStatusText(status: String): String? {
    if (status.isTvMetadataStatus()) {
        return status
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
