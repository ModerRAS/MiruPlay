@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.miruplay.tv.model.MediaPathConventions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URI
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
        ResolvingDataSource(upstreamFactory.createDataSource()) { dataSpec ->
            httpConfig.applyTo(dataSpec)
        }
}

data class PlaybackHttpRequestConfig(
    private val baseUrl: String,
    private val headers: Map<String, String>,
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val decodedBaseUrl = MediaPathConventions.decodePath(normalizedBaseUrl)
    private val baseOrigin = normalizedBaseUrl.originOrNull()

    fun applyTo(dataSpec: DataSpec): DataSpec =
        headersFor(dataSpec.uri.toString()).let { requestHeaders ->
            if (requestHeaders.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(requestHeaders)
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
            trimmed.startsWith("https://", ignoreCase = true) ||
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
