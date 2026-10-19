package com.miruplay.tv.mediasource

import android.util.Log
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.RSS_DEFAULT_FEED_URL
import com.miruplay.tv.model.RssMediaItem
import com.miruplay.tv.model.RssFeedParser
import com.miruplay.tv.model.RssTitleParser
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.StreamRange
import com.miruplay.tv.model.proxyConfig
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.model.toHttpRangeHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "RssMediaSource"
private const val SERIES_PREFIX = "rss:series:"
private const val SNAPSHOT_TTL_MS = 10 * 60 * 1000L
private const val RSS_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 MiruPlay"

/**
 * Read-only online source backed by a generic RSS 2.0 media feed.
 *
 * The feed is flat, so [listFiles] exposes virtual directories: the root lists
 * one directory per series (feed `<category>` or parsed series title), and
 * each series directory lists its episodes. Episode entries point at the
 * direct file URL, which the playback layer streams without further
 * resolution. The feed URL lives in the source connection info, so any feed
 * with the same shape can be added as another RSS source.
 */
class RssMediaSource @Inject constructor() : MediaSource {
    override val id: String = ""
    override lateinit var info: MediaSourceInfo

    override var capabilities: MediaCapabilities = MediaCapabilities(
        seekable = true,
        supportsRange = false,
        supportsList = true,
        supportsWrite = false,
    )

    private var snapshot: List<RssMediaItem> = emptyList()
    private var snapshotAtMs: Long = 0L
    private var client: OkHttpClient? = null
    private var clientProxyKey: String? = null

    constructor(info: MediaSourceInfo) : this() {
        this.info = info
    }

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        try {
            val items = when (val cached = ensureSnapshot()) {
                is Result.Success -> cached.data
                is Result.Error -> return@withContext Result.failure(cached.error)
            }
            if (path.isBlank()) {
                Result.success(seriesDirectories(items))
            } else {
                val key = path.removePrefix(SERIES_PREFIX)
                Result.success(episodeEntries(items.filter { seriesKeyOf(it) == key }))
            }
        } catch (e: Exception) {
            Log.w(TAG, "RSS listFiles failed for $path", e)
            Result.failure(AppError.MediaSourceError.ConnectionLost(info.name))
        }
    }

    override suspend fun openStream(path: String): Result<InputStream> = openStreamWithHeader(path, null)

    override suspend fun openStream(path: String, range: StreamRange): Result<InputStream> =
        openStreamWithHeader(path, range.toHttpRangeHeader())

    override suspend fun getMetadata(path: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val items = when (val cached = ensureSnapshot()) {
                is Result.Success -> cached.data
                is Result.Error -> return@withContext Result.failure(cached.error)
            }
            val item = items.firstOrNull { it.url == path }
            val name = item?.title?.takeIf { it.isNotBlank() } ?: fileNameOf(path)
            val size = item?.sizeBytes?.takeIf { it > 0L } ?: headContentLength(path)
            Result.success(
                FileMetadata(
                    name = name,
                    path = path,
                    isDirectory = false,
                    size = size,
                    lastModified = item?.pubDateMs ?: 0L,
                    mimeType = "video/mp4",
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "RSS getMetadata failed for $path", e)
            Result.failure(AppError.MediaSourceError.NotFound(path))
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            snapshotAtMs = 0L
            val items = when (val cached = ensureSnapshot()) {
                is Result.Success -> cached.data
                is Result.Error -> return@withContext Result.failure(cached.error)
            }
            if (items.isEmpty()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(info.name))
            }
            capabilities = capabilities.copy(supportsRange = probeRangeSupport(items.first().url))
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "RSS connection test failed", e)
            Result.failure(AppError.NetworkError.ServerUnreachable(feedUrl()))
        }
    }

    override suspend fun close() {
        snapshot = emptyList()
        snapshotAtMs = 0L
    }

    private suspend fun openStreamWithHeader(path: String, rangeHeader: String?): Result<InputStream> =
        withContext(Dispatchers.IO) {
            try {
                val call = httpClient().newCall(
                    Request.Builder()
                        .url(path)
                        .get()
                        .apply { rangeHeader?.let { header("Range", it) } }
                        .header("User-Agent", RSS_USER_AGENT)
                        .header("Referer", "https://api.ani.rip/")
                        .build(),
                )
                val response = call.execute()
                if (!response.isSuccessful) {
                    response.close()
                    return@withContext Result.failure(
                        AppError.NetworkError.HttpError(response.code, response.message),
                    )
                }
                val stream = response.body?.byteStream()
                    ?: run {
                        response.close()
                        return@withContext Result.failure(AppError.MediaSourceError.NotFound(path))
                    }
                Result.success(
                    object : FilterInputStream(stream) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                response.close()
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                Log.w(TAG, "RSS stream failed for $path", e)
                Result.failure(AppError.NetworkError.ServerUnreachable(path))
            }
        }

    private fun ensureSnapshot(): Result<List<RssMediaItem>> {
        val now = System.currentTimeMillis()
        if (snapshotAtMs > 0L && now - snapshotAtMs < SNAPSHOT_TTL_MS && snapshot.isNotEmpty()) {
            return Result.success(snapshot)
        }
        val request = Request.Builder()
            .url(feedUrl())
            .get()
            .header("User-Agent", RSS_USER_AGENT)
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .build()
        httpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(AppError.NetworkError.HttpError(response.code, response.message))
            }
            val xml = response.body?.string().orEmpty()
            val items = RssFeedParser.parse(xml)
            if (items.isEmpty()) {
                return Result.failure(AppError.MediaSourceError.NotFound(feedUrl()))
            }
            snapshot = items
            snapshotAtMs = now
            return Result.success(items)
        }
    }

    private fun probeRangeSupport(url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", RSS_USER_AGENT)
            .header("Referer", "https://api.ani.rip/")
            .header("Range", "bytes=0-0")
            .build()
        httpClient().newCall(request).execute().use { response ->
            if (response.code == 206) return true
            return response.header("Accept-Ranges", "").equals("bytes", ignoreCase = true)
        }
    }

    private fun headContentLength(url: String): Long {
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", RSS_USER_AGENT)
            .build()
        httpClient().newCall(request).execute().use { response ->
            return response.header("Content-Length")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }
    }

    private fun seriesDirectories(items: List<RssMediaItem>): List<FileEntry> {
        val latestBySeries = items.groupBy(::seriesKeyOf)
            .mapValues { (_, group) -> group.maxByOrNull { it.pubDateMs } ?: group.first() }
        return latestBySeries.map { (key, latest) ->
            val ref = RssTitleParser.parse(latest.title)
            FileEntry(
                name = ref?.seriesTitle ?: latest.title,
                path = SERIES_PREFIX + key,
                isDirectory = true,
                lastModified = latest.pubDateMs,
            )
        }.sortedBy { it.name }
    }

    private fun episodeEntries(items: List<RssMediaItem>): List<FileEntry> =
        items.sortedByDescending { it.pubDateMs }.map { item ->
            FileEntry(
                name = item.title,
                path = item.url,
                isDirectory = false,
                size = item.sizeBytes,
                lastModified = item.pubDateMs,
                mimeType = "video/mp4",
            )
        }

    private fun seriesKeyOf(item: RssMediaItem): String =
        item.category.ifBlank {
            RssTitleParser.parse(item.title)?.seriesTitle ?: item.guid
        }

    private fun fileNameOf(item: RssMediaItem): String = fileNameOf(item.url)

    private fun fileNameOf(url: String): String {
        val segment = url.substringBefore('?').substringAfterLast('/')
        return runCatching { URLDecoder.decode(segment, Charsets.UTF_8.name()) }
            .getOrDefault(segment)
            .ifBlank { url }
    }

    private fun feedUrl(): String = info.remoteUrl().orEmpty().ifBlank { RSS_DEFAULT_FEED_URL }

    private fun httpClient(): OkHttpClient {
        val proxy = info.proxyConfig()
        val key = if (proxy?.enabled == true) "${proxy.host}:${proxy.port}" else "direct"
        val cached = client
        if (cached != null && clientProxyKey == key) return cached
        val builder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        if (proxy?.enabled == true) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port)))
        }
        return builder.build().also {
            client = it
            clientProxyKey = key
        }
    }
}
