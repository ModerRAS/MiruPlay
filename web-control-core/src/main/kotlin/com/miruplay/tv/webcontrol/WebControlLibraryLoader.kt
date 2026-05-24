package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Episode
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
            progress.getProgress(episode.id).getOrNull()
        }
    }

    suspend fun findEpisodeById(episodeId: String): Episode? {
        return episodeResolver.findEpisodeById(episodeId)
    }

    suspend fun loadContinueWatching(): List<ContinueWatchingDto> =
        episodeResolver.loadContinueWatchingEpisodes(limit = 30).map { item ->
            item.progress.toWebControlContinueWatching(item.episode, item.anime)
        }
}
