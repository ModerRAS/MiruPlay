package com.miruplay.tv.scraper

import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataQueryPlan
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchProviderCandidate
import com.miruplay.tv.repository.DramaMetadataSearchProvider
import com.miruplay.tv.repository.metadataProviderRefHintText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvMazeDramaMetadataSearchProvider @Inject constructor(
    private val repository: TvMazeDramaMetadataRepository,
) : DramaMetadataSearchProvider {
    override val sourceName: String = "TVMaze"
    override val priority: Float = 0.98f

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
                is com.miruplay.tv.core.common.Result.Error -> Unit
                is com.miruplay.tv.core.common.Result.Success -> {
                    val maxScore = result.data.maxOfOrNull { itScore(it) } ?: 0f
                    result.data.forEachIndexed { index, item ->
                        candidates += MetadataSearchProviderCandidate(
                            providerRef = item.providerRef,
                            title = item.title,
                            localizedTitle = item.title.takeIf(::containsCjk),
                            originalTitle = item.originalTitle,
                            aliases = listOfNotNull(item.title, item.originalTitle.takeIf { it.isNotBlank() }).distinct(),
                            matchedQuery = query,
                            providerScore = itScore(item)
                                .takeIf { it > 0f && maxScore > 0f }
                                ?.let { score -> (score / maxScore).coerceIn(0f, 1f) },
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

    private fun itScore(item: com.miruplay.tv.model.DramaMetadataSearchResult): Float =
        buildList<Float> {
            if (item.posterUrl != null) add(0.15f)
            if (item.summary.isNotBlank()) add(0.1f)
            if (item.firstAirDate != null) add(0.05f)
        }.sum()

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
}

private fun containsCjk(text: String): Boolean =
    text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
