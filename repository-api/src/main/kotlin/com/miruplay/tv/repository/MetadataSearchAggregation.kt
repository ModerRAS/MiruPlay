package com.miruplay.tv.repository

import com.miruplay.tv.model.AggregatedMetadataCandidate
import com.miruplay.tv.model.AggregatedMetadataSearchResult
import com.miruplay.tv.model.MatchEvidence
import com.miruplay.tv.model.MatchRecommendation
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataQueryPlan
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchProviderCandidate
import com.miruplay.tv.model.MetadataSearchQuery
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.displayTitle
import kotlin.math.abs

interface AnimeMetadataSearchProvider {
    val sourceName: String
    val priority: Float
        get() = 1f

    suspend fun search(
        context: MetadataSearchContext,
        plan: MetadataQueryPlan,
    ): List<MetadataSearchProviderCandidate>
}

interface DramaMetadataSearchProvider {
    val sourceName: String
    val priority: Float
        get() = 1f

    suspend fun search(
        context: MetadataSearchContext,
        plan: MetadataQueryPlan,
    ): List<MetadataSearchProviderCandidate>
}

interface AnimeMetadataSearchAggregator {
    suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult
}

interface DramaMetadataSearchAggregator {
    suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult
}

object MetadataQueryPlanner {
    private const val MAX_QUERY_COUNT = 10

    fun plan(context: MetadataSearchContext): MetadataQueryPlan {
        val queries = linkedMapOf<String, MetadataSearchQuery>()
        val providerRefHints = linkedMapOf<String, MetadataProviderRef>()

        fun addProviderRefHint(raw: String?) {
            val ref = metadataParseProviderRefHint(raw) ?: return
            providerRefHints.putIfAbsent("${ref.source.lowercase()}:${ref.id}", ref)
        }

        fun addQuery(raw: String?, reason: String) {
            val text = raw?.trim().orEmpty()
            if (text.isBlank()) return
            if (metadataParseProviderRefHint(text) != null) {
                addProviderRefHint(text)
                return
            }
            queries.putIfAbsent(text.lowercase(), MetadataSearchQuery(text = text, reason = reason))
        }

        fun addSeasonlessVariant(raw: String?, reason: String) {
            val variant = raw?.stripMetadataSeasonSuffixes()?.trim().orEmpty()
            if (variant.isBlank() || variant.equals(raw?.trim(), ignoreCase = true)) return
            addQuery(variant, reason)
        }

        context.boundProviderRef?.let { ref ->
            providerRefHints.putIfAbsent("${ref.source.lowercase()}:${ref.id}", ref)
        }
        addQuery(context.manualQuery, "manual")
        addSeasonlessVariant(context.manualQuery, "manual-seasonless")
        addQuery(context.title, "title")
        addSeasonlessVariant(context.title, "title-seasonless")
        addQuery(context.localizedTitle, "localized")
        addQuery(context.originalTitle, "original")
        addSeasonlessVariant(context.originalTitle, "original-seasonless")
        context.aliases.forEach { alias ->
            addQuery(alias, "alias")
            addSeasonlessVariant(alias, "alias-seasonless")
        }
        addQuery(context.metadataTitle, "cached-metadata")
        addSeasonlessVariant(context.metadataTitle, "cached-metadata-seasonless")
        context.filePathSamples
            .flatMap(::metadataDerivePathQueries)
            .forEach { addQuery(it, "path") }

        val plannedQueries = queries.values.take(MAX_QUERY_COUNT)
        val seasonHint = context.seasonHint
            ?: plannedQueries.asSequence().mapNotNull { metadataExtractSeasonNumber(it.text) }.firstOrNull()
        val yearHint = context.yearHint
            ?: plannedQueries.asSequence().mapNotNull { metadataExtractYear(it.text) }.firstOrNull()
        return MetadataQueryPlan(
            queries = plannedQueries,
            seasonHint = seasonHint,
            yearHint = yearHint,
            providerRefHints = providerRefHints.values.toList(),
        )
    }
}

object MetadataSearchAggregationSupport {
    fun aggregate(
        context: MetadataSearchContext,
        plan: MetadataQueryPlan,
        candidates: List<MetadataSearchProviderCandidate>,
        providerPriorities: Map<String, Float>,
    ): AggregatedMetadataSearchResult {
        val deduped = dedupeWithinProvider(candidates)
        val clusters = MetadataCandidateClusterer.cluster(context, deduped)
        val reranked = MetadataCandidateReranker.rerank(
            context = context,
            plan = plan,
            clusters = clusters,
            providerPriorities = providerPriorities,
        )
        return AggregatedMetadataSearchResult(
            plan = plan,
            candidates = reranked,
        )
    }

    private fun dedupeWithinProvider(
        candidates: List<MetadataSearchProviderCandidate>,
    ): List<MetadataSearchProviderCandidate> =
        candidates
            .groupBy { it.providerRef.source.lowercase() to it.providerRef.id }
            .values
            .map { grouped -> grouped.reduce(::mergeProviderCandidates) }

    private fun mergeProviderCandidates(
        left: MetadataSearchProviderCandidate,
        right: MetadataSearchProviderCandidate,
    ): MetadataSearchProviderCandidate {
        val preferred = listOf(left, right).maxWithOrNull(
            compareBy<MetadataSearchProviderCandidate> { it.providerScore ?: 0f }
                .thenByDescending { -(it.providerRank ?: Int.MAX_VALUE) }
                .thenByDescending { candidateCompleteness(it) },
        ) ?: left
        val secondary = if (preferred === left) right else left
        return preferred.copy(
            localizedTitle = preferred.localizedTitle ?: secondary.localizedTitle,
            originalTitle = preferred.originalTitle.ifBlank { secondary.originalTitle },
            aliases = (preferred.allTitles() + secondary.allTitles()).distinct(),
            matchedQuery = preferred.matchedQuery.ifBlank { secondary.matchedQuery },
            providerScore = maxOf(preferred.providerScore ?: 0f, secondary.providerScore ?: 0f)
                .takeIf { it > 0f },
            providerRank = minOf(preferred.providerRank ?: Int.MAX_VALUE, secondary.providerRank ?: Int.MAX_VALUE)
                .takeIf { it != Int.MAX_VALUE },
            summary = preferred.summary.ifBlank { secondary.summary },
            posterUrl = preferred.posterUrl ?: secondary.posterUrl,
            fanartUrl = preferred.fanartUrl ?: secondary.fanartUrl,
            firstAirDate = preferred.firstAirDate ?: secondary.firstAirDate,
            seasonCount = preferred.seasonCount ?: secondary.seasonCount,
            episodeCount = preferred.episodeCount ?: secondary.episodeCount,
            fromLocalCache = preferred.fromLocalCache || secondary.fromLocalCache,
        )
    }
}

object MetadataCandidateClusterer {
    fun cluster(
        context: MetadataSearchContext,
        candidates: List<MetadataSearchProviderCandidate>,
    ): List<List<MetadataSearchProviderCandidate>> {
        if (candidates.isEmpty()) return emptyList()
        val orderedCandidates = candidates.sortedWith(
            compareByDescending<MetadataSearchProviderCandidate> { it.providerScore ?: 0f }
                .thenBy { it.providerRank ?: Int.MAX_VALUE }
                .thenByDescending { candidateCompleteness(it) },
        )
        val clusters = mutableListOf<MutableList<MetadataSearchProviderCandidate>>()
        orderedCandidates.forEach { candidate ->
            val cluster = clusters.firstOrNull { existing -> shouldCluster(context, existing, candidate) }
            if (cluster != null) {
                cluster += candidate
            } else {
                clusters += mutableListOf(candidate)
            }
        }
        return clusters.map { it.toList() }
    }

    private fun shouldCluster(
        context: MetadataSearchContext,
        cluster: List<MetadataSearchProviderCandidate>,
        candidate: MetadataSearchProviderCandidate,
    ): Boolean {
        val candidateSeason = candidate.detectedSeason()
        val candidateYear = metadataComparableYear(candidate.firstAirDate)
        return cluster.any { existing ->
            val similarity = existing.allTitles().maxOfOrNull { left ->
                candidate.allTitles().maxOfOrNull { right -> metadataTitleSimilarity(left, right) } ?: 0f
            } ?: 0f
            if (similarity < 0.66f) {
                return@any false
            }
            val existingSeason = existing.detectedSeason()
            if (
                candidateSeason != null &&
                existingSeason != null &&
                candidateSeason != existingSeason &&
                similarity < 0.95f
            ) {
                return@any false
            }
            val existingYear = metadataComparableYear(existing.firstAirDate)
            if (
                candidateYear != null &&
                existingYear != null &&
                abs(candidateYear - existingYear) >= 3
            ) {
                return@any false
            }
            if (
                context.seasonHint != null &&
                candidateSeason != null &&
                existingSeason != null &&
                candidateSeason != existingSeason &&
                similarity < 0.95f
            ) {
                return@any false
            }
            true
        }
    }
}

object MetadataCandidateReranker {
    fun rerank(
        context: MetadataSearchContext,
        plan: MetadataQueryPlan,
        clusters: List<List<MetadataSearchProviderCandidate>>,
        providerPriorities: Map<String, Float>,
    ): List<AggregatedMetadataCandidate> =
        clusters
            .map { cluster ->
                val titleScore = titleScore(cluster, plan.queryTexts)
                val seasonScore = seasonScore(cluster, plan.seasonHint)
                val yearScore = yearScore(cluster, plan.yearHint)
                val structureScore = structureScore(cluster, context)
                val bindingScore = bindingScore(cluster, context.boundProviderRef)
                val providerSignal = providerSignal(cluster)
                val completenessScore = cluster.maxOfOrNull(::candidateCompleteness) ?: 0f
                val sourceDiversityBoost = if (cluster.map { it.providerRef.source.lowercase() }.distinct().size > 1) 0.05f else 0f
                val providerPriorBoost = cluster.maxOfOrNull {
                    (((providerPriorities[it.providerRef.source.lowercase()] ?: 1f) - 1f) * 0.35f)
                        .coerceIn(0f, 0.05f)
                } ?: 0f

                val rerankScore = (
                    titleScore * 0.44f +
                        seasonScore * 0.14f +
                        yearScore * 0.08f +
                        structureScore * 0.14f +
                        bindingScore * 0.1f +
                        providerSignal * 0.06f +
                        completenessScore * 0.04f +
                        sourceDiversityBoost +
                        providerPriorBoost
                    ).coerceIn(0f, 1f)

                val recommendation = when {
                    rerankScore >= 0.88f -> MatchRecommendation.AUTO_ACCEPT
                    rerankScore >= 0.64f -> MatchRecommendation.REVIEW
                    else -> MatchRecommendation.LOW_CONFIDENCE
                }

                val displayCandidate = cluster.maxWithOrNull(
                    compareByDescending<MetadataSearchProviderCandidate> {
                        displayTitleMatchToQueries(it, plan.queryTexts)
                    }.thenByDescending {
                        titleMatchToQueries(it, plan.queryTexts)
                    }.thenByDescending {
                        candidateCompleteness(it)
                    }.thenByDescending {
                        it.providerScore ?: 0f
                    }.thenBy {
                        it.providerRank ?: Int.MAX_VALUE
                    },
                ) ?: cluster.first()

                AggregatedMetadataCandidate(
                    contentMode = context.contentMode,
                    title = displayCandidate.title,
                    localizedTitle = displayCandidate.localizedTitle?.takeIf(String::isNotBlank)
                        ?: cluster.firstNotNullOfOrNull { it.localizedTitle?.takeIf(String::isNotBlank) },
                    originalTitle = displayCandidate.originalTitle.ifBlank {
                        cluster.firstNotNullOfOrNull { it.originalTitle.takeIf(String::isNotBlank) }.orEmpty()
                    },
                    aliases = cluster.flatMap(MetadataSearchProviderCandidate::allTitles).distinct(),
                    summary = cluster.firstNotNullOfOrNull { it.summary.takeIf(String::isNotBlank) }.orEmpty(),
                    posterUrl = cluster.firstNotNullOfOrNull(MetadataSearchProviderCandidate::posterUrl),
                    fanartUrl = cluster.firstNotNullOfOrNull(MetadataSearchProviderCandidate::fanartUrl),
                    firstAirDate = cluster.firstNotNullOfOrNull(MetadataSearchProviderCandidate::firstAirDate),
                    seasonCount = cluster.firstNotNullOfOrNull(MetadataSearchProviderCandidate::seasonCount),
                    episodeCount = cluster.firstNotNullOfOrNull(MetadataSearchProviderCandidate::episodeCount),
                    providerCandidates = cluster.sortedWith(
                        compareBy<MetadataSearchProviderCandidate> { it.providerRank ?: Int.MAX_VALUE }
                            .thenByDescending { it.providerScore ?: 0f },
                    ),
                    rerankScore = rerankScore,
                    recommendation = recommendation,
                    evidence = buildEvidence(
                        titleScore = titleScore,
                        seasonScore = seasonScore,
                        yearScore = yearScore,
                        structureScore = structureScore,
                        bindingScore = bindingScore,
                        providerSignal = providerSignal,
                        sourceDiversityBoost = sourceDiversityBoost,
                        cluster = cluster,
                    ),
                )
            }
            .sortedWith(
                compareByDescending<AggregatedMetadataCandidate> { it.rerankScore }
                    .thenByDescending { it.providerCandidates.size }
                    .thenBy { it.providerCandidates.firstOrNull()?.providerRank ?: Int.MAX_VALUE },
            )

    private fun buildEvidence(
        titleScore: Float,
        seasonScore: Float,
        yearScore: Float,
        structureScore: Float,
        bindingScore: Float,
        providerSignal: Float,
        sourceDiversityBoost: Float,
        cluster: List<MetadataSearchProviderCandidate>,
    ): List<MatchEvidence> = buildList {
        if (titleScore >= 0.95f) add(MatchEvidence("标题完全命中", 0.22f))
        else if (titleScore >= 0.82f) add(MatchEvidence("标题高度接近", 0.16f))
        else if (titleScore >= 0.66f) add(MatchEvidence("标题存在明显相似度", 0.1f))

        if (seasonScore >= 0.95f) add(MatchEvidence("季号一致", 0.08f))
        else if (seasonScore in 0.01f..0.45f) add(MatchEvidence("季号存在冲突", -0.08f))

        if (yearScore >= 0.95f) add(MatchEvidence("年份接近", 0.05f))
        if (structureScore >= 0.9f) add(MatchEvidence("本地季集结构一致", 0.08f))
        else if (structureScore in 0.01f..0.45f) add(MatchEvidence("本地季集结构不一致", -0.06f))

        if (bindingScore > 0f) add(MatchEvidence("命中过往已绑定来源", 0.1f))
        if (providerSignal >= 0.8f) add(MatchEvidence("上游原始排序较高", 0.03f))
        if (cluster.any { it.posterUrl != null && it.summary.isNotBlank() }) add(MatchEvidence("候选信息较完整", 0.03f))
        if (sourceDiversityBoost > 0f) {
            add(MatchEvidence("多源结果收敛到同一候选", 0.05f))
        }
    }

    private fun titleScore(
        cluster: List<MetadataSearchProviderCandidate>,
        queries: List<String>,
    ): Float =
        cluster.maxOfOrNull { candidate -> titleMatchToQueries(candidate, queries) } ?: 0f

    private fun titleMatchToQueries(
        candidate: MetadataSearchProviderCandidate,
        queries: List<String>,
    ): Float =
        queries.maxOfOrNull { query ->
            candidate.allTitles().maxOfOrNull { title -> metadataTitleSimilarity(query, title) } ?: 0f
        } ?: 0f

    private fun displayTitleMatchToQueries(
        candidate: MetadataSearchProviderCandidate,
        queries: List<String>,
    ): Float =
        queries.maxOfOrNull { query -> metadataTitleSimilarity(query, candidate.displayTitle()) } ?: 0f

    private fun seasonScore(
        cluster: List<MetadataSearchProviderCandidate>,
        seasonHint: Int?,
    ): Float {
        val season = seasonHint ?: return 0f
        val candidateSeasons = cluster.mapNotNull(MetadataSearchProviderCandidate::detectedSeason)
        if (candidateSeasons.isEmpty()) return 0.55f
        return when {
            candidateSeasons.any { it == season } -> 1f
            else -> 0.25f
        }
    }

    private fun yearScore(
        cluster: List<MetadataSearchProviderCandidate>,
        yearHint: Int?,
    ): Float {
        val year = yearHint ?: return 0f
        val candidateYears = cluster.mapNotNull { metadataComparableYear(it.firstAirDate) }
        if (candidateYears.isEmpty()) return 0f
        val minDistance = candidateYears.minOf { abs(it - year) }
        return when {
            minDistance == 0 -> 1f
            minDistance == 1 -> 0.85f
            minDistance == 2 -> 0.65f
            else -> 0.2f
        }
    }

    private fun structureScore(
        cluster: List<MetadataSearchProviderCandidate>,
        context: MetadataSearchContext,
    ): Float {
        val episodeScore = matchCountHint(context.episodeCountHint, cluster.mapNotNull { it.episodeCount })
        val seasonScore = matchCountHint(context.seasonCountHint, cluster.mapNotNull { it.seasonCount })
        return listOfNotNull(episodeScore, seasonScore).averageOrNull() ?: 0f
    }

    private fun matchCountHint(hint: Int?, actualValues: List<Int>): Float? {
        if (hint == null || actualValues.isEmpty()) return null
        val distance = actualValues.minOf { abs(it - hint) }
        val ratio = distance.toFloat() / hint.coerceAtLeast(1).toFloat()
        return when {
            distance == 0 -> 1f
            ratio <= 0.1f -> 0.85f
            ratio <= 0.25f -> 0.65f
            else -> 0.25f
        }
    }

    private fun bindingScore(
        cluster: List<MetadataSearchProviderCandidate>,
        boundProviderRef: MetadataProviderRef?,
    ): Float =
        when {
            boundProviderRef == null -> 0f
            cluster.any { it.providerRef == boundProviderRef } -> 1f
            else -> 0f
        }

    private fun providerSignal(cluster: List<MetadataSearchProviderCandidate>): Float {
        val providerScore = cluster.maxOfOrNull { it.providerScore ?: 0f } ?: 0f
        val providerRank = cluster.minOfOrNull { it.providerRank ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
        val rankSignal = when {
            providerRank == Int.MAX_VALUE -> 0f
            providerRank <= 0 -> 1f
            providerRank == 1 -> 0.8f
            providerRank == 2 -> 0.65f
            providerRank <= 4 -> 0.5f
            else -> 0.3f
        }
        return maxOf(providerScore, rankSignal)
    }
}

private fun MetadataSearchProviderCandidate.allTitles(): List<String> =
    buildList {
        add(title)
        localizedTitle?.let(::add)
        add(originalTitle)
        addAll(aliases)
        matchedQuery.takeIf { it.isNotBlank() }?.let(::add)
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun MetadataSearchProviderCandidate.detectedSeason(): Int? =
    allTitles().asSequence().mapNotNull(::metadataExtractSeasonNumber).firstOrNull()

private fun candidateCompleteness(candidate: MetadataSearchProviderCandidate): Float {
    val available = listOf(
        candidate.localizedTitle?.isNotBlank() == true,
        candidate.originalTitle.isNotBlank(),
        candidate.summary.isNotBlank(),
        candidate.posterUrl != null,
        candidate.firstAirDate != null,
        candidate.seasonCount != null,
        candidate.episodeCount != null,
    )
    return available.count { it }.toFloat() / available.size.toFloat()
}

private fun List<Float>.averageOrNull(): Float? =
    takeIf { it.isNotEmpty() }?.average()?.toFloat()

private fun String.stripMetadataSeasonSuffixes(): String =
    replace(Regex("""(?i)\bseason\s*\d+\b"""), " ")
        .replace(Regex("""(?i)\bs\s*\d+\b"""), " ")
        .replace(Regex("""第\s*[一二三四五六七八九十\d]+\s*[季期]"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
