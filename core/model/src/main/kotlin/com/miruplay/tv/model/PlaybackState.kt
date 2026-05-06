package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
sealed class PlaybackState {
    @Serializable
    data object Idle : PlaybackState()

    @Serializable
    data class Loading(val source: PlaybackSource) : PlaybackState()

    @Serializable
    data class Playing(
        val source: PlaybackSource,
        val position: Long,
    ) : PlaybackState()

    @Serializable
    data class Paused(
        val source: PlaybackSource,
        val position: Long,
    ) : PlaybackState()

    @Serializable
    data class Buffering(
        val source: PlaybackSource,
        val position: Long,
    ) : PlaybackState()

    @Serializable
    data class Ended(val source: PlaybackSource) : PlaybackState()

    @Serializable
    data class Error(
        val source: PlaybackSource?,
        val error: String,
    ) : PlaybackState()
}
