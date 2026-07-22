package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.availableVersions
import com.miruplay.tv.model.groupEpisodeVersions
import com.miruplay.tv.model.isCompleted
import com.miruplay.tv.model.withVersion

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
    private val animeResolver = LibraryAnimeResolver(
        mediaSources = mediaSources,
        metadata = metadata,
        index = index,
        mergeSameAnimeEnabled = mergeSameAnimeEnabled,
    )

    suspend fun findEpisodeById(episodeId: String): Episode? {
        metadata.getCachedEpisode(episodeId).getOrNull()?.let { return it }
        findLogicalEpisodeById(episodeId)?.let { return it }
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
        loadContinueWatchingEpisodesResult(limit).getOrNull().orEmpty()

    suspend fun loadContinueWatchingEpisodesResult(limit: Int = 20): Result<List<LibraryContinueWatchingEpisode>> {
        if (limit <= 0) return Result.success(emptyList())
        val logicalEpisodesByAnimeId = mutableMapOf<String, List<Episode>>()
        val episodesByProgressId = mutableMapOf<String, Episode?>()
        val animeById = mutableMapOf<String, Anime?>()
        var candidateLimit = limit
        while (true) {
            val records = when (val result = progress.getContinueWatching(candidateLimit)) {
                is Result.Success -> result.data
                is Result.Error -> return Result.failure(result.error)
            }
            val items = records.mapNotNull { record ->
                record.toContinueWatchingEpisode(
                    logicalEpisodesByAnimeId = logicalEpisodesByAnimeId,
                    episodesByProgressId = episodesByProgressId,
                    animeById = animeById,
                )
            }
                .groupBy { it.episode.progressId }
                .mapNotNull { (_, matches) -> matches.maxByOrNull { it.progress.lastWatched } }
                .filterNot { it.episode.isCompleted(it.progress) }
                .sortedByDescending { it.progress.lastWatched }
                .take(limit)
            if (items.size == limit || records.size < candidateLimit || candidateLimit == Int.MAX_VALUE) {
                return Result.success(items)
            }
            candidateLimit = if (candidateLimit > Int.MAX_VALUE / 2) Int.MAX_VALUE else candidateLimit * 2
        }
    }

    private suspend fun ProgressRecord.toContinueWatchingEpisode(
        logicalEpisodesByAnimeId: MutableMap<String, List<Episode>>,
        episodesByProgressId: MutableMap<String, Episode?>,
        animeById: MutableMap<String, Anime?>,
    ): LibraryContinueWatchingEpisode? {
        val episode = if (episodesByProgressId.containsKey(episodeId)) {
            episodesByProgressId[episodeId]
        } else {
            findContinueWatchingEpisode(episodeId, logicalEpisodesByAnimeId)
                .also { episodesByProgressId[episodeId] = it }
        } ?: return null
        val anime = if (animeById.containsKey(episode.animeId)) {
            animeById[episode.animeId]
        } else {
            findAnimeById(episode.animeId).also { animeById[episode.animeId] = it }
        }
        return LibraryContinueWatchingEpisode(
            progress = this,
            episode = episode.copy(
                watchedPosition = positionMs,
                lastWatchedTimestamp = lastWatched,
                playCount = playCount,
            ),
            anime = anime,
        )
    }

    private suspend fun findContinueWatchingEpisode(
        episodeId: String,
        logicalEpisodesByAnimeId: MutableMap<String, List<Episode>>,
    ): Episode? {
        val logicalMatch = LOGICAL_EPISODE_PROGRESS_ID.matchEntire(episodeId)
        if (logicalMatch != null) {
            val animeId = logicalMatch.groupValues[1]
            val seasonNumber = logicalMatch.groupValues[2].toIntOrNull() ?: return null
            val episodeNumber = logicalMatch.groupValues[3].toIntOrNull() ?: return null
            return logicalEpisodesForAnime(animeId, logicalEpisodesByAnimeId)
                .firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
        }

        val physical = findEpisodeById(episodeId) ?: return null
        if (physical.versions.isNotEmpty()) return physical
        val logical = logicalEpisodesForAnime(physical.animeId, logicalEpisodesByAnimeId)
            .firstOrNull { episode -> episode.availableVersions().any { it.episodeId == physical.id } }
            ?: return physical
        return logical.availableVersions()
            .firstOrNull { it.episodeId == physical.id }
            ?.let(logical::withVersion)
            ?: logical
    }

    private suspend fun logicalEpisodesForAnime(
        animeId: String,
        cache: MutableMap<String, List<Episode>>,
    ): List<Episode> {
        cache[animeId]?.let { return it }
        val cached = metadata.getCachedEpisodes(animeId).getOrNull().orEmpty()
        val episodes = if (cached.isNotEmpty()) {
            cached.groupEpisodeVersions(logicalAnimeId = animeId)
        } else {
            animeResolver.loadAnimeDetail(animeId)?.episodes.orEmpty()
        }
        cache[animeId] = episodes
        return episodes
    }

    private suspend fun findLogicalEpisodeById(episodeId: String): Episode? {
        val match = LOGICAL_EPISODE_PROGRESS_ID.matchEntire(episodeId) ?: return null
        val animeId = match.groupValues[1]
        val seasonNumber = match.groupValues[2].toIntOrNull() ?: return null
        val episodeNumber = match.groupValues[3].toIntOrNull() ?: return null
        val cached = metadata.getCachedEpisodes(animeId)
            .getOrNull()
            .orEmpty()
            .groupEpisodeVersions(logicalAnimeId = animeId)
            .firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
        return cached ?: animeResolver.loadAnimeDetail(animeId)
            ?.episodes
            ?.firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
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
                    .filterNot(MediaIndexEntry::isSeriesExtra)
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

private val LOGICAL_EPISODE_PROGRESS_ID = Regex("^(.+)#S(\\d+)E(\\d+)$")
