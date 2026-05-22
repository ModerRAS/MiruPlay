package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.remoteUrl
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties
import javax.inject.Inject

class SmbMediaSource @Inject constructor() : MediaSource {
    override val id: String = ""
    override lateinit var info: MediaSourceInfo

    private var smbRoot: String = ""
    private var cifsContext: CIFSContext? = null

    companion object {
        private val HIDDEN_FILES = setOf(".DS_Store", "Thumbs.db", "@eaDir")
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v")
    }

    constructor(info: MediaSourceInfo) : this() {
        this.info = info
        this.smbRoot = info.remoteUrl().orEmpty()
        val user = info.connectionUsername()
        val pass = info.connectionPassword()

        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val baseContext = BaseContext(PropertyConfiguration(props))
        this.cifsContext = if (user.isNotBlank()) {
            val auth = NtlmPasswordAuthenticator(null, user, pass)
            baseContext.withCredentials(auth)
        } else {
            baseContext
        }
    }

    override val capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = true,
        supportsList = true,
        supportsWrite = false,
    )

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        try {
            val smbFile = resolvePath(path)
            if (!smbFile.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }

            val children = smbFile.listFiles()
                ?: return@withContext Result.success(emptyList())

            val entries = children
                .filter { file -> file.name !in HIDDEN_FILES }
                .map { file ->
                    FileEntry(
                        name = file.name,
                        path = file.path,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0L,
                        lastModified = file.lastModified(),
                        mimeType = if (!file.isDirectory) {
                            val ext = file.name.substringAfterLast('.', "").lowercase()
                            if (ext in VIDEO_EXTENSIONS) "video/$ext" else null
                        } else null,
                    )
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            Result.success(entries)
        } catch (e: SmbAuthException) {
            Result.failure(AppError.MediaSourceError.AuthenticationFailed(path))
        } catch (e: SmbException) {
            Result.failure(AppError.NetworkError.ServerUnreachable(path))
        } catch (e: Exception) {
            Result.failure(AppError.NetworkError.ServerUnreachable(path))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val smbFile = resolvePath(path)
            if (!smbFile.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }
            Result.success(smbFile.openInputStream())
        } catch (e: SmbAuthException) {
            Result.failure(AppError.MediaSourceError.AuthenticationFailed(path))
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val smbFile = resolvePath(path)
            if (!smbFile.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
            }

            val entry = FileEntry(
                name = smbFile.name,
                path = smbFile.path,
                isDirectory = smbFile.isDirectory,
                size = if (smbFile.isFile) smbFile.length() else 0L,
                lastModified = smbFile.lastModified(),
            )

            Result.success(FileMetadata(entry = entry))
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val smbFile = resolvePath("")
            Result.success(smbFile.exists())
        } catch (e: Exception) {
            Result.failure(AppError.NetworkError.NoConnectivity)
        }
    }

    override suspend fun close() {
        try {
            cifsContext?.close()
        } catch (_: Exception) {
            // Ignore close errors
        }
    }

    private fun resolvePath(path: String): SmbFile {
        val ctx = cifsContext ?: BaseContext(PropertyConfiguration(Properties()))
        val fullUrl = when {
            path.startsWith("smb://") -> path
            path.isBlank() -> smbRoot
            else -> "${smbRoot.trimEnd('/')}/${path.trimStart('/')}"
        }
        return SmbFile(fullUrl, ctx)
    }
}
