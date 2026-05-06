package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ScraperResult

/**
 * Standalone alias resolver interface
 * Can be replaced independently of MetadataScraper
 */
interface AliasResolver {
    /**
     * Resolve normalized name to scraper result
     */
    suspend fun resolve(normalizedName: String): Result<ScraperResult?>
    
    /**
     * Bulk resolve multiple names
     */
    suspend fun bulkResolve(names: List<String>): Result<Map<String, ScraperResult?>>
}
