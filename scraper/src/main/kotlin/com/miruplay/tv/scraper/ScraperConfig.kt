package com.miruplay.tv.scraper

/**
 * Configuration for scraper behavior
 */
data class ScraperConfig(
    val timeout: Long = 30_000,        // ms
    val maxRetries: Int = 3,
    val cacheEnabled: Boolean = true,
    val cacheDuration: Long = 24 * 60 * 60 * 1000L  // 24 hours
)

/**
 * Scraper source types
 */
enum class ScraperSource {
    ANILIST,
    BANGUMI_ARCHIVE
}