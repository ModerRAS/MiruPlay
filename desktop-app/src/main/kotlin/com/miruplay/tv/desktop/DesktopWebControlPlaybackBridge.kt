package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.player.mpv.mpvIdleStatus
import com.miruplay.tv.player.mpv.mpvPausedStatus
import com.miruplay.tv.player.mpv.mpvPauseToggledStatus
import com.miruplay.tv.player.mpv.mpvPositionSyncedStatus
import com.miruplay.tv.player.mpv.mpvResumedStatus
import com.miruplay.tv.player.mpv.mpvSeekBackStatus
import com.miruplay.tv.player.mpv.mpvSeekForwardStatus
import com.miruplay.tv.player.mpv.mpvStoppedStatus
import com.miruplay.tv.webcontrol.PlayEpisodeRequest
import com.miruplay.tv.webcontrol.PlaybackCommandRequest
import com.miruplay.tv.webcontrol.PlaybackStatusDto
import com.miruplay.tv.webcontrol.startPositionFor
import kotlin.math.absoluteValue

internal class DesktopWebControlPlaybackHandlers {
    var playEpisode: suspend (PlayEpisodeRequest, Episode) -> PlaybackStatusDto = { _, _ ->
        throw UnsupportedOperationException("Windows WebUI 暂未接入远程播放启动")
    }
    var playbackCommand: suspend (PlaybackCommandRequest) -> PlaybackStatusDto = {
        throw UnsupportedOperationException("Windows WebUI 暂未接入远程播放控制")
    }
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
    when (command.command.lowercase()) {
        "pause" -> mpvPausedStatus()
        "resume", "play" -> mpvResumedStatus()
        "toggle" -> mpvPauseToggledStatus()
        "stop" -> mpvStoppedStatus()
        "seek" -> mpvPositionSyncedStatus(command.positionMs ?: 0L)
        "seek_relative" -> if ((command.deltaMs ?: 0L) < 0L) {
            mpvSeekBackStatus(seconds = ((command.deltaMs ?: 0L).absoluteValue / 1000L).toInt())
        } else {
            mpvSeekForwardStatus(seconds = ((command.deltaMs ?: 0L) / 1000L).toInt())
        }
        "skip_forward" -> mpvSeekForwardStatus(
            seconds = ((command.deltaMs ?: PLAYBACK_SEEK_FORWARD_SECONDS * 1000L) / 1000L).toInt(),
        )
        "skip_backward" -> mpvSeekBackStatus(
            seconds = ((command.deltaMs ?: PLAYBACK_SEEK_BACK_SECONDS * 1000L) / 1000L).toInt(),
        )
        else -> mpvIdleStatus()
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

internal fun desktopWebControlPlaybackStartPosition(
    request: PlayEpisodeRequest,
    episode: Episode,
    progress: ProgressRecord?,
): Long =
    request.startPositionFor(episode, progress)

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
