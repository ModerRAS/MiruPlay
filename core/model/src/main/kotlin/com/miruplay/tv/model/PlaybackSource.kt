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

fun List<Episode>.sortedForPlaybackQueue(): List<Episode> =
    sortedWith(compareBy<Episode>({ it.seasonNumber }, { it.episodeNumber }, { it.filePath }))

fun List<Episode>.nextEpisodeAfter(currentEpisodeId: String): Episode? {
    val sortedEpisodes = sortedForPlaybackQueue()
    val currentIndex = sortedEpisodes.indexOfFirst { it.id == currentEpisodeId }
    if (currentIndex < 0 || currentIndex >= sortedEpisodes.lastIndex) return null
    return sortedEpisodes[currentIndex + 1]
}

fun Episode.toPlaybackSource(
    playableUri: String,
    progress: ProgressRecord?,
): PlaybackSource =
    PlaybackSource(
        uri = playableUri,
        mediaSourceId = animeId,
        startPosition = resumePosition(progress),
        subtitleTracks = emptyList(),
        episodeId = id,
    )
