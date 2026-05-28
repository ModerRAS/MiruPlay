package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.repository.BangumiEpisodeMetadata

/**
 * Pluggable metadata scraper contract shared by Android TV and Windows.
 */
interface MetadataScraper {
    /**
     * Source name (e.g., "AniList", "BangumiArchive").
     */
    val sourceName: String

    /**
     * Search for anime by query.
     */
    suspend fun searchAnime(query: String): Result<List<ScraperResult>>

    /**
     * Get full anime details by ID.
     */
    suspend fun getAnimeDetails(animeId: String): Result<Anime>

    /**
     * Get episode list for anime.
     */
    suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>>

    /**
     * Search by alias with multiple candidate titles.
     */
    suspend fun searchByAlias(
        normalizedName: String,
        candidates: List<String>,
    ): Result<ScraperResult?>
}

interface MetadataImageBackfillScraper {
    suspend fun getImageDetails(animeId: String): Result<Anime>
}

/**
 * Episode metadata from scraper.
 */
typealias EpisodeMetadata = BangumiEpisodeMetadata
