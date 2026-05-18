package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.playbackSourceFromInputs
import com.miruplay.tv.player.mpv.MpvCommandBuilder
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import com.miruplay.tv.player.mpv.MpvRuntimeVerifier
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.buildPreview
import com.miruplay.tv.player.mpv.mpvRuntimeConfigFromInputs

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
        val source = buildPlaybackSource(mediaPath, subtitlePath, startSeconds)
        val config = buildRuntimeConfig(mpvPath, configDir, fullscreen, keepOpen, rifeEnabled, rifeBackend)
        MpvCommandBuilder(config).buildPreview(source)
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
