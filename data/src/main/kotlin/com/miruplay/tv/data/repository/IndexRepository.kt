package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.Result

/**
 * Repository for scanned media index
 */
interface IndexRepository {
    suspend fun rebuildIndex(sourceId: Long, entries: List<IndexRepositoryEntity>): Result<Unit>
    suspend fun queryIndex(sourceId: Long, query: String): Result<List<IndexRepositoryEntity>>
    suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>>
    suspend fun clearIndex(sourceId: Long): Result<Unit>
}

data class IndexRepositoryEntity(
    val sourceId: Long,
    val path: String,
    val animeName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isDirectory: Boolean = false
)
