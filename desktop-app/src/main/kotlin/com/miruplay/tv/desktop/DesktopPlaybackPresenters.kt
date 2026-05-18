package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.playbackSourceFromInputs
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import com.miruplay.tv.player.mpv.MpvRuntimeVerification
import com.miruplay.tv.player.mpv.MpvRuntimeVerifier
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.MpvLaunch
import com.miruplay.tv.player.mpv.mpvCommandPreviewFromInputs
import com.miruplay.tv.player.mpv.mpvIdleStatus
import com.miruplay.tv.player.mpv.mpvLaunchFailedStatus
import com.miruplay.tv.player.mpv.mpvLaunchedStatus
import com.miruplay.tv.player.mpv.mpvNoActiveProcessStatus
import com.miruplay.tv.player.mpv.mpvPauseToggledStatus
import com.miruplay.tv.player.mpv.mpvPositionSyncedStatus
import com.miruplay.tv.player.mpv.mpvRuntimeConfigFromInputs
import com.miruplay.tv.player.mpv.mpvSeekBackStatus
import com.miruplay.tv.player.mpv.mpvSeekForwardStatus
import com.miruplay.tv.player.mpv.mpvStoppedStatus
import com.miruplay.tv.player.mpv.validateLaunchRuntime

internal fun runtimeStatus(mpvPath: String, configDir: String): String =
    MpvRuntimeVerifier.statusFromInputs(mpvPath, configDir)

internal fun buildCommandPreview(
    mpvPath: String,
    configDir: String,
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    fullscreen: Boolean,
    keepOpen: Boolean,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
): String =
    runCatching {
        mpvCommandPreviewFromInputs(
            mpvPath = mpvPath,
            configDir = configDir,
            mediaPath = mediaPath,
            subtitlePath = subtitlePath,
            startSeconds = startSeconds,
            fullscreen = fullscreen,
            keepOpen = keepOpen,
            rifeEnabled = rifeEnabled,
            rifeBackend = rifeBackend,
            blankMediaMessage = "Choose a media URI or file path before launching mpv.",
        )
    }.getOrElse { error ->
        error.message ?: "Unable to build mpv command."
    }

internal fun buildRuntimeConfig(
    mpvPath: String,
    configDir: String,
    fullscreen: Boolean,
    keepOpen: Boolean,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
): MpvRuntimeConfig =
    mpvRuntimeConfigFromInputs(
        mpvPath = mpvPath,
        configDir = configDir,
        fullscreen = fullscreen,
        keepOpen = keepOpen,
        rifeEnabled = rifeEnabled,
        rifeBackend = rifeBackend,
    )

internal fun validateRuntimeForLaunch(config: MpvRuntimeConfig): Result<MpvRuntimeVerification?> =
    config.validateLaunchRuntime()

internal fun buildPlaybackSource(
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    mediaSourceId: String = "desktop-compose",
    episodeId: String? = null,
): PlaybackSource =
    playbackSourceFromInputs(
        mediaPath = mediaPath,
        subtitlePath = subtitlePath,
        startSeconds = startSeconds,
        mediaSourceId = mediaSourceId,
        episodeId = episodeId,
        blankMediaMessage = "Choose a media URI or file path before launching mpv.",
    )

internal fun playableUriFor(
    source: DesktopMediaSource?,
    bridge: DesktopPlaybackUriBridge,
    mediaPath: String,
): String {
    val path = mediaPath.trim()
    return if (source != null && MediaSourceInfoConventions.shouldBridgeForPlayback(source.info.type, path)) {
        bridge.playableUri(source, path)
    } else {
        path
    }
}

internal interface DesktopPlaybackUriBridge {
    fun playableUri(source: DesktopMediaSource, path: String): String
}

internal fun playbackIdleStatus(): String =
    mpvIdleStatus()

internal fun playbackLaunchedStatus(launch: MpvLaunch): String =
    mpvLaunchedStatus(launch)

internal fun playbackLaunchFailedStatus(error: Throwable): String =
    mpvLaunchFailedStatus(error)

internal fun playbackNoActiveProcessStatus(): String =
    mpvNoActiveProcessStatus()

internal fun playbackPauseToggledStatus(): String =
    mpvPauseToggledStatus()

internal fun playbackSeekBackStatus(seconds: Int): String =
    mpvSeekBackStatus(seconds)

internal fun playbackSeekForwardStatus(seconds: Int): String =
    mpvSeekForwardStatus(seconds)

internal fun playbackStoppedStatus(): String =
    mpvStoppedStatus()

internal fun playbackPositionSyncedStatus(positionMs: Long): String =
    mpvPositionSyncedStatus(positionMs)
