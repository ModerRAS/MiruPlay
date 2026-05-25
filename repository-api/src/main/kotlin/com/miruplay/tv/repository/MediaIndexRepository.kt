package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import kotlinx.serialization.Serializable

@Serializable
enum class MediaScrapeStatus {
    PENDING,
    SCRAPING,
    SCRAPED,
    NO_MATCH,
    FAILED,
}

interface MediaIndexRepository {
    suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit>
    suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit>
    suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>>
    suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>>
    suspend fun clearIndex(sourceId: Long): Result<Unit>
    suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit>
    suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>>
    suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit>
}

@Serializable
data class MediaIndexEntry(
    val sourceId: Long,
    val path: String,
    val animeName: String? = null,
    val episodeTitle: String? = null,
    val plot: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val metadataSource: String? = null,
    val metadataId: String? = null,
    val metadataTitle: String? = null,
    val scrapeStatus: MediaScrapeStatus? = null,
    val scrapeMessage: String? = null,
    val scrapedAt: Long = 0L,
    val isDirectory: Boolean = false,
    val fileSize: Long = 0L,
    val lastModified: Long = 0L,
)
