package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaIndexPosterGroup
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.LibraryEpisodeResolver
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.toIndexedEpisodes
import com.miruplay.tv.repository.toIndexedAnime
import com.miruplay.tv.repository.toMediaIndexPosterGroups

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

    suspend fun loadLibrary(): LibraryDto {
        val anime = indexedAnimeGroups()
            .map { group -> metadata.getCachedMetadata(group.animeId).getOrNull() ?: group.toAnime() }
        return anime.toWebControlLibrary(continueWatching = loadContinueWatching())
    }

    suspend fun searchLibrary(query: String): LibraryDto =
        loadLibrary().filteredByQuery(query)

    suspend fun loadAnimeDetail(animeId: String): AnimeDetailDto {
        val group = indexedAnimeGroups().firstOrNull { it.animeId == animeId }
        val cached = metadata.getCachedMetadata(animeId).getOrNull()
        val anime = cached ?: group?.toAnime()
            ?: throw IllegalArgumentException("番剧不存在")
        val episodes = loadEpisodesForAnime(anime, group)
        return anime.toWebControlAnimeDetail(episodes) { episode ->
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

    private suspend fun loadEpisodesForAnime(
        anime: Anime,
        group: IndexedAnimeGroup?,
    ): List<Episode> {
        val cachedEpisodes = metadata.getCachedEpisodes(anime.id).getOrNull().orEmpty()
        if (cachedEpisodes.isNotEmpty()) return cachedEpisodes
        val indexedGroup = group ?: return emptyList()
        return indexedGroup.entries.toIndexedEpisodes(indexedGroup.source, indexedGroup.animeId)
    }

    private suspend fun indexedAnimeGroups(): List<IndexedAnimeGroup> {
        val sources = mediaSources.getSources().getOrNull().orEmpty()
        val mergeSameAnimeEnabled = mergeSameAnimeEnabled()
        return sources.flatMap { source ->
            index.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .toMediaIndexPosterGroups(mergeSameAnimeEnabled)
                .map { group ->
                    IndexedAnimeGroup(
                        source = source,
                        group = group,
                    )
                }
        }
    }

    private fun IndexedAnimeGroup.toAnime(): Anime {
        return group.toIndexedAnime()
    }

    private data class IndexedAnimeGroup(
        val source: MediaSourceInfo,
        val group: MediaIndexPosterGroup,
    ) {
        val animeId: String = group.animeId
        val entries = group.entries
    }
}
