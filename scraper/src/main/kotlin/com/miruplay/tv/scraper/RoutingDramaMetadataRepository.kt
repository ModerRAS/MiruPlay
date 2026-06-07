package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.repository.DramaMetadataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingDramaMetadataRepository @Inject constructor(
    private val tmdb: TmdbDramaMetadataRepository,
    private val tvMaze: TvMazeDramaMetadataRepository,
) : DramaMetadataRepository {
    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        val tmdbResult = tmdb.fetchSeriesMetadata(
            title = title,
            seasonHint = seasonHint,
            seasonNumbers = seasonNumbers,
        )
        tmdbResult.getOrNull()?.let { return Result.success(it) }

        val tvMazeResult = tvMaze.fetchSeriesMetadata(
            title = title,
            seasonHint = seasonHint,
            seasonNumbers = seasonNumbers,
        )
        tvMazeResult.getOrNull()?.let { return Result.success(it) }

        return when {
            tmdbResult is Result.Error && tmdb.canFetchSeriesMetadataByTitle() -> tmdbResult
            tvMazeResult is Result.Error -> tvMazeResult
            else -> tmdbResult
        }
    }

    override fun canFetchSeriesMetadataByTitle(): Boolean =
        tmdb.canFetchSeriesMetadataByTitle() || tvMaze.canFetchSeriesMetadataByTitle()

    override fun canFetchMetadataByProviderRef(
        providerRef: MetadataProviderRef,
    ): Boolean =
        when {
            providerRef.source.equals("TMDB", ignoreCase = true) -> tmdb.canFetchMetadataByProviderRef(providerRef)
            providerRef.source.equals("TVMaze", ignoreCase = true) -> tvMaze.canFetchMetadataByProviderRef(providerRef)
            else -> false
        }

    override suspend fun fetchSeriesMetadataByProviderRef(
        providerRef: MetadataProviderRef,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> =
        when {
            providerRef.source.equals("TMDB", ignoreCase = true) ->
                tmdb.fetchSeriesMetadataByProviderRef(providerRef, seasonNumbers)
            providerRef.source.equals("TVMaze", ignoreCase = true) ->
                tvMaze.fetchSeriesMetadataByProviderRef(providerRef, seasonNumbers)
            else -> Result.success(null)
        }

    override suspend fun searchSeriesCandidates(
        query: String,
        seasonHint: Int?,
        maxResults: Int,
    ): Result<List<DramaMetadataSearchResult>> =
        tmdb.searchSeriesCandidates(
            query = query,
            seasonHint = seasonHint,
            maxResults = maxResults,
        )
}
