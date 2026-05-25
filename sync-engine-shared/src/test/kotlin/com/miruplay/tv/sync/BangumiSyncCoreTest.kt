package com.miruplay.tv.sync

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.bangumiSyncMissingTokenMessage
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeCollection
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiSubjectCollection
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import com.miruplay.tv.repository.BangumiUser
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiSyncCoreTest {
    @Test
    fun `syncAnime returns shared missing token message without touching repositories`() = runBlocking {
        val service = FakeBangumiCollectionService(hasToken = false)
        val metadata = FakeMetadataRepository()
        val progress = FakeProgressRepository()
        val core = BangumiSyncCore(service, metadata, progress)

        val result = core.syncAnime("frieren")

        assertTrue(result is Result.Error)
        assertEquals("Bangumi 错误：${bangumiSyncMissingTokenMessage()}", (result as Result.Error).error.toUserMessage())
        assertEquals(emptyList<String>(), metadata.cachedMetadataRequests)
        assertEquals(emptyList<Int>(), service.subjectCollectionRequests)
    }

    @Test
    fun `syncAnime pulls remote watched episodes into local progress`() = runBlocking {
        val service = FakeBangumiCollectionService(
            episodeCollections = mutableMapOf(
                1001 to listOf(BangumiEpisodeCollection(episodeId = 5001, episodeNumber = 1, type = BangumiEpisodeCollectionType.DONE.value)),
            ),
        )
        val metadata = FakeMetadataRepository(
            anime = Anime(id = "frieren", title = "Frieren", bangumiId = 1001),
            episodes = listOf(
                episode(id = "ep1", remoteId = 5001, duration = 24_000L),
                episode(id = "ep2", remoteId = 5002, duration = 24_000L),
            ),
        )
        val progress = FakeProgressRepository()
        val core = BangumiSyncCore(service, metadata, progress)

        val result = core.syncAnime("frieren")

        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        assertEquals(0, summary.pushedEpisodes)
        assertEquals(1, summary.pulledEpisodes)
        assertEquals(1, summary.remoteWatchedEpisodes)
        assertEquals(BangumiSubjectCollectionType.DOING.value, summary.subjectCollectionType)
        assertEquals(24_000L, progress.records.getValue("ep1").positionMs)
        assertEquals(BangumiEpisodeCollectionType.DONE.value, metadata.episodes.getValue("frieren")[0].bangumiCollectionType)
        assertEquals(BangumiSubjectCollectionType.DOING.value, metadata.anime?.bangumiCollectionType)
        assertEquals(1, metadata.anime?.bangumiEpStatus)
    }

    @Test
    fun `syncAnime pushes locally completed episodes and marks subject done when all remote ids are watched`() = runBlocking {
        val service = FakeBangumiCollectionService()
        val metadata = FakeMetadataRepository(
            anime = Anime(id = "frieren", title = "Frieren", bangumiId = 1001),
            episodes = listOf(
                episode(id = "ep1", remoteId = 5001, duration = 10_000L),
                episode(id = "ep2", remoteId = 5002, duration = 10_000L),
            ),
        )
        val progress = FakeProgressRepository(
            records = mutableMapOf(
                "ep1" to ProgressRecord("ep1", positionMs = 9_500L, lastWatched = 1L),
                "ep2" to ProgressRecord("ep2", positionMs = 10_000L, lastWatched = 2L),
            ),
        )
        val core = BangumiSyncCore(service, metadata, progress)

        val result = core.syncAnime("frieren")

        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        assertEquals(2, summary.pushedEpisodes)
        assertEquals(0, summary.pulledEpisodes)
        assertEquals(2, summary.remoteWatchedEpisodes)
        assertEquals(listOf(5001, 5002), service.updatedEpisodeBatches.single().episodeIds)
        assertEquals(BangumiEpisodeCollectionType.DONE, service.updatedEpisodeBatches.single().type)
        assertEquals(1001 to BangumiSubjectCollectionType.DONE, service.subjectUpdates.last())
        assertEquals(BangumiSubjectCollectionType.DONE.value, metadata.anime?.bangumiCollectionType)
        assertEquals(2, metadata.anime?.bangumiEpStatus)
    }

    @Test
    fun `markEpisodeWatched updates remote episode and cached local episode`() = runBlocking {
        val service = FakeBangumiCollectionService()
        val metadata = FakeMetadataRepository(
            anime = Anime(id = "frieren", title = "Frieren", bangumiId = 1001),
            episodes = listOf(episode(id = "ep1", remoteId = 5001, duration = 10_000L)),
        )
        val core = BangumiSyncCore(service, metadata, FakeProgressRepository())

        val result = core.markEpisodeWatched("ep1")

        assertTrue(result is Result.Success)
        assertEquals(5001 to BangumiEpisodeCollectionType.DONE, service.updatedEpisodes.single())
        assertEquals(1001 to BangumiSubjectCollectionType.DOING, service.subjectUpdates.single())
        assertEquals(BangumiEpisodeCollectionType.DONE.value, metadata.episodes.getValue("frieren").single().bangumiCollectionType)
    }

    @Test
    fun `syncAnime propagates remote episode collection failures instead of treating them as empty`() = runBlocking {
        val failure = AppError.NetworkError.ServerUnreachable("https://api.bgm.tv")
        val service = FakeBangumiCollectionService(episodeCollectionsError = failure)
        val metadata = FakeMetadataRepository(
            anime = Anime(id = "frieren", title = "Frieren", bangumiId = 1001),
            episodes = listOf(episode(id = "ep1", remoteId = 5001, duration = 10_000L)),
        )
        val progress = FakeProgressRepository(
            records = mutableMapOf("ep1" to ProgressRecord("ep1", positionMs = 9_500L, lastWatched = 1L)),
        )
        val core = BangumiSyncCore(service, metadata, progress)

        val result = core.syncAnime("frieren")

        assertEquals(Result.failure(failure), result)
        assertEquals(emptyList<UpdatedEpisodeBatch>(), service.updatedEpisodeBatches)
        assertEquals(null, metadata.anime?.bangumiCollectionType)
    }

    @Test
    fun `syncAnime propagates progress lookup failures before pushing watched state`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("store", "progress unavailable")
        val service = FakeBangumiCollectionService()
        val metadata = FakeMetadataRepository(
            anime = Anime(id = "frieren", title = "Frieren", bangumiId = 1001),
            episodes = listOf(episode(id = "ep1", remoteId = 5001, duration = 10_000L)),
        )
        val progress = FakeProgressRepository(progressErrors = mutableMapOf("ep1" to failure))
        val core = BangumiSyncCore(service, metadata, progress)

        val result = core.syncAnime("frieren")

        assertEquals(Result.failure(failure), result)
        assertEquals(emptyList<UpdatedEpisodeBatch>(), service.updatedEpisodeBatches)
        assertEquals(listOf(1001 to BangumiSubjectCollectionType.DOING), service.subjectUpdates)
    }

    @Test
    fun `markEpisodeWatched propagates remote update failures without caching done state`() = runBlocking {
        val failure = AppError.NetworkError.ServerUnreachable("https://api.bgm.tv")
        val service = FakeBangumiCollectionService(updateEpisodeError = failure)
        val metadata = FakeMetadataRepository(
            anime = Anime(id = "frieren", title = "Frieren", bangumiId = 1001),
            episodes = listOf(episode(id = "ep1", remoteId = 5001, duration = 10_000L)),
        )
        val core = BangumiSyncCore(service, metadata, FakeProgressRepository())

        val result = core.markEpisodeWatched("ep1")

        assertEquals(Result.failure(failure), result)
        assertEquals(null, metadata.episodes.getValue("frieren").single().bangumiCollectionType)
    }

    private fun episode(
        id: String,
        remoteId: Int?,
        duration: Long,
    ): Episode =
        Episode(
            id = id,
            animeId = "frieren",
            episodeNumber = id.removePrefix("ep").toIntOrNull() ?: 1,
            filePath = "D:/Anime/$id.mkv",
            fileName = "$id.mkv",
            duration = duration,
            bangumiEpisodeId = remoteId,
        )

    private class FakeBangumiCollectionService(
        override val hasToken: Boolean = true,
        val subjectCollections: MutableMap<Int, BangumiSubjectCollection?> = mutableMapOf(),
        val episodeCollections: MutableMap<Int, List<BangumiEpisodeCollection>> = mutableMapOf(),
        val subjectCollectionError: AppError? = null,
        val episodeCollectionsError: AppError? = null,
        val updateEpisodeCollectionsError: AppError? = null,
        val updateEpisodeError: AppError? = null,
    ) : BangumiCollectionService {
        val subjectCollectionRequests = mutableListOf<Int>()
        val subjectUpdates = mutableListOf<Pair<Int, BangumiSubjectCollectionType>>()
        val updatedEpisodeBatches = mutableListOf<UpdatedEpisodeBatch>()
        val updatedEpisodes = mutableListOf<Pair<Int, BangumiEpisodeCollectionType>>()

        override suspend fun getCurrentUser(): Result<BangumiUser> =
            Result.success(BangumiUser(id = 1, username = "alice", nickname = "Alice"))

        override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> {
            subjectCollectionRequests += subjectId
            subjectCollectionError?.let { return Result.failure(it) }
            return Result.success(subjectCollections[subjectId])
        }

        override suspend fun upsertSubjectCollection(
            subjectId: Int,
            type: BangumiSubjectCollectionType,
        ): Result<Unit> {
            subjectUpdates += subjectId to type
            subjectCollections[subjectId] = BangumiSubjectCollection(
                subjectId = subjectId,
                type = type.value,
                rate = 0,
                epStatus = 0,
            )
            return Result.success(Unit)
        }

        override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> {
            episodeCollectionsError?.let { return Result.failure(it) }
            return Result.success(episodeCollections[subjectId].orEmpty())
        }

        override suspend fun updateEpisodeCollections(
            subjectId: Int,
            episodeIds: List<Int>,
            type: BangumiEpisodeCollectionType,
        ): Result<Unit> {
            updateEpisodeCollectionsError?.let { return Result.failure(it) }
            updatedEpisodeBatches += UpdatedEpisodeBatch(subjectId, episodeIds, type)
            return Result.success(Unit)
        }

        override suspend fun updateEpisodeCollection(
            episodeId: Int,
            type: BangumiEpisodeCollectionType,
        ): Result<Unit> {
            updateEpisodeError?.let { return Result.failure(it) }
            updatedEpisodes += episodeId to type
            return Result.success(Unit)
        }
    }

    private data class UpdatedEpisodeBatch(
        val subjectId: Int,
        val episodeIds: List<Int>,
        val type: BangumiEpisodeCollectionType,
    )

    private class FakeMetadataRepository(
        anime: Anime? = null,
        episodes: List<Episode> = emptyList(),
        val metadataError: AppError? = null,
        val episodesError: AppError? = null,
        val cacheEpisodesError: AppError? = null,
    ) : MetadataRepository {
        var anime: Anime? = anime
            private set
        val episodes = mutableMapOf<String, List<Episode>>().apply {
            anime?.id?.let { put(it, episodes) }
        }
        val cachedMetadataRequests = mutableListOf<String>()

        override suspend fun cacheMetadata(anime: Anime): Result<Unit> {
            this.anime = anime
            return Result.success(Unit)
        }

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> {
            cachedMetadataRequests += animeId
            metadataError?.let { return Result.failure(it) }
            return Result.success(anime?.takeIf { it.id == animeId })
        }

        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> {
            metadataError?.let { return Result.failure(it) }
            val item = anime?.takeIf { animeIds.contains(it.id) } ?: return Result.success(emptyList())
            return Result.success(listOf(item))
        }

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> {
            metadataError?.let { return Result.failure(it) }
            return Result.success(episodes.values.flatten().firstOrNull { it.id == episodeId })
        }

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> {
            episodesError?.let { return Result.failure(it) }
            return Result.success(episodes[animeId].orEmpty())
        }

        override suspend fun cacheEpisodes(
            animeId: String,
            episodes: List<Episode>,
        ): Result<Unit> {
            cacheEpisodesError?.let { return Result.failure(it) }
            this.episodes[animeId] = episodes
            return Result.success(Unit)
        }

        override suspend fun invalidateCache(animeId: String): Result<Unit> {
            if (anime?.id == animeId) anime = null
            episodes.remove(animeId)
            return Result.success(Unit)
        }
    }

    private class FakeProgressRepository(
        val records: MutableMap<String, ProgressRecord> = mutableMapOf(),
        val progressErrors: MutableMap<String, AppError> = mutableMapOf(),
        val saveErrors: MutableMap<String, AppError> = mutableMapOf(),
    ) : PlaybackProgressRepository {
        override suspend fun saveProgress(
            episodeId: String,
            positionMs: Long,
            lastWatched: Long,
            incrementPlayCount: Boolean,
        ): Result<Unit> {
            saveErrors[episodeId]?.let { return Result.failure(it) }
            val previous = records[episodeId]
            records[episodeId] = ProgressRecord(
                episodeId = episodeId,
                positionMs = positionMs,
                lastWatched = lastWatched,
                playCount = if (incrementPlayCount) (previous?.playCount ?: 0) + 1 else previous?.playCount ?: 0,
            )
            return Result.success(Unit)
        }

        override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> {
            progressErrors[episodeId]?.let { return Result.failure(it) }
            return Result.success(records[episodeId])
        }

        override suspend fun getAllProgress(): Result<List<ProgressRecord>> =
            Result.success(records.values.toList())

        override suspend fun deleteProgress(episodeId: String): Result<Unit> {
            records.remove(episodeId)
            return Result.success(Unit)
        }

        override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> =
            Result.success(records.values.sortedByDescending { it.lastWatched }.take(limit))
    }
}
