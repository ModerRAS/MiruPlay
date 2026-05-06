package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo

/**
 * Repository for managing media sources
 */
interface MediaRepository {
    suspend fun addSource(source: MediaSourceInfo): Result<Long>
    suspend fun removeSource(sourceId: Long): Result<Unit>
    suspend fun getSources(): Result<List<MediaSourceInfo>>
    suspend fun updateSource(source: MediaSourceInfo): Result<Unit>
    suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo>
}
