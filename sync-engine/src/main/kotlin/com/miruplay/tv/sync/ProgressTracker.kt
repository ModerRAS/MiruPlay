package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result

/**
 * Interface for tracking individual episode playback progress.
 * Independent from SyncEngine for testability.
 */
interface ProgressTracker {
    /**
     * Update playback progress for an episode
     * @param episodeId Episode identifier
     * @param positionMs Current position in milliseconds
     */
    suspend fun updateProgress(episodeId: String, positionMs: Long): Result<Unit>

    /**
     * Get saved progress for an episode
     * @return position in milliseconds, or null if never watched
     */
    suspend fun getProgress(episodeId: String): Result<Long?>

    /**
     * Mark episode as completed
     */
    suspend fun markCompleted(episodeId: String): Result<Unit>

    /**
     * Mark episode as unwatched (reset progress)
     */
    suspend fun markUnwatched(episodeId: String): Result<Unit>
}
