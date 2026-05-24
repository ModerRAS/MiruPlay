package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.resumePosition

fun PlayEpisodeRequest.startPositionFor(
    episode: Episode,
    progress: ProgressRecord?,
): Long =
    startPositionMs ?: episode.resumePosition(progress)

fun PlayEpisodeRequest.toWebControlPlaybackSource(
    episode: Episode,
    progress: ProgressRecord?,
    playableUri: String = episode.filePath,
): PlaybackSource =
    PlaybackSource(
        uri = playableUri,
        mediaSourceId = episode.animeId,
        startPosition = startPositionFor(episode, progress),
        subtitleTracks = emptyList(),
        episodeId = episode.id,
    )

fun PlaybackSource.toWebPlaybackSource(): WebPlaybackSource =
    WebPlaybackSource(
        uri = uri,
        mediaSourceId = mediaSourceId,
        startPositionMs = startPosition,
        episodeId = episodeId,
    )
