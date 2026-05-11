package com.miruplay.tv.mediasource

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
class LocalMediaSource(
    override val info: MediaSourceInfo,
    private val context: Context? = null
) : MediaSource {
    override val id: String = info.id.toString()

    override val capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = true,
        supportsList = true,
        supportsWrite = true
    )

    private val rootPath: String
        get() = info.connectionInfo["uri"] ?: info.connectionInfo["path"] ?: info.connectionInfo["url"] ?: ""

    private val isDocumentTree: Boolean
        get() = rootPath.startsWith("content://")

    // Hidden file patterns to filter + protected system dirs
    private val hiddenPatterns = listOf(
        ".DS_Store", "Thumbs.db", "@eaDir", ".Trash",
        "proc", "sys", "dev", "lost+found"
    )

    // System directories that should never be traversed
    private val systemDirs = setOf("/proc", "/sys", "/dev", "/selinux", "/sys/kernel/debug")

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        try {
            if (isDocumentTree) {
                return@withContext listDocumentFiles(path.ifEmpty { rootPath })
            }

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
            if (path.startsWith("content://")) {
                val resolver = context?.contentResolver
                    ?: return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(path))
                return@withContext resolver.openInputStream(Uri.parse(path))
                    ?.let { Result.success(it) }
                    ?: Result.failure(AppError.MediaSourceError.NotFound(path))
            }

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
            if (path.startsWith("content://")) {
                return@withContext Result.success(
                    FileMetadata(
                        entry = FileEntry(
                            name = displayNameFor(path),
                            path = path,
                            isDirectory = true
                        )
                    )
                )
            }

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
            if (isDocumentTree) {
                val resolver = context?.contentResolver
                    ?: return@withContext Result.failure(AppError.MediaSourceError.PermissionDenied(rootPath))
                return@withContext Result.success(
                    runCatching {
                        resolver.query(
                            childrenUriFor(rootPath),
                            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                            null,
                            null,
                            null
                        )?.use { true } == true
                    }.getOrDefault(false)
                )
            }

            val dir = File(rootPath)
            Result.success(dir.exists() && dir.isDirectory && dir.canRead())
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.ConnectionLost(info.name))
        }
    }

    override suspend fun close() {
        // No resources to close for local source
    }

    private fun listDocumentFiles(path: String): Result<List<FileEntry>> {
        val resolver = context?.contentResolver
            ?: return Result.failure(AppError.MediaSourceError.PermissionDenied(path))
        val entries = mutableListOf<FileEntry>()
        val childrenUri = childrenUriFor(path)
        return try {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val treeUri = Uri.parse(rootPath)
                while (cursor.moveToNext()) {
                    val name = cursor.getStringOrNull(nameIndex) ?: continue
                    if (hiddenPatterns.any { name == it }) continue

                    val documentId = cursor.getStringOrNull(idIndex) ?: continue
                    val mimeType = cursor.getStringOrNull(mimeIndex)
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
                    entries += FileEntry(
                        name = name,
                        path = fileUri,
                        isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = cursor.getLongOrZero(sizeIndex),
                        lastModified = cursor.getLongOrZero(modifiedIndex),
                        mimeType = mimeType
                    )
                }
            } ?: return Result.failure(AppError.MediaSourceError.NotFound(path))

            Result.success(entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
        } catch (e: SecurityException) {
            Result.failure(AppError.MediaSourceError.PermissionDenied(path))
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    private fun childrenUriFor(path: String): Uri {
        val treeUri = Uri.parse(rootPath)
        val documentId = if (path == rootPath) {
            DocumentsContract.getTreeDocumentId(treeUri)
        } else {
            DocumentsContract.getDocumentId(Uri.parse(path))
        }
        return DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
    }

    private fun displayNameFor(path: String): String =
        Uri.parse(path).lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: path

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun android.database.Cursor.getLongOrZero(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L
}
