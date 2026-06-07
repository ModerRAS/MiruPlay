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
    val metadataProviderRef: MetadataProviderRef? = tmdbId?.let {
        MetadataProviderRef(source = "TMDB", id = it.toString())
    },
)

@Serializable
data class DramaSeriesMetadata(
    val series: DramaSeries,
    val seasons: List<DramaSeasonMetadata> = emptyList(),
)

@Serializable
data class DramaMetadataSearchResult(
    val tmdbId: Int? = null,
    val title: String,
    val originalTitle: String = "",
    val summary: String = "",
    val firstAirDate: String? = null,
    val posterUrl: String? = null,
    val fanartUrl: String? = null,
    val providerRef: MetadataProviderRef = MetadataProviderRef(
        source = "TMDB",
        id = tmdbId?.toString().orEmpty(),
    ),
    val sourceLabels: List<String> = listOf(providerRef.source).filter { it.isNotBlank() }.distinct(),
) {
    init {
        require(providerRef.id.isNotBlank() || tmdbId != null) {
            "DramaMetadataSearchResult requires either providerRef.id or tmdbId"
        }
    }
}

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

fun MetadataProviderRef.tmdbCompatibilityId(): Int? =
    id.toIntOrNull()?.takeIf { source.equals("TMDB", ignoreCase = true) }

fun DramaSeries.tmdbCompatibilityId(): Int? =
    metadataProviderRef?.tmdbCompatibilityId()
        ?: tmdbId?.takeIf { metadataProviderRef?.id.isNullOrBlank() }

fun DramaSeries.withoutMetadataBinding(): DramaSeries =
    copy(
        tmdbId = null,
        metadataProviderRef = null,
    )

fun DramaSeries.normalizedMetadataBinding(): DramaSeries {
    val normalizedProviderRef = metadataProviderRef
        ?.takeIf { it.id.isNotBlank() }
        ?: tmdbCompatibilityId()?.let { MetadataProviderRef(source = "TMDB", id = it.toString()) }
    val normalizedTmdbId = normalizedProviderRef?.tmdbCompatibilityId()
        ?: tmdbId?.takeIf { normalizedProviderRef == null }
    return if (normalizedProviderRef == metadataProviderRef && normalizedTmdbId == tmdbId) {
        this
    } else {
        copy(
            tmdbId = normalizedTmdbId,
            metadataProviderRef = normalizedProviderRef,
        )
    }
}

fun DramaSeries.boundMetadataProviderRef(): MetadataProviderRef? =
    metadataProviderRef?.takeIf { it.id.isNotBlank() }
        ?: tmdbCompatibilityId()?.let { MetadataProviderRef(source = "TMDB", id = it.toString()) }

fun DramaMetadataSearchResult.displayTitle(): String =
    title.ifBlank { originalTitle }.ifBlank { providerRef.id }

fun DramaMetadataSearchResult.providerStableKey(): String =
    "${providerRef.source.lowercase()}:${providerRef.id}"

fun DramaMetadataSearchResult.providerDisplayLabel(): String =
    providerRef.source.ifBlank { "Metadata" }

fun DramaMetadataSearchResult.aggregatedSourceLabel(): String =
    sourceLabels
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString(" / ")
        .ifBlank { providerDisplayLabel() }

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
    hasBoundMetadata: Boolean,
    hasTmdbToken: Boolean,
    isRefreshing: Boolean,
    boundProviderLabel: String? = null,
    canRefreshBoundMetadata: Boolean = false,
): String =
    when {
        isRefreshing -> "正在刷新在线信息，当前页面会继续保留本地剧集列表。"
        hasBoundMetadata -> {
            val providerLabel = boundProviderLabel?.takeIf { it.isNotBlank() } ?: "在线"
            if (canRefreshBoundMetadata || boundProviderLabel.equals("TMDB", ignoreCase = true)) {
                "已记住 $providerLabel 元数据条目，后续刷新会优先按已保存来源更新。"
            } else {
                "已记住 $providerLabel 元数据条目；当前自动详情补全仍优先使用已支持的详情源。"
            }
        }
        hasTmdbToken -> "当前先显示本地索引结果。若还没绑定在线来源，可用“刷新信息”按标题走 TMDB 补全海报、简介和单集标题。"
        else -> "当前只显示本地索引结果。现在可以先用“在线手动匹配”搜索多源候选；如果还没绑定可直刷的在线来源，也可以在设置里配置 TMDB Token 来启用按标题直接刷新。"
    }
