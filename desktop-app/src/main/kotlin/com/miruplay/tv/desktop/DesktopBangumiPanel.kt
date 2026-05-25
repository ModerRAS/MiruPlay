package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayFocusAxis
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.design.focusIndexAfter
import com.miruplay.tv.design.gridFocusIndexAfter
import com.miruplay.tv.design.horizontalNavigationDelta
import com.miruplay.tv.design.verticalNavigationDelta
import com.miruplay.tv.model.BANGUMI_BATCH_MATCH_LIMIT
import com.miruplay.tv.model.BANGUMI_CANDIDATE_LIMIT
import com.miruplay.tv.model.BANGUMI_RESULT_LIMIT
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.bangumiCoercedPageStart
import com.miruplay.tv.model.bangumiPageStartForIndex
import com.miruplay.tv.model.bangumiPageSummary
import com.miruplay.tv.model.bangumiUiLabels
import com.miruplay.tv.model.confidencePercentLabel
import com.miruplay.tv.model.metadataBatchPageLabel
import com.miruplay.tv.model.metadataBatchPreviewedCountLabel
import com.miruplay.tv.model.metadataBatchStatusLabel
import com.miruplay.tv.model.metadataBangumiLinkedLabel
import com.miruplay.tv.model.metadataCandidatePageLabel
import com.miruplay.tv.model.metadataNoMatchLabel
import com.miruplay.tv.model.metadataNoSelectedIndexMessage
import com.miruplay.tv.model.metadataStatusText
import com.miruplay.tv.model.metadataSearchResultsPageLabel
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
    isSyncingProgress: Boolean = false,
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
    onSyncProgress: () -> Unit,
    onFocusPreviousPanel: () -> Boolean = { false },
    onFocusNextPanel: () -> Boolean = { false },
    focusVersion: Int = 0,
) {
    val labels = bangumiUiLabels(isSyncingProgress = isSyncingProgress)
    val actionFocusRequesters = remember {
        BangumiAction.entries.associateWith { FocusRequester() }
    }
    var batchPageStartState by remember(batchMatches.map { it.query }) { mutableStateOf(0) }
    val batchPageStart = bangumiCoercedPageStart(
        pageStart = batchPageStartState,
        itemCount = batchMatches.size,
        pageSize = BANGUMI_BATCH_MATCH_LIMIT,
    )
    val visibleBatchMatches = remember(batchMatches, batchPageStart) {
        batchMatches
            .drop(batchPageStart)
            .take(BANGUMI_BATCH_MATCH_LIMIT)
    }
    val batchCandidates = remember(selectedBatchMatch) {
        selectedBatchMatch
            ?.takeIf { it.candidates.size > 1 }
            ?.candidates
            .orEmpty()
    }
    var candidatePageStartState by remember(selectedBatchMatch?.query, batchCandidates.map { it.animeId to it.title }) {
        mutableStateOf(0)
    }
    val candidatePageStart = bangumiCoercedPageStart(
        pageStart = candidatePageStartState,
        itemCount = batchCandidates.size,
        pageSize = BANGUMI_CANDIDATE_LIMIT,
    )
    val visibleBatchCandidates = remember(batchCandidates, candidatePageStart) {
        batchCandidates
            .drop(candidatePageStart)
            .take(BANGUMI_CANDIDATE_LIMIT)
    }
    var resultPageStartState by remember(results.map { it.animeId to it.title }) { mutableStateOf(0) }
    val resultPageStart = bangumiCoercedPageStart(
        pageStart = resultPageStartState,
        itemCount = results.size,
        pageSize = BANGUMI_RESULT_LIMIT,
    )
    val visibleResults = remember(results, resultPageStart) {
        results
            .drop(resultPageStart)
            .take(BANGUMI_RESULT_LIMIT)
    }
    var pendingListFocus by remember { mutableStateOf<BangumiListPosition?>(null) }
    val batchFocusRequesters = remember(batchPageStart, visibleBatchMatches.map { it.query }) {
        List(visibleBatchMatches.size) { FocusRequester() }
    }
    val candidateFocusRequesters = remember(candidatePageStart, visibleBatchCandidates.map { it.animeId to it.title }) {
        List(visibleBatchCandidates.size) { FocusRequester() }
    }
    val resultFocusRequesters = remember(resultPageStart, visibleResults.map { it.animeId to it.title }) {
        List(visibleResults.size) { FocusRequester() }
    }
    val emptyResultsFocusRequester = remember { FocusRequester() }

    fun requestVisibleListFocus(position: BangumiListPosition): Boolean =
        when (position.section) {
            BangumiListSection.BatchMatches -> {
                val visibleIndex = position.index - batchPageStart
                batchFocusRequesters.getOrNull(visibleIndex)?.requestFocus() != null
            }
            BangumiListSection.BatchCandidates -> {
                val visibleIndex = position.index - candidatePageStart
                candidateFocusRequesters.getOrNull(visibleIndex)?.requestFocus() != null
            }
            BangumiListSection.SearchResults -> {
                val visibleIndex = position.index - resultPageStart
                resultFocusRequesters.getOrNull(visibleIndex)?.requestFocus() != null
            }
        }

    fun requestListFocus(position: BangumiListPosition): Boolean {
        when (position.section) {
            BangumiListSection.BatchMatches -> {
                if (position.index !in batchMatches.indices) return false
                val targetPageStart = bangumiPageStartForIndex(
                    index = position.index,
                    itemCount = batchMatches.size,
                    pageSize = BANGUMI_BATCH_MATCH_LIMIT,
                )
                batchPageStartState = targetPageStart
            }
            BangumiListSection.BatchCandidates -> {
                if (position.index !in batchCandidates.indices) return false
                val targetPageStart = bangumiPageStartForIndex(
                    index = position.index,
                    itemCount = batchCandidates.size,
                    pageSize = BANGUMI_CANDIDATE_LIMIT,
                )
                candidatePageStartState = targetPageStart
            }
            BangumiListSection.SearchResults -> {
                if (position.index !in results.indices) return false
                val targetPageStart = bangumiPageStartForIndex(
                    index = position.index,
                    itemCount = results.size,
                    pageSize = BANGUMI_RESULT_LIMIT,
                )
                resultPageStartState = targetPageStart
            }
        }
        pendingListFocus = position
        requestVisibleListFocus(position)
        return true
    }

    fun selectAndFocus(position: BangumiListPosition): Boolean {
        when (position.section) {
            BangumiListSection.BatchMatches -> {
                val match = batchMatches.getOrNull(position.index) ?: return false
                onBatchMatchSelected(match)
            }
            BangumiListSection.BatchCandidates -> {
                val match = selectedBatchMatch ?: return false
                val candidate = batchCandidates.getOrNull(position.index) ?: return false
                onBatchCandidateSelected(match, candidate)
            }
            BangumiListSection.SearchResults -> {
                val result = results.getOrNull(position.index) ?: return false
                onResultSelected(result)
            }
        }
        return requestListFocus(position)
    }

    fun focusAction(action: BangumiAction): Boolean {
        pendingListFocus = null
        actionFocusRequesters.getValue(action).requestFocus()
        return true
    }

    fun focusEmptyResults(): Boolean {
        if (visibleResults.isNotEmpty()) return false
        pendingListFocus = null
        emptyResultsFocusRequester.requestFocus()
        return true
    }

    fun requestBangumiFocus(target: BangumiActionFocusTarget?): Boolean =
        when (target) {
            is BangumiActionFocusTarget.Action -> focusAction(target.action)
            is BangumiActionFocusTarget.ListPosition -> selectAndFocus(target.position)
            BangumiActionFocusTarget.EmptyResults -> focusEmptyResults()
            BangumiActionFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            BangumiActionFocusTarget.NextPanel -> onFocusNextPanel()
            null -> false
        }

    fun moveActionFocus(action: BangumiAction, intent: MiruPlayInputIntent): Boolean =
        requestBangumiFocus(
            bangumiActionFocusTarget(
                current = action,
                intent = intent,
                batchMatchCount = batchMatches.size,
                candidateCount = batchCandidates.size,
                resultCount = results.size,
            ),
        )

    fun moveListFocus(position: BangumiListPosition, intent: MiruPlayInputIntent): Boolean =
        bangumiListNavigationTarget(
            current = position,
            intent = intent,
            batchMatchCount = batchMatches.size,
            candidateCount = batchCandidates.size,
            resultCount = results.size,
        )?.let(::selectAndFocus)
            ?: requestBangumiFocus(
                bangumiListExitFocusTarget(
                    current = position,
                    intent = intent,
                    batchMatchCount = batchMatches.size,
                    candidateCount = batchCandidates.size,
                    resultCount = results.size,
                ),
            )

    fun moveEmptyResultsFocus(intent: MiruPlayInputIntent): Boolean =
        requestBangumiFocus(
            bangumiEmptyResultsFocusTarget(
                intent = intent,
                batchMatchCount = batchMatches.size,
                candidateCount = batchCandidates.size,
            ),
        )

    LaunchedEffect(
        batchPageStart,
        visibleBatchMatches,
        candidatePageStart,
        visibleBatchCandidates,
        resultPageStart,
        visibleResults,
        pendingListFocus,
    ) {
        val position = pendingListFocus ?: return@LaunchedEffect
        if (requestVisibleListFocus(position)) {
            pendingListFocus = null
        }
    }

    LaunchedEffect(
        batchMatches.map { it.query },
        results.map { it.animeId to it.title },
        selectedBatchMatch?.query,
        selectedResult?.animeId,
        focusVersion,
    ) {
        if (focusVersion > 0) {
            pendingListFocus = null
            actionFocusRequesters.getValue(BangumiAction.UseSelected).requestFocus()
            return@LaunchedEffect
        }
        if (pendingListFocus != null) return@LaunchedEffect
        if (batchMatches.isNotEmpty()) {
            val selectedIndex = batchMatches
                .indexOfFirst { it.query == selectedBatchMatch?.query }
                .coerceAtLeast(0)
            requestListFocus(BangumiListPosition(BangumiListSection.BatchMatches, selectedIndex))
        } else if (results.isNotEmpty()) {
            val selectedIndex = results
                .indexOfFirst { it.animeId == selectedResult?.animeId }
                .coerceAtLeast(0)
            requestListFocus(BangumiListPosition(BangumiListSection.SearchResults, selectedIndex))
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
                        labels.syncProgress,
                        onClick = onSyncProgress,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .bangumiActionNavigation(
                                action = BangumiAction.SyncProgress,
                                focusRequester = actionFocusRequesters.getValue(BangumiAction.SyncProgress),
                                onMove = ::moveActionFocus,
                            ),
                    )
                }
                TvActionButton(
                    labels.acceptReview,
                    onClick = onBatchAcceptReview,
                    secondary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bangumiActionNavigation(
                            action = BangumiAction.AcceptReview,
                            focusRequester = actionFocusRequesters.getValue(BangumiAction.AcceptReview),
                            onMove = ::moveActionFocus,
                        ),
                )
                StatusBox(metadataStatusText(status))
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
                        metadataBatchPreviewedCountLabel(batchMatches.size),
                        color = TextSecondary,
                        fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                    )
                    visibleBatchMatches.forEachIndexed { index, match ->
                        val absoluteIndex = batchPageStart + index
                        BangumiBatchMatchRow(
                            match = match,
                            selected = selectedBatchMatch?.query == match.query,
                            status = batchPlan.statusFor(match),
                            onClick = { selectAndFocus(BangumiListPosition(BangumiListSection.BatchMatches, absoluteIndex)) },
                            onNavigationIntent = { intent ->
                                moveListFocus(BangumiListPosition(BangumiListSection.BatchMatches, absoluteIndex), intent)
                            },
                            modifier = Modifier.focusRequester(batchFocusRequesters[index]),
                        )
                    }
                    bangumiPageSummary(
                        label = metadataBatchPageLabel(),
                        pageStart = batchPageStart,
                        visibleCount = visibleBatchMatches.size,
                        itemCount = batchMatches.size,
                        pageSize = BANGUMI_BATCH_MATCH_LIMIT,
                    )?.let { summary ->
                        Text(
                            summary,
                            color = TextSecondary,
                            fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
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
                        val absoluteIndex = candidatePageStart + index
                        BangumiResultRow(
                            result = candidate,
                            selected = candidate.isSameCandidate(match.result),
                            onClick = { selectAndFocus(BangumiListPosition(BangumiListSection.BatchCandidates, absoluteIndex)) },
                            onNavigationIntent = { intent ->
                                moveListFocus(BangumiListPosition(BangumiListSection.BatchCandidates, absoluteIndex), intent)
                            },
                            modifier = Modifier.focusRequester(candidateFocusRequesters[index]),
                        )
                    }
                    bangumiPageSummary(
                        label = metadataCandidatePageLabel(),
                        pageStart = candidatePageStart,
                        visibleCount = visibleBatchCandidates.size,
                        itemCount = batchCandidates.size,
                        pageSize = BANGUMI_CANDIDATE_LIMIT,
                    )?.let { summary ->
                        Text(
                            summary,
                            color = TextSecondary,
                            fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                        )
                    }
                }
                if (visibleResults.isEmpty()) {
                    BangumiEmptyResultsState(
                        text = labels.emptyResults,
                        focusRequester = emptyResultsFocusRequester,
                        onMove = ::moveEmptyResultsFocus,
                    )
                } else {
                    visibleResults.forEachIndexed { index, result ->
                        val absoluteIndex = resultPageStart + index
                        BangumiResultRow(
                            result = result,
                            selected = selectedResult?.animeId == result.animeId,
                            onClick = { selectAndFocus(BangumiListPosition(BangumiListSection.SearchResults, absoluteIndex)) },
                            onNavigationIntent = { intent ->
                                moveListFocus(BangumiListPosition(BangumiListSection.SearchResults, absoluteIndex), intent)
                            },
                            modifier = Modifier.focusRequester(resultFocusRequesters[index]),
                        )
                    }
                    bangumiPageSummary(
                        label = metadataSearchResultsPageLabel(),
                        pageStart = resultPageStart,
                        visibleCount = visibleResults.size,
                        itemCount = results.size,
                        pageSize = BANGUMI_RESULT_LIMIT,
                    )?.let { summary ->
                        Text(
                            summary,
                            color = TextSecondary,
                            fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiEmptyResultsState(
    text: String,
    focusRequester: FocusRequester,
    onMove: (MiruPlayInputIntent) -> Boolean,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = Modifier
            .focusRequester(focusRequester),
        heightDp = MiruPlayUiMetrics.EMPTY_STATE_HEIGHT_DP,
        inactiveAlpha = 0.48f,
        onNavigationIntent = onMove,
    ) { active ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (active) TextPrimary else TextSecondary,
                fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp,
            )
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
            Text(metadataNoSelectedIndexMessage(), color = TextSecondary, fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp)
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
                    metadataBangumiLinkedLabel(entry.metadataTitle),
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
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        onNavigationIntent = onNavigationIntent,
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
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val result = match.result
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        heightDp = MiruPlayUiMetrics.LIST_ROW_COMPACT_HEIGHT_DP,
        inactiveAlpha = 0.44f,
        onNavigationIntent = onNavigationIntent,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                metadataBatchStatusLabel(status),
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
                    } ?: metadataNoMatchLabel(),
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    SyncProgress(row = 3, column = 1),
    AcceptReview(row = 4, column = 0),
}

internal sealed interface BangumiActionFocusTarget {
    data class Action(val action: BangumiAction) : BangumiActionFocusTarget
    data class ListPosition(val position: BangumiListPosition) : BangumiActionFocusTarget
    data object EmptyResults : BangumiActionFocusTarget
    data object PreviousPanel : BangumiActionFocusTarget
    data object NextPanel : BangumiActionFocusTarget
}

internal fun bangumiActionFocusTarget(
    current: BangumiAction,
    key: Key,
    batchMatchCount: Int = 0,
    candidateCount: Int = 0,
    resultCount: Int = 0,
): BangumiActionFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        bangumiActionFocusTarget(
            current = current,
            intent = intent,
            batchMatchCount = batchMatchCount,
            candidateCount = candidateCount,
            resultCount = resultCount,
        )
    }

internal fun bangumiActionFocusTarget(
    current: BangumiAction,
    intent: MiruPlayInputIntent,
    batchMatchCount: Int = 0,
    candidateCount: Int = 0,
    resultCount: Int = 0,
): BangumiActionFocusTarget? {
    val actions = BangumiAction.entries.sortedWith(compareBy(BangumiAction::row, BangumiAction::column))
    val currentIndex = actions.indexOf(current)
    val targetAction = gridFocusIndexAfter(
        currentIndex = currentIndex,
        intent = intent,
        columns = BANGUMI_ACTION_COLUMNS,
        itemCount = actions.size,
    )?.let(actions::get)
    if (targetAction != null) {
        return BangumiActionFocusTarget.Action(targetAction)
    }
    return when {
        intent.horizontalNavigationDelta() == 1 && current.exitsToBangumiList() ->
            firstBangumiListPosition(
                batchMatchCount = batchMatchCount,
                candidateCount = candidateCount,
                resultCount = resultCount,
            )?.let(BangumiActionFocusTarget::ListPosition)
                ?: BangumiActionFocusTarget.EmptyResults.takeIf {
                    batchMatchCount == 0 && candidateCount == 0 && resultCount == 0
                }
        intent.verticalNavigationDelta() == -1 && current.row == 0 ->
            BangumiActionFocusTarget.PreviousPanel
        intent.verticalNavigationDelta() == 1 && current.row == BangumiAction.entries.maxOf { it.row } ->
            BangumiActionFocusTarget.NextPanel
        else -> null
    }
}

private const val BANGUMI_ACTION_COLUMNS = 2

private fun BangumiAction.exitsToBangumiList(): Boolean =
    column == BANGUMI_ACTION_COLUMNS - 1 || this == BangumiAction.AcceptReview

private fun firstBangumiListPosition(
    batchMatchCount: Int,
    candidateCount: Int,
    resultCount: Int,
): BangumiListPosition? =
    when {
        batchMatchCount > 0 ->
            BangumiListPosition(BangumiListSection.BatchMatches, 0)
        candidateCount > 0 ->
            BangumiListPosition(BangumiListSection.BatchCandidates, 0)
        resultCount > 0 ->
            BangumiListPosition(BangumiListSection.SearchResults, 0)
        else -> null
    }

internal fun bangumiListNavigationTarget(
    current: BangumiListPosition,
    key: Key,
    batchMatchCount: Int,
    candidateCount: Int,
    resultCount: Int,
): BangumiListPosition? =
    key.toMiruPlayInputIntent()?.let { intent ->
        bangumiListNavigationTarget(
            current = current,
            intent = intent,
            batchMatchCount = batchMatchCount,
            candidateCount = candidateCount,
            resultCount = resultCount,
        )
    }

internal fun bangumiListNavigationTarget(
    current: BangumiListPosition,
    intent: MiruPlayInputIntent,
    batchMatchCount: Int,
    candidateCount: Int,
    resultCount: Int,
): BangumiListPosition? {
    val safeBatchMatchCount = batchMatchCount.coerceAtLeast(0)
    val safeCandidateCount = candidateCount.coerceAtLeast(0)
    val safeResultCount = resultCount.coerceAtLeast(0)
    val visibleRows = buildList {
        repeat(safeBatchMatchCount) { index ->
            add(BangumiListPosition(BangumiListSection.BatchMatches, index))
        }
        repeat(safeCandidateCount) { index ->
            add(BangumiListPosition(BangumiListSection.BatchCandidates, index))
        }
        repeat(safeResultCount) { index ->
            add(BangumiListPosition(BangumiListSection.SearchResults, index))
        }
    }
    val currentIndex = visibleRows.indexOf(current)
    if (currentIndex < 0) return null
    val targetIndex = when (intent.horizontalNavigationDelta()) {
        1 -> {
            if (current.section == BangumiListSection.BatchMatches && safeCandidateCount > 0) {
                return BangumiListPosition(
                    section = BangumiListSection.BatchCandidates,
                    index = current.index.coerceAtMost(safeCandidateCount - 1),
                )
            }
            return null
        }
        -1 -> {
            if (current.section == BangumiListSection.BatchCandidates && safeBatchMatchCount > 0) {
                return BangumiListPosition(
                    section = BangumiListSection.BatchMatches,
                    index = current.index.coerceAtMost(safeBatchMatchCount - 1),
                )
            }
            return null
        }
        else -> focusIndexAfter(
            currentIndex = currentIndex,
            intent = intent,
            axis = MiruPlayFocusAxis.Vertical,
            itemCount = visibleRows.size,
        ) ?: return null
    }
    return visibleRows.getOrNull(targetIndex)
}

internal fun bangumiListExitActionTarget(
    current: BangumiListPosition,
    key: Key,
): BangumiAction? =
    (bangumiListExitFocusTarget(current, key) as? BangumiActionFocusTarget.Action)?.action

internal fun bangumiListExitActionTarget(
    current: BangumiListPosition,
    intent: MiruPlayInputIntent,
): BangumiAction? =
    (bangumiListExitFocusTarget(current, intent) as? BangumiActionFocusTarget.Action)?.action

internal fun bangumiListExitFocusTarget(
    current: BangumiListPosition,
    key: Key,
    batchMatchCount: Int = 0,
    candidateCount: Int = 0,
    resultCount: Int = 0,
): BangumiActionFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        bangumiListExitFocusTarget(
            current = current,
            intent = intent,
            batchMatchCount = batchMatchCount,
            candidateCount = candidateCount,
            resultCount = resultCount,
        )
    }

internal fun bangumiListExitFocusTarget(
    current: BangumiListPosition,
    intent: MiruPlayInputIntent,
    batchMatchCount: Int = 0,
    candidateCount: Int = 0,
    resultCount: Int = 0,
): BangumiActionFocusTarget? =
    when (intent.horizontalNavigationDelta()) {
        -1 -> when (current.section) {
            BangumiListSection.BatchMatches -> BangumiActionFocusTarget.Action(BangumiAction.BatchPreview)
            BangumiListSection.BatchCandidates -> null
            BangumiListSection.SearchResults -> BangumiActionFocusTarget.Action(BangumiAction.ApplyMatch)
        }
        else -> when (intent.verticalNavigationDelta()) {
            1 -> BangumiActionFocusTarget.NextPanel.takeIf {
                bangumiListNavigationTarget(
                    current = current,
                    intent = intent,
                    batchMatchCount = batchMatchCount,
                    candidateCount = candidateCount,
                    resultCount = resultCount,
                ) == null
            }?.let { target ->
                if (resultCount == 0 && current.section != BangumiListSection.SearchResults) {
                    BangumiActionFocusTarget.EmptyResults
                } else {
                    target
                }
            }
            else -> null
        }
    }

internal fun bangumiEmptyResultsFocusTarget(
    key: Key,
    batchMatchCount: Int = 0,
    candidateCount: Int = 0,
): BangumiActionFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        bangumiEmptyResultsFocusTarget(
            intent = intent,
            batchMatchCount = batchMatchCount,
            candidateCount = candidateCount,
        )
    }

internal fun bangumiEmptyResultsFocusTarget(
    intent: MiruPlayInputIntent,
    batchMatchCount: Int = 0,
    candidateCount: Int = 0,
): BangumiActionFocusTarget? =
    when (intent.horizontalNavigationDelta()) {
        -1 -> BangumiActionFocusTarget.Action(BangumiAction.ApplyMatch)
        else -> when (intent.verticalNavigationDelta()) {
            -1 -> lastBangumiListPosition(
                batchMatchCount = batchMatchCount,
                candidateCount = candidateCount,
            )?.let(BangumiActionFocusTarget::ListPosition)
                ?: BangumiActionFocusTarget.Action(BangumiAction.Search)
            1 -> BangumiActionFocusTarget.NextPanel
            else -> null
        }
    }

private fun lastBangumiListPosition(
    batchMatchCount: Int,
    candidateCount: Int,
): BangumiListPosition? =
    when {
        candidateCount > 0 ->
            BangumiListPosition(
                BangumiListSection.BatchCandidates,
                candidateCount - 1,
            )
        batchMatchCount > 0 ->
            BangumiListPosition(
                BangumiListSection.BatchMatches,
                batchMatchCount - 1,
            )
        else -> null
    }

private fun Modifier.bangumiActionNavigation(
    action: BangumiAction,
    focusRequester: FocusRequester,
    onMove: (BangumiAction, MiruPlayInputIntent) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationIntentHandler { intent -> onMove(action, intent) }

internal fun bangumiTopActionKeyEvent(
    key: Key,
    type: KeyEventType,
    onFocusPreviousPanel: () -> Boolean,
): Boolean =
    desktopNavigationKeyEvent(key, type) { navigationKey ->
        bangumiActionFocusTarget(BangumiAction.UseSelected, navigationKey) == BangumiActionFocusTarget.PreviousPanel &&
            onFocusPreviousPanel()
    }
