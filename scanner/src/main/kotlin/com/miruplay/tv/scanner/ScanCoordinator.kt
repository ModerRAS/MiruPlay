package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates scanning across MediaSource, Scanner, and Repository layers.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val progressRepository: ProgressRepository
) {
    private val scanner = DefaultMediaScanner()

    /**
     * Full scan of a media source
     */
    suspend fun scanSource(sourceId: Long): Result<ScanResult> = withContext(Dispatchers.IO) {
        mediaRepository.getSourceById(sourceId).onSuccess { sourceInfo ->
            val mediaSource = mediaSourceFactory.create(sourceInfo)
            mediaSource.onSuccess { source ->
                val rootPath = sourceInfo.connectionInfo["path"] ?: "/"
                return@withContext scanner.scan(source, rootPath)
            }
            mediaSource.onError { error ->
                return@withContext Result.failure(error)
            }
        }.onError { error ->
            return@withContext Result.failure(error)
        }
        Result.failure(AppError.MediaSourceError.NotFound("source:$sourceId"))
    }

    /**
     * Quick scan all configured sources
     */
    suspend fun scanAllSources(): Result<List<ScanResult>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScanResult>()
        mediaRepository.getSources().onSuccess { sources ->
            sources.forEach { sourceInfo ->
                scanSource(sourceInfo.id).onSuccess { result ->
                    results.add(result)
                }
            }
        }
        Result.success(results)
    }
}
