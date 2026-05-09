package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

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

    companion object {
        private const val PROPFIND = "PROPFIND"
        private const val DEPTH_1 = "1"
        private const val NS_DAV = "DAV:"

        private val HIDDEN_FILES = setOf(".DS_Store", "Thumbs.db", "@eaDir")
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm")
    }

    constructor(info: MediaSourceInfo) : this() {
        this.info = info
        this.baseUrl = info.connectionInfo["url"] ?: ""
        this.username = info.connectionInfo["username"] ?: ""
        this.password = info.connectionInfo["password"] ?: ""
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
                .method(PROPFIND, RequestBody.create(xmlMedia, propfindXml()))
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
            Result.failure(AppError.NetworkError.ServerUnreachable(path))
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
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(path)
            val request = Request.Builder()
                .url(url)
                .method(PROPFIND, RequestBody.create(xmlMedia, propfindXml()))
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

            Result.success(FileMetadata(entry = entry))
        } catch (e: Exception) {
            Result.failure(AppError.MediaSourceError.NotFound(path))
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
        val base = baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return if (cleanPath.isEmpty()) "$base/" else "$base/$cleanPath"
    }

    private fun credentials(): String {
        val encoded = android.util.Base64.encodeToString(
            "$username:$password".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        return "Basic $encoded"
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
    ): List<FileEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responses = doc.getElementsByTagNameNS(NS_DAV, "response")

        val normalizedRequestedPath = normalizeRemotePath(requestedPath)
        val entries = mutableListOf<FileEntry>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as Element
            val href = getChildText(response, "href") ?: continue

            val path = hrefToRemotePath(href)
            if (path.isEmpty()) continue
            if (!includeRequestedPath && path == normalizedRequestedPath) continue

            val isDir = isCollection(response)
            val name = path.substringAfterLast('/')

            if (name in HIDDEN_FILES) continue

            entries.add(FileEntry(
                name = name,
                path = "/$path",
                isDirectory = isDir,
                size = if (!isDir) getChildText(response, "getcontentlength")?.toLongOrNull() ?: 0L else 0L,
                lastModified = parseDavDate(getChildText(response, "getlastmodified") ?: ""),
                mimeType = if (!isDir) getChildText(response, "getcontenttype") else null
            ))
        }

        // Remove root entry, return children only
        return entries.filter { it.path.isNotBlank() && it.path != "/" }
    }

    private fun hrefToRemotePath(href: String): String {
        val decoded = decodeHref(href)
        val withoutBaseUrl = decoded.removePrefix(baseUrl.trimEnd('/'))
        val basePath = try {
            URI(baseUrl).path.orEmpty().trimEnd('/')
        } catch (_: Exception) {
            ""
        }
        val withoutBasePath = when {
            basePath.isBlank() -> withoutBaseUrl
            withoutBaseUrl == basePath -> ""
            withoutBaseUrl.startsWith("$basePath/") -> withoutBaseUrl.removePrefix(basePath)
            else -> withoutBaseUrl
        }
        return normalizeRemotePath(withoutBasePath)
    }

    private fun normalizeRemotePath(path: String): String =
        path.substringBefore('?')
            .replace('\\', '/')
            .trim('/')

    private fun decodeHref(href: String): String = try {
        java.net.URLDecoder.decode(href, Charsets.UTF_8.name())
    } catch (_: Exception) {
        href
    }

    private fun isCollection(response: Element): Boolean {
        val propStats = response.getElementsByTagNameNS(NS_DAV, "propstat")
        for (i in 0 until propStats.length) {
            val propStat = propStats.item(i) as Element
            val resType = propStat.getElementsByTagNameNS(NS_DAV, "resourcetype")
            if (resType.length > 0) {
                val collection = resType.item(0).childNodes
                for (j in 0 until collection.length) {
                    if (collection.item(j).localName == "collection") return true
                }
            }
        }
        return false
    }

    private fun getChildText(element: Element, tagName: String): String? {
        val nodes = element.getElementsByTagNameNS(NS_DAV, tagName)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    private fun parseDavDate(date: String): Long {
        return try {
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
                .parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
