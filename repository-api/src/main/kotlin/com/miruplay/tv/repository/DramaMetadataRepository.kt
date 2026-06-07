package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaSeriesMetadata

interface DramaMetadataRepository {
    suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int? = null,
        seasonNumbers: List<Int> = emptyList(),
    ): Result<DramaSeriesMetadata?>
}
