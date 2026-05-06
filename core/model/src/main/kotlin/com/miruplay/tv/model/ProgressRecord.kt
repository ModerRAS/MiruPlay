package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class ProgressRecord(
    val episodeId: String,
    val positionMs: Long,
    val lastWatched: Long,  // epoch ms
    val playCount: Int = 0,
)
