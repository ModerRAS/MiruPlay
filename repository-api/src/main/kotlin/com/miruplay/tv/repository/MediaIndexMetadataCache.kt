package com.miruplay.tv.repository

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.distinctSeasonEpisodeCount

data class MediaIndexMetadataCacheResult(
    val animeCached: Int,
    val episodesCached: Int,
)

class MediaIndexMetadataCache(
    private val metadata: MetadataRepository,
) {
    suspend fun cache(
        source: MediaSourceInfo,
        entries: List<MediaIndexEntry>,
        animeTransform: suspend (animeId: String, episodes: List<Episode>) -> Anime? = { _, _ -> null },
        episodeTransform: suspend (animeId: String, episodes: List<Episode>) -> List<Episode> = { _, episodes -> episodes },
    ): MediaIndexMetadataCacheResult {
        var animeCached = 0
        var episodesCached = 0
        entries
            .mediaFilesOnly()
            .filterNot(MediaIndexEntry::isSeriesExtra)
            .groupBy { it.animeName?.takeIf(String::isNotBlank) ?: "Unknown" }
            .forEach { (animeId, animeEntries) ->
                val episodes = episodeTransform(
                    animeId,
                    animeEntries.toCachedIndexedEpisodes(source, animeId),
                )
                metadata.cacheEpisodes(animeId, episodes)
                episodesCached += episodes.size
                val logicalEpisodeCount = episodes.distinctSeasonEpisodeCount()

                val anime = animeTransform(animeId, episodes)
                    ?: metadata.getCachedMetadata(animeId).getOrNull()?.copy(episodeCount = logicalEpisodeCount)
                    ?: Anime(
                        id = animeId,
                        title = animeId,
                        titleCn = animeId,
                        episodeCount = logicalEpisodeCount,
                    )
                metadata.cacheMetadata(anime)
                animeCached += 1
            }
        return MediaIndexMetadataCacheResult(
            animeCached = animeCached,
            episodesCached = episodesCached,
        )
    }
}

fun MediaIndexEntry.toCachedIndexedEpisode(
    source: MediaSourceInfo?,
    animeId: String,
    fallbackEpisodeNumber: Int,
): Episode =
    Episode(
        id = "$sourceId:$path",
        animeId = animeId,
        seasonNumber = seasonNumber ?: 1,
        episodeNumber = episodeNumber?.takeIf { it > 0 } ?: fallbackEpisodeNumber,
        title = episodeTitle.orEmpty(),
        filePath = source.playableUriForIndexedPath(path),
        fileName = indexCacheFileName(path),
    )

fun List<MediaIndexEntry>.toCachedIndexedEpisodes(
    source: MediaSourceInfo?,
    animeId: String,
): List<Episode> {
    val sortedEntries = filterNot(MediaIndexEntry::isSeriesExtra)
        .sortedByMediaIndexEpisodeOrder()
    val usedEpisodeNumbers = sortedEntries
        .groupBy { it.seasonNumber ?: 1 }
        .mapValuesTo(mutableMapOf()) { (_, seasonEntries) ->
            seasonEntries.mapNotNull { it.episodeNumber?.takeIf { number -> number > 0 } }.toMutableSet()
        }
    val nextEpisodeNumbers = mutableMapOf<Int, Int>()

    return sortedEntries.map { entry ->
        val seasonNumber = entry.seasonNumber ?: 1
        val fallbackEpisodeNumber = entry.episodeNumber?.takeIf { it > 0 } ?: run {
            val used = usedEpisodeNumbers.getOrPut(seasonNumber, ::mutableSetOf)
            var candidate = nextEpisodeNumbers[seasonNumber] ?: 1
            while (!used.add(candidate)) candidate += 1
            nextEpisodeNumbers[seasonNumber] = candidate + 1
            candidate
        }
        entry.toCachedIndexedEpisode(
            source = source,
            animeId = animeId,
            fallbackEpisodeNumber = fallbackEpisodeNumber,
        )
    }
}

private fun indexCacheFileName(path: String): String =
    if (path.startsWith("content://")) {
        path.substringAfterLast(':', path)
            .substringAfterLast('/')
            .let(MediaPathConventions::decodePath)
    } else {
        MediaPathConventions.fileName(path)
    }
