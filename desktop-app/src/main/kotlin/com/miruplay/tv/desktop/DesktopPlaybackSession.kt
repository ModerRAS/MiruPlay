package com.miruplay.tv.desktop

import com.miruplay.tv.model.PlaybackTimingConventions

internal class DesktopPlaybackSession(
    val episodeId: String,
    startPositionMs: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var anchorPositionMs = startPositionMs.coerceAtLeast(0L)
    private var anchorWallClockMs = nowMillis()
    private var paused = false

    fun currentPositionMs(): Long {
        if (paused) return anchorPositionMs
        val elapsedMs = (nowMillis() - anchorWallClockMs).coerceAtLeast(0L)
        return anchorPositionMs + elapsedMs
    }

    fun togglePaused() {
        setPaused(!paused)
    }

    fun setPaused(value: Boolean) {
        if (paused == value) return
        anchorPositionMs = currentPositionMs()
        anchorWallClockMs = nowMillis()
        paused = value
    }

    fun seekBy(seconds: Double) {
        anchorPositionMs = (
            currentPositionMs() +
                PlaybackTimingConventions.secondsToDeltaMs(seconds)
            ).coerceAtLeast(0L)
        anchorWallClockMs = nowMillis()
    }

    fun syncPosition(positionMs: Long) {
        anchorPositionMs = positionMs.coerceAtLeast(0L)
        anchorWallClockMs = nowMillis()
    }
}
