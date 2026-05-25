package com.miruplay.tv.repository

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.distinctSeasonEpisodeCount
import com.miruplay.tv.model.mergeAnimeGroupForDisplay
import com.miruplay.tv.model.mergeSameAnimeForDisplay
import com.miruplay.tv.model.sameAnimeGroupFor
import com.miruplay.tv.model.sortedForPlaybackQueue

data class LibraryIndexedAnimeGroup(
    val source: MediaSourceInfo,
    val group: MediaIndexPosterGroup,
) {
    val animeId: String = group.animeId
    val entries: List<MediaIndexEntry> = group.entries
}

data class LibraryAnimeDetail(
    val anime: Anime,
    val episodes: List<Episode>,
)

class LibraryAnimeResolver(
    private val mediaSources: MediaSourceRepository,
    private val metadata: MetadataRepository,
    private val index: MediaIndexRepository,
    private val mergeSameAnimeEnabled: suspend () -> Boolean = { false },
) {
    suspend fun loadAnime(): List<Anime> =
        loadAnime(loadIndexedGroups())

    suspend fun loadDisplayAnime(): List<Anime> {
        val mergeSameAnimeEnabled = mergeSameAnimeEnabled()
        val anime = loadAnime(loadIndexedGroups(mergeSameAnimeEnabled))
        return if (mergeSameAnimeEnabled) {
            anime.mergeSameAnimeForDisplay()
        } else {
            anime.distinctBy { it.id }
        }
    }

    suspend fun loadAnimeDetail(animeId: String): LibraryAnimeDetail? {
        val mergeSameAnimeEnabled = mergeSameAnimeEnabled()
        val groups = loadIndexedGroups(mergeSameAnimeEnabled)
        val anime = loadAnime(groups)
        val cached = metadata.getCachedMetadata(animeId).getOrNull()
        val groupAnimePairs = groups.zip(anime)
        val anchorPair = groupAnimePairs.firstOrNull { (group, resolvedAnime) ->
            resolvedAnime.id == animeId || group.cacheKeys(resolvedAnime.id).contains(animeId)
        }
        val anchor = anchorPair?.second
            ?: cached
            ?: return null
        val relatedAnime = if (mergeSameAnimeEnabled) {
            anime.sameAnimeGroupFor(anchor)
        } else {
            listOf(anchor)
        }.ifEmpty { listOf(anchor) }
        val relatedAnimeIds = relatedAnime.map { it.id }.toSet()
        val relatedPairs = groupAnimePairs.filter { (_, resolvedAnime) ->
            resolvedAnime.id in relatedAnimeIds
        }
        val pairedAnimeIds = relatedPairs.map { (_, relatedAnimeItem) -> relatedAnimeItem.id }.toSet()
        val episodes = (
            relatedPairs.flatMap { (relatedGroup, relatedAnimeItem) ->
                loadEpisodesForAnime(relatedAnimeItem, relatedGroup)
            } + relatedAnime
                .filterNot { it.id in pairedAnimeIds }
                .flatMap { loadEpisodesForAnime(it, null) }
        )
            .distinctBy { it.id }
            .sortedForPlaybackQueue()
        val displayAnime = if (relatedAnime.size > 1) {
            relatedAnime.mergeAnimeGroupForDisplay().copy(id = animeId)
        } else {
            anchor
        }.copy(
            episodeCount = maxOf(displayEpisodeCount(relatedAnime, anchor), episodes.distinctSeasonEpisodeCount()),
        )
        return LibraryAnimeDetail(
            anime = displayAnime,
            episodes = episodes,
        )
    }

    suspend fun loadIndexedGroups(): List<LibraryIndexedAnimeGroup> =
        loadIndexedGroups(mergeSameAnimeEnabled())

    private suspend fun loadAnime(groups: List<LibraryIndexedAnimeGroup>): List<Anime> =
        groups.map { group ->
            group.cachedAnime() ?: group.toAnime()
        }

    private suspend fun loadIndexedGroups(mergeSameAnimeEnabled: Boolean): List<LibraryIndexedAnimeGroup> {
        val sources = mediaSources.getSources().getOrNull().orEmpty()
        return sources.flatMap { source ->
            index.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .toMediaIndexPosterGroups(mergeSameAnimeEnabled)
                .map { group ->
                    LibraryIndexedAnimeGroup(
                        source = source,
                        group = group,
                    )
                }
        }
    }

    suspend fun loadEpisodesForAnime(
        anime: Anime,
        group: LibraryIndexedAnimeGroup?,
    ): List<Episode> {
        val cacheKeys = group?.cacheKeys(anime.id) ?: listOf(anime.id)
        val cachedEpisodes = cacheKeys.firstNotEmptyCachedEpisodes()
        if (cachedEpisodes.isNotEmpty()) return cachedEpisodes
        val indexedGroup = group ?: return emptyList()
        return indexedGroup.entries.toIndexedEpisodes(indexedGroup.source, indexedGroup.animeId)
    }

    private suspend fun LibraryIndexedAnimeGroup.cachedAnime(): Anime? {
        for (key in cacheKeys()) {
            metadata.getCachedMetadata(key).getOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun List<String>.firstNotEmptyCachedEpisodes(): List<Episode> {
        for (key in this) {
            val episodes = metadata.getCachedEpisodes(key).getOrNull().orEmpty()
            if (episodes.isNotEmpty()) return episodes
        }
        return emptyList()
    }
}

fun LibraryIndexedAnimeGroup.toAnime(): Anime =
    group.toIndexedAnime()

private fun LibraryIndexedAnimeGroup.cacheKeys(primaryAnimeId: String = animeId): List<String> =
    buildList {
        add(primaryAnimeId)
        add(animeId)
        entries.forEach { entry ->
            entry.metadataId?.takeIf { it.isNotBlank() }?.let(::add)
            entry.animeName?.takeIf { it.isNotBlank() }?.let(::add)
            entry.metadataTitle?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.distinct()

private fun displayEpisodeCount(relatedAnime: List<Anime>, fallback: Anime): Int =
    relatedAnime.maxOfOrNull { it.episodeCount } ?: fallback.episodeCount
