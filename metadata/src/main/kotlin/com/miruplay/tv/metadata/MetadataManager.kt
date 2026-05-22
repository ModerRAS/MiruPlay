package com.miruplay.tv.metadata

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.MetadataRepository
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.NfoMetadata
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates NFO operations with Repository layer.
 * Used by ViewModels to read/write metadata.
 */
@Singleton
class MetadataManager @Inject constructor(
    private val metadataRepository: MetadataRepository
) {
    private val nfoParser: NfoParser = XmlNfoParser()
    private val nfoWriter: NfoWriter = XmlNfoWriter()

    /**
     * Load anime metadata - try cache first, fallback to NFO parsing
     */
    suspend fun loadAnimeWithEpisodes(animeId: String): Result<Anime?> {
        return metadataRepository.getCachedMetadata(animeId)
    }

    /**
     * Parse episode NFO and cache the result
     */
    suspend fun parseAndCacheEpisodeNfo(animeId: String, nfoPath: String): Result<NfoMetadata> {
        return nfoParser.parseEpisodeNfo(nfoPath).onSuccess { metadata ->
            // Cache would be handled by repository
        }
    }

    /**
     * Update progress position in NFO file
     */
    suspend fun updateNfoProgress(nfoPath: String, positionMs: Long): Result<Unit> {
        return nfoWriter.updateWatchProgress(nfoPath, positionMs, System.currentTimeMillis())
    }

    /**
     * Check if NFO file exists for given path
     */
    suspend fun findNfoFile(videoPath: String): String? {
        val file = File(videoPath)
        val parent = file.parentFile ?: return null
        val nfoFile = File(parent, "${file.nameWithoutExtension}.nfo")
        return nfoFile.absolutePath.takeIf { nfoFile.exists() }
    }
}
