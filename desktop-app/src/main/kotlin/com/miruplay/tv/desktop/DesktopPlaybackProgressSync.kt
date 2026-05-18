package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.repository.syncObservedPlaybackProgress

internal suspend fun syncPlaybackProgressFromMpv(
    session: DesktopPlaybackSession,
    queryPositionMs: suspend () -> Result<Long?>,
    saveProgress: suspend (episodeId: String, positionMs: Long, lastWatched: Long) -> Result<Unit>,
    nowMillis: () -> Long = System::currentTimeMillis,
): Result<Long?> =
    syncObservedPlaybackProgress(
        session = session,
        queryPositionMs = queryPositionMs,
        saveProgress = saveProgress,
        nowMillis = nowMillis,
    )
