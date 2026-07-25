@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.miruplay.tv.mediasource.WebDavHttpStatusException
import com.miruplay.tv.mediasource.WebDavLease
import com.miruplay.tv.mediasource.WebDavRequest
import com.miruplay.tv.mediasource.WebDavRequestCoordinator
import com.miruplay.tv.mediasource.WebDavRequestKind
import com.miruplay.tv.mediasource.WebDavTransportResult
import com.miruplay.tv.model.MediaPathConventions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackDataSourceFactory @Inject constructor(
    @ApplicationContext context: Context,
) : DataSource.Factory {
    private val upstreamFactory = DefaultDataSource.Factory(
        context,
        DefaultHttpDataSource.Factory(),
    )

    @Volatile
    private var httpConfig: PlaybackHttpRequestConfig = PlaybackHttpRequestConfig.Empty

    fun setHttpConfig(config: PlaybackHttpRequestConfig) {
        httpConfig = config
    }

    fun clearHttpConfig() {
        httpConfig = PlaybackHttpRequestConfig.Empty
    }

    override fun createDataSource(): DataSource =
        GatedPlaybackDataSource(upstreamFactory.createDataSource()) { httpConfig }
}

internal fun canonicalPlaybackUri(uri: String): String =
    if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
        MediaPathConventions.canonicalizeRemoteUrl(uri)
    } else {
        uri
    }

data class PlaybackHttpRequestConfig(
    private val baseUrl: String,
    private val headers: Map<String, String>,
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val decodedBaseUrl = MediaPathConventions.decodePath(normalizedBaseUrl)
    private val baseOrigin = normalizedBaseUrl.originOrNull()

    init {
        if (normalizedBaseUrl.isNotBlank()) WebDavRequestCoordinator.register(normalizedBaseUrl)
    }

    fun applyTo(dataSpec: DataSpec): DataSpec {
        val canonicalUri = canonicalPlaybackUri(dataSpec.uri.toString())
        val normalizedDataSpec = if (canonicalUri == dataSpec.uri.toString()) {
            dataSpec
        } else {
            dataSpec.withUri(Uri.parse(canonicalUri))
        }
        return headersFor(canonicalUri).let { requestHeaders ->
            if (requestHeaders.isEmpty()) normalizedDataSpec else normalizedDataSpec.withAdditionalHeaders(requestHeaders)
        }
    }

    fun libVlcUriFor(uri: String): String {
        val normalizedUri = normalizeVlcUri(uri)
        val credentials = basicCredentialsFor(normalizedUri) ?: return normalizedUri
        return embedCredentialsInUri(normalizedUri, credentials.first, credentials.second)
    }

    internal fun headersFor(uri: String): Map<String, String> =
        if (
            headers.isNotEmpty() &&
            (uri.isWithinBaseUrl() || uri.isSameOriginAsBaseUrl())
        ) {
            headers
        } else {
            emptyMap()
        }

    internal fun isWebDav(uri: String): Boolean =
        normalizedBaseUrl.isNotBlank() && (uri.isWithinBaseUrl() || uri.isSameOriginAsBaseUrl())

    private fun String.isWithinBaseUrl(): Boolean =
        isAtOrBelow(normalizedBaseUrl) ||
            MediaPathConventions.decodePath(this).isAtOrBelow(decodedBaseUrl)

    private fun String.isSameOriginAsBaseUrl(): Boolean =
        baseOrigin != null && originOrNull() == baseOrigin

    private fun String.originOrNull(): String? =
        runCatching {
            URI(MediaPathConventions.decodePath(this.trim())).let { parsed ->
                val scheme = parsed.scheme?.lowercase() ?: return@runCatching null
                val host = parsed.host?.lowercase() ?: return@runCatching null
                val port = if (parsed.port >= 0) parsed.port else parsed.toURL().defaultPort
                "$scheme://$host:$port"
            }
        }.getOrNull()

    private fun String.isAtOrBelow(base: String): Boolean =
        base.isNotBlank() &&
            (this == base ||
                startsWith("$base/") ||
                startsWith("$base?") ||
                startsWith("$base#"))

    private fun basicCredentialsFor(uri: String): Pair<String, String>? {
        if (!(uri.isWithinBaseUrl() || uri.isSameOriginAsBaseUrl())) return null
        val authorization = headers.entries.firstOrNull { (key, _) ->
            key.equals("Authorization", ignoreCase = true)
        }?.value ?: return null
        if (!authorization.startsWith("Basic ", ignoreCase = true)) return null
        val decoded = runCatching {
            String(
                Base64.getDecoder().decode(authorization.substringAfter(' ').trim()),
                Charsets.UTF_8,
            )
        }.getOrNull() ?: return null
        val separatorIndex = decoded.indexOf(':')
        return if (separatorIndex >= 0) {
            decoded.substring(0, separatorIndex) to decoded.substring(separatorIndex + 1)
        } else {
            decoded to ""
        }
    }

    private fun embedCredentialsInUri(
        originalUri: String,
        username: String,
        password: String,
    ): String {
        val parsed = runCatching { URI(originalUri.trim()) }.getOrNull() ?: return originalUri
        if (!parsed.userInfo.isNullOrBlank()) return originalUri
        val scheme = parsed.scheme ?: return originalUri
        val host = parsed.host ?: return originalUri
        return runCatching {
            URI(
                scheme,
                "$username:$password",
                host,
                parsed.port,
                parsed.path,
                parsed.query,
                parsed.fragment,
            ).toASCIIString()
        }.getOrDefault(originalUri)
    }

    private fun normalizeVlcUri(uri: String): String {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) return uri
        if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return canonicalPlaybackUri(trimmed)
        }
        if (
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return trimmed
        }
        if (trimmed.startsWith("/")) {
            return runCatching { URI("file", "", trimmed, null).toASCIIString() }
                .getOrDefault("file://$trimmed")
        }
        return runCatching { File(trimmed).toURI().toASCIIString() }
            .getOrDefault(trimmed)
    }

    companion object {
        val Empty = PlaybackHttpRequestConfig(baseUrl = "", headers = emptyMap())
    }
}

internal class GatedPlaybackDataSource(
    private val upstream: DataSource,
    private val config: () -> PlaybackHttpRequestConfig,
) : DataSource {
    private var lease: WebDavLease<Long>? = null
    private var ungatedOpen = false
    private var webDavInput: InputStream? = null
    private var webDavConnection: HttpURLConnection? = null
    private var webDavUri: Uri? = null
    private var webDavHeaders: Map<String, List<String>> = emptyMap()

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val currentConfig = config()
        val resolved = currentConfig.applyTo(dataSpec)
        val uri = resolved.uri.toString()
        if (!currentConfig.isWebDav(uri)) {
            ungatedOpen = true
            return upstream.open(resolved)
        }
        val request = WebDavRequest(
            method = "GET",
            url = uri,
            kind = if (resolved.position > 0L || resolved.length >= 0L) {
                WebDavRequestKind.RANGE
            } else {
                WebDavRequestKind.PLAYBACK
            },
            streaming = true,
        )
        return WebDavRequestCoordinator.execute(request) {
            openWebDav(resolved)
        }.also { lease = it }.value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        webDavInput?.read(buffer, offset, length) ?: upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = webDavUri ?: upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = webDavHeaders.ifEmpty { upstream.responseHeaders }

    override fun close() {
        try {
            webDavInput?.close()
        } finally {
            webDavInput = null
            webDavConnection?.disconnect()
            webDavConnection = null
            webDavUri = null
            webDavHeaders = emptyMap()
            lease?.close()
            lease = null
            if (ungatedOpen) upstream.close()
            ungatedOpen = false
        }
    }

    private fun openWebDav(dataSpec: DataSpec): WebDavTransportResult<Long> {
        val connection = (URL(dataSpec.uri.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = HTTP_TIMEOUT_MILLIS
            readTimeout = HTTP_TIMEOUT_MILLIS
            requestMethod = "GET"
            dataSpec.httpRequestHeaders.forEach(::setRequestProperty)
            if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) "" else dataSpec.position + dataSpec.length - 1
                setRequestProperty("Range", "bytes=${dataSpec.position}-$end")
            }
        }
        val statusCode = try {
            connection.responseCode
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
        if (statusCode !in 200..299) {
            connection.errorStream?.close()
            connection.disconnect()
            throw WebDavHttpStatusException(statusCode)
        }
        val input = connection.inputStream
        webDavInput = input
        webDavConnection = connection
        webDavUri = dataSpec.uri
        webDavHeaders = connection.headerFields
            .filterKeys { it != null }
            .mapKeys { (key, _) -> key!! }
        val available = connection.contentLengthLong
        val resolvedLength = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            available >= 0L -> available
            else -> C.LENGTH_UNSET.toLong()
        }
        return WebDavTransportResult(
            value = resolvedLength,
            statusCode = statusCode,
            close = this::closeWebDavTransport,
        )
    }

    private fun closeWebDavTransport() {
        webDavInput?.close()
        webDavInput = null
        webDavConnection?.disconnect()
        webDavConnection = null
        webDavUri = null
        webDavHeaders = emptyMap()
    }

    private companion object {
        private const val HTTP_TIMEOUT_MILLIS = 20_000
    }
}

private fun Throwable.invalidResponseCode(): Int? {
    var error: Throwable? = this
    while (error != null) {
        if (error is HttpDataSource.InvalidResponseCodeException) return error.responseCode
        error = error.cause
    }
    return null
}
