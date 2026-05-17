package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class DesktopLocalMediaSource(
    override val info: MediaSourceInfo,
) : DesktopMediaSource {
    override val id: String = info.id.toString()

    override val capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = true,
        supportsList = true,
        supportsWrite = true,
    )

    private val rootPath: Path = Paths.get(
        info.connectionInfo["path"]
            ?: info.connectionInfo["uri"]
            ?: info.connectionInfo["url"]
            ?: ""
    ).toAbsolutePath().normalize()

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val directory = resolveInsideRoot(path)
        if (directory == null || !Files.exists(directory)) {
            return@withContext Result.failure(AppError.MediaSourceError.NotFound(path.ifBlank { rootPath.toString() }))
        }
        if (!Files.isDirectory(directory)) {
            return@withContext Result.failure(AppError.MediaSourceError.NotFound(directory.toString()))
        }
        if (!Files.isReadable(directory)) {
            return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(directory.toString()))
        }

        runCatching {
            Files.list(directory).use { stream ->
                val entries = stream
                    .filter { pathEntry -> !isHidden(pathEntry) }
                    .map(::fileEntryFor)
                    .toList()
                    .sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
                Result.success(entries)
            }
        }.getOrElse { error ->
            Result.failure(AppError.MediaSourceError.NotFound(error.message ?: directory.toString()))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val file = resolveInsideRoot(path)
        if (file == null || !Files.exists(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
        }
        if (!Files.isRegularFile(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.NotFound(file.toString()))
        }
        if (!Files.isReadable(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(file.toString()))
        }

        runCatching {
            Result.success(Files.newInputStream(file))
        }.getOrElse {
            Result.failure(AppError.MediaSourceError.NotFound(file.toString()))
        }
    }

    override suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> = withContext(Dispatchers.IO) {
        val file = resolveInsideRoot(path)
        if (file == null || !Files.exists(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
        }
        if (!Files.isRegularFile(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.NotFound(file.toString()))
        }
        if (!Files.isReadable(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(file.toString()))
        }

        runCatching {
            val channel = Files.newByteChannel(file, StandardOpenOption.READ)
            channel.position(range.start)
            val stream = Channels.newInputStream(channel)
            Result.success(range.length?.let { RangeLimitedInputStream(stream, it) } ?: stream)
        }.getOrElse {
            Result.failure(AppError.MediaSourceError.NotFound(file.toString()))
        }
    }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        val file = resolveInsideRoot(path)
        if (file == null || !Files.exists(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
        }
        if (!Files.isReadable(file)) {
            return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(file.toString()))
        }

        Result.success(fileMetadataFor(file))
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(Files.isDirectory(rootPath) && Files.isReadable(rootPath))
    }

    override suspend fun close() {
        // No resources to close for local desktop filesystem access.
    }

    private fun resolveInsideRoot(path: String): Path? {
        val requested = path.trim()
        val resolved = when {
            requested.isBlank() -> rootPath
            Paths.get(requested).isAbsolute -> Paths.get(requested)
            else -> rootPath.resolve(requested)
        }.toAbsolutePath().normalize()

        return if (resolved == rootPath || resolved.startsWith(rootPath)) resolved else null
    }

    private fun fileEntryFor(path: Path): FileEntry {
        val directory = path.isDirectory()
        return FileEntry(
            name = path.name,
            path = path.absolutePathString(),
            isDirectory = directory,
            size = if (path.isRegularFile()) Files.size(path) else 0L,
            lastModified = Files.getLastModifiedTime(path).toMillis(),
            mimeType = if (directory) null else mimeTypeFor(path),
        )
    }

    private fun fileMetadataFor(path: Path): FileMetadata {
        val entry = fileEntryFor(path)
        return FileMetadata(
            name = entry.name,
            path = entry.path,
            isDirectory = entry.isDirectory,
            size = entry.size,
            lastModified = entry.lastModified,
            mimeType = entry.mimeType,
        )
    }

    private fun isHidden(path: Path): Boolean {
        val name = path.name
        if (name in hiddenNames) return true
        return runCatching { Files.isHidden(path) }.getOrDefault(false)
    }

    private fun mimeTypeFor(path: Path): String? =
        when (path.extension.lowercase()) {
            "mkv" -> "video/x-matroska"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "ass", "ssa" -> "text/x-ass"
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> null
        }

    companion object {
        fun create(name: String, rootPath: Path): DesktopLocalMediaSource =
            DesktopLocalMediaSource(
                MediaSourceInfo(
                    name = name,
                    type = MediaSourceType.LOCAL,
                    connectionInfo = mapOf("path" to rootPath.toString()),
                    isConnected = true,
                )
            )

        private val hiddenNames = setOf(
            ".DS_Store",
            "Thumbs.db",
            "@eaDir",
            ".Trash",
            "\$RECYCLE.BIN",
            "System Volume Information",
        )
    }
}
