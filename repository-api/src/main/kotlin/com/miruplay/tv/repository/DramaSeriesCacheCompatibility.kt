package com.miruplay.tv.repository

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.normalizedMetadataBinding
import com.miruplay.tv.model.tmdbCompatibilityId

private const val DRAMA_SERIES_CACHE_KEY_PREFIX = "drama-series:"

fun dramaSeriesCacheKey(seriesId: String): String =
    "$DRAMA_SERIES_CACHE_KEY_PREFIX$seriesId"

fun legacyDramaSeriesIdFromCacheKey(cacheKey: String): String? =
    cacheKey
        .takeIf { it.startsWith(DRAMA_SERIES_CACHE_KEY_PREFIX) }
        ?.removePrefix(DRAMA_SERIES_CACHE_KEY_PREFIX)

fun DramaSeries.toLegacyCachedDramaMetadata(
    cacheKey: String = dramaSeriesCacheKey(id),
): Anime {
    val normalized = normalizedMetadataBinding()
    return Anime(
        id = cacheKey,
        title = normalized.title,
        titleCn = normalized.originalTitle.ifBlank { null },
        summary = normalized.summary,
        episodeCount = normalized.episodeCount,
        airDate = normalized.firstAirDate,
        tmdbId = normalized.tmdbCompatibilityId(),
        posterUrl = normalized.posterUrl,
        fanartUrl = normalized.fanartUrl,
    )
}

fun Anime.toLegacyCachedDramaSeries(seriesId: String): DramaSeries =
    DramaSeries(
        id = seriesId,
        title = title,
        originalTitle = titleCn.orEmpty(),
        summary = summary,
        episodeCount = episodeCount,
        firstAirDate = airDate,
        tmdbId = tmdbId,
        posterUrl = posterUrl,
        fanartUrl = fanartUrl,
    ).normalizedMetadataBinding()

fun DramaSeries.toCachedDramaMetadata(
    cacheKey: String = dramaSeriesCacheKey(id),
): Anime = toLegacyCachedDramaMetadata(cacheKey)

fun Anime.toCachedDramaSeries(seriesId: String): DramaSeries =
    toLegacyCachedDramaSeries(seriesId)
