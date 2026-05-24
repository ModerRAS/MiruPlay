package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState

fun idleWebControlPlaybackStatus(): PlaybackStatusDto =
    webControlPlaybackStatus(state = "Idle")

fun webControlPlaybackStatus(
    state: String,
    uri: String? = null,
    mediaSourceId: String? = null,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    isPlaying: Boolean = false,
    error: String? = null,
): PlaybackStatusDto =
    PlaybackStatusDto(
        state = state.ifBlank { "Idle" },
        uri = uri?.takeIf { it.isNotBlank() },
        mediaSourceId = mediaSourceId?.takeIf { it.isNotBlank() },
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        isPlaying = isPlaying,
        error = error?.takeIf { it.isNotBlank() },
    )

fun String.webControlMediaSourceIdFromEpisodeId(): String? =
    substringBefore(':', "")
        .takeIf { it.isNotBlank() }

fun PlaybackState.toWebControlPlaybackStatus(
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
): PlaybackStatusDto {
    val source = webControlPlaybackSource()
    return webControlPlaybackStatus(
        state = webControlPlaybackStateName(),
        uri = source?.uri,
        mediaSourceId = source?.mediaSourceId,
        positionMs = webControlPlaybackPositionMs() ?: currentPositionMs,
        durationMs = durationMs,
        isPlaying = this is PlaybackState.Playing,
        error = (this as? PlaybackState.Error)?.error,
    )
}

fun PlaybackState.webControlPlaybackStateName(): String =
    when (this) {
        PlaybackState.Idle -> "Idle"
        is PlaybackState.Loading -> "Loading"
        is PlaybackState.Playing -> "Playing"
        is PlaybackState.Paused -> "Paused"
        is PlaybackState.Buffering -> "Buffering"
        is PlaybackState.Ended -> "Ended"
        is PlaybackState.Error -> "Error"
    }

fun PlaybackState.webControlPlaybackSource(): PlaybackSource? =
    when (this) {
        PlaybackState.Idle -> null
        is PlaybackState.Loading -> source
        is PlaybackState.Playing -> source
        is PlaybackState.Paused -> source
        is PlaybackState.Buffering -> source
        is PlaybackState.Ended -> source
        is PlaybackState.Error -> source
    }

fun PlaybackState.webControlPlaybackPositionMs(): Long? =
    when (this) {
        is PlaybackState.Playing -> position
        is PlaybackState.Paused -> position
        is PlaybackState.Buffering -> position
        else -> null
    }
