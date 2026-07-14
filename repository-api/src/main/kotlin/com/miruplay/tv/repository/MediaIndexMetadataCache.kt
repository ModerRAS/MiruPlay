package com.miruplay.tv.repository

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo

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

                val anime = animeTransform(animeId, episodes)
                    ?: metadata.getCachedMetadata(animeId).getOrNull()?.copy(episodeCount = episodes.size)
                    ?: Anime(
                        id = animeId,
                        title = animeId,
                        titleCn = animeId,
                        episodeCount = episodes.size,
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
        episodeNumber = episodeNumber ?: fallbackEpisodeNumber,
        title = episodeTitle.orEmpty(),
        filePath = source.playableUriForIndexedPath(path),
        fileName = indexCacheFileName(path),
    )

fun List<MediaIndexEntry>.toCachedIndexedEpisodes(
    source: MediaSourceInfo?,
    animeId: String,
): List<Episode> =
    filterNot(MediaIndexEntry::isSeriesExtra)
        .sortedByMediaIndexEpisodeOrder()
        .mapIndexed { index, entry ->
            entry.toCachedIndexedEpisode(
                source = source,
                animeId = animeId,
                fallbackEpisodeNumber = index + 1,
            )
        }

private fun indexCacheFileName(path: String): String =
    if (path.startsWith("content://")) {
        path.substringAfterLast(':', path)
            .substringAfterLast('/')
            .let(MediaPathConventions::decodePath)
    } else {
        MediaPathConventions.fileName(path)
    }
