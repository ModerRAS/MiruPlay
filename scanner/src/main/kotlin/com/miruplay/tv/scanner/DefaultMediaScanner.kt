package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.matchingExternalSubtitlePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default implementation of MediaScanner
 */
class DefaultMediaScanner(
    private val episodeDetector: EpisodeDetector = DefaultEpisodeDetector()
) : MediaScanner {
    
    override suspend fun scan(
        source: MediaSource,
        rootPath: String,
        config: ScanConfig
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        try {
            val allEntries = mutableListOf<MediaIndexEntry>()
            var totalFiles = 0
            var newEpisodes = 0
            
            // Scan recursively
            val result = scanRecursive(source, rootPath, config, 0)
            result.onSuccess { entries ->
                allEntries.addAll(entries)
                totalFiles = entries.count { !it.isDirectory }
                newEpisodes = entries.count { 
                    !it.isDirectory && it.episodeMatch != null 
                }
            }.onError { error ->
                return@withContext Result.failure(error)
            }
            
            Result.success(ScanResult(
                animeName = episodeDetector.extractAnimeName(rootPath) ?: "Unknown",
                episodesFound = totalFiles,
                newEpisodes = newEpisodes,
                updatedEpisodes = 0,
            ))
        } catch (e: Exception) {
            Result.failure(AppError.ParseError.InvalidEpisodePattern(rootPath))
        }
    }
    
    override suspend fun quickScan(
        source: MediaSource,
        rootPath: String,
        config: ScanConfig
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        try {
            val entries = mutableListOf<MediaIndexEntry>()
            
            source.listFiles(rootPath).onSuccess { files ->
                files.forEach { file ->
                    if (file.isDirectory && shouldInclude(file.name, config)) {
                        entries.add(MediaIndexEntry(
                            name = file.name,
                            path = file.path,
                            isDirectory = true,
                            size = 0,
                            lastModified = file.lastModified
                        ))
                    }
                }
            }.onError { error ->
                return@withContext Result.failure(error)
            }
            
            Result.success(ScanResult(
                animeName = episodeDetector.extractAnimeName(rootPath) ?: "Unknown",
                episodesFound = 0,
                newEpisodes = 0,
                updatedEpisodes = 0,
            ))
        } catch (e: Exception) {
            Result.failure(AppError.ParseError.InvalidEpisodePattern(rootPath))
        }
    }
    
    private suspend fun scanRecursive(
        source: MediaSource,
        path: String,
        config: ScanConfig,
        depth: Int
    ): Result<List<MediaIndexEntry>> = withContext(Dispatchers.IO) {
        if (depth > config.maxDepth) {
            return@withContext Result.success(emptyList())
        }
        
        val entries = mutableListOf<MediaIndexEntry>()
        
        source.listFiles(path).onSuccess { files ->
            val siblingFilePaths = files.filterNot { it.isDirectory }.map { it.path }
            files.forEach { file ->
                if (!shouldInclude(file.name, config)) return@forEach
                
                if (file.isDirectory) {
                    // Recurse into subdirectories
                    val subResult = scanRecursive(source, file.path, config, depth + 1)
                    subResult.onSuccess { subEntries ->
                        entries.addAll(subEntries)
                    }
                } else {
                    // Check if it's a media file
                    val ext = file.name.substringAfterLast('.', "").lowercase()
                    if (ext in config.extensions && file.size >= config.minFileSize) {
                        val episodeMatch = episodeDetector.detectEpisode(file.name)
                        
                        entries.add(MediaIndexEntry(
                            name = file.name,
                            path = file.path,
                            externalSubtitlePaths = matchingExternalSubtitlePaths(file.path, siblingFilePaths),
                            isDirectory = false,
                            size = file.size,
                            lastModified = file.lastModified,
                            animeName = episodeMatch?.animeName,
                            seasonNumber = episodeMatch?.seasonNumber,
                            episodeNumber = episodeMatch?.episodeNumber,
                            episodeMatch = episodeMatch
                        ))
                    }
                }
            }
        }.onError { error ->
            return@withContext Result.failure(error)
        }
        
        Result.success(entries)
    }
    
    private fun shouldInclude(name: String, config: ScanConfig): Boolean {
        if (!config.includeHidden && name.startsWith(".")) return false
        return config.ignorePatterns.none { pattern -> name.contains(pattern) }
    }
}

/**
 * Indexed file entry
 */
data class MediaIndexEntry(
    val name: String,
    val path: String,
    val externalSubtitlePaths: List<String> = emptyList(),
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val animeName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeMatch: EpisodeMatch? = null
)
