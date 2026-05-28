package com.miruplay.tv.mediasource

import android.util.Log
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.WebDavPropfindParser
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.remoteUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WebDavMediaSource @Inject constructor() : MediaSource {
    override val id: String = ""
    override lateinit var info: MediaSourceInfo

    private var baseUrl: String = ""
    private var username: String = ""
    private var password: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val xmlMedia = "application/xml".toMediaType()

    constructor(info: MediaSourceInfo) : this() {
        this.info = info
        this.baseUrl = info.remoteUrl().orEmpty()
        this.username = info.connectionUsername()
        this.password = info.connectionPassword()
    }

    override val capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = true,
        supportsList = true,
        supportsWrite = false
    )

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(path)
            val request = Request.Builder()
                .url(url)
                .method(PROPFIND, propfindXml().toRequestBody(xmlMedia))
                .header("Depth", DEPTH_1)
                .apply { if (username.isNotBlank()) header("Authorization", credentials()) }
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string()?.takeIf { it.isNotBlank() }
                return@withContext Result.failure(
                    AppError.NetworkError.HttpError(
                        response.code,
                        responseBody?.let { "${response.message}: $it" } ?: response.message
                    )
                )
            }

            val body = response.body?.string() ?: return@withContext Result.failure(
                AppError.NetworkError.ServerUnreachable(url)
            )

            val entries = parsePropfindResponse(body, path)
            Result.success(entries)
        } catch (e: Exception) {
            val url = normalizeUrl(path)
            Log.w(TAG, "WebDAV PROPFIND failed for $url", e)
            Result.failure(AppError.NetworkError.ServerUnreachable(urlWithCause(url, e)))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(path)
            val request = Request.Builder()
                .url(url)
                .get()
                .apply { if (username.isNotBlank()) header("Authorization", credentials()) }
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string()?.takeIf { it.isNotBlank() }
                return@withContext Result.failure(
                    AppError.NetworkError.HttpError(
                        response.code,
                        responseBody?.let { "${response.message}: $it" } ?: response.message
                    )
                )
            }

            val stream = response.body?.byteStream()
                ?: return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))

            Result.success(stream)
        } catch (e: Exception) {
            val url = normalizeUrl(path)
            Log.w(TAG, "WebDAV GET failed for $url", e)
            Result.failure(AppError.MediaSourceError.NotFound(urlWithCause(path.ifBlank { url }, e)))
        }
    }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(path)
            val request = Request.Builder()
                .url(url)
                .method(PROPFIND, propfindXml().toRequestBody(xmlMedia))
                .header("Depth", "0")
                .apply { if (username.isNotBlank()) header("Authorization", credentials()) }
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    AppError.NetworkError.HttpError(response.code, response.message)
                )
            }

            val body = response.body?.string() ?: return@withContext Result.failure(
                AppError.NetworkError.ServerUnreachable(url)
            )

            val entries = parsePropfindResponse(body, path, includeRequestedPath = true)
            val entry = entries.firstOrNull { !it.isDirectory }
                ?: return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))

            Result.success(MediaFileConventions.metadataFor(entry))
        } catch (e: Exception) {
            val url = normalizeUrl(path)
            Log.w(TAG, "WebDAV metadata PROPFIND failed for $url", e)
            Result.failure(AppError.MediaSourceError.NotFound(urlWithCause(path.ifBlank { url }, e)))
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        when (val result = listFiles("")) {
            is Result.Success -> Result.success(true)
            is Result.Error -> when (val error = result.error) {
                is AppError.NetworkError.HttpError -> {
                    if (error.code == 207 || error.code == 200) {
                        Result.success(true)
                    } else {
                        Result.failure(error)
                    }
                }
                else -> Result.failure(error)
            }
        }
    }

    override suspend fun close() {
        // OkHttp client is shared, no explicit cleanup
    }

    private fun normalizeUrl(path: String): String {
        return MediaPathConventions.joinRemoteUrl(baseUrl, path)
    }

    private fun credentials(): String {
        val encoded = android.util.Base64.encodeToString(
            "$username:$password".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        return "Basic $encoded"
    }

    private fun urlWithCause(url: String, error: Exception): String {
        val message = error.message?.takeIf { it.isNotBlank() }
        return if (message == null) url else "$url ($message)"
    }

    private fun propfindXml(): String = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
    <d:prop>
        <d:displayname/>
        <d:getcontentlength/>
        <d:getcontenttype/>
        <d:getlastmodified/>
        <d:resourcetype/>
    </d:prop>
</d:propfind>"""

    internal fun parsePropfindResponse(
        xml: String,
        requestedPath: String,
        includeRequestedPath: Boolean = false
    ): List<FileEntry> =
        WebDavPropfindParser.parse(
            xml = xml,
            baseUrl = baseUrl,
            requestedPath = requestedPath,
            includeRequestedPath = includeRequestedPath,
        )

    private companion object {
        private const val TAG = "WebDavMediaSource"
        private const val PROPFIND = "PROPFIND"
        private const val DEPTH_1 = "1"
    }
}
