package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopPlaybackUriBridge
import com.miruplay.tv.mediasource.desktop.playableUriFor
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.playbackSourceFromInputs
import com.miruplay.tv.player.mpv.MpvLaunch
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import com.miruplay.tv.player.mpv.MpvRuntimeVerifier
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.mpvLaunchFailedStatus
import com.miruplay.tv.player.mpv.mpvLaunchedStatus
import com.miruplay.tv.player.mpv.mpvCommandPreviewFromInputs
import com.miruplay.tv.player.mpv.mpvRuntimeConfigFromInputs
import com.miruplay.tv.player.mpv.validateLaunchRuntime

internal const val DEFAULT_DESKTOP_RIFE_ENABLED = false

internal fun mpvRuntimeStatusFromInputs(
    mpvPath: String,
    configDir: String,
): String =
    MpvRuntimeVerifier.statusFromInputs(mpvPath, configDir)

internal fun desktopMpvCommandPreviewFromInputs(
    mpvPath: String,
    configDir: String,
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    fullscreen: Boolean,
    keepOpen: Boolean,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
    blankMediaMessage: String,
    errorMessage: String,
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
            blankMediaMessage = blankMediaMessage,
        )
    }.getOrElse { error ->
        error.message ?: errorMessage
    }

internal data class DesktopPlaybackLaunchRequest(
    val mpvPath: String,
    val configDir: String,
    val mediaPath: String,
    val subtitlePath: String,
    val startSeconds: String,
    val fullscreen: Boolean,
    val keepOpen: Boolean,
    val rifeEnabled: Boolean,
    val rifeBackend: RifeBackend,
    val activeSource: DesktopMediaSource?,
    val activeSourceId: Long?,
    val blankMediaMessage: String,
    val fallbackMediaSourceId: String,
    val episodeId: String? = null,
)

internal data class DesktopPreparedPlaybackLaunch(
    val config: MpvRuntimeConfig,
    val source: PlaybackSource,
    val session: PlaybackProgressSession,
)

internal data class DesktopPlaybackLaunchResult(
    val player: MpvProcessPlayer,
    val launch: MpvLaunch,
    val source: PlaybackSource,
    val session: PlaybackProgressSession,
    val status: String,
)

internal class DesktopPlaybackLauncher(
    private val bridge: DesktopPlaybackUriBridge,
    private val playerFactory: (MpvRuntimeConfig) -> MpvProcessPlayer = { config -> MpvProcessPlayer(config) },
    private val runtimeValidator: (MpvRuntimeConfig) -> Result<*> = { config -> config.validateLaunchRuntime() },
) {
    fun prepare(request: DesktopPlaybackLaunchRequest): Result<DesktopPreparedPlaybackLaunch> {
        val config = mpvRuntimeConfigFromInputs(
            mpvPath = request.mpvPath,
            configDir = request.configDir,
            fullscreen = request.fullscreen,
            keepOpen = request.keepOpen,
            rifeEnabled = request.rifeEnabled,
            rifeBackend = request.rifeBackend,
        )
        when (val runtime = runtimeValidator(config)) {
            is Result.Success -> Unit
            is Result.Error -> return runtime
        }

        val selectedMediaPath = request.mediaPath.trim()
        val source = playbackSourceFromInputs(
            mediaPath = playableUriFor(request.activeSource, bridge, selectedMediaPath),
            subtitlePath = request.subtitlePath,
            startSeconds = request.startSeconds,
            mediaSourceId = request.activeSourceId?.toString()
                ?: request.activeSource?.info?.type?.name
                ?: request.fallbackMediaSourceId,
            episodeId = request.episodeId ?: selectedMediaPath.ifBlank { null },
            blankMediaMessage = request.blankMediaMessage,
        )
        return Result.success(
            DesktopPreparedPlaybackLaunch(
                config = config,
                source = source,
                session = PlaybackProgressSession(source.episodeId ?: selectedMediaPath, source.startPosition),
            )
        )
    }

    suspend fun launch(request: DesktopPlaybackLaunchRequest): Result<DesktopPlaybackLaunchResult> =
        when (val prepared = prepare(request)) {
            is Result.Error -> prepared
            is Result.Success -> {
                val player = playerFactory(prepared.data.config)
                when (val launched = player.play(prepared.data.source)) {
                    is Result.Error -> launched
                    is Result.Success -> Result.success(
                        DesktopPlaybackLaunchResult(
                            player = player,
                            launch = launched.data,
                            source = prepared.data.source,
                            session = prepared.data.session,
                            status = mpvLaunchedStatus(launched.data),
                        )
                    )
                }
            }
        }

    fun launchFailureStatus(error: Throwable): String =
        mpvLaunchFailedStatus(error)
}
