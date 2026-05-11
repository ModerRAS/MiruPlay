package com.miruplay.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.ui.components.LoadingIndicator
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.RemoteImage
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.components.displayTitle
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    LaunchedEffect(animeId) {
        viewModel.loadAnime(animeId)
    }
    BackHandler(onBack = onNavigateBack)

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
                    actionMessage = actionMessage,
                    isSyncing = isSyncing,
                    onPlayEpisode = onPlayEpisode,
                    onSelectSeason = viewModel::selectSeason,
                    onRescrape = viewModel::rescrapeMetadata,
                    onSyncBangumi = viewModel::syncBangumi
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    anime: Anime,
    seasons: List<Int>,
    selectedSeason: Int,
    episodes: List<Pair<Episode, ProgressRecord?>>,
    actionMessage: String?,
    isSyncing: Boolean,
    onPlayEpisode: (Episode) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onRescrape: () -> Unit,
    onSyncBangumi: () -> Unit
) {
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(anime.id, episodes.isNotEmpty()) {
        if (episodes.isNotEmpty()) {
            playButtonFocusRequester.requestFocus()
        }
    }

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
                url = anime.fanartUrl ?: anime.posterUrl,
                contentDescription = anime.displayTitle(),
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
                        TvButton(
                            text = continueButtonText(episodes),
                            onClick = { episodes.firstOrNull()?.first?.let(onPlayEpisode) },
                            enabled = episodes.isNotEmpty(),
                            modifier = Modifier
                                .width(230.dp)
                                .focusRequester(playButtonFocusRequester)
                        )
                        TvButton(
                            text = "重新刮削",
                            icon = Icons.Filled.Refresh,
                            enabled = !isSyncing,
                            onClick = onRescrape,
                            modifier = Modifier.width(170.dp)
                        )
                        TvButton(
                            text = if (isSyncing) "同步中" else "同步 Bangumi",
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
                        text = "第 $season 季",
                        onClick = { onSelectSeason(season) },
                        modifier = Modifier.width(132.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        Text(text = "选集", style = TvTypography.subtitle, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        episodes.forEach { (episode, progress) ->
            EpisodeListItem(
                episode = episode,
                progress = progress?.let {
                    (it.positionMs.toFloat() / episode.duration.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                } ?: 0f,
                isWatched = progress?.let { it.playCount > 0 } == true ||
                    episode.bangumiCollectionType == 2,
                onPlay = { onPlayEpisode(episode) }
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun DetailStats(anime: Anime) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (anime.rating > 0) {
            StatPill("评分 ${"%.1f".format(anime.rating)}", WarningYellow)
        }
        if (anime.episodeCount > 0) {
            StatPill("全 ${anime.episodeCount} 话", TextSecondary)
        }
        anime.airDate?.takeIf { it.isNotBlank() }?.let {
            StatPill(it, TextSecondary)
        }
        anime.bangumiCollectionType?.let {
            StatPill("Bangumi ${subjectCollectionLabel(it)}", AnimeRed)
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
    isWatched: Boolean,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
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
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key.isActivateKey()) {
                    onPlay()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onPlay)
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
                    text = episode.episodeNumber.toString().padStart(2, '0'),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "第 ${episode.episodeNumber} 集${episode.title.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                color = TextPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = episode.fileName.ifBlank { episode.filePath.substringAfterLast('/') },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(18.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isWatched) "已看" else if (progress > 0f) "看到 ${(progress * 100).toInt()}%" else "未看",
                color = if (isWatched) ProgressGreen else TextSecondary,
                fontSize = 13.sp
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

private fun continueButtonText(episodes: List<Pair<Episode, ProgressRecord?>>): String {
    val next = episodes.firstOrNull { (_, progress) -> progress != null && progress.positionMs > 0L }
        ?: episodes.firstOrNull()
    return next?.first?.episodeNumber?.let { "继续观看 $it" } ?: "播放"
}

private fun Key.isActivateKey(): Boolean = this == Key.DirectionCenter ||
    this == Key.Enter ||
    this == Key.NumPadEnter ||
    this == Key.Spacebar

private fun subjectCollectionLabel(type: Int): String = when (type) {
    1 -> "想看"
    2 -> "看过"
    3 -> "在看"
    4 -> "搁置"
    5 -> "抛弃"
    else -> "已关联"
}
