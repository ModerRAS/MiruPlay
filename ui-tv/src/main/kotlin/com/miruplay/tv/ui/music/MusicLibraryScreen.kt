package com.miruplay.tv.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.MusicAlbum
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.ui.components.LoadingIndicator
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val headerFocus = remember { FocusRequester() }

    OverscanContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "音乐", style = TvTypography.title, color = TextPrimary)
                    Text(text = "本地 / WebDAV / SMB", style = TvTypography.body, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val isScanning = scanState is LibraryScanState.Scanning
                    if (!isScanning) {
                        TvButton(text = "扫描", icon = Icons.Filled.Refresh, onClick = { viewModel.scanNow() }, modifier = Modifier.width(110.dp).focusRequester(headerFocus))
                    } else {
                        TvButton(text = "取消", onClick = { viewModel.cancelScan() }, modifier = Modifier.width(110.dp), secondary = true)
                    }
                    TvButton(text = "设置", onClick = onNavigateToSettings, modifier = Modifier.width(110.dp))
                }
            }

            if (scanState is LibraryScanState.Scanning) {
                val s = scanState as LibraryScanState.Scanning
                Box(modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(12.dp)) {
                    Text(text = "扫描中: ${s.filesScanned} 文件 · ${s.currentPath}", style = TvTypography.caption, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
            }

            when (val state = uiState) {
                is MusicLibraryUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                is MusicLibraryUiState.NoSources -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "还没有音乐媒体源", style = TvTypography.subtitle, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        TvButton(text = "去添加", onClick = onNavigateToSettings)
                    }
                }
                is MusicLibraryUiState.HasSourcesNoContent -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "暂无专辑，请扫描", style = TvTypography.subtitle, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        TvButton(text = "立即扫描", onClick = { viewModel.scanNow() })
                    }
                }
                is MusicLibraryUiState.ScanError -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, style = TvTypography.body, color = TextSecondary)
                }
                is MusicLibraryUiState.HasContent -> {
                    if (state.albums.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "没有匹配的专辑", style = TvTypography.body, color = TextSecondary)
                        }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
                            items(state.albums, key = { it.id }) { album ->
                                MusicAlbumCard(album = album, onClick = { onNavigateToDetail(album.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MusicAlbumCard(album: MusicAlbum, onClick: () -> Unit) {
    androidx.tv.material3.Card(onClick = onClick, modifier = Modifier.width(180.dp).height(220.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(96.dp).background(DarkSurface), contentAlignment = Alignment.Center) {
                Text(text = album.title.take(2), style = TvTypography.title, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text(text = album.title, style = TvTypography.body, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(text = album.artist ?: "未知艺术家", style = TvTypography.caption, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(text = "${album.trackCount} 首", style = TvTypography.caption, color = TextSecondary)
        }
    }
}
