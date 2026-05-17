package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class DownloadedTorrentFile(
    val file: File,
    val remoteFileName: String
)

class TorrentFileDownloader(
    private val downloadDir: File = File(System.getProperty("java.io.tmpdir"), "miruplay-rss-torrents")
) {
    private var currentProxyHost: String = ""
    private var currentProxyPort: Int = 0
    private var currentProxyEnabled: Boolean = false

    @Volatile
    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun configureProxy(enabled: Boolean, host: String, port: Int) {
        val normalizedHost = host.trim()
        val normalizedPort = port.coerceIn(1, 65535)
        if (currentProxyEnabled == enabled && currentProxyHost == normalizedHost && currentProxyPort == normalizedPort) {
            return
        }
        currentProxyEnabled = enabled
        currentProxyHost = normalizedHost
        currentProxyPort = normalizedPort

        client = client.newBuilder()
            .proxy(if (enabled && normalizedHost.isNotBlank()) Proxy(Proxy.Type.HTTP, InetSocketAddress(normalizedHost, normalizedPort)) else Proxy.NO_PROXY)
            .build()
    }

    suspend fun download(url: String, title: String, keyPrefix: String): Result<DownloadedTorrentFile> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                downloadDir.mkdirs()
                val remoteFileName = buildTorrentFileName(title, url, keyPrefix)
                val localFile = File(downloadDir, remoteFileName)
                if (localFile.exists()) localFile.delete()

                val response = client.newCall(Request.Builder().url(url).get().build()).execute()
                response.use {
                    if (!it.isSuccessful) {
                        return@withContext Result.failure(AppError.NetworkError.HttpError(it.code, it.message))
                    }
                    val body = it.body
                        ?: return@withContext Result.failure(AppError.NetworkError.ServerUnreachable(url))
                    val contentLength = body.contentLength()
                    if (contentLength > MAX_TORRENT_BYTES) {
                        return@withContext Result.failure(AppError.SyncError.WriteFailed(url, "torrent 文件过大: $contentLength bytes"))
                    }

                    var totalBytes = 0L
                    body.byteStream().use { input ->
                        localFile.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                if (totalBytes > MAX_TORRENT_BYTES) {
                                    localFile.delete()
                                    return@withContext Result.failure(AppError.SyncError.WriteFailed(url, "torrent 文件过大: $totalBytes bytes"))
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }

                    if (totalBytes == 0L) {
                        localFile.delete()
                        return@withContext Result.failure(AppError.SyncError.WriteFailed(url, "torrent 文件为空"))
                    }
                }
                Result.success(DownloadedTorrentFile(localFile, remoteFileName))
            } catch (e: Exception) {
                Result.failure(AppError.NetworkError.ServerUnreachable(url))
            }
        }

    companion object {
        private const val MAX_TORRENT_BYTES = 16L * 1024L * 1024L

        internal fun buildTorrentFileName(title: String, url: String, keyPrefix: String): String {
            val fromTitle = title.trim().takeIf { it.endsWith(".torrent", ignoreCase = true) }
            val fromUrl = runCatching {
                Request.Builder().url(url).build().url.pathSegments.lastOrNull()
            }.getOrNull()
            val baseName = (fromTitle ?: fromUrl ?: "rss-item.torrent")
                .substringBefore('?')
                .substringBefore('#')
                .ifBlank { "rss-item.torrent" }
                .let { if (it.endsWith(".torrent", ignoreCase = true)) it else "$it.torrent" }
            val safeBaseName = baseName
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .ifBlank { "rss-item.torrent" }
                .take(180)
            val prefix = keyPrefix.replace(Regex("""[^A-Za-z0-9_-]"""), "").take(12)
            return if (prefix.isBlank()) safeBaseName else "$prefix-$safeBaseName"
        }
    }
}
