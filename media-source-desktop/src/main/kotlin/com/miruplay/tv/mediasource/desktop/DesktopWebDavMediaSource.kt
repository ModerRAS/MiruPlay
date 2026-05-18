package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.WebDavPropfindParser
import com.miruplay.tv.model.toHttpRangeHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

class DesktopWebDavMediaSource(
    override val info: MediaSourceInfo,
    private val client: OkHttpClient = defaultClient(),
) : DesktopMediaSource {
    override val id: String = info.id.toString()

    override val capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = true,
        supportsList = true,
        supportsWrite = false,
    )

    private val baseUrl: String = requireNotNull(info.connectionInfo["url"]) {
        "WebDAV source requires connectionInfo[url]"
    }.trimEnd('/')
    private val username: String = info.connectionInfo["username"].orEmpty()
    private val password: String = info.connectionInfo["password"].orEmpty()

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val url = normalizeUrl(path)
        val request = Request.Builder()
            .url(url)
            .method(PROPFIND, propfindXml().toRequestBody(xmlMedia))
            .header("Depth", DEPTH_1)
            .applyAuth()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AppError.NetworkError.HttpError(response.code, response.message))
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(AppError.NetworkError.ServerUnreachable(url))
                Result.success(parsePropfindResponse(body, path))
            }
        }.getOrElse { error ->
            Result.failure(AppError.NetworkError.ServerUnreachable(error.message ?: url))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> =
        openHttpStream(path, rangeHeader = null)

    override suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> =
        openHttpStream(path, rangeHeader = range.toHttpRangeHeader())

    private suspend fun openHttpStream(path: String, rangeHeader: String?): Result<InputStream> = withContext(Dispatchers.IO) {
        val url = normalizeUrl(path)
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { rangeHeader?.let { header("Range", it) } }
            .applyAuth()
            .build()

        runCatching {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(AppError.NetworkError.HttpError(response.code, response.message))
            }
            response.body?.byteStream()
                ?.let { Result.success(ResponseClosingInputStream(it, response)) }
                ?: run {
                    response.close()
                    Result.failure(AppError.MediaSourceError.NotFound(path))
                }
        }.getOrElse {
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        val url = normalizeUrl(path)
        val request = Request.Builder()
            .url(url)
            .method(PROPFIND, propfindXml().toRequestBody(xmlMedia))
            .header("Depth", "0")
            .applyAuth()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AppError.NetworkError.HttpError(response.code, response.message))
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(AppError.NetworkError.ServerUnreachable(url))
                val entry = parsePropfindResponse(body, path, includeRequestedPath = true)
                    .firstOrNull { !it.isDirectory }
                    ?: return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
                Result.success(MediaFileConventions.metadataFor(entry))
            }
        }.getOrElse {
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun testConnection(): Result<Boolean> =
        when (val result = listFiles("")) {
            is Result.Success -> Result.success(true)
            is Result.Error -> Result.failure(result.error)
        }

    override suspend fun close() {
        // OkHttp owns no per-source resource that needs explicit cleanup here.
    }

    internal fun normalizeUrl(path: String): String {
        return MediaPathConventions.joinRemoteUrl(baseUrl, path)
    }

    internal fun parsePropfindResponse(
        xml: String,
        requestedPath: String,
        includeRequestedPath: Boolean = false,
    ): List<FileEntry> =
        WebDavPropfindParser.parse(
            xml = xml,
            baseUrl = baseUrl,
            requestedPath = requestedPath,
            includeRequestedPath = includeRequestedPath,
        )

    private fun Request.Builder.applyAuth(): Request.Builder {
        if (username.isNotBlank()) {
            header("Authorization", Credentials.basic(username, password))
        }
        return this
    }

    companion object {
        private const val PROPFIND = "PROPFIND"
        private const val DEPTH_1 = "1"
        private val xmlMedia = "application/xml; charset=utf-8".toMediaType()

        fun create(name: String, url: String, username: String = "", password: String = ""): DesktopWebDavMediaSource =
            DesktopWebDavMediaSource(
                MediaSourceInfoConventions.webDav(
                    name = name,
                    url = url,
                    username = username,
                    password = password,
                    isConnected = false,
                )
            )

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()

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
    }
}

private class ResponseClosingInputStream(
    private val delegate: InputStream,
    private val response: okhttp3.Response,
) : InputStream() {
    override fun read(): Int = delegate.read()
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
    override fun close() {
        runCatching { delegate.close() }
        response.close()
    }
}
