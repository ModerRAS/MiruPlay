package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.repository.savePlaybackProgressOnCompletion
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
): Result<Long> =
    savePlaybackProgressOnCompletion(
        session = session,
        queryDurationMs = queryDurationMs,
        queryPositionMs = queryPositionMs,
        saveProgress = saveProgress,
    )
