package com.miruplay.tv.model

class PlaybackProgressSession(
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
        anchorPositionMs = PlaybackTimingConventions.coercePlaybackPositionMs(
            currentPositionMs() + PlaybackTimingConventions.secondsToDeltaMs(seconds),
        )
        anchorWallClockMs = nowMillis()
    }

    fun syncPosition(positionMs: Long) {
        anchorPositionMs = PlaybackTimingConventions.coercePlaybackPositionMs(positionMs)
        anchorWallClockMs = nowMillis()
    }
}
