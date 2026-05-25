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
    val uniqueCandidates = (listOf(normalizedName) + candidates)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    for (candidate in uniqueCandidates) {
        val result = searchAnime(candidate).getOrNull()?.firstOrNull()
        if (result != null && result.confidence >= confidenceThreshold) {
            return Result.success(result)
        }
    }
    return Result.success(null)
}
