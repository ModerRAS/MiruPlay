package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that connects remote MediaSource implementations with Repository layer.
 */
@Singleton
class RemoteRepositoryAdapter @Inject constructor(
    private val mediaSourceFactory: MediaSourceFactory
) {
    /**
     * Create a MediaSource for a given source info
     */
    suspend fun createSource(sourceInfo: MediaSourceInfo): Result<MediaSource> = withContext(Dispatchers.IO) {
        mediaSourceFactory.create(sourceInfo)
    }

    /**
     * Test connection for a source
     */
    suspend fun testSourceConnection(sourceInfo: MediaSourceInfo): Result<Boolean> = withContext(Dispatchers.IO) {
        when (val result = createSource(sourceInfo)) {
            is Result.Success -> result.data.testConnection()
            is Result.Error -> Result.failure(result.error)
        }
    }

    /**
     * List files at path for a source
     */
    suspend fun listSourceFiles(sourceInfo: MediaSourceInfo, path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        when (val result = createSource(sourceInfo)) {
            is Result.Success -> result.data.listFiles(path)
            is Result.Error -> Result.failure(result.error)
        }
    }
}
