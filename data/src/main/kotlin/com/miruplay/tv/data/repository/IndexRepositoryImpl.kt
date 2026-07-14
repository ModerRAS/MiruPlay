package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.IndexDao
import com.miruplay.tv.data.db.MiruPlayDatabase
import com.miruplay.tv.data.entity.IndexEntryEntity
import com.miruplay.tv.repository.MediaExtraKind
import com.miruplay.tv.repository.MediaScrapeStatus
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexRepositoryImpl @Inject constructor(
    private val indexDao: IndexDao,
    private val database: MiruPlayDatabase
) : IndexRepository {

    override suspend fun rebuildIndex(sourceId: Long, entries: List<IndexRepositoryEntity>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                database.withTransaction {
                    indexDao.deleteBySourceId(sourceId)
                    if (entries.isNotEmpty()) {
                        val entities = entries.map { it.toEntity(sourceId) }
                        indexDao.insertAll(entities)
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed("index_$sourceId", e.message ?: "Index rebuild failed"))
            }
        }

    override suspend fun upsertEntry(sourceId: Long, entry: IndexRepositoryEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                indexDao.insertAll(listOf(entry.toEntity(sourceId)))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed("index_${sourceId}_${entry.path}", e.message ?: "Index upsert failed"))
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

    override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<IndexRepositoryEntity>): Result<Unit> =
        Result.success(Unit)

    override suspend fun getLastBatchUndo(sourceId: Long): Result<List<IndexRepositoryEntity>> =
        Result.success(emptyList())

    override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> =
        Result.success(Unit)
}

private fun IndexRepositoryEntity.toEntity(sourceId: Long) = IndexEntryEntity(
    sourceId = sourceId,
    path = path,
    externalSubtitlePaths = externalSubtitlePaths,
    animeName = animeName,
    episodeTitle = episodeTitle,
    plot = plot,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    metadataSource = metadataSource,
    metadataId = metadataId,
    metadataTitle = metadataTitle,
    scrapeStatus = scrapeStatus.toStorageValue(),
    scrapeMessage = scrapeMessage,
    scrapedAt = scrapedAt,
    isDirectory = isDirectory,
    fileSize = fileSize,
    lastModified = lastModified,
    extraKind = extraKind?.value,
    extraOrdinal = extraOrdinal,
    extraSortOrder = extraSortOrder,
    duration = duration,
)

private fun IndexEntryEntity.toDomain() = IndexRepositoryEntity(
    sourceId = sourceId,
    path = path,
    externalSubtitlePaths = externalSubtitlePaths,
    animeName = animeName,
    episodeTitle = episodeTitle,
    plot = plot,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    metadataSource = metadataSource,
    metadataId = metadataId,
    metadataTitle = metadataTitle,
    scrapeStatus = scrapeStatus?.toMediaScrapeStatus(),
    scrapeMessage = scrapeMessage,
    scrapedAt = scrapedAt,
    isDirectory = isDirectory,
    fileSize = fileSize,
    lastModified = lastModified,
    extraKind = extraKind?.let(MediaExtraKind::fromValue),
    extraOrdinal = extraOrdinal,
    extraSortOrder = extraSortOrder,
    duration = duration,
)

private fun MediaScrapeStatus?.toStorageValue(): String? = this?.name

private fun String.toMediaScrapeStatus(): MediaScrapeStatus? =
    runCatching { MediaScrapeStatus.valueOf(this) }.getOrNull()
