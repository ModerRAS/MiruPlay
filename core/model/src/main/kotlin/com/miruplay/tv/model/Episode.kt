package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class Episode(
    val id: String,
    val animeId: String,
    val seasonNumber: Int = 1,
    val episodeNumber: Int,
    val title: String = "",
    val filePath: String,
    val fileName: String,
    val duration: Long = 0L,  // ms
    val watchedPosition: Long = 0L,  // ms
    val lastWatchedTimestamp: Long = 0L,  // epoch ms
    val playCount: Int = 0,
    val thumbnailPath: String? = null,
    val bangumiEpisodeId: Int? = null,
    val bangumiCollectionType: Int? = null,
)
