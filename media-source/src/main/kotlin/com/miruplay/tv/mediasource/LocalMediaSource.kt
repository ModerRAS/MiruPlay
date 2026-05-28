package com.miruplay.tv.mediasource

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.localRootPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

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
        get() = info.localRootPath().orEmpty()

    private val isDocumentTree: Boolean
        get() = rootPath.startsWith("content://")

    // System directories that should never be traversed
    private val systemDirs = setOf("/proc", "/sys", "/dev", "/selinux", "/sys/kernel/debug")

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val requestedPath = path.ifEmpty { rootPath }
        val startedAtMs = System.currentTimeMillis()
        MiruLog.d(
            tag = TAG,
            message = "Local media list started",
            attributes = localListAttributes(
                path = requestedPath,
                backend = if (isDocumentTree) "document_tree" else "filesystem",
                startedAtMs = startedAtMs,
            ) + mapOf("list_phase" to "start")
        )
        try {
            if (isDocumentTree) {
                return@withContext listDocumentFiles(requestedPath, startedAtMs)
            }

            // Guard against system directories
            if (systemDirs.any { requestedPath == it || requestedPath.startsWith("$it/") }) {
                logLocalListCompleted(
                    path = requestedPath,
                    backend = "filesystem",
                    startedAtMs = startedAtMs,
                    entryCount = 0,
                    extraAttributes = mapOf("skip_reason" to "system_directory"),
                )
                return@withContext Result.success(emptyList())
            }

            val dir = File(requestedPath)
            if (!dir.exists()) {
                val error = AppError.MediaSourceError.NotFound(requestedPath)
                logLocalListFailed(requestedPath, "filesystem", startedAtMs, error)
                return@withContext Result.failure(error)
            }
            if (!dir.isDirectory) {
                val error = AppError.MediaSourceError.NotFound(requestedPath)
                logLocalListFailed(
                    path = requestedPath,
                    backend = "filesystem",
                    startedAtMs = startedAtMs,
                    error = error,
                    extraAttributes = mapOf("failure_reason" to "not_directory"),
                )
                return@withContext Result.failure(error)
            }
            if (!dir.canRead()) {
                val error = AppError.MediaSourceError.PermissionDenied(requestedPath)
                logLocalListFailed(requestedPath, "filesystem", startedAtMs, error)
                return@withContext Result.failure(error)
            }

            val listedFiles = dir.listFiles()
            if (listedFiles == null) {
                MiruLog.w(
                    tag = TAG,
                    message = "Local media list returned null",
                    attributes = localListAttributes(
                        path = requestedPath,
                        backend = "filesystem",
                        startedAtMs = startedAtMs,
                    ) + mapOf(
                        "list_phase" to "null_result",
                        "duration_ms" to (System.currentTimeMillis() - startedAtMs).toString(),
                    )
                )
                return@withContext Result.success(emptyList())
            }

            val entries = listedFiles
                .filter { file -> !MediaFileConventions.isHiddenName(file.name) }
                .map { file ->
                    // Use absolutePath — does NOT follow symlinks, so paths stay within root
                    // (unlike canonicalPath which resolves symlinks and can escape root)
                    FileEntry(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0L,
                        lastModified = file.lastModified(),
                        mimeType = MediaFileConventions.mimeTypeForName(file.name),
                    )
                }

            val sortedEntries = entries.sortedWith(mediaSourceFileEntryComparator())
            logLocalListCompleted(
                path = requestedPath,
                backend = "filesystem",
                startedAtMs = startedAtMs,
                entryCount = sortedEntries.size,
            )
            Result.success(sortedEntries)
        } catch (e: SecurityException) {
            val error = AppError.MediaSourceError.PermissionDenied(requestedPath)
            logLocalListFailed(requestedPath, "filesystem", startedAtMs, error, e)
            Result.failure(error)
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
                val entry = FileEntry(
                    name = displayNameFor(path),
                    path = path,
                    isDirectory = true,
                )
                return@withContext Result.success(
                    MediaFileConventions.metadataFor(entry)
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
            Result.success(MediaFileConventions.metadataFor(entry))
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

    private fun listDocumentFiles(path: String, startedAtMs: Long): Result<List<FileEntry>> {
        val resolver = context?.contentResolver
            ?: return AppError.MediaSourceError.PermissionDenied(path).let { error ->
                logLocalListFailed(path, "document_tree", startedAtMs, error)
                Result.failure(error)
            }
        val entries = mutableListOf<FileEntry>()
        val childrenUri = childrenUriFor(path)
        return try {
            MiruLog.d(
                tag = TAG,
                message = "Local document tree query started",
                attributes = localListAttributes(
                    path = path,
                    backend = "document_tree",
                    startedAtMs = startedAtMs,
                ) + mapOf(
                    "list_phase" to "query_start",
                    "children_uri_tail" to pathTailForLog(childrenUri.toString()),
                    "children_uri_hash" to hashForLog(childrenUri.toString()),
                )
            )
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
                    if (MediaFileConventions.isHiddenName(name)) continue

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
            } ?: return AppError.MediaSourceError.NotFound(path).let { error ->
                logLocalListFailed(
                    path = path,
                    backend = "document_tree",
                    startedAtMs = startedAtMs,
                    error = error,
                    extraAttributes = mapOf("failure_reason" to "query_returned_null"),
                )
                Result.failure(error)
            }

            val sortedEntries = entries.sortedWith(mediaSourceFileEntryComparator())
            logLocalListCompleted(
                path = path,
                backend = "document_tree",
                startedAtMs = startedAtMs,
                entryCount = sortedEntries.size,
            )
            Result.success(sortedEntries)
        } catch (e: SecurityException) {
            val error = AppError.MediaSourceError.PermissionDenied(path)
            logLocalListFailed(path, "document_tree", startedAtMs, error, e)
            Result.failure(error)
        } catch (e: Exception) {
            val error = AppError.MediaSourceError.NotFound(path)
            logLocalListFailed(path, "document_tree", startedAtMs, error, e)
            Result.failure(error)
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

    private fun mediaSourceFileEntryComparator(): Comparator<FileEntry> =
        MediaFileConventions.fileEntryComparator(FileEntry::isDirectory, FileEntry::name)

    private fun logLocalListCompleted(
        path: String,
        backend: String,
        startedAtMs: Long,
        entryCount: Int,
        extraAttributes: Map<String, String> = emptyMap(),
    ) {
        val durationMs = System.currentTimeMillis() - startedAtMs
        val attributes = localListAttributes(path, backend, startedAtMs) + mapOf(
            "list_phase" to "complete",
            "entry_count" to entryCount.toString(),
            "duration_ms" to durationMs.toString(),
        ) + extraAttributes
        if (durationMs >= SLOW_LOCAL_LIST_MS) {
            MiruLog.w(
                tag = TAG,
                message = "Local media list slow",
                attributes = attributes,
            )
        } else {
            MiruLog.d(
                tag = TAG,
                message = "Local media list completed",
                attributes = attributes,
            )
        }
    }

    private fun logLocalListFailed(
        path: String,
        backend: String,
        startedAtMs: Long,
        error: AppError,
        throwable: Throwable? = null,
        extraAttributes: Map<String, String> = emptyMap(),
    ) {
        MiruLog.w(
            tag = TAG,
            message = "Local media list failed",
            throwable = throwable,
            attributes = localListAttributes(path, backend, startedAtMs) + mapOf(
                "list_phase" to "failed",
                "duration_ms" to (System.currentTimeMillis() - startedAtMs).toString(),
                "error_type" to error::class.java.simpleName,
                "error_message" to normalizeForLog(error.toString(), MAX_LOG_TEXT_LENGTH),
            ) + extraAttributes,
        )
    }

    private fun localListAttributes(
        path: String,
        backend: String,
        startedAtMs: Long,
    ): Map<String, String> = mapOf(
        "media_source_id" to info.id.toString(),
        "media_source_name" to normalizeForLog(info.name, MAX_LOG_TEXT_LENGTH),
        "backend" to backend,
        "is_document_tree" to isDocumentTree.toString(),
        "path_tail" to pathTailForLog(path),
        "path_hash" to hashForLog(path),
        "started_at_ms" to startedAtMs.toString(),
    )

    private fun pathTailForLog(path: String): String {
        val normalized = path.replace('\\', '/').substringBefore('?').trim('/')
        if (normalized.isBlank()) return ""
        return normalized
            .split('/')
            .filter { it.isNotBlank() }
            .takeLast(MAX_PATH_TAIL_SEGMENTS_IN_LOG)
            .joinToString("/")
            .let { normalizeForLog(it, MAX_PATH_TAIL_LENGTH_IN_LOG) }
    }

    private fun normalizeForLog(value: String, maxLength: Int): String =
        value.replace(whitespaceRegex, " ").trim().take(maxLength)

    private fun hashForLog(value: String): String =
        value.takeIf { it.isNotBlank() }?.let(::sha256Hex).orEmpty()

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun android.database.Cursor.getLongOrZero(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L

    private companion object {
        private const val TAG = "LocalMediaSource"
        private const val SLOW_LOCAL_LIST_MS = 5_000L
        private const val MAX_LOG_TEXT_LENGTH = 120
        private const val MAX_PATH_TAIL_SEGMENTS_IN_LOG = 4
        private const val MAX_PATH_TAIL_LENGTH_IN_LOG = 240
        private val whitespaceRegex = Regex("\\s+")
    }
}
