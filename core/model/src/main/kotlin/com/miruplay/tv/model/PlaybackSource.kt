package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackSource(
    val uri: String,
    val mediaSourceId: String,
    val startPosition: Long = 0L,  // ms
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
)
