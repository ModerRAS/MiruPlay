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
import com.miruplay.tv.model.StreamRange
import com.miruplay.tv.model.WebDavPropfindParser
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.model.toHttpRangeHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal fun webDavTransportError(path: String, error: Exception): AppError =
    AppError.NetworkError.ServerUnreachable(
        error.message?.takeIf(String::isNotBlank)?.let { "$path ($it)" } ?: path,
    )

class WebDavMediaSource @Inject constructor() : MediaSource {
    override val id: String = ""
    override lateinit var info: MediaSourceInfo

    private var baseUrl: String = ""
    private var username: String = ""
    private var password: String = ""

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
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
        WebDavRequestCoordinator.register(baseUrl)
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
            val body = executeBytesWithAnonymousFallback(
                url = url,
                kind = WebDavRequestKind.PROPFIND,
            ) { authorization ->
                Request.Builder()
                    .url(url)
                    .method(PROPFIND, propfindXml().toRequestBody(xmlMedia))
                    .header("Depth", DEPTH_1)
                    .applyAuthorizationHeader(authorization)
                    .build()
            }.toString(Charsets.UTF_8)

            val entries = parsePropfindResponse(body, path)
            Result.success(entries)
        } catch (e: Exception) {
            val url = normalizeUrl(path)
            Log.w(TAG, "WebDAV PROPFIND failed for $url", e)
            Result.failure(e.toWebDavError(url))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> = openStream(path, null)

    override suspend fun openStream(path: String, range: StreamRange): Result<InputStream> =
        openStream(path, range.toHttpRangeHeader())

    private suspend fun openStream(path: String, rangeHeader: String?): Result<InputStream> =
        withContext(Dispatchers.IO) {
            try {
                val url = normalizeUrl(path)
                val lease = executeStreamingWithAnonymousFallback(
                    url = url,
                    kind = if (rangeHeader == null) requestKindFor(path) else WebDavRequestKind.RANGE,
                ) { authorization ->
                    Request.Builder()
                        .url(url)
                        .get()
                        .apply { rangeHeader?.let { header("Range", it) } }
                        .applyAuthorizationHeader(authorization)
                        .build()
                }
                val stream = lease.value.body?.byteStream()
                    ?: run {
                        lease.close()
                        return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
                    }

                Result.success(
                    object : FilterInputStream(stream) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                lease.close()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                val url = normalizeUrl(path)
                Log.w(TAG, "WebDAV GET failed for $url", e)
                Result.failure(e.toWebDavError(path.ifBlank { url }))
            }
        }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(path)
            val body = executeBytesWithAnonymousFallback(
                url = url,
                kind = WebDavRequestKind.HEAD,
            ) { authorization ->
                Request.Builder()
                    .url(url)
                    .method(PROPFIND, propfindXml().toRequestBody(xmlMedia))
                    .header("Depth", "0")
                    .applyAuthorizationHeader(authorization)
                    .build()
            }.toString(Charsets.UTF_8)

            val entries = parsePropfindResponse(body, path, includeRequestedPath = true)
            val entry = entries.firstOrNull { !it.isDirectory }
                ?: return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))

            Result.success(MediaFileConventions.metadataFor(entry))
        } catch (e: Exception) {
            val url = normalizeUrl(path)
            Log.w(TAG, "WebDAV metadata PROPFIND failed for $url", e)
            Result.failure(e.toWebDavError(path.ifBlank { url }))
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
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        return "Basic $encoded"
    }

    private fun anonymousCredentials(): String {
        val encoded = Base64.getEncoder().encodeToString("anonymous:".toByteArray())
        return "Basic $encoded"
    }

    private fun Request.Builder.applyAuthorizationHeader(authorization: String?): Request.Builder =
        apply {
            authorization?.let { header("Authorization", it) }
        }

    private fun executeBytesWithAnonymousFallback(
        url: String,
        kind: WebDavRequestKind,
        buildRequest: (String?) -> Request,
    ): ByteArray = try {
        executeBytes(url, kind, buildRequest(primaryAuthorization()))
    } catch (error: WebDavHttpStatusException) {
        if (error.statusCode != 401 || username.isNotBlank()) throw error
        executeBytes(url, kind, buildRequest(anonymousCredentials()))
    }

    private fun executeBytes(url: String, kind: WebDavRequestKind, request: Request): ByteArray =
        WebDavRequestCoordinator.executeBytes(
            WebDavRequest(method = request.method, url = url, kind = kind),
        ) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.bytes() ?: byteArrayOf()
                if (!response.isSuccessful) {
                    throw WebDavHttpStatusException(
                        statusCode = response.code,
                        message = body.toString(Charsets.UTF_8).takeIf(String::isNotBlank)
                            ?.let { "${response.message}: $it" }
                            ?: response.message,
                    )
                }
                WebDavTransportResult(body, response.code)
            }
        }

    private fun executeStreamingWithAnonymousFallback(
        url: String,
        kind: WebDavRequestKind,
        buildRequest: (String?) -> Request,
    ): WebDavLease<Response> {
        val primary = executeStreaming(url, kind, buildRequest(primaryAuthorization()))
        if (primary.value.code != 401 || username.isNotBlank()) {
            if (!primary.value.isSuccessful) {
                val error = primary.value.toStatusException()
                primary.close()
                throw error
            }
            return primary
        }
        primary.close()
        return executeStreaming(url, kind, buildRequest(anonymousCredentials())).also { lease ->
            if (!lease.value.isSuccessful) {
                val error = lease.value.toStatusException()
                lease.close()
                throw error
            }
        }
    }

    private fun executeStreaming(
        url: String,
        kind: WebDavRequestKind,
        request: Request,
    ): WebDavLease<Response> = WebDavRequestCoordinator.execute(
        WebDavRequest(method = request.method, url = url, kind = kind, streaming = true),
    ) {
        val response = client.newCall(request).execute()
        WebDavTransportResult(response, response.code, response::close)
    }

    private fun Response.toStatusException(): WebDavHttpStatusException {
        val responseBody = body?.string()?.takeIf(String::isNotBlank)
        return WebDavHttpStatusException(
            statusCode = code,
            message = responseBody?.let { "$message: $it" } ?: message,
        )
    }

    private fun requestKindFor(path: String): WebDavRequestKind = when {
        path.equals("library.db", ignoreCase = true) -> WebDavRequestKind.LIBRARY_DATABASE
        path.startsWith("MLIP-Artwork/", ignoreCase = true) ||
            path.startsWith("/MLIP-Artwork/", ignoreCase = true) -> WebDavRequestKind.ARTWORK_PACK
        MediaFileConventions.isVideoName(path) -> WebDavRequestKind.PLAYBACK
        else -> WebDavRequestKind.ARTWORK
    }

    private fun primaryAuthorization(): String? =
        username.takeIf { it.isNotBlank() }?.let { credentials() }

    private fun Exception.toWebDavError(path: String): AppError =
        if (this is WebDavHttpStatusException) {
            AppError.NetworkError.HttpError(statusCode, message.orEmpty())
        } else {
            webDavTransportError(path, this)
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
