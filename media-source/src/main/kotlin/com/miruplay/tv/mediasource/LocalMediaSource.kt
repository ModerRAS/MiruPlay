package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Local filesystem media source implementation
 */
class LocalMediaSource(override val info: MediaSourceInfo) : MediaSource {
    override val id: String = info.id.toString()

    override val capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = true,
        supportsList = true,
        supportsWrite = true
    )

    private val rootPath: String
        get() = info.connectionInfo["path"] ?: ""

    // Hidden file patterns to filter + protected system dirs
    private val hiddenPatterns = listOf(
        ".DS_Store", "Thumbs.db", "@eaDir", ".Trash",
        "proc", "sys", "dev", "lost+found"
    )

    // System directories that should never be traversed
    private val systemDirs = setOf("/proc", "/sys", "/dev", "/selinux", "/sys/kernel/debug")

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        try {
            // Guard against system directories
            if (systemDirs.any { path == it || path.startsWith("$it/") }) {
                return@withContext Result.success(emptyList())
            }

            val dir = File(path.ifEmpty { rootPath })
            if (!dir.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }
            if (!dir.isDirectory) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }
            if (!dir.canRead()) {
                return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(path))
            }

            val entries = dir.listFiles()
                ?.filter { file -> hiddenPatterns.none { file.name == it } }
                ?.map { file ->
                    // Use absolutePath — does NOT follow symlinks, so paths stay within root
                    // (unlike canonicalPath which resolves symlinks and can escape root)
                    FileEntry(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0L,
                        lastModified = file.lastModified(),
                        mimeType = file.extension.lowercase().let { ext ->
                            when (ext) {
                                "mkv", "mp4", "avi", "mov" -> "video/$ext"
                                "jpg", "jpeg", "png", "webp" -> "image/$ext"
                                "srt", "ass", "ssa" -> "text/$ext"
                                else -> null
                            }
                        }
                    )
                }
                ?: emptyList()

            Result.success(entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
        } catch (e: SecurityException) {
            Result.failure(AppError.MediaSourceError.PermissionDenied(path))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }
            if (!file.canRead()) {
                return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(path))
            }
            Result.success(FileInputStream(file))
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }

            val entry = FileEntry(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified()
            )
            Result.success(FileMetadata(entry = entry))
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val dir = File(rootPath)
            Result.success(dir.exists() && dir.isDirectory && dir.canRead())
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.ConnectionLost(info.name))
        }
    }

    override suspend fun close() {
        // No resources to close for local source
    }
}