package com.miruplay.tv.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.miruplay.tv.model.MediaPathConventions
import dagger.hilt.android.qualifiers.ApplicationContext
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

    fun applyTo(dataSpec: DataSpec): DataSpec =
        headersFor(dataSpec.uri.toString()).let { requestHeaders ->
            if (requestHeaders.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(requestHeaders)
        }

    internal fun headersFor(uri: String): Map<String, String> =
        if (headers.isNotEmpty() && uri.isWithinBaseUrl()) headers else emptyMap()

    private fun String.isWithinBaseUrl(): Boolean =
        isAtOrBelow(normalizedBaseUrl) ||
            MediaPathConventions.decodePath(this).isAtOrBelow(decodedBaseUrl)

    private fun String.isAtOrBelow(base: String): Boolean =
        base.isNotBlank() &&
            (this == base ||
                startsWith("$base/") ||
                startsWith("$base?") ||
                startsWith("$base#"))

    companion object {
        val Empty = PlaybackHttpRequestConfig(baseUrl = "", headers = emptyMap())
    }
}
