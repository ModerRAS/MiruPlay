package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackProgressSession

suspend fun syncObservedPlaybackProgress(
    session: PlaybackProgressSession,
    queryPositionMs: suspend () -> Result<Long?>,
    saveProgress: suspend (episodeId: String, positionMs: Long, lastWatched: Long) -> Result<Unit>,
    nowMillis: () -> Long = System::currentTimeMillis,
): Result<Long?> =
    when (val position = queryPositionMs()) {
        is Result.Success -> {
            val positionMs = position.data ?: return Result.success(null)
            session.syncPosition(positionMs)
            when (val saved = saveProgress(session.episodeId, positionMs, nowMillis())) {
                is Result.Success -> Result.success(positionMs)
                is Result.Error -> Result.failure(saved.error)
            }
        }
        is Result.Error -> Result.failure(position.error)
    }
