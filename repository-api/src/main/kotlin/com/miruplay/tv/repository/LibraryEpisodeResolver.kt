package com.miruplay.tv.repository

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.isCompleted

data class LibraryContinueWatchingEpisode(
    val progress: ProgressRecord,
    val episode: Episode,
    val anime: Anime?,
)

class LibraryEpisodeResolver(
    private val mediaSources: MediaSourceRepository,
    private val metadata: MetadataRepository,
    private val index: MediaIndexRepository,
    private val progress: PlaybackProgressRepository,
    private val mergeSameAnimeEnabled: suspend () -> Boolean = { false },
) {
    suspend fun findEpisodeById(episodeId: String): Episode? {
        metadata.getCachedEpisode(episodeId).getOrNull()?.let { return it }
        findIndexedEpisodeById(episodeId)?.let { return it }
        findIndexedEpisodeByPath(episodeId)?.let { return it }

        val sources = mediaSources.getSources().getOrNull().orEmpty()
        val candidateAnimeIds = linkedSetOf<String>()
        val mergeSameAnimeEnabled = mergeSameAnimeEnabled()
        for (source in sources) {
            candidateAnimeIds += index.getAnimeInIndex(source.id).getOrNull().orEmpty()
            candidateAnimeIds += index.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .toMediaIndexPosterGroups(mergeSameAnimeEnabled)
                .map { it.animeId }
        }

        for (animeId in candidateAnimeIds) {
            val episode = metadata.getCachedEpisodes(animeId)
                .getOrNull()
                .orEmpty()
                .firstOrNull { it.id == episodeId || it.filePath == episodeId }
            if (episode != null) return episode
        }
        return null
    }

    suspend fun loadContinueWatchingEpisodes(limit: Int = 20): List<LibraryContinueWatchingEpisode> =
        progress.getContinueWatching(limit).getOrNull().orEmpty().mapNotNull { record ->
            val episode = findEpisodeById(record.episodeId) ?: return@mapNotNull null
            if (episode.isCompleted(record)) return@mapNotNull null
            LibraryContinueWatchingEpisode(
                progress = record,
                episode = episode.copy(
                    watchedPosition = record.positionMs,
                    lastWatchedTimestamp = record.lastWatched,
                    playCount = record.playCount,
                ),
                anime = findAnimeById(episode.animeId),
            )
        }

    private suspend fun findIndexedEpisodeById(episodeId: String): Episode? {
        val sourceParts = episodeId.split(":", limit = 2)
        val sourceId = sourceParts.getOrNull(0)?.toLongOrNull() ?: return null
        val path = sourceParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return findIndexedEpisodeByPath(path = path, episodeId = episodeId, sourceIdHint = sourceId)
    }

    private suspend fun findIndexedEpisodeByPath(
        path: String,
        episodeId: String = path,
        sourceIdHint: Long? = null,
    ): Episode? {
        val sourceIds = sourceIdHint?.let(::listOf) ?: mediaSources.getSources().getOrNull().orEmpty().map { it.id }
        val fileName = MediaPathConventions.fileName(path)
        val normalizedStem = MediaPathConventions.stem(path)
        val inferredAnimeName = MediaPathConventions.animeNameFromEpisodePath(path)
        val searchTerms = buildList {
            inferredAnimeName?.takeIf { it.isNotBlank() }?.let(::add)
            normalizedStem.takeIf { it.isNotBlank() }?.let(::add)
            fileName.substringBeforeLast('.', fileName).takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()

        for (sourceId in sourceIds) {
            val source = mediaSources.getSourceById(sourceId).getOrNull()
            for (query in searchTerms) {
                val entries = index.queryIndex(sourceId, query)
                    .getOrNull()
                    .orEmpty()
                val entry = entries.firstOrNull { it.path == path || episodeId.endsWith(it.path) || path.endsWith(it.path) }
                    ?: entries.firstOrNull { MediaPathConventions.fileName(it.path) == fileName }
                    ?: continue
                val animeId = entry.mediaIndexPosterAnimeId(mergeSameAnimeEnabled())
                return entry.toIndexedEpisode(source, animeId).copy(id = episodeId)
            }
        }
        return null
    }

    private suspend fun findAnimeById(animeId: String): Anime? {
        metadata.getCachedMetadata(animeId).getOrNull()?.let { return it }
        return findIndexedAnimeById(animeId)
    }

    private suspend fun findIndexedAnimeById(animeId: String): Anime? {
        val mergeSameAnimeEnabled = mergeSameAnimeEnabled()
        val sources = mediaSources.getSources().getOrNull().orEmpty()
        for (source in sources) {
            val group = index.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .toMediaIndexPosterGroups(mergeSameAnimeEnabled)
                .firstOrNull { it.animeId == animeId }
                ?: continue
            return group.toIndexedAnime()
        }
        return null
    }
}
