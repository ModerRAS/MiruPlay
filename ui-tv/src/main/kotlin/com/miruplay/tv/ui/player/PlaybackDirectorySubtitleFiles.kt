package com.miruplay.tv.ui.player

import android.net.Uri
import android.provider.DocumentsContract
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo

internal suspend fun listPlaybackSiblingPaths(
    mediaSourceFactory: MediaSourceFactory,
    mediaSourceInfo: MediaSourceInfo,
    videoPath: String,
): List<String> {
    val parentPath = playbackParentDirectoryPath(videoPath) ?: return emptyList()
    val mediaSource = mediaSourceFactory.create(mediaSourceInfo).getOrNull() ?: return emptyList()
    return try {
        mediaSource.listFiles(parentPath)
            .getOrNull()
            .orEmpty()
            .asSequence()
            .filterNot { it.isDirectory }
            .map { it.path }
            .toList()
    } catch (_: Exception) {
        emptyList()
    } finally {
        try {
            mediaSource.close()
        } catch (_: Exception) {
            // Directory subtitle discovery is best-effort and must not block playback.
        }
    }
}

internal fun playbackParentDirectoryPath(videoPath: String): String? {
    if (!videoPath.startsWith("content://", ignoreCase = true)) {
        return MediaPathConventions.parentPath(videoPath)
    }
    return runCatching {
        val videoUri = Uri.parse(videoPath)
        val documentId = DocumentsContract.getDocumentId(videoUri)
        val parentDocumentId = documentId.substringBeforeLast('/', "").takeIf(String::isNotBlank)
            ?: return@runCatching null
        DocumentsContract.buildDocumentUriUsingTree(videoUri, parentDocumentId).toString()
    }.getOrNull()
}
