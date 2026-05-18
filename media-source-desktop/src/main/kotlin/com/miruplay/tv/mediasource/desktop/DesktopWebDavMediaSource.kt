package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.InputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

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
        openHttpStream(path, rangeHeader = range.toHttpHeader())

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
        val cleanPath = MediaPathConventions.normalizeRemotePath(path)
        if (cleanPath.isBlank()) return "$baseUrl/"
        val encodedPath = MediaPathConventions.encodeRemotePath(cleanPath)
        return "$baseUrl/$encodedPath"
    }

    internal fun parsePropfindResponse(
        xml: String,
        requestedPath: String,
        includeRequestedPath: Boolean = false,
    ): List<FileEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responses = doc.getElementsByTagNameNS(NS_DAV, "response")

        val normalizedRequestedPath = MediaPathConventions.normalizeRemotePath(requestedPath)
        val entries = mutableListOf<FileEntry>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val href = getChildText(response, "href") ?: continue
            val path = hrefToRemotePath(href)
            if (path.isBlank()) continue
            if (!includeRequestedPath && path == normalizedRequestedPath) continue

            val name = path.trimEnd('/').substringAfterLast('/')
            if (MediaFileConventions.isHiddenName(name)) continue

            val directory = isCollection(response)
            entries += FileEntry(
                name = name,
                path = "/${path.trim('/')}",
                isDirectory = directory,
                size = if (directory) 0L else getChildText(response, "getcontentlength")?.toLongOrNull() ?: 0L,
                lastModified = parseDavDate(getChildText(response, "getlastmodified").orEmpty()),
                mimeType = if (directory) null else getChildText(response, "getcontenttype"),
            )
        }
        return MediaFileConventions.sortEntries(entries.filter { it.path != "/" })
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        if (username.isNotBlank()) {
            header("Authorization", Credentials.basic(username, password))
        }
        return this
    }

    private fun DesktopStreamRange.toHttpHeader(): String =
        "bytes=$start-${endInclusive?.toString().orEmpty()}"

    private fun hrefToRemotePath(href: String): String {
        val decoded = decodeHref(href)
        val basePath = extractBasePath()
        val withoutBase = when {
            basePath.isBlank() -> URI.create(decoded).path ?: decoded
            decoded == basePath -> ""
            decoded.startsWith("$basePath/") -> decoded.removePrefix(basePath)
            else -> URI.create(decoded).path?.removePrefix(basePath).orEmpty()
        }
        return MediaPathConventions.normalizeRemotePath(withoutBase)
    }

    private fun extractBasePath(): String =
        runCatching { URI(baseUrl).path?.trimEnd('/') ?: "" }.getOrDefault("")

    private fun decodeHref(href: String): String =
        MediaPathConventions.decodePath(href)

    private fun isCollection(response: Element): Boolean {
        val resTypes = response.getElementsByTagNameNS(NS_DAV, "resourcetype")
        for (i in 0 until resTypes.length) {
            val nodes = resTypes.item(i).childNodes
            for (j in 0 until nodes.length) {
                if (nodes.item(j).localName == "collection") return true
            }
        }
        return false
    }

    private fun getChildText(element: Element, tagName: String): String? {
        val nodes = element.getElementsByTagNameNS(NS_DAV, tagName)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    private fun parseDavDate(date: String): Long =
        runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(date)?.time ?: 0L
        }.getOrDefault(0L)

    companion object {
        private const val PROPFIND = "PROPFIND"
        private const val DEPTH_1 = "1"
        private const val NS_DAV = "DAV:"
        private val xmlMedia = "application/xml; charset=utf-8".toMediaType()

        fun create(name: String, url: String, username: String = "", password: String = ""): DesktopWebDavMediaSource =
            DesktopWebDavMediaSource(
                MediaSourceInfo(
                    name = name,
                    type = MediaSourceType.WEBDAV,
                    connectionInfo = buildMap {
                        put("url", url)
                        if (username.isNotBlank()) put("username", username)
                        if (password.isNotBlank()) put("password", password)
                    },
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
