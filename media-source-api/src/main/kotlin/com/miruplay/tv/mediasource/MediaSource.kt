package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.StreamRange
import com.miruplay.tv.model.applyRange
import java.io.InputStream

/**
 * Platform-neutral media source interface for file access.
 *
 * Media source implementations share this contract so scanner, repository,
 * and playback code can stay source-agnostic.
 */
interface MediaSource {
    val id: String
    val info: MediaSourceInfo
    val capabilities: MediaCapabilities

    suspend fun listFiles(path: String = ""): Result<List<FileEntry>>
    suspend fun openStream(path: String): Result<InputStream>
    suspend fun openStream(path: String, range: StreamRange): Result<InputStream> =
        openStream(path).map { stream -> stream.applyRange(range) }
    suspend fun getMetadata(path: String): Result<FileMetadata>
    suspend fun testConnection(): Result<Boolean>
    suspend fun close()
}

/**
 * Factory for creating platform media source instances.
 */
interface MediaSourceFactory {
    fun create(info: MediaSourceInfo): Result<MediaSource>
    fun supports(type: MediaSourceType): Boolean
}
