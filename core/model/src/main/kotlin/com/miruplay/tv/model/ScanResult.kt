package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class ScanResult(
    val animeName: String,
    val episodesFound: Int,
    val newEpisodes: Int = 0,
    val updatedEpisodes: Int = 0,
)
