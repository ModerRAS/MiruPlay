package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.localRootPath
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.model.sortedForPlaybackQueue

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

    return source.playableUriForIndexedPath(path)
}

fun MediaSourceInfo?.playableUriForIndexedPath(path: String): String =
    when {
        path.isAlreadyPlayableUri() -> path
        this == null -> path
        type == MediaSourceType.WEBDAV -> MediaPathConventions.joinRemoteUrl(remoteUrl().orEmpty(), path)
        type == MediaSourceType.SMB -> {
            val baseUrl = remoteUrl().orEmpty()
            when {
                path.startsWith("smb://", ignoreCase = true) -> path
                baseUrl.isBlank() -> path
                else -> MediaPathConventions.joinRemoteUrl(baseUrl, path)
            }
        }
        else -> path
    }

fun MediaIndexEntry.toIndexedEpisode(
    source: MediaSourceInfo?,
    animeId: String,
): Episode {
    val episodeId = "$sourceId:$path"
    return Episode(
        id = episodeId,
        animeId = animeId,
        seasonNumber = seasonNumber ?: 1,
        episodeNumber = episodeNumber ?: 1,
        title = episodeTitle.orEmpty(),
        filePath = source.playableUriForIndexedPath(path),
        fileName = MediaPathConventions.fileName(path),
    )
}

fun List<MediaIndexEntry>.toIndexedEpisodes(
    source: MediaSourceInfo?,
    animeId: String,
): List<Episode> =
    map { entry -> entry.toIndexedEpisode(source, animeId) }
        .sortedForPlaybackQueue()

private fun MediaSourceInfo.matchesPath(path: String): Boolean =
    when (type) {
        MediaSourceType.LOCAL -> {
            val root = localRootPath()
            root != null && (path == root || path.startsWith("${root.trimEnd('/')}/"))
        }
        MediaSourceType.WEBDAV -> path.startsWith("/")
        MediaSourceType.SMB -> path.startsWith("smb://")
    }

private fun String.isAlreadyPlayableUri(): Boolean =
    startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true) ||
        startsWith("content://", ignoreCase = true)
