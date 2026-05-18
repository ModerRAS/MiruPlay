package com.miruplay.tv.player.mpv

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackTimingConventions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class MpvProcessPlayer(
    private val config: MpvRuntimeConfig,
    private val commandBuilder: MpvCommandBuilder = MpvCommandBuilder(config),
) {
    private var process: Process? = null
    private val ipcClient: MpvIpcClient? = config.ipcServer
        ?.takeIf { it.isNotBlank() }
        ?.let(::MpvIpcClient)

    suspend fun play(source: PlaybackSource): Result<MpvLaunch> = withContext(Dispatchers.IO) {
        if (!Files.isRegularFile(config.mpvExecutable)) {
            return@withContext Result.failure(
                AppError.PlaybackError.StreamError("mpv executable not found: ${config.mpvExecutable}")
            )
        }
        val verification = config.configDirectory
            ?.let { configDirectory ->
                MpvRuntimeVerifier.verify(
                    MpvRuntimeLayout(
                        rootDirectory = configDirectory.parent ?: config.mpvExecutable.parent ?: configDirectory,
                        executable = config.mpvExecutable,
                        configDirectory = configDirectory,
                    )
                )
            }
        val requestedBackend = config.rife?.backend
        if (requestedBackend != null && verification?.availableRifeBackends?.contains(requestedBackend) == false) {
            return@withContext Result.failure(
                AppError.PlaybackError.StreamError(
                    "RIFE script not found: ${verification.layout.rifeScript(requestedBackend)}"
                )
            )
        }

        runCatching {
            val command = commandBuilder.build(source)
            val started = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            process = started
            Result.success(MpvLaunch(command = command, pid = started.pid()))
        }.getOrElse { error ->
            Result.failure(
                AppError.PlaybackError.StreamError(error.message ?: "Failed to start mpv")
            )
        }
    }

    suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val activeProcess = process
            if (activeProcess != null && activeProcess.isAlive) {
                ipcClient?.quit()
                if (!activeProcess.waitFor(1_500L, TimeUnit.MILLISECONDS)) {
                    activeProcess.destroy()
                }
            }
            process = null
            Result.success(Unit)
        }.getOrElse { error ->
            Result.failure(
                AppError.PlaybackError.StreamError(error.message ?: "Failed to stop mpv")
            )
        }
    }

    suspend fun togglePause(): Result<Unit> =
        ipcClientOrError()?.cyclePause() ?: missingIpcError()

    suspend fun setPaused(paused: Boolean): Result<Unit> =
        ipcClientOrError()?.setPaused(paused) ?: missingIpcError()

    suspend fun seekBy(seconds: Double, mode: MpvSeekMode = MpvSeekMode.RELATIVE_EXACT): Result<Unit> =
        ipcClientOrError()?.seekBy(seconds, mode) ?: missingIpcError()

    suspend fun queryTimePositionMs(): Result<Long?> =
        ipcClientOrError()?.getTimePositionSeconds()?.map { seconds ->
            seconds?.let(PlaybackTimingConventions::secondsToPositionMsFloored)
        } ?: missingIpcError()

    private fun ipcClientOrError(): MpvIpcClient? =
        ipcClient

    private fun <T> missingIpcError(): Result<T> =
        Result.failure(AppError.PlaybackError.StreamError("mpv IPC server is not configured"))
}

data class MpvLaunch(
    val command: List<String>,
    val pid: Long,
)
