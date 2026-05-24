package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.mediaIndexPosterAnimeId
import com.miruplay.tv.repository.toIndexedEpisode
import com.miruplay.tv.repository.toIndexedEpisodes
import com.miruplay.tv.repository.toMediaIndexPosterGroups

class WebControlLibraryLoader(
    private val mediaSources: MediaSourceRepository,
    private val metadata: MetadataRepository,
    private val index: MediaIndexRepository,
    private val progress: PlaybackProgressRepository,
    private val mergeSameAnimeEnabled: suspend () -> Boolean = { false },
) {
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
        metadata.getCachedEpisode(episodeId).getOrNull()?.let { return it }
        findIndexedEpisodeById(episodeId)?.let { return it }

        val sources = mediaSources.getSources().getOrNull().orEmpty()
        val candidateAnimeIds = linkedSetOf<String>()
        for (source in sources) {
            candidateAnimeIds += index.getAnimeInIndex(source.id).getOrNull().orEmpty()
        }
        candidateAnimeIds += indexedAnimeGroups().map { it.animeId }

        for (animeId in candidateAnimeIds) {
            val episode = metadata.getCachedEpisodes(animeId)
                .getOrNull()
                .orEmpty()
                .firstOrNull { it.id == episodeId || it.filePath == episodeId }
            if (episode != null) return episode
        }
        return null
    }

    suspend fun loadContinueWatching(): List<ContinueWatchingDto> =
        progress.getContinueWatching(30).getOrNull().orEmpty().map { record ->
            val episode = findEpisodeById(record.episodeId)
            val anime = episode?.let { metadata.getCachedMetadata(it.animeId).getOrNull() }
                ?: episode?.let { indexedAnimeGroups().firstOrNull { group -> group.animeId == it.animeId }?.toAnime() }
            record.toWebControlContinueWatching(episode, anime)
        }

    private suspend fun findIndexedEpisodeById(episodeId: String): Episode? {
        val sourceParts = episodeId.split(":", limit = 2)
        val sourceId = sourceParts.getOrNull(0)?.toLongOrNull() ?: return null
        val path = sourceParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val source = mediaSources.getSourceById(sourceId).getOrNull()
        val fileName = MediaPathConventions.fileName(path)
        val entries = index.queryIndex(sourceId, MediaPathConventions.stem(path))
            .getOrNull()
            .orEmpty()
        val entry = entries.firstOrNull { it.path == path || episodeId.endsWith(it.path) }
            ?: entries.firstOrNull { MediaPathConventions.fileName(it.path) == fileName }
            ?: return null
        val animeId = entry.mediaIndexPosterAnimeId(mergeSameAnimeEnabled())
        return entry.toIndexedEpisode(source, animeId).copy(id = episodeId)
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
                        animeId = group.animeId,
                        title = group.title,
                        entries = group.entries,
                    )
                }
        }
    }

    private fun IndexedAnimeGroup.toAnime(): Anime {
        val first = entries.first()
        return Anime(
            id = animeId,
            title = title,
            episodeCount = entries.size,
            summary = first.plot.orEmpty(),
        )
    }

    private data class IndexedAnimeGroup(
        val source: MediaSourceInfo,
        val animeId: String,
        val title: String,
        val entries: List<MediaIndexEntry>,
    )
}
