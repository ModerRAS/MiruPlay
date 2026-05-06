package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class Season(
    val seasonNumber: Int,
    val title: String,
    val episodeCount: Int = 0,
    val episodes: List<Episode> = emptyList(),
)
