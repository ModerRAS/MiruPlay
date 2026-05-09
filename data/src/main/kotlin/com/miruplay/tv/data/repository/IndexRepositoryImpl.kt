package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.IndexDao
import com.miruplay.tv.data.entity.IndexEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexRepositoryImpl @Inject constructor(
    private val indexDao: IndexDao
) : IndexRepository {

    override suspend fun rebuildIndex(sourceId: Long, entries: List<IndexRepositoryEntity>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                indexDao.deleteBySourceId(sourceId)
                if (entries.isNotEmpty()) {
                    val entities = entries.map { it.toEntity(sourceId) }
                    indexDao.insertAll(entities)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed("index_$sourceId", e.message ?: "Index rebuild failed"))
            }
        }

    override suspend fun queryIndex(sourceId: Long, query: String): Result<List<IndexRepositoryEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val results = indexDao.search(sourceId, query)
                Result.success(results.map { it.toDomain() })
            } catch (e: Exception) {
                Result.success(emptyList())
            }
        }

    override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val names = indexDao.getDistinctAnimeNames(sourceId)
                Result.success(names)
            } catch (e: Exception) {
                Result.success(emptyList())
            }
        }

    override suspend fun clearIndex(sourceId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                indexDao.deleteBySourceId(sourceId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed("index_$sourceId", e.message ?: "Clear failed"))
            }
        }
}

private fun IndexRepositoryEntity.toEntity(sourceId: Long) = IndexEntryEntity(
    sourceId = sourceId,
    path = path,
    animeName = animeName,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    isDirectory = isDirectory,
    fileSize = fileSize,
    lastModified = lastModified
)

private fun IndexEntryEntity.toDomain() = IndexRepositoryEntity(
    sourceId = sourceId,
    path = path,
    animeName = animeName,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    isDirectory = isDirectory
)
