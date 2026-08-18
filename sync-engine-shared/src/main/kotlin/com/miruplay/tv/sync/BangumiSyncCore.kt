package com.miruplay.tv.sync

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.bangumiSyncEpisodeFailedMessage
import com.miruplay.tv.model.bangumiSyncFailedMessage
import com.miruplay.tv.model.bangumiSyncMissingSubjectIdMessage
import com.miruplay.tv.model.bangumiSyncMissingTokenMessage
import com.miruplay.tv.model.bangumiSyncNoEpisodesMessage
import com.miruplay.tv.model.isCompleted
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BangumiSyncSummary(
    val animeId: String,
    val subjectId: Int,
    val pushedEpisodes: Int,
    val pulledEpisodes: Int,
    val remoteWatchedEpisodes: Int,
    val subjectCollectionType: Int?
)

data class BangumiSyncAnimeStatus(
    val animeId: String,
    val subjectId: Int? = null,
    val reason: String,
)

data class BangumiSyncAllSummary(
    val synced: List<BangumiSyncSummary>,
    val failed: List<BangumiSyncAnimeStatus>,
) {
    val animeCount: Int get() = synced.size + failed.size
    val totalPushedEpisodes: Int get() = synced.sumOf { it.pushedEpisodes }
    val totalPulledEpisodes: Int get() = synced.sumOf { it.pulledEpisodes }
    val totalRemoteWatchedEpisodes: Int get() = synced.sumOf { it.remoteWatchedEpisodes }
}

class BangumiSyncCore(
    private val bangumiService: BangumiCollectionService,
    private val metadataRepository: MetadataRepository,
    private val progressRepository: PlaybackProgressRepository
) {
    suspend fun syncAnime(animeId: String): Result<BangumiSyncSummary> = withContext(Dispatchers.IO) {
        try {
            if (!bangumiService.hasToken) {
                return@withContext Result.failure(AppError.ScrapingError.ApiError("Bangumi", bangumiSyncMissingTokenMessage()))
            }

            val anime = when (val result = metadataRepository.getCachedMetadata(animeId)) {
                is Result.Success -> result.data
                    ?: return@withContext Result.failure(AppError.ScrapingError.NoMatchFound(animeId))
                is Result.Error -> return@withContext result
            }
            val subjectId = anime.bangumiId
                ?: return@withContext Result.failure(AppError.ScrapingError.ApiError("Bangumi", bangumiSyncMissingSubjectIdMessage()))

            val episodes = when (val result = metadataRepository.getCachedEpisodes(animeId)) {
                is Result.Success -> result.data
                is Result.Error -> return@withContext result
            }
            if (episodes.isEmpty()) {
                return@withContext Result.failure(AppError.ScrapingError.ApiError("Bangumi", bangumiSyncNoEpisodesMessage()))
            }

            when (val collection = ensureSubjectCollection(subjectId)) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext collection
            }

            val remoteCollections = when (val result = bangumiService.getEpisodeCollections(subjectId)) {
                is Result.Success -> result.data.associateBy { it.episodeId }
                is Result.Error -> return@withContext result
            }
            var pulled = 0
            val localWatchedRemoteIds = mutableListOf<Int>()
            val updatedEpisodes = mutableListOf<Episode>()
            for (episode in episodes) {
                val remoteId = episode.bangumiEpisodeId
                val remote = remoteId?.let { remoteCollections[it] }
                val progress = when (val result = progressRepository.getProgress(episode.id)) {
                    is Result.Success -> result.data
                    is Result.Error -> return@withContext result
                }
                val localDone = isLocallyWatched(episode, progress?.positionMs, progress?.playCount ?: 0)

                if (remoteId != null && localDone) {
                    localWatchedRemoteIds += remoteId
                }

                if (remote?.type == BangumiEpisodeCollectionType.DONE.value && !localDone) {
                    val watchedPosition = episode.duration.takeIf { it > 0 } ?: progress?.positionMs ?: 0L
                    when (
                        val saved = progressRepository.saveProgress(
                            episode.id,
                            watchedPosition,
                            System.currentTimeMillis(),
                        )
                    ) {
                        is Result.Success -> Unit
                        is Result.Error -> return@withContext saved
                    }
                    pulled += 1
                }

                updatedEpisodes += episode.copy(bangumiCollectionType = remote?.type)
            }

            val remoteDoneIds = remoteCollections.values
                .filter { it.type == BangumiEpisodeCollectionType.DONE.value }
                .map { it.episodeId }
                .toSet()
            val toPush = localWatchedRemoteIds.distinct().filterNot { it in remoteDoneIds }
            if (toPush.isNotEmpty()) {
                when (
                    val updated = bangumiService.updateEpisodeCollections(
                        subjectId = subjectId,
                        episodeIds = toPush,
                        type = BangumiEpisodeCollectionType.DONE
                    )
                ) {
                    is Result.Success -> Unit
                    is Result.Error -> return@withContext updated
                }
            }

            val watchedCount = (remoteDoneIds + localWatchedRemoteIds).size
            val targetSubjectType = if (watchedCount >= episodes.count { it.bangumiEpisodeId != null }.coerceAtLeast(1)) {
                BangumiSubjectCollectionType.DONE
            } else {
                BangumiSubjectCollectionType.DOING
            }
            when (val subject = bangumiService.upsertSubjectCollection(subjectId, targetSubjectType)) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext subject
            }

            when (
                val cachedEpisodes = metadataRepository.cacheEpisodes(
                    animeId,
                    updatedEpisodes.map { episode ->
                        if (episode.bangumiEpisodeId in (remoteDoneIds + localWatchedRemoteIds)) {
                            episode.copy(bangumiCollectionType = BangumiEpisodeCollectionType.DONE.value)
                        } else {
                            episode
                        }
                    },
                )
            ) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext cachedEpisodes
            }
            when (
                val cachedAnime = metadataRepository.cacheMetadata(
                    anime.copy(
                        bangumiCollectionType = targetSubjectType.value,
                        bangumiEpStatus = watchedCount
                    )
                )
            ) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext cachedAnime
            }

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
            Result.failure(AppError.ScrapingError.ApiError("Bangumi", e.message ?: bangumiSyncFailedMessage()))
        }
    }

    suspend fun markEpisodeWatched(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!bangumiService.hasToken) return@withContext Result.success(Unit)
            val episode = when (val result = metadataRepository.getCachedEpisode(episodeId)) {
                is Result.Success -> result.data ?: return@withContext Result.success(Unit)
                is Result.Error -> return@withContext result
            }
            val bangumiEpisodeId = episode.bangumiEpisodeId ?: return@withContext Result.success(Unit)

            val anime = when (val result = metadataRepository.getCachedMetadata(episode.animeId)) {
                is Result.Success -> result.data ?: return@withContext Result.success(Unit)
                is Result.Error -> return@withContext result
            }
            val subjectId = anime.bangumiId
            if (subjectId != null) {
                when (val collection = ensureSubjectCollection(subjectId)) {
                    is Result.Success -> Unit
                    is Result.Error -> return@withContext collection
                }
            }

            when (
                val updated = bangumiService.updateEpisodeCollection(
                    episodeId = bangumiEpisodeId,
                    type = BangumiEpisodeCollectionType.DONE
                )
            ) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext updated
            }
            val episodes = when (val result = metadataRepository.getCachedEpisodes(episode.animeId)) {
                is Result.Success -> result.data
                is Result.Error -> return@withContext result
            }
            when (
                val cached = metadataRepository.cacheEpisodes(
                    episode.animeId,
                    episodes.map {
                        if (it.id == episodeId) it.copy(bangumiCollectionType = BangumiEpisodeCollectionType.DONE.value) else it
                    }
                )
            ) {
                is Result.Success -> Unit
                is Result.Error -> return@withContext cached
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError("Bangumi", e.message ?: bangumiSyncEpisodeFailedMessage()))
        }
    }

    private suspend fun ensureSubjectCollection(subjectId: Int): Result<Unit> {
        val current = when (val result = bangumiService.getSubjectCollection(subjectId)) {
            is Result.Success -> result.data
            is Result.Error -> return result
        }
        if (current == null || current.type == BangumiSubjectCollectionType.WISH.value) {
            return bangumiService.upsertSubjectCollection(subjectId, BangumiSubjectCollectionType.DOING)
        }
        return Result.success(Unit)
    }

    private fun isLocallyWatched(episode: Episode, positionMs: Long?, playCount: Int): Boolean {
        val progress = ProgressRecord(
            episodeId = episode.id,
            positionMs = positionMs ?: 0L,
            lastWatched = 0L,
            playCount = playCount
        )
        return episode.isCompleted(progress)
    }
}
