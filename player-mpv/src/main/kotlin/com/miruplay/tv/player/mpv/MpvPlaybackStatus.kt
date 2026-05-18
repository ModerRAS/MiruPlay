package com.miruplay.tv.player.mpv

import com.miruplay.tv.model.formatPlaybackPosition

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

fun mpvSeekBackStatus(seconds: Int): String =
    "mpv seeked back ${seconds}s."

fun mpvSeekForwardStatus(seconds: Int): String =
    "mpv seeked forward ${seconds}s."

fun mpvStoppedStatus(): String =
    "mpv stopped."

fun mpvPositionSyncedStatus(positionMs: Long): String =
    "mpv position synced at ${formatPlaybackPosition(positionMs)}."
