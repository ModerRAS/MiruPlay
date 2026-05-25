package com.miruplay.tv.sync

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.detailBangumiDetailsFailedMessage
import com.miruplay.tv.model.detailBangumiNoReliableMatchMessage
import com.miruplay.tv.model.metadataApplyBangumiRequiredMessage
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.metadataQuery
import com.miruplay.tv.scraper.EpisodeMetadata
import com.miruplay.tv.scraper.METADATA_ALIAS_CONFIDENCE_THRESHOLD
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.scraper.searchPreferredResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BangumiMetadataRefreshResult(
    val cacheAnimeId: String,
    val match: ScraperResult,
    val details: Anime,
    val episodes: List<Episode>,
)

private data class RemoteBangumiMetadata(
    val details: Anime,
    val episodes: List<EpisodeMetadata>,
)

fun MediaIndexEntry.bangumiMetadataCacheId(): String =
    metadataId?.takeIf { it.isNotBlank() }
        ?: metadataTitle?.takeIf { it.isNotBlank() }
        ?: animeName?.takeIf { it.isNotBlank() }
        ?: metadataQuery()?.takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').substringAfterLast('\\')

fun MediaIndexEntry.toBangumiLocalEpisode(animeId: String): Episode =
    Episode(
        id = path,
        animeId = animeId,
        seasonNumber = seasonNumber ?: 1,
        episodeNumber = episodeNumber ?: 1,
        title = episodeTitle.orEmpty(),
        filePath = path,
        fileName = MediaPathConventions.fileName(path),
    )

class BangumiMetadataRefreshCore(
    private val metadataRepository: MetadataRepository,
    private val bangumiScraper: MetadataScraper,
) {
    suspend fun cacheMatchedIndexMetadata(
        entry: MediaIndexEntry,
        relatedEntries: List<MediaIndexEntry>,
        match: ScraperResult,
    ): Result<BangumiMetadataRefreshResult> {
        val animeId = entry.bangumiMetadataCacheId()
        return cacheMatchedMetadata(
            cacheAnimeId = animeId,
            match = match,
            localEpisodes = relatedEntries.localBangumiEpisodes(entry, animeId),
        )
    }

    suspend fun ensureCachedIndexMetadata(
        entry: MediaIndexEntry,
        relatedEntries: List<MediaIndexEntry>,
    ): Result<String> {
        val animeId = entry.bangumiMetadataCacheId()
        val cached = metadataRepository.getCachedMetadata(animeId).getOrNull()
        val cachedEpisodes = metadataRepository.getCachedEpisodes(animeId).getOrNull()
        if (cached?.bangumiId != null && cachedEpisodes.orEmpty().any { it.bangumiEpisodeId != null }) {
            return Result.success(animeId)
        }

        val metadataId = entry.metadataId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(
                AppError.ScrapingError.ApiError("Bangumi", metadataApplyBangumiRequiredMessage())
            )
        return cacheBangumiMetadata(
            cacheAnimeId = animeId,
            bangumiAnimeId = metadataId,
            localEpisodes = relatedEntries.localBangumiEpisodes(entry, animeId),
        ).map { animeId }
    }

    suspend fun refresh(
        cacheAnimeId: String,
        query: String,
        candidates: List<String>,
        localEpisodes: List<Episode>,
        minimumConfidence: Float = METADATA_ALIAS_CONFIDENCE_THRESHOLD,
    ): Result<BangumiMetadataRefreshResult> = withContext(Dispatchers.IO) {
        val normalizedCandidates = candidates.normalizedCandidates(query)
        val match = bangumiScraper.searchPreferredResults(
            query = query,
            candidates = normalizedCandidates,
            confidenceThreshold = minimumConfidence,
        ).getOrNull()
            ?.firstOrNull { it.confidence >= minimumConfidence }
            ?: return@withContext Result.failure(
                AppError.ScrapingError.ApiError("Bangumi", detailBangumiNoReliableMatchMessage())
            )

        cacheMatchedMetadata(
            cacheAnimeId = cacheAnimeId,
            match = match,
            localEpisodes = localEpisodes,
            detailsFailureMessage = detailBangumiDetailsFailedMessage(),
        )
    }

    suspend fun cacheMatchedMetadata(
        cacheAnimeId: String,
        match: ScraperResult,
        localEpisodes: List<Episode>,
    ): Result<BangumiMetadataRefreshResult> = withContext(Dispatchers.IO) {
        cacheMatchedMetadata(
            cacheAnimeId = cacheAnimeId,
            match = match,
            localEpisodes = localEpisodes,
            detailsFailureMessage = null,
        )
    }

    suspend fun cacheBangumiMetadata(
        cacheAnimeId: String,
        bangumiAnimeId: String,
        localEpisodes: List<Episode>,
    ): Result<List<Episode>> = withContext(Dispatchers.IO) {
        val remote = when (val result = fetchRemoteMetadata(bangumiAnimeId, detailsFailureMessage = null)) {
            is Result.Error -> return@withContext result
            is Result.Success -> result.data
        }
        cacheResolvedMetadata(
            cacheAnimeId = cacheAnimeId,
            details = remote.details,
            localEpisodes = localEpisodes,
            remoteEpisodes = remote.episodes,
        )
    }

    suspend fun cacheResolvedMetadata(
        cacheAnimeId: String,
        details: Anime,
        localEpisodes: List<Episode>,
        remoteEpisodes: List<EpisodeMetadata>,
    ): Result<List<Episode>> = withContext(Dispatchers.IO) {
        val episodes = mergeEpisodes(
            cacheAnimeId = cacheAnimeId,
            localEpisodes = localEpisodes,
            remoteEpisodes = remoteEpisodes,
        )
        when (
            val cachedMetadata = metadataRepository.cacheMetadata(
                details.copy(
                    id = cacheAnimeId,
                    episodeCount = maxOf(details.episodeCount, episodes.size.coerceAtLeast(1)),
                )
            )
        ) {
            is Result.Error -> return@withContext cachedMetadata
            is Result.Success -> Unit
        }
        when (val cachedEpisodes = metadataRepository.cacheEpisodes(cacheAnimeId, episodes)) {
            is Result.Error -> cachedEpisodes
            is Result.Success -> Result.success(episodes)
        }
    }

    fun mergeEpisodes(
        cacheAnimeId: String,
        localEpisodes: List<Episode>,
        remoteEpisodes: List<EpisodeMetadata>,
    ): List<Episode> {
        val remoteByNumber = remoteEpisodes.associateBy { it.episodeNumber }
        return localEpisodes.map { episode ->
            val remote = remoteByNumber[episode.episodeNumber] ?: return@map episode.copy(animeId = cacheAnimeId)
            episode.copy(
                animeId = cacheAnimeId,
                title = remote.title ?: episode.title,
                duration = episode.duration.takeIf { it > 0 } ?: remote.durationMs,
                thumbnailPath = remote.thumbnailUrl ?: episode.thumbnailPath,
                bangumiEpisodeId = remote.bangumiEpisodeId ?: episode.bangumiEpisodeId,
            )
        }
    }

    private suspend fun cacheMatchedMetadata(
        cacheAnimeId: String,
        match: ScraperResult,
        localEpisodes: List<Episode>,
        detailsFailureMessage: String?,
    ): Result<BangumiMetadataRefreshResult> {
        val remote = when (val result = fetchRemoteMetadata(match.animeId, detailsFailureMessage)) {
            is Result.Error -> return result
            is Result.Success -> result.data
        }
        return cacheResolvedMetadata(
            cacheAnimeId = cacheAnimeId,
            details = remote.details,
            localEpisodes = localEpisodes,
            remoteEpisodes = remote.episodes,
        ).map { episodes ->
            BangumiMetadataRefreshResult(
                cacheAnimeId = cacheAnimeId,
                match = match,
                details = remote.details.copy(
                    id = cacheAnimeId,
                    episodeCount = maxOf(remote.details.episodeCount, episodes.size.coerceAtLeast(1)),
                ),
                episodes = episodes,
            )
        }
    }

    private suspend fun fetchRemoteMetadata(
        bangumiAnimeId: String,
        detailsFailureMessage: String?,
    ): Result<RemoteBangumiMetadata> {
        val details = when (val detailsResult = bangumiScraper.getAnimeDetails(bangumiAnimeId)) {
            is Result.Success -> detailsResult.data
            is Result.Error -> return detailsFailureMessage?.let { message ->
                Result.failure(AppError.ScrapingError.ApiError("Bangumi", message))
            } ?: detailsResult
        }
        val remoteEpisodes = bangumiScraper.getEpisodes(bangumiAnimeId).getOrNull().orEmpty()
        return Result.success(RemoteBangumiMetadata(details = details, episodes = remoteEpisodes))
    }
}

private fun List<String>.normalizedCandidates(query: String): List<String> =
    (listOf(query) + this)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

private fun List<MediaIndexEntry>.localBangumiEpisodes(
    fallbackEntry: MediaIndexEntry,
    animeId: String,
): List<Episode> =
    ifEmpty { listOf(fallbackEntry) }
        .map { it.toBangumiLocalEpisode(animeId = animeId) }
