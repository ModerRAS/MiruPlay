package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ScraperResult

/**
 * Pluggable metadata scraper interface
 */
interface MetadataScraper {
    /**
     * Source name (e.g., "AniList", "BangumiArchive")
     */
    val sourceName: String
    
    /**
     * Search for anime by query
     */
    suspend fun searchAnime(query: String): Result<List<ScraperResult>>
    
    /**
     * Get full anime details by ID
     */
    suspend fun getAnimeDetails(animeId: String): Result<Anime>
    
    /**
     * Get episode list for anime
     */
    suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>>
    
    /**
     * Search by alias with multiple candidate titles
     */
    suspend fun searchByAlias(
        normalizedName: String,
        candidates: List<String>
    ): Result<ScraperResult?>
}

/**
 * Episode metadata from scraper
 */
data class EpisodeMetadata(
    val episodeNumber: Int,
    val title: String? = null,
    val airDate: String? = null,
    val summary: String? = null,
    val thumbnailUrl: String? = null,
    val isSpecial: Boolean = false,
    val bangumiEpisodeId: Int? = null,
    val durationMs: Long = 0L,
    val collectionType: Int? = null
)
