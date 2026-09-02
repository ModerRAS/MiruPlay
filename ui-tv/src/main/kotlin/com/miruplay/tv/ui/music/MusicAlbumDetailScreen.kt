package com.miruplay.tv.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.ui.components.LoadingIndicator
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicAlbumDetailScreen(
    onNavigateBack: () -> Unit,
    onPlayTrack: (String) -> Unit,
    viewModel: MusicAlbumDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OverscanContainer {
        when (val s = state) {
            is MusicAlbumDetailUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            is MusicAlbumDetailUiState.Error -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = s.message, style = TvTypography.body, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                TvButton(text = "返回", onClick = onNavigateBack)
            }
            is MusicAlbumDetailUiState.HasContent -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = s.album.title, style = TvTypography.title, color = TextPrimary)
                            Text(text = "${s.album.artist ?: "未知艺术家"} · ${s.tracks.size} 首", style = TvTypography.body, color = TextSecondary)
                        }
                        TvButton(text = "返回", onClick = onNavigateBack, modifier = Modifier.width(110.dp), secondary = true)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(s.tracks, key = { _, t -> t.id }) { idx, track ->
                            androidx.tv.material3.Card(onClick = { onPlayTrack(track.id) }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "${track.trackNumber ?: idx + 1}.", style = TvTypography.body, color = TextSecondary, modifier = Modifier.width(36.dp))
                                        Column {
                                            Text(text = track.title, style = TvTypography.body, color = TextPrimary, maxLines = 1)
                                            Text(text = track.artist ?: "", style = TvTypography.caption, color = TextSecondary, maxLines = 1)
                                        }
                                    }
                                    Text(text = formatDuration(track.duration), style = TvTypography.caption, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}
