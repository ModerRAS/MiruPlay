package com.miruplay.tv.webcontrol

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
