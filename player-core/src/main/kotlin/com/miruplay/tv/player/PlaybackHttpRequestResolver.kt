package com.miruplay.tv.player

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.repository.MediaSourceRepository
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackHttpRequestResolver @Inject constructor(
    private val mediaSources: MediaSourceRepository,
) {
    suspend fun configFor(source: PlaybackSource): PlaybackHttpRequestConfig {
        val mediaSource = findMediaSource(source) ?: return PlaybackHttpRequestConfig.Empty
        if (mediaSource.type != MediaSourceType.WEBDAV) return PlaybackHttpRequestConfig.Empty

        val remoteUrl = mediaSource.remoteUrl().orEmpty()
        if (remoteUrl.isBlank()) return PlaybackHttpRequestConfig.Empty

        return PlaybackHttpRequestConfig(
            baseUrl = remoteUrl,
            headers = mapOf(AUTHORIZATION_HEADER to mediaSource.playbackAuthorizationHeader()),
        )
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
            .firstOrNull { mediaSource ->
                mediaSource.type == MediaSourceType.WEBDAV &&
                    source.uri.isAtOrBelowRemoteUrl(mediaSource.remoteUrl().orEmpty())
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
