package com.miruplay.tv.repository.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.PlaybackProgressRepository

internal class FileBackedProgressRepository(
    private val store: DesktopRepositoryStore,
) : PlaybackProgressRepository {
    override suspend fun saveProgress(
        episodeId: String,
        positionMs: Long,
        lastWatched: Long,
        incrementPlayCount: Boolean,
    ): Result<Unit> = runCatching {
        store.update { state ->
            val existing = state.progress.firstOrNull { it.episodeId == episodeId }
            val record = ProgressRecord(
                episodeId = episodeId,
                positionMs = positionMs.coerceAtLeast(0L),
                lastWatched = lastWatched,
                playCount = (existing?.playCount ?: 0) + if (incrementPlayCount) 1 else 0,
            )
            state.copy(
                progress = state.progress.filterNot { it.episodeId == episodeId } + record,
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("progress", it.message ?: "save failed")) },
    )

    override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> = runCatching {
        store.read { state -> state.progress.firstOrNull { it.episodeId == episodeId } }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(null) },
    )

    override suspend fun getAllProgress(): Result<List<ProgressRecord>> = runCatching {
        store.read { it.progress.sortedByDescending { record -> record.lastWatched } }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )

    override suspend fun deleteProgress(episodeId: String): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(progress = state.progress.filterNot { it.episodeId == episodeId }) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("progress-delete", it.message ?: "delete failed")) },
    )

    override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> = runCatching {
        store.read { state ->
            state.progress
                .filter { it.positionMs > 0L || it.playCount > 0 }
                .sortedByDescending { it.lastWatched }
                .take(limit.coerceAtLeast(0))
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )
}
