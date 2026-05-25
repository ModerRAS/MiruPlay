package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ScraperResult

const val METADATA_ALIAS_CONFIDENCE_THRESHOLD = 0.62f

suspend fun MetadataScraper.searchPreferredResults(
    query: String,
    candidates: List<String>,
    confidenceThreshold: Float = METADATA_ALIAS_CONFIDENCE_THRESHOLD,
): Result<List<ScraperResult>> =
    when (val directResults = searchAnime(query)) {
        is Result.Error -> directResults
        is Result.Success -> Result.success(
            preferredMetadataResults(
                directResults = directResults.data,
                aliasMatch = if ((directResults.data.firstOrNull()?.confidence ?: 0f) < confidenceThreshold) {
                    searchByAlias(
                        normalizedName = "",
                        candidates = candidates.excludingQuery(query),
                    ).getOrNull()?.takeIf { it.confidence >= confidenceThreshold }
                } else {
                    null
                },
            )
        )
    }

private fun preferredMetadataResults(
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

private fun List<String>.excludingQuery(query: String): List<String> {
    val normalizedQuery = query.trim()
    return map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it == normalizedQuery }
        .distinct()
}
