package com.miruplay.tv.repository

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.availableVersions
import com.miruplay.tv.model.coercePlaybackPosition
import com.miruplay.tv.model.toPlaybackSource

class EpisodePlaybackSourceResolver(
    private val progress: PlaybackProgressRepository,
    private val mediaSources: MediaSourceRepository,
    private val playbackUriForEpisode: suspend (Episode) -> String = { episode ->
        resolvePlayableUri(
            path = episode.filePath,
            episodeId = episode.id,
            mediaRepository = mediaSources,
        )
    },
) {
    suspend fun build(
        episode: Episode,
        startPositionOverrideMs: Long? = null,
    ): PlaybackSource =
        buildEpisodePlaybackSource(
            episode = episode,
            progress = progress.progressFor(episode),
            playableUri = playbackUriForEpisode(episode),
            startPositionOverrideMs = startPositionOverrideMs,
        )
}

suspend fun PlaybackProgressRepository.progressFor(episode: Episode): ProgressRecord? =
    (listOf(episode.progressId) + episode.availableVersions().map { it.episodeId })
        .distinct()
        .mapNotNull { getProgress(it).getOrNull() }
        .maxByOrNull(ProgressRecord::lastWatched)

fun buildEpisodePlaybackSource(
    episode: Episode,
    progress: ProgressRecord?,
    playableUri: String = episode.filePath,
    startPositionOverrideMs: Long? = null,
): PlaybackSource =
    episode.toPlaybackSource(
        playableUri = playableUri,
        progress = progress,
    ).let { source ->
        startPositionOverrideMs?.let { source.copy(startPosition = episode.coercePlaybackPosition(it)) } ?: source
    }
