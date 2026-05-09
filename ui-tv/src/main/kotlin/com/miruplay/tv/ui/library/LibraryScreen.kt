package com.miruplay.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
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
            
            when (val state = uiState) {
                is LibraryUiState.Loading -> {
                    LoadingIndicator()
                }
                
                is LibraryUiState.NoSources -> {
                    EmptyState(
                        message = "添加媒体源开始使用",
                        buttonText = "添加源",
                        onClick = onNavigateToSettings
                    )
                }
                
                is LibraryUiState.HasSources -> {
                    EmptyState(
                        message = "已配置媒体源\n点击刷新开始扫描",
                        buttonText = "设置",
                        onClick = onNavigateToSettings
                    )
                }
                
                is LibraryUiState.Scanning -> {
                    ScanningState(
                        state = state,
                        onCancel = { viewModel.cancelScan() }
                    )
                }
                
                is LibraryUiState.ScanError -> {
                    EmptyState(
                        message = state.message,
                        buttonText = "重试",
                        onClick = { viewModel.refresh() }
                    )
                }
                
                is LibraryUiState.HasContent -> {
                    LibraryContent(
                        continueWatching = state.continueWatching,
                        recentlyAdded = state.recentlyAdded,
                        allAnime = state.allAnime,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = TextSecondary,
            style = TvTypography.subtitle,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TvButton(text = buttonText, onClick = onClick)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScanningState(
    state: LibraryUiState.Scanning,
    onCancel: () -> Unit
) {
    var focusRequester = remember { FocusRequester() }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
            color = AnimeRed
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "正在扫描媒体库...",
            color = TextPrimary,
            style = TvTypography.subtitle
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "已扫描 ${state.filesScanned} 个文件",
            color = TextSecondary,
            style = TvTypography.body
        )
        if (state.currentPath.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.currentPath,
                color = TextSecondary,
                style = TvTypography.caption
            )
        }
        Spacer(Modifier.height(32.dp))
        TvButton(
            text = "取消扫描",
            onClick = onCancel,
            modifier = Modifier.focusRequester(focusRequester)
        )
        
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun LibraryContent(
    continueWatching: List<ProgressWithEpisode>,
    recentlyAdded: List<Anime>,
    allAnime: List<Anime>,
    onNavigateToDetail: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Continue Watching
        if (continueWatching.isNotEmpty()) {
            Text(
                text = "继续观看",
                style = TvTypography.subtitle,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                items(continueWatching.filter { it.anime != null }) { item ->
                    val animeId = item.anime?.id ?: return@items
                    FocusableCard(
                        title = item.anime.title,
                        subtitle = "第 ${item.episode?.episodeNumber ?: "?"} 集",
                        progress = item.progress?.let { rec ->
                            val duration = item.episode?.duration ?: 1L
                            (rec.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } ?: 0f,
                        modifier = Modifier.padding(end = 12.dp),
                        onClick = { onNavigateToDetail(animeId) }
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
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                items(recentlyAdded.filter { it.id.isNotBlank() }) { anime ->
                    FocusableCard(
                        title = anime.title.ifBlank { anime.titleCn ?: "未知" },
                        subtitle = anime.titleCn ?: "",
                        modifier = Modifier.padding(end = 12.dp),
                        onClick = { onNavigateToDetail(anime.id) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(DarkSurface))
                    }
                }
            }
        }

        // All Anime Grid — weight(1f) ensures it fills remaining space without infinite constraints
        if (allAnime.isNotEmpty()) {
            Text(
                text = "所有番剧",
                style = TvTypography.subtitle,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(allAnime.filter { it.id.isNotBlank() }) { anime ->
                    FocusableCard(
                        title = anime.title.ifBlank { anime.titleCn ?: "未知" },
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
