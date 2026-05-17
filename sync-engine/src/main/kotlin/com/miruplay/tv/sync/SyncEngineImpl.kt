package com.miruplay.tv.sync

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.metadata.MetadataManager
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.NfoMetadata
import com.miruplay.tv.repository.PlaybackProgressRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngineImpl @Inject constructor(
    private val progressRepository: PlaybackProgressRepository,
    private val metadataManager: MetadataManager,
    private val config: SyncConfig = SyncConfig()
) : SyncEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoSyncJob: Job? = null

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    override suspend fun syncEpisode(episode: Episode, nfoPath: String): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = SyncStatus.Syncing(episode.id)

            // Get local progress
            val localProgress = progressRepository.getProgress(episode.id).getOrNull()

            // Parse NFO for remote progress
            val nfoMetadata = metadataManager.parseAndCacheEpisodeNfo(episode.animeId, nfoPath).getOrNull()

            val result = when {
                localProgress == null && nfoMetadata == null -> {
                    // No progress anywhere, skip
                    SyncResult(episode.id, SyncAction.SKIPPED, 0L, System.currentTimeMillis())
                }
                localProgress != null && nfoMetadata == null -> {
                    // Local only, write to NFO
                    metadataManager.updateNfoProgress(nfoPath, localProgress.positionMs)
                    SyncResult(episode.id, SyncAction.SYNCED_TO_NFO, localProgress.positionMs, System.currentTimeMillis())
                }
                localProgress == null && nfoMetadata != null -> {
                    // Remote only, import from NFO
                    val position = nfoMetadata.resumePosition * 1000 // convert seconds to ms
                    progressRepository.saveProgress(episode.id, position, System.currentTimeMillis())
                    SyncResult(episode.id, SyncAction.SYNCED_FROM_NFO, position, System.currentTimeMillis())
                }
                else -> {
                    // Both exist, check timestamps
                    val localTime = localProgress!!.lastWatched
                    val remoteTime = parseNfoTimestamp(nfoMetadata!!.lastplayed)
                    
                    when (config.conflictResolution) {
                        ConflictResolution.LOCAL_WINS -> {
                            metadataManager.updateNfoProgress(nfoPath, localProgress.positionMs)
                            SyncResult(episode.id, SyncAction.SYNCED_TO_NFO, localProgress.positionMs, System.currentTimeMillis())
                        }
                        ConflictResolution.REMOTE_WINS -> {
                            val position = nfoMetadata.resumePosition * 1000
                            progressRepository.saveProgress(episode.id, position, System.currentTimeMillis())
                            SyncResult(episode.id, SyncAction.SYNCED_FROM_NFO, position, System.currentTimeMillis())
                        }
                        ConflictResolution.TIMESTAMP_WINS -> {
                            if (localTime >= remoteTime) {
                                metadataManager.updateNfoProgress(nfoPath, localProgress.positionMs)
                                SyncResult(episode.id, SyncAction.SYNCED_TO_NFO, localProgress.positionMs, System.currentTimeMillis())
                            } else {
                                val position = nfoMetadata.resumePosition * 1000
                                progressRepository.saveProgress(episode.id, position, System.currentTimeMillis())
                                SyncResult(episode.id, SyncAction.SYNCED_FROM_NFO, position, System.currentTimeMillis())
                            }
                        }
                        ConflictResolution.MANUAL -> {
                            SyncResult(episode.id, SyncAction.CONFLICT, localProgress.positionMs, System.currentTimeMillis())
                        }
                    }
                }
            }

            _syncStatus.value = SyncStatus.Idle
            Result.success(result)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(episode.id, e.message ?: "Sync failed")
            Result.failure(AppError.SyncError.WriteFailed(nfoPath, e.message ?: "Unknown"))
        }
    }

    override suspend fun syncAllEpisodes(episodes: List<Episode>): Result<List<SyncResult>> = withContext(Dispatchers.IO) {
        try {
            val results = episodes.mapNotNull { episode ->
                val nfoPath = metadataManager.findNfoFile(episode.filePath)
                if (nfoPath != null) {
                    syncEpisode(episode, nfoPath).getOrNull()
                } else {
                    null
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("batch", e.message ?: "Unknown"))
        }
    }

    override suspend fun resolveConflict(local: Episode, remote: NfoMetadata, nfoPath: String): Result<Episode> = withContext(Dispatchers.IO) {
        try {
            // Always keep local progress by default
            progressRepository.saveProgress(local.id, local.id.hashCode().toLong(), System.currentTimeMillis())
            Result.success(local)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.ConflictDetected(local.id))
        }
    }

    /**
     * Start periodic auto-sync
     */
    fun startAutoSync(episodes: () -> List<Episode>) {
        autoSyncJob?.cancel()
        autoSyncJob = scope.launch {
            while (isActive) {
                delay(config.autoSyncInterval)
                val currentEpisodes = episodes()
                syncAllEpisodes(currentEpisodes)
            }
        }
    }

    /**
     * Stop periodic auto-sync
     */
    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    private fun parseNfoTimestamp(timestamp: String?): Long {
        if (timestamp == null) return 0L
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .parse(timestamp)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data class Syncing(val episodeId: String) : SyncStatus()
    data class Error(val episodeId: String, val message: String) : SyncStatus()
}
