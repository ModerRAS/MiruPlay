package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val id: String,
    val title: String,  // 日文原名
    val titleCn: String? = null,  // 中文名
    val summary: String = "",
    val genres: List<String> = emptyList(),
    val studio: String? = null,
    val director: String? = null,
    val episodeCount: Int = 0,
    val airDate: String? = null,
    val rating: Float = 0f,
    val bangumiId: Int? = null,
    val anilistId: Int? = null,
    val tmdbId: Int? = null,
    val posterUrl: String? = null,
    val fanartUrl: String? = null,
)
