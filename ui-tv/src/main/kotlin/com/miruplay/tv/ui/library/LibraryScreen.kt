package com.miruplay.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.displayTitle
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
                Column {
                    Text(
                        text = "探索",
                        style = TvTypography.title,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "本地媒体库 · Bangumi 元数据",
                        style = TvTypography.body,
                        color = TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = "扫描",
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.scanNow() },
                        modifier = Modifier.width(132.dp)
                    )
                    TvButton(
                        text = "设置",
                        onClick = onNavigateToSettings,
                        modifier = Modifier.width(132.dp)
                    )
                }
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
                        message = "已配置媒体源\n点击扫描建立媒体库",
                        buttonText = "扫描媒体库",
                        onClick = { viewModel.scanNow() }
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
                        buttonText = "手动扫描",
                        onClick = { viewModel.scanNow() }
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
    val library = remember(allAnime) { allAnime.distinctBy { it.id }.sortedBy { it.displayTitle() } }
    val featured = remember(library) {
        library.sortedWith(
            compareByDescending<Anime> { it.rating }
                .thenByDescending { it.episodeCount }
                .thenBy { it.displayTitle() }
        ).take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (featured.isNotEmpty()) {
            SectionHeader(title = "最高热度", trailing = "已收录 ${library.size} 部")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            ) {
                items(featured, key = { it.id }) { anime ->
                    FeatureAnimeCard(
                        anime = anime,
                        onClick = { onNavigateToDetail(anime.id) }
                    )
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            SectionHeader(title = "继续观看")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            ) {
                items(continueWatching.filter { it.anime != null }, key = { it.episode?.id ?: it.anime?.id.orEmpty() }) { item ->
                    val anime = item.anime ?: return@items
                    val animeId = anime.id
                    val episodeNumber = item.episode?.episodeNumber
                    val duration = item.episode?.duration?.takeIf { it > 0 } ?: 1L
                    AnimePosterCard(
                        anime = anime,
                        subtitle = if (episodeNumber != null) "继续观看 ${episodeNumber.toString().padStart(2, '0')}" else "继续观看",
                        progress = item.progress?.let { rec ->
                            (rec.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } ?: 0f,
                        onClick = { onNavigateToDetail(animeId) }
                    )
                }
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            SectionHeader(title = "最近添加")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            ) {
                items(recentlyAdded.filter { it.id.isNotBlank() }, key = { it.id }) { anime ->
                    AnimePosterCard(
                        anime = anime,
                        onClick = { onNavigateToDetail(anime.id) }
                    )
                }
            }
        }

        if (library.isNotEmpty()) {
            SectionHeader(title = "海报墙")
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
            ) {
                library.filter { it.id.isNotBlank() }.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { anime ->
                            AnimePosterCard(
                                anime = anime,
                                width = 170.dp,
                                height = 254.dp,
                                onClick = { onNavigateToDetail(anime.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TvTypography.subtitle,
            color = TextPrimary
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = TvTypography.body,
                color = TextSecondary
            )
        }
    }
}
