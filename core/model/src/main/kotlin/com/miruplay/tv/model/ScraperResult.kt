package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScraperSource { ANILIST, BANGUMI }

@Serializable
data class ScraperResult(
    val animeId: String,
    val title: String,
    val titleCn: String? = null,
    val matchedTitle: String,
    val confidence: Float,  // 0-1
    val source: ScraperSource,
)
