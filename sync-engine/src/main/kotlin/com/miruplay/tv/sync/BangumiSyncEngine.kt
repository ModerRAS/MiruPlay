package com.miruplay.tv.sync

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.MetadataRepository
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.scraper.BangumiEpisodeCollectionType
import com.miruplay.tv.scraper.BangumiScraper
import com.miruplay.tv.scraper.BangumiSubjectCollectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class BangumiSyncSummary(
    val animeId: String,
    val subjectId: Int,
    val pushedEpisodes: Int,
    val pulledEpisodes: Int,
    val remoteWatchedEpisodes: Int,
    val subjectCollectionType: Int?
)

@Singleton
class BangumiSyncEngine @Inject constructor(
    private val bangumiScraper: BangumiScraper,
    private val metadataRepository: MetadataRepository,
    private val progressRepository: ProgressRepository
) {
    suspend fun syncAnime(animeId: String): Result<BangumiSyncSummary> = withContext(Dispatchers.IO) {
        try {
            if (!bangumiScraper.hasToken) {
                return@withContext Result.failure(AppError.ScrapingError.ApiError("Bangumi", "请先在设置里保存 Access Token"))
            }

            val anime = metadataRepository.getCachedMetadata(animeId).getOrNull()
                ?: return@withContext Result.failure(AppError.ScrapingError.NoMatchFound(animeId))
            val subjectId = anime.bangumiId
                ?: return@withContext Result.failure(AppError.ScrapingError.ApiError("Bangumi", "当前番剧还没有 Bangumi 条目 ID，请先重新刮削"))

            val episodes = metadataRepository.getCachedEpisodes(animeId).getOrNull().orEmpty()
            if (episodes.isEmpty()) {
                return@withContext Result.failure(AppError.ScrapingError.ApiError("Bangumi", "当前番剧没有可同步剧集"))
            }

            ensureSubjectCollection(subjectId)

            val remoteCollections = bangumiScraper.getEpisodeCollections(subjectId).getOrNull().orEmpty()
                .associateBy { it.episodeId }
            var pulled = 0
            val localWatchedRemoteIds = mutableListOf<Int>()
            val updatedEpisodes = episodes.map { episode ->
                val remoteId = episode.bangumiEpisodeId
                val remote = remoteId?.let { remoteCollections[it] }
                val progress = progressRepository.getProgress(episode.id).getOrNull()
                val localDone = isLocallyWatched(episode, progress?.positionMs, progress?.playCount ?: 0)

                if (remoteId != null && localDone) {
                    localWatchedRemoteIds += remoteId
                }

                if (remote?.type == BangumiEpisodeCollectionType.DONE.value && !localDone) {
                    val watchedPosition = episode.duration.takeIf { it > 0 } ?: progress?.positionMs ?: 0L
                    progressRepository.saveProgress(episode.id, watchedPosition, System.currentTimeMillis())
                    pulled += 1
                }

                episode.copy(bangumiCollectionType = remote?.type)
            }

            val remoteDoneIds = remoteCollections.values
                .filter { it.type == BangumiEpisodeCollectionType.DONE.value }
                .map { it.episodeId }
                .toSet()
            val toPush = localWatchedRemoteIds.distinct().filterNot { it in remoteDoneIds }
            if (toPush.isNotEmpty()) {
                bangumiScraper.updateEpisodeCollections(
                    subjectId = subjectId,
                    episodeIds = toPush,
                    type = BangumiEpisodeCollectionType.DONE
                )
            }

            val watchedCount = (remoteDoneIds + localWatchedRemoteIds).size
            val targetSubjectType = if (watchedCount >= episodes.count { it.bangumiEpisodeId != null }.coerceAtLeast(1)) {
                BangumiSubjectCollectionType.DONE
            } else {
                BangumiSubjectCollectionType.DOING
            }
            bangumiScraper.upsertSubjectCollection(subjectId, targetSubjectType)

            metadataRepository.cacheEpisodes(animeId, updatedEpisodes.map { episode ->
                if (episode.bangumiEpisodeId in (remoteDoneIds + localWatchedRemoteIds)) {
                    episode.copy(bangumiCollectionType = BangumiEpisodeCollectionType.DONE.value)
                } else {
                    episode
                }
            })
            metadataRepository.cacheMetadata(
                anime.copy(
                    bangumiCollectionType = targetSubjectType.value,
                    bangumiEpStatus = watchedCount
                )
            )

            Result.success(
                BangumiSyncSummary(
                    animeId = animeId,
                    subjectId = subjectId,
                    pushedEpisodes = toPush.size,
                    pulledEpisodes = pulled,
                    remoteWatchedEpisodes = watchedCount,
                    subjectCollectionType = targetSubjectType.value
                )
            )
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError("Bangumi", e.message ?: "同步失败"))
        }
    }

    suspend fun markEpisodeWatched(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!bangumiScraper.hasToken) return@withContext Result.success(Unit)
            val episode = metadataRepository.getCachedEpisode(episodeId).getOrNull()
                ?: return@withContext Result.success(Unit)
            val bangumiEpisodeId = episode.bangumiEpisodeId ?: return@withContext Result.success(Unit)

            val anime = metadataRepository.getCachedMetadata(episode.animeId).getOrNull()
                ?: return@withContext Result.success(Unit)
            val subjectId = anime.bangumiId
            if (subjectId != null) {
                ensureSubjectCollection(subjectId)
            }

            bangumiScraper.updateEpisodeCollection(
                episodeId = bangumiEpisodeId,
                type = BangumiEpisodeCollectionType.DONE
            )
            metadataRepository.cacheEpisodes(
                episode.animeId,
                metadataRepository.getCachedEpisodes(episode.animeId).getOrNull().orEmpty().map {
                    if (it.id == episodeId) it.copy(bangumiCollectionType = BangumiEpisodeCollectionType.DONE.value) else it
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError("Bangumi", e.message ?: "同步剧集失败"))
        }
    }

    private suspend fun ensureSubjectCollection(subjectId: Int) {
        val current = bangumiScraper.getSubjectCollection(subjectId).getOrNull()
        if (current == null || current.type == BangumiSubjectCollectionType.WISH.value) {
            bangumiScraper.upsertSubjectCollection(subjectId, BangumiSubjectCollectionType.DOING)
        }
    }

    private fun isLocallyWatched(episode: Episode, positionMs: Long?, playCount: Int): Boolean {
        if (playCount > 0 && (positionMs ?: 0L) > 0L && episode.duration <= 0L) return true
        if (playCount > 0 && (positionMs ?: 0L) >= 60_000L) return true
        if (episode.duration > 0L && (positionMs ?: 0L) >= (episode.duration * 0.9f).toLong()) return true
        return false
    }
}
