package com.miruplay.tv.ui.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.tv.material3.*
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.ui.components.*
import com.miruplay.tv.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playbackSource: PlaybackSource,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val controlsVisible by viewModel.controlsVisible.collectAsStateWithLifecycle()
    val availableSubtitles by viewModel.availableSubtitles.collectAsStateWithLifecycle()
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Start playback on first composition
    LaunchedEffect(playbackSource) {
        viewModel.play(playbackSource)
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3000)
            viewModel.toggleControls()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video surface (placeholder - actual Media3 PlayerSurface)
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶",
                color = Color.White,
                fontSize = 64.sp
            )
        }

        // Error overlay
        if (errorMessage != null) {
            ErrorMessage(
                message = errorMessage!!,
                onRetry = { viewModel.play(playbackSource) }
            )
        }

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                // Top info bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = playbackSource.mediaSourceId,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (playbackSource.startPosition > 0) {
                            Text(
                                text = "恢复播放",
                                color = WarningYellow,
                                fontSize = 14.sp
                            )
                        }
                    }
                    TvButton(text = "退出", onClick = onNavigateBack)
                }

                // Center controls
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip backward 10s
                        TvButton(
                            text = "⏪ 10s",
                            onClick = { viewModel.skipBackward() },
                            modifier = Modifier.width(100.dp).height(48.dp)
                        )
                        // Play/Pause
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AnimeRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (playbackState) {
                                    is PlaybackState.Playing -> "⏸"
                                    is PlaybackState.Paused -> "▶"
                                    is PlaybackState.Loading -> "⏳"
                                    else -> "▶"
                                },
                                color = Color.White,
                                fontSize = 28.sp
                            )
                        }
                        // Skip forward 30s
                        TvButton(
                            text = "30s ⏩",
                            onClick = { viewModel.skipForward() },
                            modifier = Modifier.width(100.dp).height(48.dp)
                        )
                    }
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp)
                ) {
                    // Progress bar
                    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    
                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    // Selection rows
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Subtitle selector
                        if (availableSubtitles.isNotEmpty()) {
                            Column {
                                Text(text = "字幕", color = TextSecondary, fontSize = 12.sp)
                                LazyRow {
                                    items(availableSubtitles.size) { index ->
                                        val track = availableSubtitles[index]
                                        TvButton(
                                            text = track.language,
                                            onClick = { viewModel.selectSubtitle(index) },
                                            modifier = Modifier.width(80.dp).height(40.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Audio track selector
                        if (availableAudioTracks.isNotEmpty()) {
                            Column {
                                Text(text = "音轨", color = TextSecondary, fontSize = 12.sp)
                                LazyRow {
                                    items(availableAudioTracks.size) { index ->
                                        val track = availableAudioTracks[index]
                                        TvButton(
                                            text = track.language,
                                            onClick = { viewModel.selectAudioTrack(index) },
                                            modifier = Modifier.width(80.dp).height(40.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Speed selector
                        Column {
                            Text(text = "速度", color = TextSecondary, fontSize = 12.sp)
                            LazyRow {
                                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                items(speeds.size) { index ->
                                    val speed = speeds[index]
                                    TvButton(
                                        text = "${speed}x",
                                        onClick = { viewModel.setPlaybackSpeed(speed) },
                                        modifier = Modifier.width(70.dp).height(40.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}