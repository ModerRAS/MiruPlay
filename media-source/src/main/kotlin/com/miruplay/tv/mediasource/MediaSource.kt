package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.*
import java.io.InputStream

/**
 * Core media source interface for file access.
 * Implementations: LocalMediaSource, WebDavMediaSource, SmbMediaSource
 */
interface MediaSource {
    val id: String
    val info: MediaSourceInfo
    val capabilities: MediaCapabilities

    suspend fun listFiles(path: String): Result<List<FileEntry>>
    suspend fun openStream(path: String): Result<InputStream>
    suspend fun getMetadata(path: String): Result<FileMetadata>
    suspend fun testConnection(): Result<Boolean>
    suspend fun close()
}

/**
 * File entry in a directory listing
 */
@kotlinx.serialization.Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String? = null,
)

/**
 * Extended metadata for media files (video)
 */
@kotlinx.serialization.Serializable
data class FileMetadata(
    val entry: FileEntry,
    val duration: Long = 0L,  // ms
    val width: Int = 0,
    val height: Int = 0,
    val codecInfo: String? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
)

/**
 * Subtitle track info
 */
@kotlinx.serialization.Serializable
data class SubtitleTrack(
    val language: String = "und",
    val title: String? = null,
    val isExternal: Boolean = false,
    val path: String? = null,
    val format: SubtitleFormat = SubtitleFormat.INTERNAL,
)

@kotlinx.serialization.Serializable
enum class SubtitleFormat {
    ASS, SRT, VTT, SUBRIP, INTERNAL, OTHER
}

/**
 * Factory for creating MediaSource instances
 */
interface MediaSourceFactory {
    fun create(info: MediaSourceInfo): Result<MediaSource>
    fun supports(type: MediaSourceType): Boolean
}
