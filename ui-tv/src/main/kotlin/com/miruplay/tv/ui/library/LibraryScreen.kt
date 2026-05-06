package com.miruplay.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.row.TvLazyRow
import androidx.tv.foundation.lazy.row.items
import androidx.tv.material3.*
import com.miruplay.tv.model.Anime
import com.miruplay.tv.ui.components.*
import com.miruplay.tv.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val allAnime by viewModel.allAnime.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isEmpty = continueWatching.isEmpty() && recentlyAdded.isEmpty() && allAnime.isEmpty()
    
    OverscanContainer {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MiruPlay",
                    style = TvTypography.title,
                    color = AnimeRed
                )
                TvButton(text = "设置", onClick = onNavigateToSettings)
            }
            
            if (isLoading) {
                LoadingIndicator()
            } else if (isEmpty) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "添加媒体源开始使用",
                        color = TextSecondary,
                        style = TvTypography.subtitle,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    TvButton(
                        text = "添加源",
                        onClick = onNavigateToSettings
                    )
                }
            } else {
                // Content
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    // Continue Watching
                    if (continueWatching.isNotEmpty()) {
                        Text(
                            text = "继续观看",
                            style = TvTypography.subtitle,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                        ) {
                            items(continueWatching) { item ->
                                FocusableCard(
                                    title = item.anime?.title ?: "",
                                    subtitle = "第 ${item.episode?.episodeNumber} 集",
                                    progress = item.progress?.let { 
                                        it.positionMs.toFloat() / (item.episode?.duration?.toFloat() ?: 1f) 
                                    } ?: 0f,
                                    modifier = Modifier.padding(end = 12.dp),
                                    onClick = { onNavigateToDetail(item.anime?.id ?: "") }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(DarkSurface))
                                }
                            }
                        }
                    }
                    
                    // Recently Added
                    if (recentlyAdded.isNotEmpty()) {
                        Text(
                            text = "最近添加",
                            style = TvTypography.subtitle,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        TvLazyRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                        ) {
                            items(recentlyAdded) { anime ->
                                FocusableCard(
                                    title = anime.title,
                                    subtitle = anime.titleCn ?: "",
                                    modifier = Modifier.padding(end = 12.dp),
                                    onClick = { onNavigateToDetail(anime.id) }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(DarkSurface))
                                }
                            }
                        }
                    }
                    
                    // All Anime Grid
                    if (allAnime.isNotEmpty()) {
                        Text(
                            text = "所有番剧",
                            style = TvTypography.subtitle,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        TvLazyVerticalGrid(
                            columns = TvGridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allAnime) { anime ->
                                FocusableCard(
                                    title = anime.title,
                                    subtitle = "${anime.episodeCount} 集",
                                    modifier = Modifier.padding(8.dp),
                                    onClick = { onNavigateToDetail(anime.id) }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(DarkSurface))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}