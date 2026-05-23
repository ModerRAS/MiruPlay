package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.repository.savePlaybackProgressOnStop

internal data class DesktopPlaybackStopResult(
    val savedPositionMs: Long?,
    val stoppedPlayer: Boolean,
)

internal suspend fun stopDesktopPlayback(
    player: MpvProcessPlayer?,
    session: PlaybackProgressSession?,
    saveProgress: suspend (
        episodeId: String,
        positionMs: Long,
        lastWatched: Long,
        incrementPlayCount: Boolean,
    ) -> Result<Unit>,
): DesktopPlaybackStopResult {
    val savedPosition = session?.let { activeSession ->
        savePlaybackProgressOnStop(
            session = activeSession,
            queryPositionMs = null,
            saveProgress = saveProgress,
        ).getOrNull()
    }
    player?.stop()
    return DesktopPlaybackStopResult(
        savedPositionMs = savedPosition,
        stoppedPlayer = player != null,
    )
}
