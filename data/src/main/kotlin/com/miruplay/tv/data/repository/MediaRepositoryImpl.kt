package com.miruplay.tv.data.repository

import android.util.Base64
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.IndexDao
import com.miruplay.tv.data.dao.MediaSourceDao
import com.miruplay.tv.data.entity.MediaSourceEntity
import com.miruplay.tv.data.secure.MediaSourceSecretStore
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.connectionPasswordOrNull
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.hasConnectionPassword
import com.miruplay.tv.model.persistenceLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaSourceDao: MediaSourceDao,
    private val indexDao: IndexDao,
    private val secretStore: MediaSourceSecretStore
) : MediaRepository {

    override suspend fun addSource(source: MediaSourceInfo): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val sourceLocation = source.persistenceLocation()
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
            secretStore.setMediaSourcePassword(id, source.connectionPasswordOrNull())
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
            secretStore.clearMediaSourcePassword(sourceId)
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
            if (source.hasConnectionPassword()) {
                secretStore.setMediaSourcePassword(source.id, source.connectionPasswordOrNull())
            }
            mediaSourceDao.update(
                id = source.id,
                name = source.name,
                url = source.persistenceLocation(),
                username = source.connectionUsername().ifBlank { null },
                password = null,
                extraConfig = source.extraConnectionInfoJson(),
                isConnected = source.isConnected,
                lastScanned = source.lastScanned
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

    private fun MediaSourceInfo.toEntity(): MediaSourceEntity = MediaSourceEntity(
        name = name,
        type = type.name,
        url = persistenceLocation(),
        username = connectionUsername().ifBlank { null },
        password = null,
        extraConfig = extraConnectionInfoJson(),
        isConnected = isConnected,
        lastScanned = lastScanned
    )

    private fun MediaSourceEntity.toDomain(): MediaSourceInfo {
        val decodedLegacyPassword = decodeLegacyPassword(password)
        val securedPassword = secretStore.getMediaSourcePassword(id)
            ?: decodedLegacyPassword?.also { secretStore.setMediaSourcePassword(id, it) }
        val sourceType = try { MediaSourceType.valueOf(type) } catch (e: Exception) { MediaSourceType.LOCAL }

        return MediaSourceInfo(
            id = id,
            name = name,
            type = sourceType,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfoFromPersistence(
                type = sourceType,
                location = url,
                username = username,
                password = securedPassword,
                extraConnectionInfo = extraConfig
                    ?.let { runCatching { mediaSourceJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                    .orEmpty(),
            ),
            isConnected = isConnected,
            lastScanned = lastScanned
        )
    }

    private fun decodeLegacyPassword(encoded: String?): String? =
        encoded?.let {
            runCatching { String(Base64.decode(it, Base64.NO_WRAP)) }.getOrNull()
        }

    private fun MediaSourceInfo.extraConnectionInfoJson(): String? =
        connectionInfo
            .filterKeys { it !in persistedConnectionKeys }
            .takeIf { it.isNotEmpty() }
            ?.let { mediaSourceJson.encodeToString(it) }
}

private val mediaSourceJson = Json { ignoreUnknownKeys = true }
private val persistedConnectionKeys = MediaSourceInfoConventions.PERSISTED_CONNECTION_KEYS
