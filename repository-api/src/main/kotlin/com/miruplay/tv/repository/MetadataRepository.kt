package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.Episode

interface MetadataRepository {
    suspend fun cacheMetadata(anime: Anime): Result<Unit>
    suspend fun getCachedMetadata(animeId: String): Result<Anime?>
    suspend fun getCachedMetadataByBangumiId(bangumiId: Int): Result<Anime?> = Result.success(null)
    suspend fun getCachedAnimeWithBangumiId(): Result<List<Anime>> = Result.success(emptyList())
    suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>>
    suspend fun getCachedEpisode(episodeId: String): Result<Episode?>
    suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>>
    suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit>
    suspend fun cacheDramaSeries(seriesId: String, series: DramaSeries): Result<Unit> =
        cacheMetadata(series.toLegacyCachedDramaMetadata(dramaSeriesCacheKey(seriesId)))

    suspend fun getCachedDramaSeries(seriesId: String): Result<DramaSeries?> =
        getCachedMetadata(dramaSeriesCacheKey(seriesId))
            .map { cached -> cached?.toLegacyCachedDramaSeries(seriesId) }

    suspend fun invalidateDramaSeriesCache(seriesId: String): Result<Unit> =
        invalidateCache(dramaSeriesCacheKey(seriesId))

    suspend fun invalidateCache(animeId: String): Result<Unit>
}
