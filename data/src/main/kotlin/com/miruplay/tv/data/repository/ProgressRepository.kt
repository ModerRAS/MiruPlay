package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ProgressRecord

/**
 * Repository for tracking playback progress
 */
interface ProgressRepository {
    suspend fun saveProgress(episodeId: String, positionMs: Long, lastWatched: Long): Result<Unit>
    suspend fun getProgress(episodeId: String): Result<ProgressRecord?>
    suspend fun getAllProgress(): Result<List<ProgressRecord>>
    suspend fun deleteProgress(episodeId: String): Result<Unit>
    suspend fun getContinueWatching(limit: Int = 20): Result<List<ProgressRecord>>
}
