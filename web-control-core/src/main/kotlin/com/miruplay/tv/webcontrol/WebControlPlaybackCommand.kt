package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS

enum class WebControlPlaybackCommandKind {
    PAUSE,
    RESUME,
    TOGGLE,
    STOP,
    SEEK,
    SEEK_RELATIVE,
    SKIP_FORWARD,
    SKIP_BACKWARD,
    SPEED,
    UNKNOWN,
}

fun PlaybackCommandRequest.playbackCommandKind(): WebControlPlaybackCommandKind =
    when (command.trim().lowercase()) {
        "pause" -> WebControlPlaybackCommandKind.PAUSE
        "resume", "play" -> WebControlPlaybackCommandKind.RESUME
        "toggle" -> WebControlPlaybackCommandKind.TOGGLE
        "stop" -> WebControlPlaybackCommandKind.STOP
        "seek" -> WebControlPlaybackCommandKind.SEEK
        "seek_relative" -> WebControlPlaybackCommandKind.SEEK_RELATIVE
        "skip_forward" -> WebControlPlaybackCommandKind.SKIP_FORWARD
        "skip_backward" -> WebControlPlaybackCommandKind.SKIP_BACKWARD
        "speed" -> WebControlPlaybackCommandKind.SPEED
        else -> WebControlPlaybackCommandKind.UNKNOWN
    }

fun PlaybackCommandRequest.absoluteSeekPositionMs(): Long =
    (positionMs ?: 0L).coerceAtLeast(0L)

fun PlaybackCommandRequest.relativeSeekDeltaMs(): Long =
    deltaMs ?: 0L

fun PlaybackCommandRequest.skipForwardDeltaMs(): Long =
    deltaMs ?: PLAYBACK_SEEK_FORWARD_SECONDS * MILLIS_PER_SECOND

fun PlaybackCommandRequest.skipBackwardDeltaMs(): Long =
    deltaMs ?: PLAYBACK_SEEK_BACK_SECONDS * MILLIS_PER_SECOND

fun PlaybackCommandRequest.seekTargetPositionMs(currentPositionMs: Long): Long? =
    when (playbackCommandKind()) {
        WebControlPlaybackCommandKind.SEEK -> absoluteSeekPositionMs()
        WebControlPlaybackCommandKind.SEEK_RELATIVE ->
            (currentPositionMs + relativeSeekDeltaMs()).coerceAtLeast(0L)
        WebControlPlaybackCommandKind.SKIP_FORWARD ->
            (currentPositionMs + skipForwardDeltaMs()).coerceAtLeast(0L)
        WebControlPlaybackCommandKind.SKIP_BACKWARD ->
            (currentPositionMs - skipBackwardDeltaMs()).coerceAtLeast(0L)
        else -> null
    }

fun PlaybackCommandRequest.playbackSpeed(): Float =
    speed ?: 1.0f

private const val MILLIS_PER_SECOND = 1_000L
