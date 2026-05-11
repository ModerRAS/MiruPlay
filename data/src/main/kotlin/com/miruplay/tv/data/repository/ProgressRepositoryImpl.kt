package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.ProgressDao
import com.miruplay.tv.data.entity.ProgressEntity
import com.miruplay.tv.model.ProgressRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val progressDao: ProgressDao
) : ProgressRepository {

    override suspend fun saveProgress(
        episodeId: String,
        positionMs: Long,
        lastWatched: Long,
        incrementPlayCount: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val existing = progressDao.getByEpisodeId(episodeId)
            val playCount = (existing?.playCount ?: 0) + if (incrementPlayCount) 1 else 0
            progressDao.upsert(
                ProgressEntity(
                    episodeId = episodeId,
                    positionMs = positionMs.coerceAtLeast(0L),
                    lastWatched = lastWatched,
                    playCount = playCount
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("progress", e.message ?: "Unknown"))
        }
    }

    override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> = withContext(Dispatchers.IO) {
        try {
            val entity = progressDao.getByEpisodeId(episodeId)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    override suspend fun getAllProgress(): Result<List<ProgressRecord>> = withContext(Dispatchers.IO) {
        try {
            val records = progressDao.getAll().map { it.toDomain() }
            Result.success(records)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun deleteProgress(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            progressDao.deleteByEpisodeId(episodeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("progress-delete", e.message ?: "Unknown"))
        }
    }

    override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> = withContext(Dispatchers.IO) {
        try {
            val records = progressDao.getContinueWatching(limit).map { it.toDomain() }
            Result.success(records)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }
}

private fun ProgressEntity.toDomain(): ProgressRecord = ProgressRecord(
    episodeId = episodeId,
    positionMs = positionMs,
    lastWatched = lastWatched,
    playCount = playCount
)
