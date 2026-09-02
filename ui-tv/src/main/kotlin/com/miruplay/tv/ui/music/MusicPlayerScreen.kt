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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.player.MusicRepeatMode
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    trackId: String,
    onNavigateBack: () -> Unit,
    viewModel: MusicPlayerViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(trackId) {
        if (trackId.isNotBlank()) viewModel.playTrackById(trackId)
    }
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val current = queue.currentTrack

    OverscanContainer(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TvButton(text = "返回", onClick = onNavigateBack, modifier = Modifier.width(110.dp), secondary = true)
                Text(text = "音乐播放", style = TvTypography.body, color = TextSecondary)
                Spacer(Modifier.width(110.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(220.dp).background(DarkSurface), contentAlignment = Alignment.Center) {
                    Text(text = current?.title?.take(2) ?: "♪", style = TvTypography.title, color = TextPrimary)
                }
                Spacer(Modifier.height(16.dp))
                Text(text = current?.title ?: "未播放", style = TvTypography.title, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(text = current?.artist ?: "", style = TvTypography.body, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                Text(text = "${formatMs(position)} / ${formatMs(duration)}", style = TvTypography.caption, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                // Simple progress bar via Box
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).background(TextPrimary))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton(text = "上一首", icon = Icons.Filled.SkipPrevious, onClick = { viewModel.previous() }, modifier = Modifier.width(130.dp), secondary = true)
                TvButton(text = if (isPlaying) "暂停" else "播放", icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, onClick = { viewModel.togglePlayPause() }, modifier = Modifier.width(130.dp))
                TvButton(text = "下一首", icon = Icons.Filled.SkipNext, onClick = { viewModel.next() }, modifier = Modifier.width(130.dp), secondary = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = if (queue.shuffle) "随机开" else "随机关",
                    icon = Icons.Filled.Shuffle,
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.width(130.dp),
                    secondary = !queue.shuffle
                )
                val repeatLabel = when (queue.repeat) {
                    MusicRepeatMode.OFF -> "不循环"
                    MusicRepeatMode.ONE -> "单曲循环"
                    MusicRepeatMode.ALL -> "列表循环"
                }
                val repeatIcon = when (queue.repeat) {
                    MusicRepeatMode.ONE -> Icons.Filled.RepeatOne
                    else -> Icons.Filled.Repeat
                }
                TvButton(
                    text = repeatLabel,
                    icon = repeatIcon,
                    onClick = {
                        val next = when (queue.repeat) {
                            MusicRepeatMode.OFF -> MusicRepeatMode.ALL
                            MusicRepeatMode.ALL -> MusicRepeatMode.ONE
                            MusicRepeatMode.ONE -> MusicRepeatMode.OFF
                        }
                        viewModel.setRepeat(next)
                    },
                    modifier = Modifier.width(150.dp),
                    secondary = queue.repeat == MusicRepeatMode.OFF
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return String.format("%d:%02d", s / 60, s % 60)
}
