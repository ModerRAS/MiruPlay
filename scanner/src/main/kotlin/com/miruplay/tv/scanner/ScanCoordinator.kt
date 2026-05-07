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
        val sourceResult = mediaRepository.getSourceById(sourceId)
        if (sourceResult !is Result.Success) {
            return@withContext Result.failure((sourceResult as Result.Error).error)
        }
        val sourceInfo = sourceResult.data
        val mediaSourceResult = mediaSourceFactory.create(sourceInfo)
        if (mediaSourceResult !is Result.Success) {
            return@withContext Result.failure((mediaSourceResult as Result.Error).error)
        }
        val rootPath = sourceInfo.connectionInfo["path"] ?: "/"
        scanner.scan(mediaSourceResult.data, rootPath)
    }

    /**
     * Quick scan all configured sources
     */
    suspend fun scanAllSources(): Result<List<ScanResult>> = withContext(Dispatchers.IO) {
        val sourcesResult = mediaRepository.getSources()
        if (sourcesResult !is Result.Success) {
            return@withContext Result.success(emptyList())
        }
        val results = mutableListOf<ScanResult>()
        for (sourceInfo in sourcesResult.data) {
            val scanResult = scanSource(sourceInfo.id)
            if (scanResult is Result.Success) {
                results.add(scanResult.data)
            }
        }
        Result.success(results)
    }
}
