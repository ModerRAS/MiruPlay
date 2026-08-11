package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.buildExternalSubtitleTracks
import com.miruplay.tv.model.matchingExternalSubtitlePaths

class PlaybackSubtitleResolver(
    private val index: MediaIndexRepository,
    private val mediaSources: MediaSourceRepository,
    private val listSiblingPaths: suspend (MediaSourceInfo, String) -> List<String> = { _, _ -> emptyList() },
) {
    suspend fun resolve(source: PlaybackSource): PlaybackSource {
        val episodeId = source.episodeId ?: return source
        val sourceId = episodeId.substringBefore(':').toLongOrNull() ?: return source
        val mediaSource = when (val result = mediaSources.getSourceById(sourceId)) {
            is Result.Success -> result.data
            is Result.Error -> return source
        }
        val episodePath = episodeId.substringAfter(':', "")
        if (episodePath.isBlank()) return source
        val entries = index.queryIndex(sourceId, "").getOrNull().orEmpty()
        val entry = entries.firstOrNull { indexed ->
            mediaSource.playableUriForIndexedPath(indexed.path) == source.uri
        } ?: entries.firstOrNull { indexed -> indexed.path == episodePath }
        val videoPath = entry?.path ?: episodePath
        val siblingSubtitlePaths = matchingExternalSubtitlePaths(
            videoPath = videoPath,
            siblingPaths = listSiblingPaths(mediaSource, videoPath),
        )
        val discoveredTracks = buildExternalSubtitleTracks(
            (entry?.externalSubtitlePaths.orEmpty() + siblingSubtitlePaths)
                .map(mediaSource::playableUriForIndexedPath),
        )
        if (discoveredTracks.isEmpty()) return source
        return source.copy(
            subtitleTracks = (source.subtitleTracks + discoveredTracks).distinctBy { it.path },
        )
    }
}
