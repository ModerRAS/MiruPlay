package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result

internal suspend fun syncPlaybackProgressFromMpv(
    session: DesktopPlaybackSession,
    queryPositionMs: suspend () -> Result<Long?>,
    saveProgress: suspend (episodeId: String, positionMs: Long, lastWatched: Long) -> Result<Unit>,
    nowMillis: () -> Long = System::currentTimeMillis,
): Result<Long?> {
    return when (val position = queryPositionMs()) {
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
}
