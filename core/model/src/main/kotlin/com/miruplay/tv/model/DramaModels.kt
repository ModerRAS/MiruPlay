package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class DramaSeries(
    val id: String,
    val title: String,
    val originalTitle: String = "",
    val summary: String = "",
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val posterUrl: String? = null,
    val fanartUrl: String? = null,
    val firstAirDate: String? = null,
    val tmdbId: Int? = null,
)

@Serializable
data class DramaSeriesMetadata(
    val series: DramaSeries,
    val seasons: List<DramaSeasonMetadata> = emptyList(),
)

@Serializable
data class DramaSeason(
    val seasonNumber: Int,
    val episodeCount: Int,
)

@Serializable
data class DramaSeasonMetadata(
    val seasonNumber: Int,
    val title: String = "",
    val episodes: List<DramaEpisodeMetadata> = emptyList(),
)

@Serializable
data class DramaEpisode(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int = 1,
    val episodeNumber: Int,
    val title: String = "",
    val summary: String = "",
    val filePath: String,
    val fileName: String,
)

@Serializable
data class DramaEpisodeMetadata(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String = "",
    val summary: String = "",
)

fun DramaSeries.displayTitle(): String =
    title.ifBlank { originalTitle }.ifBlank { id }
