package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import java.net.URLEncoder

suspend fun resolvePlayableUri(
    path: String,
    episodeId: String,
    mediaRepository: MediaRepository
): String {
    if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("content://")) {
        return path
    }

    val sources = when (val result = mediaRepository.getSources()) {
        is Result.Success -> result.data
        is Result.Error -> emptyList()
    }

    val sourceId = episodeId.substringBefore(':').toLongOrNull()
    val source = if (sourceId != null) {
        sources.firstOrNull { it.id == sourceId }
    } else {
        sources.firstOrNull { source ->
            source.matchesPath(path)
        }
    }

    return if (source?.type == MediaSourceType.WEBDAV) {
        joinRemoteUrl(source.connectionInfo["url"].orEmpty(), path)
    } else {
        path
    }
}

private fun MediaSourceInfo.matchesPath(path: String): Boolean {
    return when (type) {
        MediaSourceType.LOCAL -> {
            val root = connectionInfo["path"] ?: connectionInfo["url"] ?: return false
            path == root || path.startsWith("${root.trimEnd('/')}/")
        }
        MediaSourceType.WEBDAV -> path.startsWith("/")
        MediaSourceType.SMB -> path.startsWith("smb://")
    }
}

private fun joinRemoteUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trimEnd('/')
    if (base.isBlank()) return path
    if (path.startsWith(base)) return path
    val encodedPath = path
        .trimStart('/')
        .split('/')
        .joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
    return "$base/$encodedPath"
}
