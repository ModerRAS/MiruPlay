package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.availableVersions
import com.miruplay.tv.model.withVersion
import com.miruplay.tv.repository.LibraryAnimeResolver
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.LibraryEpisodeResolver
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository

class WebControlLibraryLoader(
    private val mediaSources: MediaSourceRepository,
    private val metadata: MetadataRepository,
    private val index: MediaIndexRepository,
    private val progress: PlaybackProgressRepository,
    private val mergeSameAnimeEnabled: suspend () -> Boolean = { false },
) {
    private val episodeResolver = LibraryEpisodeResolver(
        mediaSources = mediaSources,
        metadata = metadata,
        index = index,
        progress = progress,
        mergeSameAnimeEnabled = mergeSameAnimeEnabled,
    )
    private val animeResolver = LibraryAnimeResolver(
        mediaSources = mediaSources,
        metadata = metadata,
        index = index,
        mergeSameAnimeEnabled = mergeSameAnimeEnabled,
    )

    suspend fun loadLibrary(): LibraryDto {
        val anime = animeResolver.loadDisplayAnime()
        return anime.toWebControlLibrary(continueWatching = loadContinueWatching())
    }

    suspend fun searchLibrary(query: String): LibraryDto =
        loadLibrary().filteredByQuery(query)

    suspend fun loadAnimeDetail(animeId: String): AnimeDetailDto {
        val detail = animeResolver.loadAnimeDetail(animeId)
            ?: throw IllegalArgumentException("番剧不存在")
        return detail.anime.toWebControlAnimeDetail(detail.episodes) { episode ->
            (listOf(episode.progressId) + episode.availableVersions().map { it.episodeId })
                .distinct()
                .mapNotNull { progress.getProgress(it).getOrNull() }
                .maxByOrNull(ProgressRecord::lastWatched)
        }
    }

    suspend fun findEpisodeById(episodeId: String): Episode? {
        val physical = episodeResolver.findEpisodeById(episodeId) ?: return null
        val logical = animeResolver.loadAnimeDetail(physical.animeId)
            ?.episodes
            ?.firstOrNull { episode -> episode.availableVersions().any { it.episodeId == episodeId } }
            ?: return physical
        val version = logical.availableVersions().first { it.episodeId == episodeId }
        return logical.withVersion(version)
    }

    suspend fun loadContinueWatching(): List<ContinueWatchingDto> =
        episodeResolver.loadContinueWatchingEpisodes(limit = 30).map { item ->
            item.progress.toWebControlContinueWatching(item.episode, item.anime)
        }
}
