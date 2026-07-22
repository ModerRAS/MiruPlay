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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.availableVersions
import com.miruplay.tv.model.confidencePercentLabel
import com.miruplay.tv.model.continueActionLabel
import com.miruplay.tv.model.continueEpisode
import com.miruplay.tv.model.detailBangumiCollectionPillLabel
import com.miruplay.tv.model.detailBangumiCandidateTermsSectionTitle
import com.miruplay.tv.model.detailBangumiManualCloseActionLabel
import com.miruplay.tv.model.detailBangumiManualMatchTitleLabel
import com.miruplay.tv.model.detailBangumiManualSearchRequiredMessage
import com.miruplay.tv.model.detailEpisodeCountLabel
import com.miruplay.tv.model.detailEpisodeSectionTitle
import com.miruplay.tv.model.detailEpisodeTitleLabel
import com.miruplay.tv.model.detailRatingLabel
import com.miruplay.tv.model.detailRescrapeActionLabel
import com.miruplay.tv.model.detailSeasonLabel
import com.miruplay.tv.model.detailSyncProgressActionLabel
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.metadataApplyMatchActionLabel
import com.miruplay.tv.model.metadataEmptyResultsMessage
import com.miruplay.tv.model.metadataQueryFieldLabel
import com.miruplay.tv.model.metadataSearchActionLabel
import com.miruplay.tv.model.metadataSearchResultsPageLabel
import com.miruplay.tv.model.isCompleted
import com.miruplay.tv.model.progressFraction
import com.miruplay.tv.model.progressLabel
import com.miruplay.tv.model.withVersion
import com.miruplay.tv.ui.components.EpisodeVersionDialog
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
fun AnimeDetailScreen(
    animeId: String,
    onNavigateBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    viewModel: AnimeDetailViewModel = hiltViewModel()
) {
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val episodes by viewModel.episodesWithProgress.collectAsStateWithLifecycle()
    val extras by viewModel.extrasWithProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val manualMatch by viewModel.manualMatch.collectAsStateWithLifecycle()
    var versionChoice by remember { mutableStateOf<Episode?>(null) }
    val requestPlay: (Episode) -> Unit = { episode ->
        if (episode.availableVersions().size > 1) {
            versionChoice = episode
        } else {
            onPlayEpisode(episode)
        }
    }

    LaunchedEffect(animeId) {
        viewModel.loadAnime(animeId)
    }
    BackHandler(enabled = manualMatch.isOpen) {
        if (!manualMatch.isSearching && !manualMatch.isApplying) {
            viewModel.closeRescrapeMatcher()
        }
    }
    BackHandler(enabled = versionChoice != null) {
        versionChoice = null
    }
    BackHandler(enabled = !manualMatch.isOpen && versionChoice == null, onBack = onNavigateBack)

    Box(modifier = Modifier.fillMaxSize()) {
        OverscanContainer {
            if (isLoading) {
                LoadingIndicator()
            } else {
                anime?.let { animeData ->
                    DetailContent(
                        anime = animeData,
                        seasons = seasons.map { it.seasonNumber },
                        selectedSeason = selectedSeason,
                        episodes = episodes,
                        extras = extras,
                        actionMessage = actionMessage,
                        isSyncing = isSyncing,
                        onPlayEpisode = requestPlay,
                        onSelectSeason = viewModel::selectSeason,
                        onRescrape = viewModel::openRescrapeMatcher,
                        onSyncBangumi = viewModel::syncBangumi
                    )
                }
            }
        }

        versionChoice?.let { episode ->
            EpisodeVersionDialog(
                episode = episode,
                onDismiss = { versionChoice = null },
                onPlay = { version ->
                    versionChoice = null
                    onPlayEpisode(episode.withVersion(version))
                },
            )
        }

        if (manualMatch.isOpen) {
            BangumiManualMatchDialog(
                state = manualMatch,
                onDismiss = viewModel::closeRescrapeMatcher,
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
private fun DetailContent(
    anime: Anime,
    seasons: List<Int>,
    selectedSeason: Int,
    episodes: List<Pair<Episode, ProgressRecord?>>,
    extras: List<Pair<Episode, ProgressRecord?>>,
    actionMessage: String?,
    isSyncing: Boolean,
    onPlayEpisode: (Episode) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onRescrape: () -> Unit,
    onSyncBangumi: () -> Unit
) {
    val primaryActionFocus = rememberInitialFocusHandle(
        key = anime.id,
        enabled = episodes.isNotEmpty(),
    )
    val backdropUrl = anime.fanartUrl ?: anime.posterUrl
    val backdropLocalPath = if (anime.fanartUrl.isNullOrBlank()) anime.posterLocalPath else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
        ) {
            RemoteImage(
                url = backdropUrl,
                contentDescription = anime.displayTitle(),
                localPath = backdropLocalPath,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DarkBg.copy(alpha = 0.96f),
                                DarkBg.copy(alpha = 0.72f),
                                DarkBg.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                RemoteImage(
                    url = anime.posterUrl,
                    contentDescription = anime.displayTitle(),
                    localPath = anime.posterLocalPath,
                    modifier = Modifier
                        .width(205.dp)
                        .height(302.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(26.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anime.displayTitle(),
                        style = TvTypography.title,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val originalTitle = anime.title.takeIf { it.isNotBlank() && it != anime.displayTitle() }
                    if (originalTitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = originalTitle,
                            style = TvTypography.body,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    DetailStats(anime)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val continueTarget = episodes.continueEpisode()
                        TvButton(
                            text = episodes.continueActionLabel(),
                            onClick = { continueTarget?.let(onPlayEpisode) },
                            enabled = continueTarget != null,
                            modifier = Modifier
                                .width(230.dp)
                                .then(primaryActionFocus.modifier())
                        )
                        if (!anime.id.startsWith("mlip:")) {
                            TvButton(
                                text = detailRescrapeActionLabel(),
                                icon = Icons.Filled.Refresh,
                                enabled = !isSyncing,
                                onClick = onRescrape,
                                modifier = Modifier.width(170.dp)
                            )
                        }
                        TvButton(
                            text = detailSyncProgressActionLabel(isSyncing),
                            icon = Icons.Filled.Sync,
                            enabled = !isSyncing,
                            onClick = onSyncBangumi,
                            modifier = Modifier.width(210.dp)
                        )
                    }
                    if (!actionMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = actionMessage,
                            color = if (actionMessage.contains("完成") || actionMessage.contains("已更新")) ProgressGreen else WarningYellow,
                            style = TvTypography.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        if (anime.summary.isNotBlank()) {
            Text(
                text = anime.summary,
                color = TextSecondary,
                style = TvTypography.body,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(18.dp))
        }

        if (anime.genres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                anime.genres.take(8).forEach { genre ->
                    TagChip(genre)
                }
            }
            Spacer(Modifier.height(24.dp))
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

        Text(text = detailEpisodeSectionTitle(), style = TvTypography.subtitle, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        episodes.forEach { (episode, progress) ->
            EpisodeListItem(
                episode = episode,
                progress = episode.progressFraction(progress),
                progressText = episode.progressLabel(progress),
                isWatched = episode.isCompleted(progress),
                onPlay = { onPlayEpisode(episode) }
            )
        }
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text(text = "特典", style = TvTypography.subtitle, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            extras.forEach { (extra, progress) ->
                EpisodeListItem(
                    episode = extra,
                    progress = extra.progressFraction(progress),
                    progressText = extra.progressLabel(progress),
                    isWatched = extra.isCompleted(progress),
                    isExtra = true,
                    onPlay = { onPlayEpisode(extra) },
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BangumiManualMatchDialog(
    state: BangumiManualMatchUiState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleCandidate: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (ScraperResult) -> Unit,
    onApply: () -> Unit,
) {
    val busy = state.isSearching || state.isApplying
    val dialogMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.9f).coerceAtLeast(420.dp)

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 840.dp)
                .heightIn(max = dialogMaxHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = detailBangumiManualMatchTitleLabel(),
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = detailBangumiManualCloseActionLabel(),
                    icon = Icons.Filled.Close,
                    enabled = !busy,
                    onClick = onDismiss,
                    modifier = Modifier.width(140.dp),
                    secondary = true
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                item(key = "candidate-title") {
                    Text(
                        text = detailBangumiCandidateTermsSectionTitle(),
                        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
                if (state.candidateTerms.isEmpty()) {
                    item(key = "candidate-empty") {
                        Text(
                            text = detailBangumiManualSearchRequiredMessage(),
                            style = TvTypography.body,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(state.candidateTerms, key = { it }) { candidate ->
                        ManualCandidateChip(
                            text = candidate,
                            selected = candidate in state.selectedCandidateTerms,
                            enabled = !busy,
                            onClick = { onToggleCandidate(candidate) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item(key = "query-row") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            label = metadataQueryFieldLabel(),
                            modifier = Modifier.weight(1f)
                        )
                        TvButton(
                            text = metadataSearchActionLabel(),
                            icon = Icons.Filled.Search,
                            enabled = !busy && (state.query.isNotBlank() || state.selectedCandidateTerms.isNotEmpty()),
                            onClick = onSearch,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }

                item(key = "results-title") {
                    Text(
                        text = metadataSearchResultsPageLabel(),
                        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
                if (state.results.isEmpty()) {
                    item(key = "results-empty") {
                        Text(
                            text = metadataEmptyResultsMessage(),
                            style = TvTypography.body,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(state.results, key = { "${it.source.name}:${it.animeId}" }) { result ->
                        ManualResultItem(
                            result = result,
                            selected = state.selectedResult?.animeId == result.animeId &&
                                state.selectedResult?.source == result.source,
                            enabled = !busy,
                            onClick = { onSelectResult(result) }
                        )
                    }
                }

                if (!state.statusMessage.isNullOrBlank()) {
                    item(key = "status-message") {
                        Text(
                            text = state.statusMessage,
                            style = TvTypography.body,
                            color = if (state.statusMessage.contains("找到") ||
                                state.statusMessage.contains("已选择") ||
                                state.statusMessage.contains("已更新")
                            ) {
                                ProgressGreen
                            } else {
                                WarningYellow
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = metadataApplyMatchActionLabel(),
                    icon = Icons.Filled.CheckCircle,
                    enabled = !busy && state.selectedResult != null,
                    onClick = onApply,
                    modifier = Modifier.width(180.dp)
                )
                TvButton(
                    text = detailBangumiManualCloseActionLabel(),
                    icon = Icons.Filled.Close,
                    enabled = !busy,
                    onClick = onDismiss,
                    modifier = Modifier.width(140.dp),
                    secondary = true
                )
            }
        }
    }
}

@Composable
private fun ManualCandidateChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        !enabled -> DarkSurface
        selected -> AnimeRed.copy(alpha = 0.28f)
        isFocused -> AccentBlue.copy(alpha = 0.74f)
        else -> CardBg
    }
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.12f)
    }

    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .tvFocusableClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) TextPrimary else TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ManualResultItem(
    result: ScraperResult,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        selected -> AnimeRed.copy(alpha = 0.24f)
        isFocused -> AccentBlue.copy(alpha = 0.68f)
        else -> CardBg
    }
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.1f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .tvFocusableClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.displayTitle(),
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(result.title, result.matchedTitle)
                    .filter { it.isNotBlank() && it != result.displayTitle() }
                    .distinct()
                    .joinToString(" · ")
                    .ifBlank { result.animeId },
                style = TvTypography.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
            Text(
                text = result.confidencePercentLabel(),
                style = TvTypography.body.copy(fontWeight = FontWeight.Bold),
                color = if (selected) ProgressGreen else TextSecondary,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = result.animeId,
                style = TvTypography.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailStats(anime: Anime) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (anime.rating > 0) {
            StatPill(detailRatingLabel(anime.rating), WarningYellow)
        }
        if (anime.episodeCount > 0) {
            StatPill(detailEpisodeCountLabel(anime.episodeCount), TextSecondary)
        }
        anime.airDate?.takeIf { it.isNotBlank() }?.let {
            StatPill(it, TextSecondary)
        }
        anime.bangumiCollectionType?.let {
            StatPill(detailBangumiCollectionPillLabel(it), AnimeRed)
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = text, color = color, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun TagChip(genre: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AccentBlue.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = genre,
            color = TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeListItem(
    episode: Episode,
    progress: Float,
    progressText: String,
    isWatched: Boolean,
    onPlay: () -> Unit,
    isExtra: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.015f else 1f, label = "episodeScale")

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
                shape = RoundedCornerShape(8.dp)
            )
            .tvFocusableClickable(
                interactionSource = interactionSource,
                onClick = onPlay
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isWatched) ProgressGreen else AnimeRed.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            if (isWatched) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = if (isExtra) "EX" else episode.episodeNumber.toString().padStart(2, '0'),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isExtra) episode.title.ifBlank { episode.fileName } else {
                    detailEpisodeTitleLabel(episode.episodeNumber, episode.title)
                },
                color = TextPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = episode.availableVersions().joinToString("\n") { it.filePath },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = episode.availableVersions().size.coerceIn(1, 4),
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(18.dp))
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(180.dp)
        ) {
            Text(
                text = progressText,
                color = if (isWatched) ProgressGreen else TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(if (isWatched) ProgressGreen else AnimeRed)
                )
            }
        }
    }
}

private fun Episode.displayPath(): String {
    val sourcePath = id
        .takeIf { it.substringBefore(':').toLongOrNull() != null }
        ?.substringAfter(':')
        ?.takeIf { it.isNotBlank() }

    return sourcePath ?: Uri.decode(filePath).ifBlank { fileName }
}
