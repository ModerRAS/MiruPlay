package com.miruplay.tv.metadata

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.NfoMetadata
import com.miruplay.tv.model.TvShowNfoMetadata

/**
 * NFO file writer interface
 */
interface NfoWriter {
    /**
     * Write episode NFO file
     */
    suspend fun writeEpisodeNfo(nfoPath: String, metadata: NfoMetadata): Result<Unit>

    /**
     * Write TV show NFO file
     */
    suspend fun writeTvShowNfo(nfoPath: String, metadata: TvShowNfoMetadata): Result<Unit>

    /**
     * Update watch progress in existing NFO
     */
    suspend fun updateWatchProgress(nfoPath: String, position: Long, lastWatched: Long): Result<Unit>
}

/**
 * Options for NFO writing
 */
data class NfoWriteOptions(
    val preserveUnknownTags: Boolean = true,
    val createBackup: Boolean = true,
    val backupSuffix: String = ".bak"
)
