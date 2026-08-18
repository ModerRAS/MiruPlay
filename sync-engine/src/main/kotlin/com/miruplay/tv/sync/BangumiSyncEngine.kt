package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BangumiSyncEngine @Inject constructor(
    bangumiService: BangumiCollectionService,
    val metadataRepository: MetadataRepository,
    progressRepository: PlaybackProgressRepository
) {
    private val core = BangumiSyncCore(
        bangumiService = bangumiService,
        metadataRepository = metadataRepository,
        progressRepository = progressRepository
    )

    suspend fun syncAnime(animeId: String): Result<BangumiSyncSummary> =
        core.syncAnime(animeId)

    suspend fun markEpisodeWatched(episodeId: String): Result<Unit> =
        core.markEpisodeWatched(episodeId)

    suspend fun syncAllBangumi(): BangumiSyncAllSummary {
        val matched = when (val result = metadataRepository.getCachedAnimeWithBangumiId()) {
            is Result.Success -> result.data
            is Result.Error -> return BangumiSyncAllSummary(
                synced = emptyList(),
                failed = listOf(
                    BangumiSyncAnimeStatus(
                        animeId = "__all__",
                        reason = result.error.toUserMessage(),
                    ),
                ),
            )
        }

        val synced = mutableListOf<BangumiSyncSummary>()
        val failed = mutableListOf<BangumiSyncAnimeStatus>()
        for (anime in matched) {
            when (val result = core.syncAnime(anime.id)) {
                is Result.Success -> synced += result.data
                is Result.Error -> failed += BangumiSyncAnimeStatus(
                    animeId = anime.id,
                    reason = result.error.toUserMessage(),
                )
            }
        }
        return BangumiSyncAllSummary(synced = synced, failed = failed)
    }
}
