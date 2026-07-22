package com.miruplay.tv.player

import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.DEFAULT_CLOUD_DRIVE_ENDPOINT_URL
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.repository.MediaSourceRepository
import java.net.URI
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackHttpRequestResolver @Inject constructor(
    private val mediaSources: MediaSourceRepository,
    private val mediaSourceFactory: MediaSourceFactory? = null,
) {
    suspend fun configFor(source: PlaybackSource): PlaybackHttpRequestConfig {
        val mediaSource = findMediaSource(source) ?: return PlaybackHttpRequestConfig.Empty
        if (mediaSource.type != MediaSourceType.WEBDAV) return PlaybackHttpRequestConfig.Empty

        val remoteUrl = mediaSource.remoteUrl().orEmpty()
        if (remoteUrl.isBlank()) return PlaybackHttpRequestConfig.Empty

        if (remoteUrl.isDefaultCloudDriveWebDavEndpoint()) {
            warmWebDavParentDirectory(mediaSource, source.uri, remoteUrl)
        }
        return PlaybackHttpRequestConfig(
            baseUrl = remoteUrl,
            headers = mapOf(AUTHORIZATION_HEADER to mediaSource.playbackAuthorizationHeader()),
        )
    }

    private suspend fun warmWebDavParentDirectory(
        source: MediaSourceInfo,
        uri: String,
        remoteUrl: String,
    ) {
        val parentPath = webDavParentDirectoryForPlayback(uri, remoteUrl) ?: return
        val mediaSource = mediaSourceFactory?.create(source)?.getOrNull() ?: return
        try {
            runCatching { mediaSource.listFiles(parentPath) }
        } finally {
            runCatching { mediaSource.close() }
        }
    }

    private suspend fun findMediaSource(source: PlaybackSource): MediaSourceInfo? {
        source.sourceIdHint()?.let { sourceId ->
            mediaSources.getSourceById(sourceId)
                .getOrNull()
                ?.takeIf { candidate ->
                    candidate.type == MediaSourceType.WEBDAV &&
                        source.uri.isAtOrBelowRemoteUrl(candidate.remoteUrl().orEmpty())
                }
                ?.let { return it }
        }

        return mediaSources.getSources()
            .getOrNull()
            .orEmpty()
            .filter { mediaSource ->
                mediaSource.type == MediaSourceType.WEBDAV &&
                    source.uri.isAtOrBelowRemoteUrl(mediaSource.remoteUrl().orEmpty())
            }
            .maxByOrNull { mediaSource ->
                MediaPathConventions.decodePath(mediaSource.remoteUrl().orEmpty()).trimEnd('/').length
            }
    }

    private fun PlaybackSource.sourceIdHint(): Long? =
        episodeId?.substringBefore(':')?.toLongOrNull()
            ?: mediaSourceId.toLongOrNull()

    private fun String.isAtOrBelowRemoteUrl(remoteUrl: String): Boolean {
        val base = remoteUrl.trimEnd('/')
        if (base.isBlank()) return false
        val decodedValue = MediaPathConventions.decodePath(this)
        val decodedBase = MediaPathConventions.decodePath(base)
        return isAtOrBelow(base) || decodedValue.isAtOrBelow(decodedBase)
    }

    private fun String.isAtOrBelow(base: String): Boolean =
        this == base ||
            startsWith("$base/") ||
            startsWith("$base?") ||
            startsWith("$base#")

    private fun MediaSourceInfo.playbackAuthorizationHeader(): String {
        val username = connectionUsername()
        if (username.isBlank()) {
            return anonymousAuthorizationHeader()
        }
        return basicAuthorizationHeader(
            username = username,
            password = connectionPassword(),
        )
    }

    private fun basicAuthorizationHeader(username: String, password: String): String {
        val token = Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }

    private fun anonymousAuthorizationHeader(): String {
        val token = Base64.getEncoder()
            .encodeToString("anonymous:".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }

    private companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
    }
}

private fun String.isDefaultCloudDriveWebDavEndpoint(): Boolean =
    runCatching {
        URI(MediaPathConventions.canonicalizeRemoteUrl(this)).port == URI(DEFAULT_CLOUD_DRIVE_ENDPOINT_URL).port
    }.getOrDefault(false)

internal fun webDavParentDirectoryForPlayback(uri: String, remoteUrl: String): String? {
    val decodedUri = MediaPathConventions.decodePath(uri.substringBefore('?').substringBefore('#'))
    val decodedBase = MediaPathConventions.decodePath(remoteUrl).trimEnd('/')
    if (decodedBase.isBlank() || (decodedUri != decodedBase && !decodedUri.startsWith("$decodedBase/"))) {
        return null
    }
    return decodedUri.removePrefix(decodedBase).substringBeforeLast('/', "")
}
