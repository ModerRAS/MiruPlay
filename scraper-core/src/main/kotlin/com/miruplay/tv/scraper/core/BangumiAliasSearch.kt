package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.scraper.METADATA_ALIAS_CONFIDENCE_THRESHOLD

const val BANGUMI_ALIAS_CONFIDENCE_THRESHOLD = METADATA_ALIAS_CONFIDENCE_THRESHOLD

suspend fun BangumiApiClient.searchByAlias(
    normalizedName: String,
    candidates: List<String>,
    confidenceThreshold: Float = BANGUMI_ALIAS_CONFIDENCE_THRESHOLD,
): Result<ScraperResult?> {
    val seasonSpecificCandidates = candidates
        .map { it.trim() }
        .filter { it.isNotBlank() && it.hasSeasonQualifier() }
        .sortedByDescending { it.length }

    val fallbackCandidates = (listOf(normalizedName) + candidates)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.hasSeasonQualifier() }

    val uniqueCandidates = (seasonSpecificCandidates + fallbackCandidates).distinct()

    val matches = linkedMapOf<String, ScraperResult>()
    for (candidate in uniqueCandidates) {
        val result = searchAnime(candidate).getOrNull().orEmpty()
            .firstOrNull { it.confidence >= confidenceThreshold }
        if (result != null) {
            val existing = matches[result.animeId]
            if (existing == null || result.confidence > existing.confidence) {
                matches[result.animeId] = result
            }
        }
    }
    return Result.success(matches.values.maxByOrNull { it.confidence })
}

private fun String.hasSeasonQualifier(): Boolean =
    seasonQualifierRegex.containsMatchIn(this)

private val seasonQualifierRegex = Regex(
    pattern = """(?i)(\bs\d{1,2}\b|\bseason\s*\d{1,2}\b|\b\d{1,2}(st|nd|rd|th)?\s+season\b|第\s*[0-9一二三四五六七八九十百]+\s*[季期])"""
)
