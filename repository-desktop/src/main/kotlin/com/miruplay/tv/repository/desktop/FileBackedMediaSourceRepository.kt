package com.miruplay.tv.repository.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.sourceLocation
import com.miruplay.tv.repository.MediaSourceRepository

internal class FileBackedMediaSourceRepository(
    private val store: DesktopRepositoryStore,
) : MediaSourceRepository {
    override suspend fun addSource(source: MediaSourceInfo): Result<Long> = runCatching {
        store.update { state ->
            val sourceLocation = source.sourceLocation()
            val duplicate = state.mediaSources.firstOrNull { existing ->
                existing.type == source.type &&
                    existing.sourceLocation() == sourceLocation
            }
            if (duplicate != null) {
                val updated = source.copy(
                    id = duplicate.id,
                    lastScanned = source.lastScanned.takeIf { it != 0L } ?: duplicate.lastScanned,
                )
                state.copy(
                    mediaSources = state.mediaSources.map { existing ->
                        if (existing.id == duplicate.id) updated else existing
                    },
                ) to duplicate.id
            } else {
                val id = state.nextSourceId
                val persisted = source.copy(id = id)
                state.copy(
                    nextSourceId = id + 1,
                    mediaSources = state.mediaSources + persisted,
                ) to id
            }
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("media-sources", it.message ?: "add failed")) },
    )

    override suspend fun removeSource(sourceId: Long): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                mediaSources = state.mediaSources.filterNot { it.id == sourceId },
                index = state.index.filterNot { it.sourceId == sourceId },
                indexBatchUndo = state.indexBatchUndo.filterNot { it.sourceId == sourceId },
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("media-sources", it.message ?: "remove failed")) },
    )

    override suspend fun getSources(): Result<List<MediaSourceInfo>> = runCatching {
        store.read { it.mediaSources.sortedBy { source -> source.name.lowercase() } }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )

    override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> = runCatching {
        store.update { state ->
            val exists = state.mediaSources.any { it.id == source.id }
            if (!exists) {
                throw NoSuchElementException("Source id: ${source.id}")
            }
            state.copy(
                mediaSources = state.mediaSources.map { if (it.id == source.id) source else it },
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.MediaSourceError.NotFound("Source id: ${source.id}")) },
    )

    override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> = runCatching {
        store.read { state ->
            state.mediaSources.firstOrNull { it.id == sourceId }
                ?: throw NoSuchElementException("Source id: $sourceId")
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(AppError.MediaSourceError.NotFound("Source id: $sourceId")) },
    )
}
