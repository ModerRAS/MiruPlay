package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType

suspend fun resolvePlayableUri(
    path: String,
    episodeId: String,
    mediaRepository: MediaSourceRepository
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
        MediaPathConventions.joinRemoteUrl(source.connectionInfo["url"].orEmpty(), path)
    } else {
        path
    }
}

private fun MediaSourceInfo.matchesPath(path: String): Boolean =
    when (type) {
        MediaSourceType.LOCAL -> {
            val root = connectionInfo["path"] ?: connectionInfo["url"]
            root != null && (path == root || path.startsWith("${root.trimEnd('/')}/"))
        }
        MediaSourceType.WEBDAV -> path.startsWith("/")
        MediaSourceType.SMB -> path.startsWith("smb://")
    }
