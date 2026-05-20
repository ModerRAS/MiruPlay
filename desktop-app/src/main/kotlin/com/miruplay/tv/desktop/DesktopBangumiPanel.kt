package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.confidencePercentLabel
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.displayName
import com.miruplay.tv.repository.isSameCandidate
import com.miruplay.tv.repository.statusFor

@Composable
internal fun BangumiPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedIndexEntry: MediaIndexEntry?,
    results: List<ScraperResult>,
    selectedResult: ScraperResult?,
    batchMatches: List<MetadataBatchMatch>,
    selectedBatchMatch: MetadataBatchMatch?,
    batchPlan: MetadataBatchPlan?,
    status: String,
    onUseSelectedEntry: () -> Unit,
    onSearch: () -> Unit,
    onBatchPreview: () -> Unit,
    onBatchApply: () -> Unit,
    onBatchUndo: () -> Unit,
    onBatchMatchSelected: (MetadataBatchMatch) -> Unit,
    onBatchCandidateSelected: (MetadataBatchMatch, ScraperResult) -> Unit,
    onBatchAcceptReview: () -> Unit,
    onResultSelected: (ScraperResult) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onFocusPreviousPanel: () -> Boolean = { false },
    focusVersion: Int = 0,
) {
    val labels = desktopBangumiUiLabels()
    val actionFocusRequesters = remember {
        BangumiAction.entries.associateWith { FocusRequester() }
    }
    val visibleBatchMatches = remember(batchMatches) { batchMatches.take(BANGUMI_BATCH_MATCH_LIMIT) }
    val visibleBatchCandidates = remember(selectedBatchMatch) {
        selectedBatchMatch
            ?.takeIf { it.candidates.size > 1 }
            ?.candidates
            ?.take(BANGUMI_CANDIDATE_LIMIT)
            .orEmpty()
    }
    val visibleResults = remember(results) { results.take(BANGUMI_RESULT_LIMIT) }
    val batchFocusRequesters = remember(visibleBatchMatches.map { it.query }) {
        List(visibleBatchMatches.size) { FocusRequester() }
    }
    val candidateFocusRequesters = remember(visibleBatchCandidates.map { it.animeId to it.title }) {
        List(visibleBatchCandidates.size) { FocusRequester() }
    }
    val resultFocusRequesters = remember(visibleResults.map { it.animeId to it.title }) {
        List(visibleResults.size) { FocusRequester() }
    }

    fun selectAndFocus(position: BangumiListPosition): Boolean =
        when (position.section) {
            BangumiListSection.BatchMatches -> {
                val match = visibleBatchMatches.getOrNull(position.index) ?: return false
                onBatchMatchSelected(match)
                batchFocusRequesters.getOrNull(position.index)?.requestFocus()
                true
            }
            BangumiListSection.BatchCandidates -> {
                val match = selectedBatchMatch ?: return false
                val candidate = visibleBatchCandidates.getOrNull(position.index) ?: return false
                onBatchCandidateSelected(match, candidate)
                candidateFocusRequesters.getOrNull(position.index)?.requestFocus()
                true
            }
            BangumiListSection.SearchResults -> {
                val result = visibleResults.getOrNull(position.index) ?: return false
                onResultSelected(result)
                resultFocusRequesters.getOrNull(position.index)?.requestFocus()
                true
            }
        }

    fun focusAction(action: BangumiAction): Boolean {
        actionFocusRequesters.getValue(action).requestFocus()
        return true
    }

    fun moveActionFocus(action: BangumiAction, key: Key): Boolean =
        when (
            val target = bangumiActionFocusTarget(
                current = action,
                key = key,
                batchMatchCount = visibleBatchMatches.size,
                candidateCount = visibleBatchCandidates.size,
                resultCount = visibleResults.size,
            )
        ) {
            is BangumiActionFocusTarget.Action -> focusAction(target.action)
            is BangumiActionFocusTarget.ListPosition -> selectAndFocus(target.position)
            BangumiActionFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            null -> false
        }

    fun moveListFocus(position: BangumiListPosition, key: Key): Boolean =
        bangumiListNavigationTarget(
            current = position,
            key = key,
            batchMatchCount = visibleBatchMatches.size,
            candidateCount = visibleBatchCandidates.size,
            resultCount = visibleResults.size,
        )?.let(::selectAndFocus)
            ?: bangumiListExitActionTarget(position, key)?.let(::focusAction)
            ?: false

    LaunchedEffect(
        visibleBatchMatches,
        visibleResults,
        selectedBatchMatch?.query,
        selectedResult?.animeId,
        focusVersion,
    ) {
        if (focusVersion > 0) {
            actionFocusRequesters.getValue(BangumiAction.UseSelected).requestFocus()
        } else if (visibleBatchMatches.isNotEmpty()) {
            val selectedIndex = visibleBatchMatches.indexOfFirst { it.query == selectedBatchMatch?.query }.coerceAtLeast(0)
            batchFocusRequesters.getOrNull(selectedIndex)?.requestFocus()
        } else if (visibleResults.isNotEmpty()) {
            val selectedIndex = visibleResults.indexOfFirst { it.animeId == selectedResult?.animeId }.coerceAtLeast(0)
            resultFocusRequesters.getOrNull(selectedIndex)?.requestFocus()
        }
    }

    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text(labels.title, color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField(labels.query, query, onValueChange = onQueryChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton(
                        labels.useSelected,
                        onClick = onUseSelectedEntry,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.UseSelected,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.UseSelected),
                                onMove = ::moveActionFocus,
                            ),
                    )
                    TvActionButton(
                        labels.search,
                        onClick = onSearch,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.Search,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.Search),
                                onMove = ::moveActionFocus,
                            ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton(
                        labels.applyMatch,
                        onClick = onApply,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.ApplyMatch,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.ApplyMatch),
                                onMove = ::moveActionFocus,
                            ),
                    )
                    TvActionButton(
                        labels.clearMetadata,
                        onClick = onClear,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.ClearMetadata,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.ClearMetadata),
                                onMove = ::moveActionFocus,
                            ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton(
                        labels.batchPreview,
                        onClick = onBatchPreview,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.BatchPreview,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.BatchPreview),
                                onMove = ::moveActionFocus,
                            ),
                    )
                    TvActionButton(
                        labels.applyBatch,
                        onClick = onBatchApply,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.ApplyBatch,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.ApplyBatch),
                                onMove = ::moveActionFocus,
                            ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton(
                        labels.undoBatch,
                        onClick = onBatchUndo,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.UndoBatch,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.UndoBatch),
                                onMove = ::moveActionFocus,
                            ),
                    )
                    TvActionButton(
                        labels.acceptReview,
                        onClick = onBatchAcceptReview,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.AcceptReview,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.AcceptReview),
                                onMove = ::moveActionFocus,
                            ),
                    )
                }
                StatusBox(desktopBangumiStatusText(status))
            }
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Text(labels.selectedIndex, color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                SelectedIndexSummary(selectedIndexEntry)
                Text(labels.matches, color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                if (visibleBatchMatches.isNotEmpty()) {
                    Text(
                        "批量：${batchMatches.size} 个查询已预览",
                        color = TextSecondary,
                        fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                    )
                    visibleBatchMatches.forEachIndexed { index, match ->
                        BangumiBatchMatchRow(
                            match = match,
                            selected = selectedBatchMatch?.query == match.query,
                            status = batchPlan.statusFor(match),
                            onClick = { onBatchMatchSelected(match) },
                            onNavigationKey = { key ->
                                moveListFocus(BangumiListPosition(BangumiListSection.BatchMatches, index), key)
                            },
                            modifier = Modifier.focusRequester(batchFocusRequesters[index]),
                        )
                    }
                }
                selectedBatchMatch?.takeIf { visibleBatchCandidates.isNotEmpty() }?.let { match ->
                    Text(
                        labels.batchCandidates,
                        color = TextPrimary,
                        fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    visibleBatchCandidates.forEachIndexed { index, candidate ->
                        BangumiResultRow(
                            result = candidate,
                            selected = candidate.isSameCandidate(match.result),
                            onClick = { onBatchCandidateSelected(match, candidate) },
                            onNavigationKey = { key ->
                                moveListFocus(BangumiListPosition(BangumiListSection.BatchCandidates, index), key)
                            },
                            modifier = Modifier.focusRequester(candidateFocusRequesters[index]),
                        )
                    }
                }
                if (visibleResults.isEmpty()) {
                    DesktopEmptyState(labels.emptyResults)
                } else {
                    visibleResults.forEachIndexed { index, result ->
                        BangumiResultRow(
                            result = result,
                            selected = selectedResult?.animeId == result.animeId,
                            onClick = { onResultSelected(result) },
                            onNavigationKey = { key ->
                                moveListFocus(BangumiListPosition(BangumiListSection.SearchResults, index), key)
                            },
                            modifier = Modifier.focusRequester(resultFocusRequesters[index]),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedIndexSummary(entry: MediaIndexEntry?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.STACK_GAP_DP.dp),
    ) {
        if (entry == null) {
            Text("尚未选择索引视频。", color = TextSecondary, fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.TINY_GAP_DP.dp)) {
                Text(
                    entry.displayName(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.metadataTitle?.let { "Bangumi：$it" } ?: "Bangumi：未关联",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.path,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BangumiResultRow(
    result: ScraperResult,
    selected: Boolean,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier.onPreviewKeyEvent { event ->
            bangumiRowKeyEvent(
                key = event.key,
                type = event.type,
                onClick = onClick,
                onNavigationKey = onNavigationKey,
            )
        },
    ) { active ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                result.confidencePercentLabel(),
                color = if (active) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(MiruPlayUiMetrics.BATCH_SCORE_WIDTH_DP.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    result.bangumiResultTitle(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "ID ${result.animeId} / ${result.title}",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BangumiBatchMatchRow(
    match: MetadataBatchMatch,
    selected: Boolean,
    status: String,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val result = match.result
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier.onPreviewKeyEvent { event ->
            bangumiRowKeyEvent(
                key = event.key,
                type = event.type,
                onClick = onClick,
                onNavigationKey = onNavigationKey,
            )
        },
        heightDp = MiruPlayUiMetrics.LIST_ROW_COMPACT_HEIGHT_DP,
        inactiveAlpha = 0.44f,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                desktopBangumiBatchStatusLabel(status),
                color = if (status == "conflict") AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(MiruPlayUiMetrics.BATCH_STATUS_WIDTH_DP.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    match.query,
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    result?.let {
                        val candidateSuffix = if (match.candidates.size > 1) {
                            " / ${match.desktopSelectedCandidateLabel()}"
                        } else {
                            ""
                        }
                        it.bangumiCandidateSummary(candidateSuffix)
                    } ?: "无匹配",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val BANGUMI_BATCH_MATCH_LIMIT = 4
private const val BANGUMI_CANDIDATE_LIMIT = 4
private const val BANGUMI_RESULT_LIMIT = 6

internal data class DesktopBangumiUiLabels(
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
    val selectedIndex: String,
    val matches: String,
    val batchCandidates: String,
    val emptyResults: String,
)

internal fun desktopBangumiUiLabels(): DesktopBangumiUiLabels =
    DesktopBangumiUiLabels(
        title = "Bangumi 元数据",
        query = "Bangumi 搜索词",
        useSelected = "使用当前条目",
        search = "搜索",
        applyMatch = "应用匹配",
        clearMetadata = "清除元数据",
        batchPreview = "批量预览",
        applyBatch = "应用批量",
        undoBatch = "撤销批量",
        acceptReview = "接受复核",
        selectedIndex = "当前索引",
        matches = "Bangumi 匹配",
        batchCandidates = "候选条目",
        emptyResults = "搜索后在这里显示 Bangumi 匹配。",
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

internal fun desktopBangumiStatusText(status: String): String =
    when {
        status == "Select an indexed video first." ->
            "请先选择一个索引视频。"
        status == "Query set from selected index entry." ->
            "已从当前索引条目填入搜索词。"
        status == "Open or scan a source first." ->
            "请先打开或扫描媒体源。"
        status == "Selected review has no matching indexed entries." ->
            "当前复核项没有匹配的索引条目。"
        status == "Select an indexed video before clearing metadata." ->
            "请先选择索引视频，再清除元数据。"
        status == "Run Batch preview first; no high-confidence matches are ready." ->
            "请先运行批量预览；当前没有可直接应用的高置信匹配。"
        status == "No batch Bangumi changes are available to undo." ->
            "没有可撤销的 Bangumi 批量更改。"
        else -> desktopBangumiDynamicStatusText(status) ?: status
    }

private fun desktopBangumiDynamicStatusText(status: String): String? {
    metadataInitialStatusRegex.matchEntire(status)?.let { match ->
        return "选择索引视频后可搜索 ${match.groupValues[1]}。"
    }
    metadataQueryRequiredRegex.matchEntire(status)?.let { match ->
        return "请输入 ${match.groupValues[1]} 搜索词，或先选择索引视频。"
    }
    metadataSearchStartedRegex.matchEntire(status)?.let { match ->
        return "正在搜索 ${match.groupValues[1]}：\"${match.groupValues[2]}\"..."
    }
    metadataNoMatchRegex.matchEntire(status)?.let { match ->
        return "没有匹配 \"${match.groupValues[2]}\" 的 ${match.groupValues[1]} 元数据。"
    }
    metadataFoundRegex.matchEntire(status)?.let { match ->
        return "找到 ${match.groupValues[1]} 个 ${match.groupValues[2]} 匹配。"
    }
    selectedBatchCandidateRegex.matchEntire(status)?.let { match ->
        return "已选择批量候选：${match.groupValues[1]} -> ${match.groupValues[2]}"
    }
    selectedBatchReviewRegex.matchEntire(status)?.let { match ->
        return "已选择批量复核：${match.groupValues[1]}"
    }
    metadataBatchResultRequiredRegex.matchEntire(status)?.let { match ->
        return "请先选择带 ${match.groupValues[1]} 结果的批量匹配。"
    }
    metadataReviewConflictRegex.matchEntire(status)?.let { match ->
        return "当前复核项有 ${match.groupValues[1]} 个元数据冲突，未覆盖任何内容。"
    }
    selectedMetadataRegex.matchEntire(status)?.let { match ->
        return "已选择：${match.groupValues[1]}"
    }
    metadataApplyRequiredRegex.matchEntire(status)?.let { match ->
        return "请先选择索引视频，再应用 ${match.groupValues[1]} 元数据。"
    }
    metadataSearchSelectionRequiredRegex.matchEntire(status)?.let { match ->
        return "请先搜索 ${match.groupValues[1]} 并选择一个匹配。"
    }
    metadataBatchAppliedRegex.matchEntire(status)?.let { match ->
        return "已将 Bangumi 批量元数据应用到 ${match.groupValues[1]} 个索引条目，跳过 ${match.groupValues[2]} 个冲突。"
    }
    metadataAppliedRegex.matchEntire(status)?.let { match ->
        return "已将 ${match.groupValues[1]} 元数据应用到 ${match.groupValues[2]}。"
    }
    metadataClearedRegex.matchEntire(status)?.let { match ->
        return "已清除 ${match.groupValues[1]} 的外部元数据。"
    }
    metadataBatchSearchingRegex.matchEntire(status)?.let { match ->
        return "正在用 ${match.groupValues[1]} 搜索 ${match.groupValues[2]} 个索引标题..."
    }
    noMetadataBatchEntriesRegex.matchEntire(status)?.let { match ->
        return "没有可用于 ${match.groupValues[1]} 批量匹配的索引条目。"
    }
    metadataPlanSummaryRegex.matchEntire(status)?.let { match ->
        return "${match.groupValues[1]} 个可应用，${match.groupValues[2]} 个需复核，${match.groupValues[3]} 个冲突"
    }
    metadataReviewAcceptedRegex.matchEntire(status)?.let { match ->
        return "已接受复核的 Bangumi 匹配，更新 ${match.groupValues[1]} 个索引条目。"
    }
    metadataBatchRestoredRegex.matchEntire(status)?.let { match ->
        return "已从上一次 Bangumi 批量更改中恢复 ${match.groupValues[1]} 个索引条目。"
    }
    return null
}

internal fun desktopBangumiBatchStatusLabel(status: String): String =
    when (status) {
        "preview" -> "预览"
        "ready" -> "可用"
        "review" -> "复核"
        "conflict" -> "冲突"
        else -> status
    }

internal fun MetadataBatchMatch.desktopSelectedCandidateLabel(): String {
    val selectedIndex = candidates.indexOfFirst { it.isSameCandidate(result) }
    return if (selectedIndex >= 0) {
        "候选 ${selectedIndex + 1}/${candidates.size}"
    } else {
        "${candidates.size} 个候选"
    }
}

internal enum class BangumiListSection {
    BatchMatches,
    BatchCandidates,
    SearchResults,
}

internal data class BangumiListPosition(
    val section: BangumiListSection,
    val index: Int,
)

internal enum class BangumiAction(
    val row: Int,
    val column: Int,
) {
    UseSelected(row = 0, column = 0),
    Search(row = 0, column = 1),
    ApplyMatch(row = 1, column = 0),
    ClearMetadata(row = 1, column = 1),
    BatchPreview(row = 2, column = 0),
    ApplyBatch(row = 2, column = 1),
    UndoBatch(row = 3, column = 0),
    AcceptReview(row = 3, column = 1),
}

internal sealed interface BangumiActionFocusTarget {
    data class Action(val action: BangumiAction) : BangumiActionFocusTarget
    data class ListPosition(val position: BangumiListPosition) : BangumiActionFocusTarget
    data object PreviousPanel : BangumiActionFocusTarget
}

internal fun bangumiActionFocusTarget(
    current: BangumiAction,
    key: Key,
    batchMatchCount: Int = 0,
    candidateCount: Int = 0,
    resultCount: Int = 0,
): BangumiActionFocusTarget? =
    when (key) {
        Key.DirectionLeft -> bangumiActionAt(current.row, current.column - 1)?.let(BangumiActionFocusTarget::Action)
        Key.DirectionRight ->
            bangumiActionAt(current.row, current.column + 1)?.let(BangumiActionFocusTarget::Action)
                ?: firstBangumiListPosition(
                    batchMatchCount = batchMatchCount,
                    candidateCount = candidateCount,
                    resultCount = resultCount,
                )?.takeIf { current.column == 1 }?.let(BangumiActionFocusTarget::ListPosition)
        Key.DirectionDown -> bangumiActionAt(current.row + 1, current.column)?.let(BangumiActionFocusTarget::Action)
        Key.DirectionUp -> {
            val target = bangumiActionAt(current.row - 1, current.column)
            if (target == null && current.row == 0) {
                BangumiActionFocusTarget.PreviousPanel
            } else {
                target?.let(BangumiActionFocusTarget::Action)
            }
        }
        else -> null
    }

private fun bangumiActionAt(row: Int, column: Int): BangumiAction? =
    BangumiAction.entries.firstOrNull { it.row == row && it.column == column }

private fun firstBangumiListPosition(
    batchMatchCount: Int,
    candidateCount: Int,
    resultCount: Int,
): BangumiListPosition? =
    when {
        batchMatchCount.coerceAtMost(BANGUMI_BATCH_MATCH_LIMIT) > 0 ->
            BangumiListPosition(BangumiListSection.BatchMatches, 0)
        candidateCount.coerceAtMost(BANGUMI_CANDIDATE_LIMIT) > 0 ->
            BangumiListPosition(BangumiListSection.BatchCandidates, 0)
        resultCount.coerceAtMost(BANGUMI_RESULT_LIMIT) > 0 ->
            BangumiListPosition(BangumiListSection.SearchResults, 0)
        else -> null
    }

internal fun bangumiListNavigationTarget(
    current: BangumiListPosition,
    key: Key,
    batchMatchCount: Int,
    candidateCount: Int,
    resultCount: Int,
): BangumiListPosition? {
    val visibleRows = buildList {
        repeat(batchMatchCount.coerceIn(0, BANGUMI_BATCH_MATCH_LIMIT)) { index ->
            add(BangumiListPosition(BangumiListSection.BatchMatches, index))
        }
        repeat(candidateCount.coerceIn(0, BANGUMI_CANDIDATE_LIMIT)) { index ->
            add(BangumiListPosition(BangumiListSection.BatchCandidates, index))
        }
        repeat(resultCount.coerceIn(0, BANGUMI_RESULT_LIMIT)) { index ->
            add(BangumiListPosition(BangumiListSection.SearchResults, index))
        }
    }
    val currentIndex = visibleRows.indexOf(current)
    if (currentIndex < 0) return null
    val targetIndex = when (key) {
        Key.DirectionRight -> {
            if (current.section == BangumiListSection.BatchMatches && candidateCount > 0) {
                return BangumiListPosition(
                    section = BangumiListSection.BatchCandidates,
                    index = current.index.coerceAtMost(candidateCount.coerceAtMost(BANGUMI_CANDIDATE_LIMIT) - 1),
                )
            }
            return null
        }
        Key.DirectionLeft -> {
            if (current.section == BangumiListSection.BatchCandidates && batchMatchCount > 0) {
                return BangumiListPosition(
                    section = BangumiListSection.BatchMatches,
                    index = current.index.coerceAtMost(batchMatchCount.coerceAtMost(BANGUMI_BATCH_MATCH_LIMIT) - 1),
                )
            }
            return null
        }
        Key.DirectionDown -> currentIndex + 1
        Key.DirectionUp -> currentIndex - 1
        else -> return null
    }
    return visibleRows.getOrNull(targetIndex)
}

internal fun bangumiListExitActionTarget(
    current: BangumiListPosition,
    key: Key,
): BangumiAction? =
    if (key != Key.DirectionLeft) {
        null
    } else {
        when (current.section) {
            BangumiListSection.BatchMatches -> BangumiAction.BatchPreview
            BangumiListSection.BatchCandidates -> null
            BangumiListSection.SearchResults -> BangumiAction.ApplyMatch
        }
    }

private fun Modifier.bangumiActionNavigation(
    action: BangumiAction,
    focusRequester: FocusRequester,
    onMove: (BangumiAction, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && onMove(action, event.key)
        }

private fun bangumiRowKeyEvent(
    key: Key,
    type: KeyEventType,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return when (key) {
        Key.Enter,
        Key.NumPadEnter,
        -> {
            onClick()
            true
        }
        else -> onNavigationKey(key)
    }
}

internal fun bangumiTopActionKeyEvent(
    key: Key,
    type: KeyEventType,
    onFocusPreviousPanel: () -> Boolean,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return bangumiActionFocusTarget(BangumiAction.UseSelected, key) == BangumiActionFocusTarget.PreviousPanel &&
        onFocusPreviousPanel()
}
