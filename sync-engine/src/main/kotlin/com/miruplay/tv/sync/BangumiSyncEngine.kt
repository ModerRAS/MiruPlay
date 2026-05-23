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
    metadataRepository: MetadataRepository,
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
}
