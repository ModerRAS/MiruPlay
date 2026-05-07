package com.miruplay.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.Episode
import com.miruplay.tv.ui.components.*
import com.miruplay.tv.ui.theme.*

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

    LaunchedEffect(animeId) {
        viewModel.loadAnime(animeId)
    }

    OverscanContainer {
        if (isLoading) {
            LoadingIndicator()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Back button
                TvButton(text = "← 返回", onClick = onNavigateBack)
                Spacer(Modifier.height(16.dp))

                anime?.let { animeData ->
                    // Fanart Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(DarkSurface, DarkBg)
                                )
                            )
                    ) {
                        // Black gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.7f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                        
                        // Title overlay at bottom
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(24.dp)
                        ) {
                            Text(
                                text = animeData.title,
                                style = TvTypography.title,
                                color = Color.White
                            )
                            val titleCn = animeData.titleCn
                            if (titleCn != null && titleCn != animeData.title) {
                                Text(
                                    text = titleCn,
                                    style = TvTypography.subtitle,
                                    color = TextSecondary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (animeData.rating > 0) {
                                    Text(
                                        text = "评分: ${animeData.rating}",
                                        color = WarningYellow,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = "${animeData.episodeCount} 集",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                animeData.airDate?.let {
                                    Text(text = it, color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Summary
                    if (animeData.summary?.isNotBlank() == true) {
                        Text(
                            text = animeData.summary,
                            color = TextSecondary,
                            style = TvTypography.body,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    // Genre tags
                    if (animeData.genres.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            animeData.genres.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(AccentBlue)
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        color = TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    
                    // Season selector
                    if (seasons.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            seasons.forEach { season ->
                                val isSelected = season.seasonNumber == selectedSeason
                                TvButton(
                                    text = "第 ${season.seasonNumber} 季",
                                    onClick = { viewModel.selectSeason(season.seasonNumber) },
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    // Episode list
                    episodes.forEach { (episode, progress) ->
                        EpisodeListItem(
                            episode = episode,
                            progress = progress?.let { 
                                it.positionMs.toFloat() / (episode.duration.toFloat().coerceAtLeast(1f))
                            } ?: 0f,
                            isWatched = progress?.let { it.playCount > 0 } ?: false,
                            onPlay = { onPlayEpisode(episode) }
                        )
                    }
                    
                    // Action buttons
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TvButton(text = "播放", onClick = {
                            episodes.firstOrNull()?.first?.let { onPlayEpisode(it) }
                        }, modifier = Modifier.width(240.dp))
                        
                        episodes.firstOrNull()?.first?.let { firstEp ->
                            if (firstEp.id.isNotEmpty()) {
                                TvButton(
                                    text = "从开头重新播放",
                                    onClick = { onPlayEpisode(firstEp) }
                                )
                            }
                        }
                        
                        TvButton(text = "重新获取元数据", onClick = {
                            viewModel.rescrapeMetadata()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: Episode,
    progress: Float,
    isWatched: Boolean,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isWatched -> ProgressGreen
                        progress > 0f -> ProgressGreen.copy(alpha = progress)
                        else -> TextSecondary.copy(alpha = 0.3f)
                    }
                )
        )
        
        Spacer(Modifier.width(12.dp))
        
        // Episode info
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = "第 ${episode.episodeNumber} 集",
                    color = TextPrimary,
                    fontSize = 16.sp
                )
                if (episode.title?.isNotBlank() == true) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = episode.title,
                        color = TextSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (episode.duration > 0) {
                Text(
                    text = "${episode.duration / 60000} 分钟",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
        
        // Play button
        TvButton(
            text = if (progress > 0f) "继续" else "播放",
            onClick = onPlay,
            modifier = Modifier.width(120.dp)
        )
    }
}