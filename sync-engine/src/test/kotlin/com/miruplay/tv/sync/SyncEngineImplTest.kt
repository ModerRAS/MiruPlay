package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.metadata.MetadataManager
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncEngineImplTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `syncEpisode imports NFO resume position as milliseconds`() = runBlocking {
        val progressRepository = RecordingProgressRepository()
        val engine = SyncEngineImpl(
            progressRepository = progressRepository,
            metadataManager = MetadataManager(FakeMetadataRepository())
        )
        val nfoFile = temporaryFolder.newFile("episode.nfo").apply {
            writeText(
                """
                <episodedetails>
                    <title>Episode 1</title>
                    <season>1</season>
                    <episode>1</episode>
                    <resume>12.5</resume>
                </episodedetails>
                """.trimIndent()
            )
        }

        val result = engine.syncEpisode(
            Episode(
                id = "episode-1",
                animeId = "anime-1",
                episodeNumber = 1,
                filePath = "episode.mkv",
                fileName = "episode.mkv"
            ),
            nfoFile.absolutePath
        )

        assertTrue(result is Result.Success)
        val syncResult = (result as Result.Success).data
        assertEquals(SyncAction.SYNCED_FROM_NFO, syncResult.action)
        assertEquals(750_000L, syncResult.resolvedPosition)
        assertEquals(750_000L, progressRepository.savedPositionMs)
    }

    private class RecordingProgressRepository : PlaybackProgressRepository {
        private var progress: ProgressRecord? = null
        var savedPositionMs: Long? = null
            private set

        override suspend fun saveProgress(
            episodeId: String,
            positionMs: Long,
            lastWatched: Long,
            incrementPlayCount: Boolean
        ): Result<Unit> {
            savedPositionMs = positionMs
            progress = ProgressRecord(episodeId, positionMs, lastWatched)
            return Result.success(Unit)
        }

        override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> =
            Result.success(progress?.takeIf { it.episodeId == episodeId })

        override suspend fun getAllProgress(): Result<List<ProgressRecord>> =
            Result.success(progress?.let(::listOf).orEmpty())

        override suspend fun deleteProgress(episodeId: String): Result<Unit> {
            if (progress?.episodeId == episodeId) {
                progress = null
            }
            return Result.success(Unit)
        }

        override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> =
            getAllProgress()
    }

    private class FakeMetadataRepository : MetadataRepository {
        override suspend fun cacheMetadata(anime: Anime): Result<Unit> =
            Result.success(Unit)

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
            Result.success(null)

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(null)

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(emptyList())

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> =
            Result.success(Unit)

        override suspend fun invalidateCache(animeId: String): Result<Unit> =
            Result.success(Unit)
    }
}
