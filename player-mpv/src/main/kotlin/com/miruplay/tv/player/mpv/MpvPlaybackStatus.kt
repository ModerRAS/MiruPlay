package com.miruplay.tv.player.mpv

import com.miruplay.tv.model.coercePlaybackSpeed
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.model.playbackSpeedValueLabel

fun mpvIdleStatus(): String =
    "mpv is idle."

fun mpvLaunchedStatus(launch: MpvLaunch): String =
    "mpv launched: pid ${launch.pid}"

fun mpvLaunchFailedStatus(error: Throwable): String =
    error.message ?: "Unable to launch mpv."

fun mpvNoActiveProcessStatus(): String =
    "No mpv process is active."

fun mpvPauseToggledStatus(): String =
    "mpv pause toggled."

fun mpvResumedStatus(): String =
    "mpv resumed."

fun mpvPausedStatus(): String =
    "mpv paused."

fun mpvSeekBackStatus(seconds: Int): String =
    "mpv seeked back ${seconds}s."

fun mpvSeekForwardStatus(seconds: Int): String =
    "mpv seeked forward ${seconds}s."

fun mpvSpeedChangedStatus(speed: Float): String =
    "mpv speed set to ${playbackSpeedValueLabel(coercePlaybackSpeed(speed))}."

fun mpvStoppedStatus(): String =
    "mpv stopped."

fun mpvExitedStatus(): String =
    "mpv exited."

fun mpvPlaybackCompletedStatus(positionMs: Long): String =
    "mpv playback completed at ${formatPlaybackPosition(positionMs)}."

fun mpvPositionSyncedStatus(positionMs: Long): String =
    "mpv position synced at ${formatPlaybackPosition(positionMs)}."
