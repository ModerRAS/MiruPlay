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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.detailEpisodeCountLabel
import com.miruplay.tv.model.detailEpisodeSectionTitle
import com.miruplay.tv.model.detailEpisodeTitleLabel
import com.miruplay.tv.model.detailSeasonLabel
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.progressFraction
import com.miruplay.tv.model.progressLabel
import com.miruplay.tv.ui.components.LoadingIndicator
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.RemoteImage
import com.miruplay.tv.ui.components.TvButton
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
    val primaryActionEpisode by viewModel.primaryActionEpisode.collectAsStateWithLifecycle()
    val hasPlayableEpisodes by viewModel.hasPlayableEpisodes.collectAsStateWithLifecycle()
    val primaryActionLabel by viewModel.primaryActionLabel.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
    }
    BackHandler(onBack = onNavigateBack)

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
                primaryActionLabel = primaryActionLabel,
                actionMessage = actionMessage,
                primaryActionEpisode = primaryActionEpisode,
                hasPlayableEpisodes = hasPlayableEpisodes,
                onNavigateBack = onNavigateBack,
                onPlayEpisode = onPlayEpisode,
                onSelectSeason = viewModel::selectSeason,
                onRefreshMetadata = viewModel::refreshSeries,
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
    primaryActionLabel: String,
    actionMessage: String?,
    primaryActionEpisode: DramaEpisode?,
    hasPlayableEpisodes: Boolean,
    onNavigateBack: () -> Unit,
    onPlayEpisode: (DramaEpisode) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onRefreshMetadata: () -> Unit,
) {
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(series.id, primaryActionEpisode?.id, hasPlayableEpisodes) {
        if (hasPlayableEpisodes) {
            playButtonFocusRequester.requestFocus()
        }
    }

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
                                .focusRequester(playButtonFocusRequester),
                        )
                        TvButton(
                            text = "刷新信息",
                            icon = Icons.Filled.Refresh,
                            onClick = onRefreshMetadata,
                            modifier = Modifier.width(180.dp),
                        )
                        TvButton(
                            text = "返回",
                            onClick = onNavigateBack,
                            modifier = Modifier.width(160.dp),
                            secondary = true,
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
                    progress = episode.toPlaybackEpisode().progressFraction(progress),
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
        DramaStatPill(detailEpisodeCountLabel(series.episodeCount), TextSecondary)
        DramaStatPill("共 ${series.seasonCount} 季", TextSecondary)
        series.firstAirDate?.takeIf { it.isNotBlank() }?.let {
            DramaStatPill(it, WarningYellow)
        }
        series.tmdbId?.let {
            DramaStatPill("TMDB", AnimeRed)
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
