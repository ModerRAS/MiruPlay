package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.mediasource.MediaSource

/**
 * Media scanner for discovering and indexing media files
 */
interface MediaScanner {
    /**
     * Full scan of source directory tree
     */
    suspend fun scan(source: MediaSource, rootPath: String, config: ScanConfig = ScanConfig()): Result<ScanResult>
    
    /**
     * Quick scan - only index top level directories
     */
    suspend fun quickScan(source: MediaSource, rootPath: String, config: ScanConfig = ScanConfig()): Result<ScanResult>
}

/**
 * Configuration for media scanning
 */
data class ScanConfig(
    val includeHidden: Boolean = false,
    val ignorePatterns: List<String> = listOf(
        "@eaDir", ".tmp", ".cache", "System Volume Information"
    ),
    val maxDepth: Int = 10,
    val minFileSize: Long = 1024 * 1024,  // 1MB minimum
    val probeVideoHeaders: Boolean = false,
    val extensions: Set<String> = setOf(
        "mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "mts"
    )
)
