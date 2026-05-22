package com.miruplay.tv.repository.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository

internal class FileBackedMediaIndexRepository(
    private val store: DesktopRepositoryStore,
) : MediaIndexRepository {
    override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                index = state.index.filterNot { it.sourceId == sourceId } + entries.map { it.copy(sourceId = sourceId) },
                indexBatchUndo = state.indexBatchUndo.filterNot { it.sourceId == sourceId },
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("index_$sourceId", it.message ?: "rebuild failed")) },
    )

    override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> = runCatching {
        val normalizedEntry = entry.copy(sourceId = sourceId)
        store.update { state ->
            state.copy(
                index = state.index
                    .filterNot { it.sourceId == sourceId && it.path == normalizedEntry.path } + normalizedEntry,
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("index_${sourceId}_${entry.path}", it.message ?: "upsert failed")) },
    )

    override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> = runCatching {
        val normalizedQuery = query.trim().lowercase()
        store.read { state ->
            state.index
                .filter { it.sourceId == sourceId }
                .filter { entry ->
                    normalizedQuery.isBlank() ||
                        entry.path.lowercase().contains(normalizedQuery) ||
                        entry.animeName?.lowercase()?.contains(normalizedQuery) == true ||
                        entry.episodeTitle?.lowercase()?.contains(normalizedQuery) == true ||
                        entry.plot?.lowercase()?.contains(normalizedQuery) == true ||
                        entry.metadataTitle?.lowercase()?.contains(normalizedQuery) == true ||
                        entry.metadataId?.lowercase()?.contains(normalizedQuery) == true
                }
                .sortedBy { it.path.lowercase() }
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )

    override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> = runCatching {
        store.read { state ->
            state.index
                .asSequence()
                .filter { it.sourceId == sourceId }
                .mapNotNull { it.animeName?.takeIf(String::isNotBlank) }
                .distinct()
                .sorted()
                .toList()
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )

    override suspend fun clearIndex(sourceId: Long): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                index = state.index.filterNot { it.sourceId == sourceId },
                indexBatchUndo = state.indexBatchUndo.filterNot { it.sourceId == sourceId },
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("index_$sourceId", it.message ?: "clear failed")) },
    )

    override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = runCatching {
        val normalizedEntries = entries
            .map { it.copy(sourceId = sourceId) }
            .distinctBy { it.path }
        store.update { state ->
            val withoutSource = state.indexBatchUndo.filterNot { it.sourceId == sourceId }
            val nextUndo = if (normalizedEntries.isEmpty()) {
                withoutSource
            } else {
                withoutSource + MediaIndexBatchUndoState(
                    sourceId = sourceId,
                    savedAt = System.currentTimeMillis(),
                    entries = normalizedEntries,
                )
            }
            state.copy(indexBatchUndo = nextUndo) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("index_${sourceId}_batch_undo", it.message ?: "save undo failed")) },
    )

    override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> = runCatching {
        store.read { state ->
            state.indexBatchUndo
                .firstOrNull { it.sourceId == sourceId }
                ?.entries
                .orEmpty()
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )

    override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(indexBatchUndo = state.indexBatchUndo.filterNot { it.sourceId == sourceId }) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("index_${sourceId}_batch_undo", it.message ?: "clear undo failed")) },
    )
}
