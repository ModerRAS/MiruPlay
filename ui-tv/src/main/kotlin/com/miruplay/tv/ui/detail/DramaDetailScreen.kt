package com.miruplay.tv.ui.detail

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.aggregatedSourceLabel
import com.miruplay.tv.model.boundMetadataProviderRef
import com.miruplay.tv.model.detailEpisodeSectionTitle
import com.miruplay.tv.model.detailEpisodeTitleLabel
import com.miruplay.tv.model.detailSeasonLabel
import com.miruplay.tv.model.dramaEpisodeCountLabel
import com.miruplay.tv.model.dramaMetadataStatusMessage
import com.miruplay.tv.model.dramaRefreshActionLabel
import com.miruplay.tv.model.dramaSeasonCountLabel
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.progressLabel
import com.miruplay.tv.model.providerDisplayLabel
import com.miruplay.tv.model.providerStableKey
import com.miruplay.tv.ui.drama.DramaBackdropArtworkPlaceholder
import com.miruplay.tv.ui.drama.DramaPosterArtworkPlaceholder
import com.miruplay.tv.ui.drama.dramaEpisodeProgressIndicatorFraction
import com.miruplay.tv.ui.components.LoadingIndicator
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.RemoteImage
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.components.TvTextField
import com.miruplay.tv.ui.components.rememberInitialFocusHandle
import com.miruplay.tv.ui.components.tvFocusableClickable
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.DarkBg
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.ProgressGreen
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import com.miruplay.tv.ui.theme.WarningYellow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DramaDetailScreen(
    seriesId: String,
    onNavigateBack: () -> Unit,
    onPlayEpisode: (DramaEpisode) -> Unit,
    viewModel: DramaDetailViewModel = hiltViewModel(),
) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val episodes by viewModel.episodesWithProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshingMetadata by viewModel.isRefreshingMetadata.collectAsStateWithLifecycle()
    val hasTmdbTokenConfigured by viewModel.hasTmdbTokenConfigured.collectAsStateWithLifecycle()
    val canRefreshBoundMetadata by viewModel.canRefreshBoundMetadata.collectAsStateWithLifecycle()
    val primaryActionEpisode by viewModel.primaryActionEpisode.collectAsStateWithLifecycle()
    val hasPlayableEpisodes by viewModel.hasPlayableEpisodes.collectAsStateWithLifecycle()
    val primaryActionLabel by viewModel.primaryActionLabel.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val manualMatch by viewModel.manualMatch.collectAsStateWithLifecycle()

    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
    }
    BackHandler(enabled = manualMatch.isOpen) {
        if (!manualMatch.isSearching && !manualMatch.isApplying) {
            viewModel.closeManualMatch()
        }
    }
    BackHandler(enabled = !manualMatch.isOpen, onBack = onNavigateBack)

    Box(modifier = Modifier.fillMaxSize()) {
        OverscanContainer {
            if (isLoading) {
                LoadingIndicator()
            } else {
                val currentSeries = series
                if (currentSeries == null) {
                    DramaDetailMissingState(onNavigateBack = onNavigateBack)
                    return@OverscanContainer
                }
                DramaDetailContent(
                    series = currentSeries,
                    seasons = seasons.map { it.seasonNumber },
                    selectedSeason = selectedSeason,
                    episodes = episodes,
                    isRefreshingMetadata = isRefreshingMetadata,
                    hasTmdbTokenConfigured = hasTmdbTokenConfigured,
                    canRefreshBoundMetadata = canRefreshBoundMetadata,
                    primaryActionLabel = primaryActionLabel,
                    actionMessage = actionMessage,
                    primaryActionEpisode = primaryActionEpisode,
                    hasPlayableEpisodes = hasPlayableEpisodes,
                    onNavigateBack = onNavigateBack,
                    onPlayEpisode = onPlayEpisode,
                    onSelectSeason = viewModel::selectSeason,
                    onRefreshMetadata = viewModel::refreshSeries,
                    onOpenManualMatch = viewModel::openManualMatch,
                )
            }
        }

        if (manualMatch.isOpen) {
            DramaManualMatchDialog(
                state = manualMatch,
                onDismiss = viewModel::closeManualMatch,
                onQueryChange = viewModel::updateManualMatchQuery,
                onToggleCandidate = viewModel::toggleManualMatchCandidate,
                onSearch = viewModel::searchManualMatches,
                onSelectResult = viewModel::selectManualMatchResult,
                onApply = viewModel::applyManualMatch,
            )
        }
    }
}

@Composable
private fun DramaDetailMissingState(
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "没有找到这部电视剧",
            style = TvTypography.title,
            color = TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "可能是源被删了，或者这条内容还没重新扫出来。",
            style = TvTypography.body,
            color = TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        TvButton(
            text = "返回",
            onClick = onNavigateBack,
        )
    }
}

@Composable
private fun DramaDetailContent(
    series: com.miruplay.tv.model.DramaSeries,
    seasons: List<Int>,
    selectedSeason: Int,
    episodes: List<Pair<DramaEpisode, ProgressRecord?>>,
    isRefreshingMetadata: Boolean,
    hasTmdbTokenConfigured: Boolean,
    canRefreshBoundMetadata: Boolean,
    primaryActionLabel: String,
    actionMessage: String?,
    primaryActionEpisode: DramaEpisode?,
    hasPlayableEpisodes: Boolean,
    onNavigateBack: () -> Unit,
    onPlayEpisode: (DramaEpisode) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onRefreshMetadata: () -> Unit,
    onOpenManualMatch: () -> Unit,
) {
    val primaryActionFocus = rememberInitialFocusHandle(
        key = series.id,
        enabled = hasPlayableEpisodes,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface),
        ) {
            RemoteImage(
                url = series.fanartUrl ?: series.posterUrl,
                contentDescription = series.displayTitle(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = { DramaBackdropArtworkPlaceholder(title = series.displayTitle()) },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DarkBg.copy(alpha = 0.96f),
                                DarkBg.copy(alpha = 0.72f),
                                DarkBg.copy(alpha = 0.35f),
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                RemoteImage(
                    url = series.posterUrl,
                    contentDescription = series.displayTitle(),
                    modifier = Modifier
                        .width(205.dp)
                        .height(302.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = { DramaPosterArtworkPlaceholder(title = series.displayTitle()) },
                )
                Spacer(Modifier.width(26.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = series.displayTitle(),
                        style = TvTypography.title,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val originalTitle = series.originalTitle.takeIf {
                        it.isNotBlank() && it != series.displayTitle()
                    }
                    if (originalTitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = originalTitle,
                            style = TvTypography.body,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    DramaDetailStats(series)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(
                            text = primaryActionLabel,
                            onClick = { primaryActionEpisode?.let(onPlayEpisode) },
                            enabled = primaryActionEpisode != null,
                            modifier = Modifier
                                .width(230.dp)
                                .then(primaryActionFocus.modifier()),
                        )
                        TvButton(
                            text = dramaRefreshActionLabel(isRefreshingMetadata),
                            icon = Icons.Filled.Refresh,
                            onClick = onRefreshMetadata,
                            enabled = !isRefreshingMetadata,
                            modifier = Modifier.width(180.dp),
                        )
                        TvButton(
                            text = "手动匹配",
                            icon = Icons.Filled.Search,
                            onClick = onOpenManualMatch,
                            enabled = !isRefreshingMetadata,
                            modifier = Modifier.width(170.dp),
                        )
                    }
                    if (!actionMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        val positiveMessage = actionMessage.contains("已切换") ||
                            actionMessage.contains("已刷新") ||
                            actionMessage.contains("已更新")
                        Text(
                            text = actionMessage,
                            color = if (positiveMessage) ProgressGreen else WarningYellow,
                            style = TvTypography.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        DramaMetadataHintCard(
            message = dramaMetadataStatusMessage(
                hasBoundMetadata = series.boundMetadataProviderRef() != null,
                hasTmdbToken = hasTmdbTokenConfigured,
                isRefreshing = isRefreshingMetadata,
                boundProviderLabel = series.boundMetadataProviderRef()?.source,
                canRefreshBoundMetadata = canRefreshBoundMetadata,
            ),
            highlightColor = when {
                isRefreshingMetadata -> AccentBlue
                series.boundMetadataProviderRef() != null -> ProgressGreen
                hasTmdbTokenConfigured -> TextSecondary
                else -> WarningYellow
            },
            iconColor = when {
                isRefreshingMetadata -> AccentBlue
                series.boundMetadataProviderRef() != null -> ProgressGreen
                hasTmdbTokenConfigured -> TextSecondary
                else -> WarningYellow
            },
        )

        Spacer(Modifier.height(18.dp))

        if (series.summary.isNotBlank()) {
            Text(
                text = series.summary,
                color = TextSecondary,
                style = TvTypography.body,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
        }

        if (seasons.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                seasons.forEach { season ->
                    TvButton(
                        text = detailSeasonLabel(season),
                        onClick = { onSelectSeason(season) },
                        modifier = Modifier.width(132.dp),
                        secondary = selectedSeason != season,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        Text(
            text = detailEpisodeSectionTitle(),
            style = TvTypography.subtitle,
            color = TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        if (episodes.isEmpty()) {
            DramaEpisodeEmptyState(
                message = actionMessage ?: "当前季还没有可播放剧集。",
                onNavigateBack = onNavigateBack,
            )
        } else {
            episodes.forEach { (episode, progress) ->
                DramaEpisodeListItem(
                    episode = episode,
                    progress = dramaEpisodeProgressIndicatorFraction(episode, progress),
                    progressText = episode.toPlaybackEpisode().progressLabel(progress),
                    isWatched = episode.toPlaybackEpisode().progressLabel(progress) == "已看",
                    onPlay = { onPlayEpisode(episode) },
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DramaManualMatchDialog(
    state: DramaManualMatchUiState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleCandidate: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (DramaMetadataSearchResult) -> Unit,
    onApply: () -> Unit,
) {
    val busy = state.isSearching || state.isApplying
    val dialogMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.9f).coerceAtLeast(420.dp)

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 860.dp)
                .heightIn(max = dialogMaxHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "在线手动匹配",
                    style = TvTypography.subtitle,
                    color = TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = "关闭",
                    icon = Icons.Filled.Close,
                    enabled = !busy,
                    onClick = onDismiss,
                    modifier = Modifier.width(140.dp),
                    secondary = true,
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                item(key = "candidate-title") {
                    Text(
                        text = "候选标题",
                        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                }
                if (state.candidateTerms.isEmpty()) {
                    item(key = "candidate-empty") {
                        Text(
                            text = "当前没有可用的本地候选标题，请直接输入搜索词。",
                            style = TvTypography.body,
                            color = TextSecondary,
                        )
                    }
                } else {
                    items(state.candidateTerms, key = { it }) { candidate ->
                        DramaManualCandidateChip(
                            text = candidate,
                            selected = candidate in state.selectedCandidateTerms,
                            enabled = !busy,
                            onClick = { onToggleCandidate(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item(key = "query-row") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TvTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            label = "搜索词",
                            modifier = Modifier.weight(1f),
                        )
                        TvButton(
                            text = "搜索",
                            icon = Icons.Filled.Search,
                            enabled = !busy && (state.query.isNotBlank() || state.selectedCandidateTerms.isNotEmpty()),
                            onClick = onSearch,
                            modifier = Modifier.width(150.dp),
                        )
                    }
                }

                item(key = "results-title") {
                    Text(
                        text = "搜索结果",
                        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                    )
                }
                if (state.results.isEmpty()) {
                    item(key = "results-empty") {
                        Text(
                            text = "还没有在线结果，先点搜索。",
                            style = TvTypography.body,
                            color = TextSecondary,
                        )
                    }
                } else {
                    items(state.results, key = { it.providerStableKey() }) { result ->
                        DramaManualMatchResultItem(
                            result = result,
                            selected = state.selectedResult?.providerStableKey() == result.providerStableKey(),
                            enabled = !busy,
                            onClick = { onSelectResult(result) },
                        )
                    }
                }

                if (!state.statusMessage.isNullOrBlank()) {
                    item(key = "status-message") {
                        Text(
                            text = state.statusMessage,
                            style = TvTypography.body,
                            color = if (
                                state.statusMessage.contains("找到") ||
                                state.statusMessage.contains("已选择") ||
                                state.statusMessage.contains("已应用")
                            ) {
                                ProgressGreen
                            } else {
                                WarningYellow
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "应用匹配",
                    icon = Icons.Filled.CheckCircle,
                    enabled = !busy && state.selectedResult != null,
                    onClick = onApply,
                    modifier = Modifier.width(180.dp),
                )
                TvButton(
                    text = "关闭",
                    icon = Icons.Filled.Close,
                    enabled = !busy,
                    onClick = onDismiss,
                    modifier = Modifier.width(140.dp),
                    secondary = true,
                )
            }
        }
    }
}

@Composable
private fun DramaManualCandidateChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> AnimeRed.copy(alpha = 0.76f)
                    isFocused -> AccentBlue.copy(alpha = 0.72f)
                    else -> CardBg
                },
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
            )
            .tvFocusableClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.Search,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = TextPrimary,
            style = TvTypography.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DramaManualMatchResultItem(
    result: DramaMetadataSearchResult,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        selected -> AnimeRed
        isFocused -> FocusBorder
        else -> Color.White.copy(alpha = 0.08f)
    }
    val backgroundColor = when {
        selected -> AnimeRed.copy(alpha = 0.16f)
        isFocused -> AccentBlue.copy(alpha = 0.7f)
        else -> CardBg
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(if (isFocused || selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .tvFocusableClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = result.posterUrl,
            contentDescription = result.displayTitle(),
            modifier = Modifier
                .width(90.dp)
                .height(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            placeholder = { DramaPosterArtworkPlaceholder(title = result.displayTitle()) },
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.displayTitle(),
                color = TextPrimary,
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            result.originalTitle.takeIf { it.isNotBlank() && it != result.displayTitle() }?.let { originalTitle ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = originalTitle,
                    color = TextSecondary,
                    style = TvTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                result.firstAirDate?.takeIf { it.isNotBlank() }?.let { airDate ->
                    DramaStatPill(airDate, WarningYellow)
                }
                if (result.sourceLabels.size > 1) {
                    DramaStatPill("聚合 ${result.aggregatedSourceLabel()}", TextSecondary)
                    DramaStatPill("应用 ${result.providerDisplayLabel()} #${result.providerRef.id}", TextSecondary)
                } else {
                    DramaStatPill("${result.providerDisplayLabel()} #${result.providerRef.id}", TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = result.summary.ifBlank { "这条结果还没有简介。" },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DramaMetadataHintCard(
    message: String,
    highlightColor: Color,
    iconColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(highlightColor.copy(alpha = 0.12f))
            .border(1.dp, highlightColor.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (highlightColor == ProgressGreen) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = message,
            color = TextPrimary,
            style = TvTypography.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DramaEpisodeEmptyState(
    message: String,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = message,
            color = TextSecondary,
            style = TvTypography.body,
        )
        TvButton(
            text = "返回",
            onClick = onNavigateBack,
            modifier = Modifier.width(160.dp),
            secondary = true,
        )
    }
}

@Composable
private fun DramaDetailStats(
    series: com.miruplay.tv.model.DramaSeries,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        DramaStatPill(dramaEpisodeCountLabel(series.episodeCount), TextSecondary)
        DramaStatPill(dramaSeasonCountLabel(series.seasonCount), TextSecondary)
        series.firstAirDate?.takeIf { it.isNotBlank() }?.let {
            DramaStatPill(it, WarningYellow)
        }
        series.boundMetadataProviderRef()?.let { providerRef ->
            DramaStatPill(providerRef.source, AnimeRed)
        }
    }
}

@Composable
private fun DramaStatPill(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, color = color, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun DramaEpisodeListItem(
    episode: DramaEpisode,
    progress: Float,
    progressText: String,
    isWatched: Boolean,
    onPlay: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.015f else 1f, label = "dramaEpisodeScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) AccentBlue.copy(alpha = 0.68f) else CardBg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
            )
            .tvFocusableClickable(
                interactionSource = interactionSource,
                onClick = onPlay,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isWatched) ProgressGreen else AnimeRed.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isWatched) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = episode.episodeNumber.toString().padStart(2, '0'),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detailEpisodeTitleLabel(episode.episodeNumber, episode.title),
                color = TextPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = episode.summary.ifBlank { episode.displayPath() },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(18.dp))
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(180.dp),
        ) {
            Text(
                text = progressText,
                color = if (isWatched) ProgressGreen else TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(if (isWatched) ProgressGreen else AnimeRed),
                )
            }
        }
    }
}

private fun DramaEpisode.toPlaybackEpisode() =
    com.miruplay.tv.model.Episode(
        id = id,
        animeId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = title,
        filePath = filePath,
        fileName = fileName,
    )

private fun DramaEpisode.displayPath(): String {
    val sourcePath = id
        .takeIf { it.substringBefore(':').toLongOrNull() != null }
        ?.substringAfter(':')
        ?.takeIf { it.isNotBlank() }

    return sourcePath ?: Uri.decode(filePath).ifBlank { fileName }
}
