package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.buildExternalAudioTracks
import com.miruplay.tv.model.buildExternalSubtitleTracks
import com.miruplay.tv.model.matchingExternalAudioPaths
import com.miruplay.tv.model.matchingExternalSubtitlePaths

class PlaybackSidecarResolver(
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
        val siblingPaths = listSiblingPaths(mediaSource, videoPath)
        val discoveredSubtitles = buildExternalSubtitleTracks(
            (entry?.externalSubtitlePaths.orEmpty() + matchingExternalSubtitlePaths(videoPath, siblingPaths))
                .map(mediaSource::playableUriForIndexedPath),
        )
        val discoveredAudio = buildExternalAudioTracks(
            matchingExternalAudioPaths(videoPath, siblingPaths)
                .map(mediaSource::playableUriForIndexedPath),
        )
        if (discoveredSubtitles.isEmpty() && discoveredAudio.isEmpty()) return source
        return source.copy(
            subtitleTracks = (source.subtitleTracks + discoveredSubtitles).distinctBy { it.path },
            externalAudioTracks = (source.externalAudioTracks + discoveredAudio).distinctBy { it.path },
        )
    }
}
