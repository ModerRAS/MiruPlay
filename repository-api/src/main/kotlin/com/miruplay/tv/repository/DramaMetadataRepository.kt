package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MetadataProviderRef

interface DramaMetadataRepository {
    suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int? = null,
        seasonNumbers: List<Int> = emptyList(),
    ): Result<DramaSeriesMetadata?>

    fun canFetchSeriesMetadataByTitle(): Boolean = false

    fun canFetchMetadataByProviderRef(
        providerRef: MetadataProviderRef,
    ): Boolean = false

    suspend fun fetchSeriesMetadataByProviderRef(
        providerRef: MetadataProviderRef,
        seasonNumbers: List<Int> = emptyList(),
    ): Result<DramaSeriesMetadata?> = Result.success(null)

    suspend fun searchSeriesCandidates(
        query: String,
        seasonHint: Int? = null,
        maxResults: Int = 10,
    ): Result<List<DramaMetadataSearchResult>> = Result.success(emptyList())
}
