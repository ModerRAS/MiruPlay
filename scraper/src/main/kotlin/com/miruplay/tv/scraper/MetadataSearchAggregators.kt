package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.AggregatedMetadataSearchResult
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataQueryPlan
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchIntent
import com.miruplay.tv.model.MetadataSearchProviderCandidate
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.repository.AnimeMetadataSearchAggregator
import com.miruplay.tv.repository.AnimeMetadataSearchProvider
import com.miruplay.tv.repository.DramaMetadataSearchAggregator
import com.miruplay.tv.repository.DramaMetadataSearchProvider
import com.miruplay.tv.repository.MetadataQueryPlanner
import com.miruplay.tv.repository.MetadataSearchAggregationSupport
import com.miruplay.tv.repository.metadataProviderRefHintText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BangumiAnimeMetadataSearchProvider(
    private val scraper: MetadataScraper,
    private val manualSearch: ManualMetadataSearchScraper,
) : AnimeMetadataSearchProvider {
    @Inject constructor(scraper: BangumiScraper) : this(scraper, scraper)

    override val sourceName: String = scraper.sourceName
    override val priority: Float = 1.06f

    override suspend fun search(
        context: MetadataSearchContext,
        plan: MetadataQueryPlan,
    ): List<MetadataSearchProviderCandidate> =
        hintedCandidatesForAnimeProvider(scraper, sourceName, plan) +
            searchScraperResults(
                scraper = scraper,
                plan = plan,
                directSearch = { query -> manualSearch.searchManualAnime(query) },
                allowAliasFallback = context.intent != MetadataSearchIntent.MANUAL_MATCH,
            )
}

@Singleton
class AniListAnimeMetadataSearchProvider @Inject constructor(
    private val scraper: AniListScraper,
) : AnimeMetadataSearchProvider {
    override val sourceName: String = scraper.sourceName
    override val priority: Float = 1.0f

    override suspend fun search(
        context: MetadataSearchContext,
        plan: MetadataQueryPlan,
    ): List<MetadataSearchProviderCandidate> =
        hintedCandidatesForAnimeProvider(scraper, sourceName, plan) +
            searchScraperResults(
                scraper = scraper,
                plan = plan,
                directSearch = scraper::searchAnime,
                allowAliasFallback = context.intent != MetadataSearchIntent.MANUAL_MATCH,
            )
}

@Singleton
class TmdbDramaMetadataSearchProvider @Inject constructor(
    private val repository: TmdbDramaMetadataRepository,
) : DramaMetadataSearchProvider {
    override val sourceName: String = "TMDB"
    override val priority: Float = 1.02f

    override suspend fun search(
        context: MetadataSearchContext,
        plan: MetadataQueryPlan,
    ): List<MetadataSearchProviderCandidate> {
        val seasonHint = plan.seasonHint ?: context.seasonHint
        val seasonNumbers = listOfNotNull(seasonHint)
        val candidates = mutableListOf<MetadataSearchProviderCandidate>()

        plan.providerRefHints
            .filter { it.source.equals(sourceName, ignoreCase = true) }
            .forEach { providerRef ->
                repository.fetchSeriesMetadataByProviderRef(
                    providerRef = providerRef,
                    seasonNumbers = seasonNumbers,
                ).getOrNull()?.let { metadata ->
                    candidates += metadata.toProviderCandidate(providerRef)
                }
            }

        plan.queryTexts.forEach { query ->
            when (val result = repository.searchSeriesCandidates(query, seasonHint = seasonHint)) {
                is Result.Error -> Unit
                is Result.Success -> {
                    result.data.forEachIndexed { index, item ->
                        candidates += MetadataSearchProviderCandidate(
                            providerRef = item.providerRef,
                            title = item.title,
                            localizedTitle = item.title.takeIf(::containsCjk),
                            originalTitle = item.originalTitle,
                            aliases = listOfNotNull(item.title, item.originalTitle.takeIf { it.isNotBlank() }).distinct(),
                            matchedQuery = query,
                            providerRank = index,
                            summary = item.summary,
                            posterUrl = item.posterUrl,
                            fanartUrl = item.fanartUrl,
                            firstAirDate = item.firstAirDate,
                        )
                    }
                }
            }
        }
        return candidates
    }
}

@Singleton
class DefaultAnimeMetadataSearchAggregator @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards AnimeMetadataSearchProvider>,
) : AnimeMetadataSearchAggregator {
    override suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult {
        val plan = MetadataQueryPlanner.plan(context)
        if (plan.queries.isEmpty() && plan.providerRefHints.isEmpty()) {
            return AggregatedMetadataSearchResult(plan = plan, candidates = emptyList())
        }
        val availableProviders = providers.sortedByDescending(AnimeMetadataSearchProvider::priority)
        val providerResults = coroutineScope {
            availableProviders.map { provider ->
                async {
                    provider.search(context, plan)
                }
            }.awaitAll().flatten()
        }
        return MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = providerResults,
            providerPriorities = availableProviders.associate { it.sourceName.lowercase() to it.priority },
        )
    }
}

@Singleton
class DefaultDramaMetadataSearchAggregator @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards DramaMetadataSearchProvider>,
) : DramaMetadataSearchAggregator {
    override suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult {
        val plan = MetadataQueryPlanner.plan(context)
        if (plan.queries.isEmpty() && plan.providerRefHints.isEmpty()) {
            return AggregatedMetadataSearchResult(plan = plan, candidates = emptyList())
        }
        val availableProviders = providers.sortedByDescending(DramaMetadataSearchProvider::priority)
        val providerResults = coroutineScope {
            availableProviders.map { provider ->
                async {
                    provider.search(context, plan)
                }
            }.awaitAll().flatten()
        }
        return MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = providerResults,
            providerPriorities = availableProviders.associate { it.sourceName.lowercase() to it.priority },
        )
    }
}

private suspend fun hintedCandidatesForAnimeProvider(
    scraper: MetadataScraper,
    sourceName: String,
    plan: MetadataQueryPlan,
): List<MetadataSearchProviderCandidate> =
    plan.providerRefHints
        .filter { it.source.equals(sourceName, ignoreCase = true) }
        .mapNotNull { providerRef ->
            scraper.getAnimeDetails(providerRef.id)
                .getOrNull()
                ?.toProviderCandidate(providerRef)
        }

private suspend fun searchScraperResults(
    scraper: MetadataScraper,
    plan: MetadataQueryPlan,
    directSearch: suspend (String) -> Result<List<ScraperResult>>,
    allowAliasFallback: Boolean = true,
): List<MetadataSearchProviderCandidate> {
    val candidates = mutableListOf<MetadataSearchProviderCandidate>()
    plan.queryTexts.forEach { query ->
        when (val result = scraper.searchPreferredWith(directSearch, query, plan.queryTexts, allowAliasFallback)) {
            is Result.Error -> Unit
            is Result.Success -> {
                result.data.forEachIndexed { index, item ->
                    candidates += MetadataSearchProviderCandidate(
                        providerRef = MetadataProviderRef(source = scraper.sourceName, id = item.animeId),
                        title = item.title,
                        localizedTitle = item.titleCn,
                        originalTitle = item.title.takeIf { item.titleCn?.isNotBlank() == true && item.title != item.titleCn }.orEmpty(),
                        aliases = listOfNotNull(item.title, item.titleCn, item.matchedTitle.takeIf { it.isNotBlank() }).distinct(),
                        matchedQuery = query,
                        providerScore = item.confidence,
                        providerRank = index,
                        fromLocalCache = item.fromLocalArchive,
                    )
                }
            }
        }
    }
    return candidates
}

private suspend fun MetadataScraper.searchPreferredWith(
    directSearch: suspend (String) -> Result<List<ScraperResult>>,
    query: String,
    candidates: List<String>,
    allowAliasFallback: Boolean,
): Result<List<ScraperResult>> =
    when (val directResults = directSearch(query)) {
        is Result.Error -> directResults
        is Result.Success -> {
            val aliasMatch = if (allowAliasFallback && (directResults.data.firstOrNull()?.confidence ?: 0f) < METADATA_ALIAS_CONFIDENCE_THRESHOLD) {
                searchByAlias(
                    normalizedName = "",
                    candidates = candidates.excludeQuery(query),
                ).getOrNull()?.takeIf { it.confidence >= METADATA_ALIAS_CONFIDENCE_THRESHOLD }
            } else {
                null
            }
            Result.success(preferredScraperResults(directResults.data, aliasMatch))
        }
    }

private fun preferredScraperResults(
    directResults: List<ScraperResult>,
    aliasMatch: ScraperResult?,
): List<ScraperResult> {
    val preferred = aliasMatch ?: return directResults
    val topConfidence = directResults.firstOrNull()?.confidence ?: 0f
    if (preferred.confidence <= topConfidence) {
        return directResults
    }
    return listOf(preferred) + directResults.filterNot { it.animeId == preferred.animeId }
}

private fun List<String>.excludeQuery(query: String): List<String> {
    val normalizedQuery = query.trim()
    return map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it == normalizedQuery }
        .distinct()
}

private fun Anime.toProviderCandidate(
    providerRef: MetadataProviderRef,
): MetadataSearchProviderCandidate = MetadataSearchProviderCandidate(
    providerRef = providerRef,
    title = title,
    localizedTitle = titleCn,
    originalTitle = title.takeIf { titleCn?.isNotBlank() == true && title != titleCn }.orEmpty(),
    aliases = listOfNotNull(title, titleCn).distinct(),
    matchedQuery = metadataProviderRefHintText(providerRef),
    providerScore = 1f,
    providerRank = 0,
    summary = summary,
    posterUrl = posterUrl,
    fanartUrl = fanartUrl,
    firstAirDate = airDate,
    episodeCount = episodeCount.takeIf { it > 0 },
)

private fun DramaSeriesMetadata.toProviderCandidate(
    providerRef: MetadataProviderRef,
): MetadataSearchProviderCandidate = MetadataSearchProviderCandidate(
    providerRef = providerRef,
    title = series.title,
    localizedTitle = series.title.takeIf(::containsCjk),
    originalTitle = series.originalTitle,
    aliases = listOfNotNull(series.title, series.originalTitle.takeIf { it.isNotBlank() }).distinct(),
    matchedQuery = metadataProviderRefHintText(providerRef),
    providerScore = 1f,
    providerRank = 0,
    summary = series.summary,
    posterUrl = series.posterUrl,
    fanartUrl = series.fanartUrl,
    firstAirDate = series.firstAirDate,
    seasonCount = series.seasonCount.takeIf { it > 0 },
    episodeCount = series.episodeCount.takeIf { it > 0 },
)

private fun containsCjk(text: String): Boolean =
    text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
