package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class NfoMetadata(
    val title: String = "",
    val showTitle: String? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val plot: String = "",
    val premiered: String? = null,
    val rating: Float = 0f,
    val playcount: Int = 0,
    val lastplayed: String? = null,
    val resumePosition: Long = 0L,  // seconds
    val uniqueIds: List<UniqueId> = emptyList(),
)

@Serializable
data class TvShowNfoMetadata(
    val title: String = "",
    val originalTitle: String = "",
    val sortTitle: String? = null,
    val plot: String = "",
    val genre: List<String> = emptyList(),
    val premiered: String? = null,
    val studio: String? = null,
    val rating: Float = 0f,
    val uniqueIds: List<UniqueId> = emptyList(),
    val actors: List<Actor> = emptyList(),
)

@Serializable
data class UniqueId(
    val type: String,  // "bangumi", "anilist", "tmdb", "anidb"
    val value: String,
    val isDefault: Boolean = false,
)

@Serializable
data class Actor(
    val name: String,
    val role: String = "",
)
