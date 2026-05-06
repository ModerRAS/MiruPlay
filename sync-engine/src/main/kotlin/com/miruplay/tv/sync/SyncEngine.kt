package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.NfoMetadata

/**
 * Core sync engine interface.
 * Manages bidirectional synchronization between Room database and NFO files.
 */
interface SyncEngine {
    /**
     * Sync a single episode's progress to/from its NFO file
     */
    suspend fun syncEpisode(episode: Episode, nfoPath: String): Result<SyncResult>

    /**
     * Batch sync all episodes to their respective NFO files
     */
    suspend fun syncAllEpisodes(episodes: List<Episode>): Result<List<SyncResult>>

    /**
     * Resolve conflict between local (Room) and remote (NFO) progress
     */
    suspend fun resolveConflict(
        local: Episode,
        remote: NfoMetadata,
        nfoPath: String
    ): Result<Episode>
}

/**
 * Result of a single sync operation
 */
data class SyncResult(
    val episodeId: String,
    val action: SyncAction,
    val resolvedPosition: Long,
    val timestamp: Long
)

enum class SyncAction {
    SYNCED_TO_NFO,     // Local progress written to NFO
    SYNCED_FROM_NFO,   // NFO progress imported to local
    CONFLICT,          // Conflict detected, needs resolution
    SKIPPED            // Skipped (no change needed)
}

/**
 * Conflict resolution strategy
 */
enum class ConflictResolution {
    LOCAL_WINS,      // Local (Room) data takes precedence
    REMOTE_WINS,     // Remote (NFO) data takes precedence
    TIMESTAMP_WINS,  // Most recent timestamp wins
    MANUAL           // Requires manual intervention
}

/**
 * Conflict info for manual resolution
 */
data class ConflictInfo(
    val episodeId: String,
    val localPosition: Long,
    val remotePosition: Long,
    val localTimestamp: Long,
    val remoteTimestamp: Long,
    val resolution: ConflictResolution? = null
)
