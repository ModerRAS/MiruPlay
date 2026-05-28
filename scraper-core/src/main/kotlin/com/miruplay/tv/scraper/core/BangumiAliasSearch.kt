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

    for (candidate in uniqueCandidates) {
        val result = searchAnime(candidate).getOrNull()?.firstOrNull()
        if (result != null && result.confidence >= confidenceThreshold) {
            return Result.success(result)
        }
    }
    return Result.success(null)
}

private fun String.hasSeasonQualifier(): Boolean =
    seasonQualifierRegex.containsMatchIn(this)

private val seasonQualifierRegex = Regex(
    pattern = """(?i)(\bs\d{1,2}\b|\bseason\s*\d{1,2}\b|\b\d{1,2}(st|nd|rd|th)?\s+season\b|第\s*[0-9一二三四五六七八九十百]+\s*[季期])"""
)
