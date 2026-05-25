package com.miruplay.tv.repository

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.nextEpisodeAfter
import com.miruplay.tv.model.toPlaybackSource

class NextPlaybackSourceResolver(
    private val metadata: MetadataRepository,
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
    suspend fun build(currentSource: PlaybackSource): PlaybackSource? =
        build(currentSource.episodeId)

    suspend fun build(currentEpisodeId: String?): PlaybackSource? =
        buildNextPlaybackSource(
            currentEpisodeId = currentEpisodeId,
            loadCurrentEpisode = { episodeId -> metadata.getCachedEpisode(episodeId).getOrNull() },
            loadEpisodes = { animeId -> metadata.getCachedEpisodes(animeId).getOrNull().orEmpty() },
            loadProgress = { episodeId -> progress.getProgress(episodeId).getOrNull() },
            resolvePlayableUri = playbackUriForEpisode,
        )
}

suspend fun buildNextPlaybackSource(
    currentEpisodeId: String?,
    loadCurrentEpisode: suspend (String) -> Episode?,
    loadEpisodes: suspend (String) -> List<Episode>,
    loadProgress: suspend (String) -> ProgressRecord?,
    resolvePlayableUri: suspend (Episode) -> String = { episode -> episode.filePath },
): PlaybackSource? {
    val episodeId = currentEpisodeId ?: return null
    val currentEpisode = loadCurrentEpisode(episodeId) ?: return null
    val episodes = loadEpisodes(currentEpisode.animeId)
    val nextEpisode = episodes.nextEpisodeAfter(currentEpisode.id) ?: return null
    val progress = loadProgress(nextEpisode.id)
    return nextEpisode.toPlaybackSource(
        playableUri = resolvePlayableUri(nextEpisode),
        progress = progress,
    )
}

suspend fun buildNextPlaybackSource(
    currentSource: PlaybackSource,
    loadCurrentEpisode: suspend (String) -> Episode?,
    loadEpisodes: suspend (String) -> List<Episode>,
    loadProgress: suspend (String) -> ProgressRecord?,
    resolvePlayableUri: suspend (Episode) -> String = { episode -> episode.filePath },
): PlaybackSource? =
    buildNextPlaybackSource(
        currentEpisodeId = currentSource.episodeId,
        loadCurrentEpisode = loadCurrentEpisode,
        loadEpisodes = loadEpisodes,
        loadProgress = loadProgress,
        resolvePlayableUri = resolvePlayableUri,
    )
