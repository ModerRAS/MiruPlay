package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.PlaybackTimingConventions

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

fun PlaybackCommandRequest.absoluteSeekPositionMs(durationMs: Long = 0L): Long =
    PlaybackTimingConventions.coercePlaybackPositionMs(positionMs ?: 0L, durationMs)

fun PlaybackCommandRequest.relativeSeekDeltaMs(): Long =
    deltaMs ?: 0L

fun PlaybackCommandRequest.skipForwardDeltaMs(): Long =
    deltaMs ?: PLAYBACK_SEEK_FORWARD_SECONDS * MILLIS_PER_SECOND

fun PlaybackCommandRequest.skipBackwardDeltaMs(): Long =
    deltaMs ?: PLAYBACK_SEEK_BACK_SECONDS * MILLIS_PER_SECOND

fun PlaybackCommandRequest.seekTargetPositionMs(currentPositionMs: Long, durationMs: Long = 0L): Long? =
    when (playbackCommandKind()) {
        WebControlPlaybackCommandKind.SEEK -> absoluteSeekPositionMs(durationMs)
        WebControlPlaybackCommandKind.SEEK_RELATIVE ->
            PlaybackTimingConventions.coercePlaybackPositionMs(currentPositionMs + relativeSeekDeltaMs(), durationMs)
        WebControlPlaybackCommandKind.SKIP_FORWARD ->
            PlaybackTimingConventions.coercePlaybackPositionMs(currentPositionMs + skipForwardDeltaMs(), durationMs)
        WebControlPlaybackCommandKind.SKIP_BACKWARD ->
            PlaybackTimingConventions.coercePlaybackPositionMs(currentPositionMs - skipBackwardDeltaMs(), durationMs)
        else -> null
    }

fun PlaybackCommandRequest.playbackSpeed(): Float =
    speed ?: 1.0f

suspend fun PlaybackCommandRequest.executeWebControlPlaybackCommand(
    target: WebControlPlaybackCommandTarget,
) {
    when (playbackCommandKind()) {
        WebControlPlaybackCommandKind.PAUSE -> target.pause()
        WebControlPlaybackCommandKind.RESUME -> target.resume()
        WebControlPlaybackCommandKind.TOGGLE -> target.toggle()
        WebControlPlaybackCommandKind.STOP -> target.stop()
        WebControlPlaybackCommandKind.SEEK -> {
            target.seekTo(absoluteSeekPositionMs(target.durationMs()))
        }
        WebControlPlaybackCommandKind.SEEK_RELATIVE,
        WebControlPlaybackCommandKind.SKIP_FORWARD,
        WebControlPlaybackCommandKind.SKIP_BACKWARD -> {
            target.seekTo(
                requireNotNull(
                    seekTargetPositionMs(
                        currentPositionMs = target.currentPositionMs(),
                        durationMs = target.durationMs(),
                    ),
                ),
            )
        }
        WebControlPlaybackCommandKind.SPEED -> target.setPlaybackSpeed(playbackSpeed())
        WebControlPlaybackCommandKind.UNKNOWN -> throw IllegalArgumentException("未知播放命令: $command")
    }
}

interface WebControlPlaybackCommandTarget {
    suspend fun pause()
    suspend fun resume()
    suspend fun toggle()
    suspend fun stop()
    suspend fun seekTo(positionMs: Long)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun currentPositionMs(): Long
    suspend fun durationMs(): Long = 0L

    suspend fun seekBy(deltaMs: Long) {
        seekTo(
            PlaybackTimingConventions.coercePlaybackPositionMs(
                currentPositionMs() + deltaMs,
                durationMs(),
            ),
        )
    }
}

fun webControlPlaybackCommandTarget(
    pause: suspend () -> Unit,
    resume: suspend () -> Unit,
    toggle: suspend () -> Unit,
    stop: suspend () -> Unit,
    seekTo: suspend (Long) -> Unit,
    setPlaybackSpeed: suspend (Float) -> Unit,
    currentPositionMs: suspend () -> Long,
    durationMs: suspend () -> Long = { 0L },
): WebControlPlaybackCommandTarget =
    LambdaWebControlPlaybackCommandTarget(
        pauseAction = pause,
        resumeAction = resume,
        toggleAction = toggle,
        stopAction = stop,
        seekToAction = seekTo,
        setPlaybackSpeedAction = setPlaybackSpeed,
        currentPositionMsProvider = currentPositionMs,
        durationMsProvider = durationMs,
    )

private class LambdaWebControlPlaybackCommandTarget(
    private val pauseAction: suspend () -> Unit,
    private val resumeAction: suspend () -> Unit,
    private val toggleAction: suspend () -> Unit,
    private val stopAction: suspend () -> Unit,
    private val seekToAction: suspend (Long) -> Unit,
    private val setPlaybackSpeedAction: suspend (Float) -> Unit,
    private val currentPositionMsProvider: suspend () -> Long,
    private val durationMsProvider: suspend () -> Long,
) : WebControlPlaybackCommandTarget {
    override suspend fun pause(): Unit = pauseAction()

    override suspend fun resume(): Unit = resumeAction()

    override suspend fun toggle(): Unit = toggleAction()

    override suspend fun stop(): Unit = stopAction()

    override suspend fun seekTo(positionMs: Long): Unit = seekToAction.invoke(positionMs)

    override suspend fun setPlaybackSpeed(speed: Float): Unit = setPlaybackSpeedAction.invoke(speed)

    override suspend fun currentPositionMs(): Long = currentPositionMsProvider.invoke()

    override suspend fun durationMs(): Long = durationMsProvider.invoke()
}

private const val MILLIS_PER_SECOND = 1_000L
