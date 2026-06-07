package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaSeriesMetadata

interface DramaMetadataRepository {
    suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int? = null,
        seasonNumbers: List<Int> = emptyList(),
    ): Result<DramaSeriesMetadata?>

    suspend fun fetchSeriesMetadataById(
        tmdbId: Int,
        seasonNumbers: List<Int> = emptyList(),
    ): Result<DramaSeriesMetadata?> = Result.success(null)

    suspend fun searchSeriesCandidates(
        query: String,
        seasonHint: Int? = null,
        maxResults: Int = 10,
    ): Result<List<DramaMetadataSearchResult>> = Result.success(emptyList())
}
