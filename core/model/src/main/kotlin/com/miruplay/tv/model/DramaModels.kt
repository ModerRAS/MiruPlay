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
data class DramaMetadataSearchResult(
    val tmdbId: Int,
    val title: String,
    val originalTitle: String = "",
    val summary: String = "",
    val firstAirDate: String? = null,
    val posterUrl: String? = null,
    val fanartUrl: String? = null,
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

fun DramaMetadataSearchResult.displayTitle(): String =
    title.ifBlank { originalTitle }.ifBlank { tmdbId.toString() }

fun DramaSeries.dramaPosterSubtitle(): String =
    buildList {
        if (seasonCount > 1) add("共 ${seasonCount.coerceAtLeast(0)} 季")
        if (episodeCount > 0) add("${episodeCount.coerceAtLeast(0)} 集")
    }.joinToString(" · ")

fun DramaSeries.dramaFeatureSubtitle(): String =
    buildList {
        firstAirDate?.takeIf { it.isNotBlank() }?.let(::add)
        dramaPosterSubtitle().takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ").ifBlank { displayTitle() }

fun dramaEpisodeCountLabel(episodeCount: Int): String =
    "全 ${episodeCount.coerceAtLeast(0)} 集"

fun dramaSeasonCountLabel(seasonCount: Int): String =
    "共 ${seasonCount.coerceAtLeast(0)} 季"

fun dramaRefreshActionLabel(isRefreshing: Boolean): String =
    if (isRefreshing) "刷新中" else "刷新信息"

fun dramaMetadataStatusMessage(
    hasTmdbMatch: Boolean,
    hasTmdbToken: Boolean,
    isRefreshing: Boolean,
): String =
    when {
        isRefreshing -> "正在刷新在线信息，当前页面会继续保留本地剧集列表。"
        hasTmdbMatch -> "已记住 TMDB 条目，后续刷新会优先按已保存编号更新。"
        hasTmdbToken -> "当前先显示本地索引结果，点“刷新信息”可补全海报、简介和单集标题。"
        else -> "当前只显示本地索引结果。先在设置里填 TMDB 令牌，再点“刷新信息”补全海报、简介和单集标题。"
    }
