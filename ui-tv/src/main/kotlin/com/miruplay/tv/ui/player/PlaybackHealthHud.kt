package com.miruplay.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.player.PlaybackHealthSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Debug-only playback health HUD. Visible only while a health/monitor snapshot exists
 * (both are gated by the playback debug switch, next-playback boundary). Polls the
 * controller once a second; shows nothing in production. Never focusable.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlaybackHealthHud(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    var health by remember { mutableStateOf<PlaybackHealthSnapshot?>(null) }
    var libassUpdated by remember { mutableStateOf<Long?>(null) }
    var libassSkipped by remember { mutableStateOf<Long?>(null) }
    var libassSlowCommit by remember { mutableStateOf<Long?>(null) }
    var libassCommitP95Ms by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(viewModel) {
        while (isActive) {
            health = viewModel.currentPlaybackHealthSnapshot()
            viewModel.currentLibassMonitorSnapshot()?.let { snap ->
                libassUpdated = snap.updatedRenderCount
                libassSkipped = snap.timelineProvenSkippedCueCount
                libassSlowCommit = snap.slowCommitCount
                libassCommitP95Ms = snap.commitDuration.p95Ns / 1_000_000L
            } ?: run {
                libassUpdated = null
                libassSkipped = null
                libassSlowCommit = null
                libassCommitP95Ms = null
            }
            delay(HEALTH_HUD_POLL_MS)
        }
    }

    val updated = libassUpdated
    val numbers = if (updated == null) {
        null
    } else {
        LibassHudNumbers(
            updated = updated,
            skipped = libassSkipped ?: 0L,
            slow = libassSlowCommit ?: 0L,
            commitP95Ms = libassCommitP95Ms ?: 0L,
        )
    }
    PlaybackHealthHudContent(
        health = health,
        libass = numbers,
        modifier = modifier,
    )
}

/** Pure presentational half of the HUD: hidden when both snapshots are null. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlaybackHealthHudContent(
    health: PlaybackHealthSnapshot?,
    libass: LibassHudNumbers?,
    modifier: Modifier = Modifier,
) {
    if (health == null && libass == null) return
    Box(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = buildString {
                if (health != null) {
                    val video = health.video
                    val hw = if (video.hardwareAccelerated == true) "HW" else "SW?"
                    appendLine("V ${health.backend} ${video.decoderName.ifBlank { "?" }} [$hw] ${video.width}x${video.height}")
                    appendLine("drop=${video.droppedFramesTotal}/${video.renderedFrames} au=${video.audioUnderruns}")
                    val ui = health.ui
                    appendLine("UI jank=${ui.jankFrames}/${ui.totalFrames} max=${ui.maxDeltaMs}ms@${ui.maxDeltaAtElapsedMs}ms")
                }
                if (libass != null) {
                    append("SUB upd=${libass.updated} skip=${libass.skipped} slow=${libass.slow} p95=${libass.commitP95Ms}ms")
                }
            },
            color = Color.Green,
            fontSize = 14.sp,
            textAlign = TextAlign.Start,
        )
    }
}

internal data class LibassHudNumbers(
    val updated: Long,
    val skipped: Long,
    val slow: Long,
    val commitP95Ms: Long,
)

private const val HEALTH_HUD_POLL_MS = 1000L
