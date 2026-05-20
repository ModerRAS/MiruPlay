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
import com.miruplay.tv.repository.selectedCandidateLabel
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
                Text("Bangumi metadata", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("Bangumi query", query, onValueChange = onQueryChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton(
                        "Use selected",
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
                        "Search",
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
                        "Apply match",
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
                        "Clear metadata",
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
                        "Batch preview",
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
                        "Apply batch",
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
                        "Undo batch",
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
                        "Accept review",
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
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Text("Selected index", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                SelectedIndexSummary(selectedIndexEntry)
                Text("Bangumi matches", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                if (visibleBatchMatches.isNotEmpty()) {
                    Text(
                        "Batch: ${batchMatches.size} quer${if (batchMatches.size == 1) "y" else "ies"} previewed",
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
                        "Batch candidates",
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
                    DesktopEmptyState("Search to show Bangumi matches.")
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
            Text("No indexed video selected.", color = TextSecondary, fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp)
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
                    entry.metadataTitle?.let { "Bangumi: $it" } ?: "Bangumi: not linked",
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
                status,
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
                            " / ${match.selectedCandidateLabel()}"
                        } else {
                            ""
                        }
                        it.bangumiCandidateSummary(candidateSuffix)
                    } ?: "No match",
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
