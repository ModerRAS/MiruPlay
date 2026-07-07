package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
enum class MetadataSearchIntent {
    MANUAL_MATCH,
    BATCH_PREVIEW,
    DETAIL_REFRESH,
}

@Serializable
data class MetadataProviderRef(
    val source: String,
    val id: String,
)

@Serializable
data class MatchEvidence(
    val reason: String,
    val weight: Float = 0f,
)

@Serializable
enum class MatchRecommendation {
    AUTO_ACCEPT,
    REVIEW,
    LOW_CONFIDENCE,
}

@Serializable
data class MetadataSearchQuery(
    val text: String,
    val reason: String,
)

@Serializable
data class MetadataQueryPlan(
    val queries: List<MetadataSearchQuery>,
    val seasonHint: Int? = null,
    val yearHint: Int? = null,
    val providerRefHints: List<MetadataProviderRef> = emptyList(),
) {
    val queryTexts: List<String>
        get() = queries.map(MetadataSearchQuery::text)
}

@Serializable
data class MetadataSearchContext(
    val contentMode: MediaContentMode,
    val intent: MetadataSearchIntent = MetadataSearchIntent.MANUAL_MATCH,
    val title: String = "",
    val localizedTitle: String = "",
    val originalTitle: String = "",
    val aliases: List<String> = emptyList(),
    val filePathSamples: List<String> = emptyList(),
    val manualQuery: String = "",
    val metadataTitle: String? = null,
    val boundProviderRef: MetadataProviderRef? = null,
    val seasonHint: Int? = null,
    val yearHint: Int? = null,
    val episodeCountHint: Int? = null,
    val seasonCountHint: Int? = null,
)

@Serializable
data class MetadataSearchProviderCandidate(
    val providerRef: MetadataProviderRef,
    val title: String,
    val localizedTitle: String? = null,
    val originalTitle: String = "",
    val aliases: List<String> = emptyList(),
    val matchedQuery: String = "",
    val providerScore: Float? = null,
    val providerRank: Int? = null,
    val summary: String = "",
    val posterUrl: String? = null,
    val fanartUrl: String? = null,
    val firstAirDate: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val fromLocalCache: Boolean = false,
)

@Serializable
data class AggregatedMetadataCandidate(
    val contentMode: MediaContentMode,
    val title: String,
    val localizedTitle: String? = null,
    val originalTitle: String = "",
    val aliases: List<String> = emptyList(),
    val summary: String = "",
    val posterUrl: String? = null,
    val fanartUrl: String? = null,
    val firstAirDate: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val providerCandidates: List<MetadataSearchProviderCandidate>,
    val rerankScore: Float,
    val recommendation: MatchRecommendation,
    val evidence: List<MatchEvidence> = emptyList(),
) {
    val providerRefs: List<MetadataProviderRef>
        get() = providerCandidates.map(MetadataSearchProviderCandidate::providerRef).distinct()
}

@Serializable
data class AggregatedMetadataSearchResult(
    val plan: MetadataQueryPlan,
    val candidates: List<AggregatedMetadataCandidate>,
)

fun MetadataSearchProviderCandidate.displayTitle(): String =
    localizedTitle?.takeIf { it.isNotBlank() }
        ?: title.ifBlank { originalTitle }
        ?: providerRef.id

fun AggregatedMetadataCandidate.displayTitle(): String =
    localizedTitle?.takeIf { it.isNotBlank() }
        ?: title.ifBlank { originalTitle }
        ?: providerCandidates.firstOrNull()?.providerRef?.id.orEmpty()

fun AggregatedMetadataCandidate.providerSourceLabels(): List<String> =
    providerCandidates.map { it.providerRef.source }.distinct()

fun AggregatedMetadataCandidate.preferredProviderCandidate(
    preferredSources: List<String>,
): MetadataSearchProviderCandidate? {
    if (providerCandidates.isEmpty()) return null
    val preferredOrder = preferredSources.mapIndexed { index, source -> source.lowercase() to index }.toMap()
    return providerCandidates.minWithOrNull(
        compareBy<MetadataSearchProviderCandidate> {
            preferredOrder[it.providerRef.source.lowercase()] ?: Int.MAX_VALUE
        }.thenBy { it.providerRank ?: Int.MAX_VALUE }
            .thenByDescending { it.providerScore ?: 0f },
    ) ?: providerCandidates.first()
}

fun AggregatedMetadataCandidate.toPreferredScraperResult(
    preferredSources: List<String> = listOf("Bangumi"),
): ScraperResult? {
    val preferred = preferredProviderCandidate(preferredSources) ?: return null
    val source = preferred.providerRef.source.toScraperSourceOrNull() ?: return null
    return ScraperResult(
        animeId = preferred.providerRef.id,
        title = preferred.title,
        titleCn = preferred.localizedTitle,
        matchedTitle = preferred.matchedQuery.ifBlank { displayTitle() },
        confidence = preferred.providerScore ?: rerankScore,
        source = source,
        fromLocalArchive = preferred.fromLocalCache,
    )
}

fun AggregatedMetadataCandidate.toPreferredDramaSearchResult(
    preferredSources: List<String> = listOf("TMDB"),
): DramaMetadataSearchResult? {
    val preferred = preferredProviderCandidate(preferredSources) ?: return null
    val tmdbId = preferred.providerRef.id.toIntOrNull()
        ?.takeIf { preferred.providerRef.source.equals("TMDB", ignoreCase = true) }
    return DramaMetadataSearchResult(
        tmdbId = tmdbId,
        title = preferred.title,
        originalTitle = preferred.originalTitle,
        summary = preferred.summary,
        firstAirDate = preferred.firstAirDate,
        posterUrl = preferred.posterUrl,
        fanartUrl = preferred.fanartUrl,
        providerRef = preferred.providerRef,
        sourceLabels = providerSourceLabels(),
    )
}

fun AggregatedMetadataCandidate.toDramaSearchResult(): DramaMetadataSearchResult? {
    val representative = representativeDramaProviderCandidate() ?: return null
    val tmdbId = representative.providerRef.id.toIntOrNull()
        ?.takeIf { representative.providerRef.source.equals("TMDB", ignoreCase = true) }
    val resolvedTitle = representative.displayTitle()
    val resolvedOriginalTitle = listOfNotNull(
        originalTitle.takeIf { it.isNotBlank() },
        representative.originalTitle.takeIf { it.isNotBlank() },
        title.takeIf { it.isNotBlank() && !it.equals(resolvedTitle, ignoreCase = true) },
    ).firstOrNull { !it.equals(resolvedTitle, ignoreCase = true) }.orEmpty()
    return DramaMetadataSearchResult(
        tmdbId = tmdbId,
        title = resolvedTitle,
        originalTitle = resolvedOriginalTitle,
        summary = summary,
        firstAirDate = firstAirDate,
        posterUrl = posterUrl,
        fanartUrl = fanartUrl,
        providerRef = representative.providerRef,
        sourceLabels = providerSourceLabels(),
    )
}

fun AggregatedMetadataCandidate.representativeDramaProviderCandidate(): MetadataSearchProviderCandidate? {
    if (providerCandidates.isEmpty()) return null
    val aggregatedDisplayTitle = displayTitle().trim()
    val aggregatedOriginalTitle = originalTitle.trim()
    return providerCandidates.maxWithOrNull(
        compareByDescending<MetadataSearchProviderCandidate> {
            it.representativeDisplayScore(aggregatedDisplayTitle)
        }.thenByDescending {
            it.representativeDisplayScore(aggregatedOriginalTitle)
        }.thenByDescending {
            it.representativeCompletenessScore()
        }.thenByDescending {
            it.providerScore ?: 0f
        }.thenBy {
            it.providerRank ?: Int.MAX_VALUE
        },
    ) ?: providerCandidates.first()
}

private fun MetadataSearchProviderCandidate.representativeDisplayScore(
    targetTitle: String,
): Float {
    val normalizedTarget = targetTitle.trim()
    if (normalizedTarget.isBlank()) return 0f
    val displayTitle = displayTitle().trim()
    return when {
        displayTitle.equals(normalizedTarget, ignoreCase = true) -> 1f
        displayTitle.contains(normalizedTarget, ignoreCase = true) ||
            normalizedTarget.contains(displayTitle, ignoreCase = true) -> 0.65f
        else -> 0f
    }
}

private fun MetadataSearchProviderCandidate.representativeCompletenessScore(): Float {
    val available = listOf(
        title.isNotBlank(),
        localizedTitle?.isNotBlank() == true,
        originalTitle.isNotBlank(),
        summary.isNotBlank(),
        posterUrl != null,
        firstAirDate != null,
        seasonCount != null,
        episodeCount != null,
    )
    return available.count { it }.toFloat() / available.size.toFloat()
}

private fun MetadataSearchProviderCandidate.candidateTitles(): List<String> =
    buildList {
        add(title)
        localizedTitle?.let(::add)
        add(originalTitle)
        addAll(aliases)
        matchedQuery.takeIf { it.isNotBlank() }?.let(::add)
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun String.toScraperSourceOrNull(): ScraperSource? =
    when {
        equals("Bangumi", ignoreCase = true) -> ScraperSource.BANGUMI
        equals("AniList", ignoreCase = true) -> ScraperSource.ANILIST
        else -> null
    }
