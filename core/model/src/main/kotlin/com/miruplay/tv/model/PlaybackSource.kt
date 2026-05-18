package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackSource(
    val uri: String,
    val mediaSourceId: String,
    val startPosition: Long = 0L,  // ms
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val episodeId: String? = null,
)

fun playbackSourceFromInputs(
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    mediaSourceId: String,
    episodeId: String? = null,
    blankMediaMessage: String = "Choose a media URI or file path before launching playback.",
): PlaybackSource {
    val media = requireNotNull(mediaPath.trim().takeIf { it.isNotBlank() }) {
        blankMediaMessage
    }
    return PlaybackSource(
        uri = media,
        mediaSourceId = mediaSourceId,
        startPosition = PlaybackTimingConventions.parseSecondsToPositionMs(startSeconds),
        subtitleTracks = buildExternalSubtitleTracks(subtitlePath.trim()),
        episodeId = episodeId ?: media,
    )
}
