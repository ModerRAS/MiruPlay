package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.IndexDao
import com.miruplay.tv.data.dao.MediaSourceDao
import com.miruplay.tv.data.entity.MediaSourceEntity
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaSourceDao: MediaSourceDao,
    private val indexDao: IndexDao
) : MediaRepository {

    override suspend fun addSource(source: MediaSourceInfo): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val sourceLocation = source.connectionInfo["url"] ?: source.connectionInfo["path"]
            // Check for duplicate url+type
            val existing = mediaSourceDao.getAll()
            val duplicate = existing.any {
                it.url == sourceLocation && it.type == source.type.name
            }
            if (duplicate) {
                return@withContext Result.failure(
                    AppError.MediaSourceError.NotFound("Duplicate source: ${source.name}")
                )
            }
            
            val entity = source.toEntity()
            val id = mediaSourceDao.insert(entity)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.ConnectionLost(source.name))
        }
    }

    override suspend fun removeSource(sourceId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Cascade delete related index entries
            indexDao.deleteBySourceId(sourceId)
            mediaSourceDao.delete(sourceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound("Source id: $sourceId"))
        }
    }

    override suspend fun getSources(): Result<List<MediaSourceInfo>> = withContext(Dispatchers.IO) {
        try {
            val entities = mediaSourceDao.getAll()
            Result.success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mediaSourceDao.update(
                id = source.id,
                name = source.name,
                url = source.connectionInfo["url"],
                username = source.connectionInfo["username"],
                password = source.connectionInfo["password"]?.let {
                    android.util.Base64.encodeToString(it.toByteArray(), android.util.Base64.NO_WRAP)
                },
                isConnected = source.isConnected
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound(source.name))
        }
    }

    override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> = withContext(Dispatchers.IO) {
        try {
            val entity = mediaSourceDao.getById(sourceId)
                ?: return@withContext Result.failure(
                    AppError.MediaSourceError.NotFound("Source id: $sourceId")
                )
            Result.success(entity.toDomain())
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound("Source id: $sourceId"))
        }
    }
}

// Extension functions for mapping
private fun MediaSourceInfo.toEntity(): MediaSourceEntity = MediaSourceEntity(
    name = name,
    type = type.name,
    url = connectionInfo["url"] ?: connectionInfo["path"],
    username = connectionInfo["username"],
    password = connectionInfo["password"]?.let { 
        android.util.Base64.encodeToString(it.toByteArray(), android.util.Base64.NO_WRAP)
    },
    isConnected = isConnected,
    lastScanned = lastScanned
)

private fun MediaSourceEntity.toDomain(): MediaSourceInfo = MediaSourceInfo(
    id = id,
    name = name,
    type = try { MediaSourceType.valueOf(type) } catch (e: Exception) { MediaSourceType.LOCAL },
    connectionInfo = buildMap {
        url?.let { put("url", it) }
        url?.let { put("path", it) }
        username?.let { put("username", it) }
        password?.let {
            put("password", String(android.util.Base64.decode(it, android.util.Base64.NO_WRAP)))
        }
    },
    isConnected = isConnected,
    lastScanned = lastScanned
)
