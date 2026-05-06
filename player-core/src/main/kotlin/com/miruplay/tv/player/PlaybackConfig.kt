package com.miruplay.tv.player

/**
 * Player configuration
 */
data class PlaybackConfig(
    val preferredAudioLanguage: String = "ja",
    val preferredSubtitleLanguage: String = "zh",
    val autoResume: Boolean = true,
    val respectEmbeddedSubtitles: Boolean = false,
)