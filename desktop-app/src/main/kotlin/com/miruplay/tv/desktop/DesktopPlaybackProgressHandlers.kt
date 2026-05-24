package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.repository.savePlaybackProgressSnapshot

internal suspend fun saveDesktopPlaybackStartProgress(
    session: PlaybackProgressSession,
    source: PlaybackSource,
    saveProgress: suspend (
        episodeId: String,
        positionMs: Long,
        lastWatched: Long,
        incrementPlayCount: Boolean,
    ) -> Result<Unit>,
): Result<Unit> =
    savePlaybackProgressSnapshot(
        episodeId = session.episodeId,
        positionMs = source.startPosition,
        incrementPlayCount = false,
        saveProgress = saveProgress,
    )

internal suspend fun saveDesktopPlaybackCompletionProgress(
    session: PlaybackProgressSession,
    queryDurationMs: suspend () -> Result<Long?>,
    queryPositionMs: suspend () -> Result<Long?>,
    saveProgress: suspend (
        episodeId: String,
        positionMs: Long,
        lastWatched: Long,
        incrementPlayCount: Boolean,
    ) -> Result<Unit>,
): Result<Long> {
    val completedPosition = when (val duration = queryDurationMs()) {
        is Result.Success -> duration.data
        is Result.Error -> null
    } ?: when (val position = queryPositionMs()) {
        is Result.Success -> position.data
        is Result.Error -> null
    } ?: session.currentPositionMs()

    return when (
        val saved = savePlaybackProgressSnapshot(
            episodeId = session.episodeId,
            positionMs = completedPosition,
            incrementPlayCount = true,
            saveProgress = saveProgress,
        )
    ) {
        is Result.Success -> Result.success(completedPosition.coerceAtLeast(0L))
        is Result.Error -> Result.failure(saved.error)
    }
}
