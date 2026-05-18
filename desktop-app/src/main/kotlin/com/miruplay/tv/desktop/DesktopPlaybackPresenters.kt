package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.buildExternalSubtitleTracks
import com.miruplay.tv.player.mpv.MpvCommandBuilder
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import com.miruplay.tv.player.mpv.MpvRuntimeVerifier
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.RifeInterpolationConfig
import java.nio.file.Paths

internal fun runtimeStatus(mpvPath: String, configDir: String): String =
    runCatching {
        val verification = MpvRuntimeVerifier.verify(DesktopRuntimeDefaults.runtimeRoot(mpvPath, configDir))
        verification.detailMessage()
    }.getOrElse { error ->
        "Runtime check failed: ${error.message ?: error::class.simpleName}"
    }

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
        MpvCommandBuilder(config).build(source).joinToString(" ") { it.quoteForPreview() }
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
    MpvRuntimeConfig(
        mpvExecutable = Paths.get(mpvPath.trim()),
        configDirectory = configDir.trim().takeIf { it.isNotBlank() }?.let(Paths::get),
        startFullscreen = fullscreen,
        keepOpen = keepOpen,
        rife = if (rifeEnabled) RifeInterpolationConfig(backend = rifeBackend) else null,
    )

internal fun buildPlaybackSource(
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    mediaSourceId: String = "desktop-compose",
    episodeId: String? = null,
): PlaybackSource {
    val media = requireNotNull(mediaPath.trim().takeIf { it.isNotBlank() }) {
        "Choose a media URI or file path before launching mpv."
    }
    val startMs = PlaybackTimingConventions.parseSecondsToPositionMs(startSeconds)
    return PlaybackSource(
        uri = media,
        mediaSourceId = mediaSourceId,
        startPosition = startMs,
        subtitleTracks = buildExternalSubtitleTracks(subtitlePath.trim()),
        episodeId = episodeId ?: media,
    )
}

internal fun playableUriFor(
    source: DesktopMediaSource?,
    bridge: DesktopPlaybackUriBridge,
    mediaPath: String,
): String {
    val path = mediaPath.trim()
    if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
        return path
    }
    return when (source?.info?.type) {
        MediaSourceType.WEBDAV -> if (path.startsWith("/")) bridge.playableUri(source, path) else path
        MediaSourceType.SMB -> if (path.startsWith("smb://", ignoreCase = true)) bridge.playableUri(source, path) else path
        MediaSourceType.LOCAL,
        null -> path
    }
}

internal interface DesktopPlaybackUriBridge {
    fun playableUri(source: DesktopMediaSource, path: String): String
}

private fun String.quoteForPreview(): String =
    if (any { it.isWhitespace() }) "\"${replace("\"", "\\\"")}\"" else this
