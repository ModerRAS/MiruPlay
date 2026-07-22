package com.miruplay.tv.repository

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.EpisodeVersion
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.availableVersions
import com.miruplay.tv.model.groupEpisodeVersions
import com.miruplay.tv.model.nearestTo
import com.miruplay.tv.model.toPlaybackSource
import com.miruplay.tv.model.withVersion

class NextPlaybackSourceResolver(
    private val metadata: MetadataRepository,
    private val progress: PlaybackProgressRepository,
    private val mediaSources: MediaSourceRepository,
    index: MediaIndexRepository? = null,
    mergeSameAnimeEnabled: suspend () -> Boolean = { false },
    private val playbackUriForEpisode: suspend (Episode) -> String = { episode ->
        resolvePlayableUri(
            path = episode.filePath,
            episodeId = episode.id,
            mediaRepository = mediaSources,
        )
    },
) {
    private val libraryEpisodeResolver = index?.let {
        LibraryEpisodeResolver(mediaSources, metadata, it, progress, mergeSameAnimeEnabled)
    }
    private val libraryAnimeResolver = index?.let {
        LibraryAnimeResolver(mediaSources, metadata, it, mergeSameAnimeEnabled)
    }

    suspend fun build(currentSource: PlaybackSource): PlaybackSource? {
        val currentPath = currentSource.episodeId
            ?.let { loadEpisode(it)?.filePath }
            ?: currentSource.uri
        val nextEpisode = nextEpisode(currentSource) ?: return null
        val version = nextEpisode.availableVersions().nearestTo(currentPath) ?: return null
        return build(nextEpisode, version)
    }

    suspend fun build(currentEpisodeId: String?): PlaybackSource? {
        val episodeId = currentEpisodeId ?: return null
        val currentEpisode = loadEpisode(episodeId) ?: return null
        return build(
            PlaybackSource(
                uri = currentEpisode.filePath,
                mediaSourceId = currentEpisode.animeId,
                episodeId = currentEpisode.id,
                progressId = currentEpisode.progressId,
            ),
        )
    }

    suspend fun nextEpisode(currentSource: PlaybackSource): Episode? =
        findNextLogicalEpisode(
            currentEpisodeId = currentSource.episodeId,
            currentProgressId = currentSource.progressId,
            currentAnimeId = currentSource.mediaSourceId,
            loadCurrentEpisode = ::loadEpisode,
            loadEpisodes = ::loadEpisodes,
        )

    private suspend fun loadEpisode(episodeId: String): Episode? =
        metadata.getCachedEpisode(episodeId).getOrNull()
            ?: libraryEpisodeResolver?.findEpisodeById(episodeId)

    private suspend fun loadEpisodes(animeId: String): List<Episode> =
        libraryAnimeResolver?.loadAnimeDetail(animeId)?.episodes
            ?: metadata.getCachedEpisodes(animeId).getOrNull().orEmpty()

    suspend fun build(episode: Episode, version: EpisodeVersion): PlaybackSource {
        val selected = episode.withVersion(version)
        return selected.toPlaybackSource(
            playableUri = playbackUriForEpisode(selected),
            progress = progress.progressFor(episode),
        )
    }
}

suspend fun buildNextPlaybackSource(
    currentEpisodeId: String?,
    loadCurrentEpisode: suspend (String) -> Episode?,
    loadEpisodes: suspend (String) -> List<Episode>,
    loadProgress: suspend (String) -> ProgressRecord?,
    resolvePlayableUri: suspend (Episode) -> String = { episode -> episode.filePath },
    currentPath: String? = null,
    currentProgressId: String? = null,
    currentAnimeId: String? = null,
): PlaybackSource? {
    val nextEpisode = findNextLogicalEpisode(
        currentEpisodeId = currentEpisodeId,
        currentProgressId = currentProgressId,
        currentAnimeId = currentAnimeId,
        loadCurrentEpisode = loadCurrentEpisode,
        loadEpisodes = loadEpisodes,
    ) ?: return null
    val version = nextEpisode.availableVersions().nearestTo(currentPath.orEmpty()) ?: return null
    val selected = nextEpisode.withVersion(version)
    val progress = (listOf(nextEpisode.progressId) + nextEpisode.availableVersions().map { it.episodeId })
        .distinct()
        .mapNotNull { loadProgress(it) }
        .maxByOrNull(ProgressRecord::lastWatched)
    return selected.toPlaybackSource(
        playableUri = resolvePlayableUri(selected),
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
        currentProgressId = currentSource.progressId,
        currentPath = currentSource.uri,
        currentAnimeId = currentSource.mediaSourceId,
        loadCurrentEpisode = loadCurrentEpisode,
        loadEpisodes = loadEpisodes,
        loadProgress = loadProgress,
        resolvePlayableUri = resolvePlayableUri,
    )

private suspend fun findNextLogicalEpisode(
    currentEpisodeId: String?,
    currentProgressId: String?,
    currentAnimeId: String? = null,
    loadCurrentEpisode: suspend (String) -> Episode?,
    loadEpisodes: suspend (String) -> List<Episode>,
): Episode? {
    val episodeId = currentEpisodeId ?: return null
    val currentEpisode = loadCurrentEpisode(episodeId) ?: return null
    val logicalAnimeId = currentAnimeId?.takeIf(String::isNotBlank) ?: currentEpisode.animeId
    val episodes = loadEpisodes(logicalAnimeId).groupEpisodeVersions(logicalAnimeId)
    val currentIndex = episodes.indexOfFirst { episode ->
        episode.progressId == currentProgressId ||
            episode.availableVersions().any { it.episodeId == episodeId }
    }
    if (currentIndex < 0 || currentIndex >= episodes.lastIndex) return null
    return episodes[currentIndex + 1]
}
