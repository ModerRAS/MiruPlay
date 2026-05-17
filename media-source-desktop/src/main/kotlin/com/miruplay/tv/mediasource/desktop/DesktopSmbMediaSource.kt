package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URLEncoder
import java.util.Properties

class DesktopSmbMediaSource(
    override val info: MediaSourceInfo,
) : DesktopMediaSource {
    override val id: String = info.id.toString()

    override val capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = true,
        supportsList = true,
        supportsWrite = false,
    )

    private val rootUrl: String = normalizeRoot(
        requireNotNull(info.connectionInfo["url"]) {
            "SMB source requires connectionInfo[url]"
        }
    )
    private val cifsContext: CIFSContext = createContext(info.connectionInfo)

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val smbFile = resolvePath(path)
            if (!smbFile.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path.ifBlank { rootUrl }))
            }
            if (!smbFile.isDirectory) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(smbFile.path))
            }

            val entries = smbFile.listFiles()
                .orEmpty()
                .filter { file -> cleanName(file.name) !in hiddenNames }
                .map(::fileEntryFor)
                .sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })

            Result.success(entries)
        }.getOrElse { error ->
            Result.failure(error.toAppError(path.ifBlank { rootUrl }))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        runCatching {
            val smbFile = resolvePath(path)
            if (!smbFile.exists() || !smbFile.isFile) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }
            Result.success(smbFile.openInputStream())
        }.getOrElse { error ->
            Result.failure(error.toAppError(path))
        }
    }

    override suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> = withContext(Dispatchers.IO) {
        runCatching {
            val smbFile = resolvePath(path)
            if (!smbFile.exists() || !smbFile.isFile) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }

            val randomAccess = SmbRandomAccessFile(smbFile, "r")
            randomAccess.seek(range.start)
            val stream = SmbRandomAccessInputStream(randomAccess)
            Result.success(range.length?.let { RangeLimitedInputStream(stream, it) } ?: stream)
        }.getOrElse { error ->
            Result.failure(error.toAppError(path))
        }
    }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val smbFile = resolvePath(path)
            if (!smbFile.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }
            val entry = fileEntryFor(smbFile)
            Result.success(
                FileMetadata(
                    name = entry.name,
                    path = entry.path,
                    isDirectory = entry.isDirectory,
                    size = entry.size,
                    lastModified = entry.lastModified,
                    mimeType = entry.mimeType,
                )
            )
        }.getOrElse { error ->
            Result.failure(error.toAppError(path))
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            Result.success(resolvePath("").exists())
        }.getOrElse { error ->
            Result.failure(error.toAppError(rootUrl))
        }
    }

    override suspend fun close() {
        runCatching { cifsContext.close() }
    }

    internal fun resolveUrl(path: String): String {
        if (path.startsWith(SMB_SCHEME, ignoreCase = true)) return path
        if (path.startsWith("\\\\") || path.startsWith("//")) return normalizeRoot(path)

        val cleanPath = path.replace('\\', '/').trim('/')
        if (cleanPath.isBlank()) return "$rootUrl/"

        return "$rootUrl/${cleanPath.split('/').joinToString("/") { encodeSegment(it) }}"
    }

    private fun resolvePath(path: String): SmbFile =
        SmbFile(resolveUrl(path), cifsContext)

    private fun fileEntryFor(file: SmbFile): FileEntry {
        val directory = file.isDirectory
        val name = cleanName(file.name)
        return FileEntry(
            name = name,
            path = file.path,
            isDirectory = directory,
            size = if (directory) 0L else file.length(),
            lastModified = file.lastModified(),
            mimeType = if (directory) null else mimeTypeFor(name),
        )
    }

    private fun Throwable.toAppError(path: String): AppError =
        when (this) {
            is SmbAuthException -> AppError.MediaSourceError.AuthenticationFailed(path)
            is SmbException -> AppError.NetworkError.ServerUnreachable(path)
            else -> AppError.NetworkError.ServerUnreachable(path)
        }

    companion object {
        private const val SMB_SCHEME = "smb://"
        private val hiddenNames = setOf(".DS_Store", "Thumbs.db", "@eaDir")

        fun create(
            name: String,
            url: String,
            username: String = "",
            password: String = "",
            domain: String = "",
        ): DesktopSmbMediaSource =
            DesktopSmbMediaSource(
                MediaSourceInfo(
                    name = name,
                    type = MediaSourceType.SMB,
                    connectionInfo = buildMap {
                        put("url", normalizeRoot(url))
                        if (username.isNotBlank()) put("username", username)
                        if (password.isNotBlank()) put("password", password)
                        if (domain.isNotBlank()) put("domain", domain)
                    },
                    isConnected = false,
                )
            )

        fun normalizeRoot(rawUrl: String): String {
            val normalized = rawUrl.trim().replace('\\', '/').trimEnd('/')
            val withScheme = when {
                normalized.startsWith(SMB_SCHEME, ignoreCase = true) -> normalized
                normalized.startsWith("//") -> "smb:$normalized"
                else -> "smb://$normalized"
            }
            return withScheme.trimEnd('/')
        }

        internal fun mimeTypeFor(name: String): String? =
            when (name.substringAfterLast('.', "").lowercase()) {
                "mkv" -> "video/x-matroska"
                "mp4" -> "video/mp4"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "wmv" -> "video/x-ms-wmv"
                "flv" -> "video/x-flv"
                "m4v" -> "video/x-m4v"
                "ass", "ssa" -> "text/x-ass"
                "srt" -> "application/x-subrip"
                "vtt" -> "text/vtt"
                else -> null
            }

        private fun createContext(connectionInfo: Map<String, String>): CIFSContext {
            val properties = Properties().apply {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
            }
            val baseContext = BaseContext(PropertyConfiguration(properties))
            val username = connectionInfo["username"].orEmpty()
            if (username.isBlank()) return baseContext

            val auth = NtlmPasswordAuthenticator(
                connectionInfo["domain"].orEmpty().ifBlank { null },
                username,
                connectionInfo["password"].orEmpty(),
            )
            return baseContext.withCredentials(auth)
        }

        private fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

        private fun cleanName(name: String): String =
            name.trimEnd('/')
    }
}

private class SmbRandomAccessInputStream(
    private val randomAccess: SmbRandomAccessFile,
) : InputStream() {
    override fun read(): Int =
        randomAccess.read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        randomAccess.read(buffer, offset, length)

    override fun close() {
        randomAccess.close()
    }
}
