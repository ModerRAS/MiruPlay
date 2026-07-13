package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.buildExternalSubtitleTracks

class PlaybackSubtitleResolver(
    private val index: MediaIndexRepository,
    private val mediaSources: MediaSourceRepository,
) {
    suspend fun resolve(source: PlaybackSource): PlaybackSource {
        val episodeId = source.episodeId ?: return source
        val sourceId = episodeId.substringBefore(':').toLongOrNull() ?: return source
        val mediaSource = when (val result = mediaSources.getSourceById(sourceId)) {
            is Result.Success -> result.data
            is Result.Error -> return source
        }
        val entries = when (val result = index.queryIndex(sourceId, "")) {
            is Result.Success -> result.data
            is Result.Error -> return source
        }
        val episodePath = episodeId.substringAfter(':', "")
        val entry = entries.firstOrNull { indexed ->
            mediaSource.playableUriForIndexedPath(indexed.path) == source.uri
        } ?: entries.firstOrNull { indexed -> indexed.path == episodePath }
            ?: return source

        val discoveredTracks = buildExternalSubtitleTracks(
            entry.externalSubtitlePaths.map(mediaSource::playableUriForIndexedPath),
        )
        if (discoveredTracks.isEmpty()) return source
        return source.copy(
            subtitleTracks = (source.subtitleTracks + discoveredTracks).distinctBy { it.path },
        )
    }
}
