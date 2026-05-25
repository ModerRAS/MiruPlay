package com.miruplay.tv.player.mpv

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackTimingConventions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MpvProcessPlayer(
    private val config: MpvRuntimeConfig,
    private val commandBuilder: MpvCommandBuilder = MpvCommandBuilder(config),
    private val processLauncher: MpvProcessLauncher = ProcessBuilderMpvProcessLauncher,
    private val ipcClient: MpvIpcController? = config.ipcServer
        ?.takeIf { it.isNotBlank() }
        ?.let(::MpvIpcClient),
) {
    private var process: Process? = null

    suspend fun play(source: PlaybackSource): Result<MpvLaunch> = withContext(Dispatchers.IO) {
        when (val validation = config.validateLaunchRuntime()) {
            is Result.Success -> Unit
            is Result.Error -> return@withContext validation
        }

        runCatching {
            val command = commandBuilder.build(source)
            val started = processLauncher.start(command)
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
            if (activeProcess != null) {
                val descendants = runCatching {
                    activeProcess.toHandle().descendants().toList().asReversed()
                }.getOrDefault(emptyList())
                ipcClient?.quit()
                if (!activeProcess.waitFor(1_500L, TimeUnit.MILLISECONDS)) {
                    activeProcess.destroy()
                    if (!activeProcess.waitFor(1_500L, TimeUnit.MILLISECONDS)) {
                        activeProcess.destroyForcibly()
                        activeProcess.waitFor(1_500L, TimeUnit.MILLISECONDS)
                    }
                }
                descendants.forEach { descendant ->
                    if (descendant.isAlive) {
                        descendant.destroy()
                    }
                }
                Thread.sleep(250L)
                descendants.forEach { descendant ->
                    if (descendant.isAlive) {
                        descendant.destroyForcibly()
                    }
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

    fun isActive(): Boolean =
        process?.isAlive == true

    suspend fun togglePause(): Result<Unit> =
        ipcClientOrError()?.cyclePause() ?: missingIpcError()

    suspend fun setPaused(paused: Boolean): Result<Unit> =
        ipcClientOrError()?.setPaused(paused) ?: missingIpcError()

    suspend fun setSpeed(speed: Double): Result<Unit> =
        ipcClientOrError()?.setSpeed(speed.coerceIn(0.25, 3.0)) ?: missingIpcError()

    suspend fun seekBy(seconds: Double, mode: MpvSeekMode = MpvSeekMode.RELATIVE_EXACT): Result<Unit> =
        ipcClientOrError()?.seekBy(seconds, mode) ?: missingIpcError()

    suspend fun queryTimePositionMs(): Result<Long?> =
        ipcClientOrError()?.getTimePositionSeconds()?.map { seconds ->
            seconds?.let(PlaybackTimingConventions::secondsToPositionMsFloored)
        } ?: missingIpcError()

    suspend fun queryDurationMs(): Result<Long?> =
        ipcClientOrError()?.getDurationSeconds()?.map { seconds ->
            seconds?.let(PlaybackTimingConventions::secondsToPositionMsFloored)
        } ?: missingIpcError()

    suspend fun queryPaused(): Result<Boolean?> =
        ipcClientOrError()?.getPaused() ?: missingIpcError()

    suspend fun queryEofReached(): Result<Boolean?> =
        ipcClientOrError()?.getEofReached() ?: missingIpcError()

    private fun ipcClientOrError(): MpvIpcController? =
        ipcClient

    private fun <T> missingIpcError(): Result<T> =
        Result.failure(AppError.PlaybackError.StreamError("mpv IPC server is not configured"))
}

fun interface MpvProcessLauncher {
    fun start(command: List<String>): Process
}

private object ProcessBuilderMpvProcessLauncher : MpvProcessLauncher {
    override fun start(command: List<String>): Process =
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
}

data class MpvLaunch(
    val command: List<String>,
    val pid: Long,
)
