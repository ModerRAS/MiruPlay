package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.player.mpv.mpvIdleStatus
import com.miruplay.tv.player.mpv.mpvPausedStatus
import com.miruplay.tv.player.mpv.mpvPauseToggledStatus
import com.miruplay.tv.player.mpv.mpvPositionSyncedStatus
import com.miruplay.tv.player.mpv.mpvResumedStatus
import com.miruplay.tv.player.mpv.mpvSeekBackStatus
import com.miruplay.tv.player.mpv.mpvSeekForwardStatus
import com.miruplay.tv.player.mpv.mpvSpeedChangedStatus
import com.miruplay.tv.player.mpv.mpvStoppedStatus
import com.miruplay.tv.webcontrol.PlayEpisodeRequest
import com.miruplay.tv.webcontrol.PlaybackCommandRequest
import com.miruplay.tv.webcontrol.PlaybackStatusDto
import com.miruplay.tv.webcontrol.WebControlPlaybackCommandKind
import com.miruplay.tv.webcontrol.absoluteSeekPositionMs
import com.miruplay.tv.webcontrol.idleWebControlPlaybackStatus
import com.miruplay.tv.webcontrol.playbackCommandKind
import com.miruplay.tv.webcontrol.playbackSpeed
import com.miruplay.tv.webcontrol.relativeSeekDeltaMs
import com.miruplay.tv.webcontrol.skipBackwardDeltaMs
import com.miruplay.tv.webcontrol.skipForwardDeltaMs
import com.miruplay.tv.webcontrol.toWebControlPlaybackSource
import kotlin.math.absoluteValue

internal class DesktopWebControlPlaybackHandlers {
    @Volatile
    private var playEpisodeHandler: suspend (PlayEpisodeRequest, Episode) -> PlaybackStatusDto = { _, _ ->
        idleWebControlPlaybackStatus()
    }

    @Volatile
    private var playbackCommandHandler: suspend (PlaybackCommandRequest) -> PlaybackStatusDto = {
        idleWebControlPlaybackStatus()
    }

    fun updatePlayEpisode(handler: suspend (PlayEpisodeRequest, Episode) -> PlaybackStatusDto) {
        playEpisodeHandler = handler
    }

    fun updatePlaybackCommand(handler: suspend (PlaybackCommandRequest) -> PlaybackStatusDto) {
        playbackCommandHandler = handler
    }

    suspend fun playEpisode(request: PlayEpisodeRequest, episode: Episode): PlaybackStatusDto =
        playEpisodeHandler(request, episode)

    suspend fun playbackCommand(command: PlaybackCommandRequest): PlaybackStatusDto =
        playbackCommandHandler(command)
}

internal data class DesktopWebControlPlaybackSourceSelection(
    val sourceId: Long?,
    val source: DesktopMediaSource?,
    val ownsSource: Boolean,
)

internal data class DesktopWebControlNextPlaybackSource(
    val source: DesktopMediaSource?,
    val sourceId: Long?,
    val episodeId: String?,
)

internal fun webControlPlaybackCommandStatus(command: PlaybackCommandRequest): String =
    when (command.playbackCommandKind()) {
        WebControlPlaybackCommandKind.PAUSE -> mpvPausedStatus()
        WebControlPlaybackCommandKind.RESUME -> mpvResumedStatus()
        WebControlPlaybackCommandKind.TOGGLE -> mpvPauseToggledStatus()
        WebControlPlaybackCommandKind.STOP -> mpvStoppedStatus()
        WebControlPlaybackCommandKind.SEEK -> mpvPositionSyncedStatus(command.absoluteSeekPositionMs())
        WebControlPlaybackCommandKind.SEEK_RELATIVE -> {
            val deltaMs = command.relativeSeekDeltaMs()
            if (deltaMs < 0L) {
                mpvSeekBackStatus(seconds = (deltaMs.absoluteValue / 1000L).toInt())
            } else {
                mpvSeekForwardStatus(seconds = (deltaMs / 1000L).toInt())
            }
        }
        WebControlPlaybackCommandKind.SKIP_FORWARD -> mpvSeekForwardStatus(
            seconds = (command.skipForwardDeltaMs() / 1000L).toInt(),
        )
        WebControlPlaybackCommandKind.SKIP_BACKWARD -> mpvSeekBackStatus(
            seconds = (command.skipBackwardDeltaMs() / 1000L).toInt(),
        )
        WebControlPlaybackCommandKind.SPEED -> mpvSpeedChangedStatus(command.playbackSpeed())
        WebControlPlaybackCommandKind.UNKNOWN -> mpvIdleStatus()
    }

internal suspend fun desktopWebControlPlaybackSourceSelection(
    episode: Episode,
    savedSources: List<MediaSourceInfo>,
    activeSourceId: Long?,
    activeSource: DesktopMediaSource?,
    activeLocalSource: DesktopLocalMediaSource?,
    loadSourceById: suspend (Long) -> MediaSourceInfo?,
    sourceFactory: (MediaSourceInfo) -> DesktopMediaSource = ::desktopSourceFromInfo,
): DesktopWebControlPlaybackSourceSelection {
    val sourceId = episode.sourceIdFromEpisodeId()
    val sourceInfo = sourceId?.let { id ->
        savedSources.firstOrNull { it.id == id } ?: loadSourceById(id)
    }
    val source = when {
        sourceInfo == null -> null
        sourceInfo.type == MediaSourceType.LOCAL -> activeLocalSource ?: sourceFactory(sourceInfo)
        sourceInfo.id == activeSourceId -> activeSource ?: sourceFactory(sourceInfo)
        else -> sourceFactory(sourceInfo)
    }
    return DesktopWebControlPlaybackSourceSelection(
        sourceId = sourceId,
        source = source,
        ownsSource = source != null && source !== activeSource && source !== activeLocalSource,
    )
}

internal fun desktopWebControlPlaybackSource(
    request: PlayEpisodeRequest,
    episode: Episode,
    progress: ProgressRecord?,
): PlaybackSource =
    request.toWebControlPlaybackSource(episode, progress)

internal fun desktopWebControlNextPlaybackSource(
    nextTarget: PlaybackSource?,
    currentWebControlPlaybackSource: DesktopMediaSource?,
): DesktopWebControlNextPlaybackSource =
    DesktopWebControlNextPlaybackSource(
        source = nextTarget?.let { currentWebControlPlaybackSource },
        sourceId = nextTarget?.episodeId?.sourceIdFromEpisodeId(),
        episodeId = nextTarget?.episodeId,
    )

private fun Episode.sourceIdFromEpisodeId(): Long? =
    id.sourceIdFromEpisodeId()

private fun String.sourceIdFromEpisodeId(): Long? =
    substringBefore(':', "").toLongOrNull()
