package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.StreamRange
import com.miruplay.tv.model.applyRange
import java.io.InputStream

typealias DesktopStreamRange = StreamRange

interface DesktopMediaSource {
    val id: String
    val info: MediaSourceInfo
    val capabilities: MediaCapabilities

    suspend fun listFiles(path: String = ""): Result<List<FileEntry>>
    suspend fun openStream(path: String): Result<InputStream>
    suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> =
        openStream(path).map { stream -> stream.applyRange(range) }
    suspend fun getMetadata(path: String): Result<FileMetadata>
    suspend fun testConnection(): Result<Boolean>
    suspend fun close()
}
