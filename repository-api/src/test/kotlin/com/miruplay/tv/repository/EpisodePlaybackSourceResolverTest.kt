package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodePlaybackSourceResolverTest {
    @Test
    fun `buildEpisodePlaybackSource resumes from progress`() {
        val episode = episode(duration = 120_000L)

        val source = buildEpisodePlaybackSource(
            episode = episode,
            progress = ProgressRecord(
                episodeId = episode.id,
                positionMs = 45_000L,
                lastWatched = 1L,
            ),
            playableUri = "https://media.example.test/episode-1.mkv",
        )

        assertEquals(
            PlaybackSource(
                uri = "https://media.example.test/episode-1.mkv",
                mediaSourceId = "anime-1",
                startPosition = 45_000L,
                episodeId = "episode-1",
            ),
            source,
        )
    }

    @Test
    fun `buildEpisodePlaybackSource honors explicit start override`() {
        val episode = episode(duration = 120_000L)

        val source = buildEpisodePlaybackSource(
            episode = episode,
            progress = ProgressRecord(
                episodeId = episode.id,
                positionMs = 45_000L,
                lastWatched = 1L,
            ),
            startPositionOverrideMs = 12_000L,
        )

        assertEquals(12_000L, source.startPosition)
    }

    @Test
    fun `buildEpisodePlaybackSource resets completed progress`() {
        val episode = episode(duration = 100_000L)

        val source = buildEpisodePlaybackSource(
            episode = episode,
            progress = ProgressRecord(
                episodeId = episode.id,
                positionMs = 95_000L,
                lastWatched = 1L,
            ),
        )

        assertEquals(0L, source.startPosition)
    }

    @Test
    fun `resolver uses repository progress and shared playable uri`() = runBlocking {
        val episode = episode(
            id = "7:/Show/Episode 01.mkv",
            filePath = "/Show/Episode 01.mkv",
            duration = 120_000L,
        )
        val resolver = EpisodePlaybackSourceResolver(
            progress = FakeProgressRepository(
                ProgressRecord(
                    episodeId = episode.id,
                    positionMs = 30_000L,
                    lastWatched = 1L,
                ),
            ),
            mediaSources = FakeMediaSourceRepository(
                listOf(MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 7L)),
            ),
        )

        val source = resolver.build(episode)

        assertEquals(
            PlaybackSource(
                uri = "https://dav.example/anime/Show/Episode%2001.mkv",
                mediaSourceId = "anime-1",
                startPosition = 30_000L,
                episodeId = episode.id,
            ),
            source,
        )
    }

    private fun episode(
        id: String = "episode-1",
        filePath: String = "/anime/episode-1.mkv",
        duration: Long,
    ): Episode =
        Episode(
            id = id,
            animeId = "anime-1",
            episodeNumber = 1,
            filePath = filePath,
            fileName = filePath.substringAfterLast('/').substringAfterLast('\\'),
            duration = duration,
        )

    private class FakeProgressRepository(
        private val progress: ProgressRecord?,
    ) : PlaybackProgressRepository {
        override suspend fun saveProgress(
            episodeId: String,
            positionMs: Long,
            lastWatched: Long,
            incrementPlayCount: Boolean,
        ): Result<Unit> =
            Result.success(Unit)

        override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> =
            Result.success(progress?.takeIf { it.episodeId == episodeId })

        override suspend fun getAllProgress(): Result<List<ProgressRecord>> =
            Result.success(listOfNotNull(progress))

        override suspend fun deleteProgress(episodeId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> =
            Result.success(listOfNotNull(progress).take(limit))
    }

    private class FakeMediaSourceRepository(
        private val sources: List<MediaSourceInfo>,
    ) : MediaSourceRepository {
        override suspend fun addSource(source: MediaSourceInfo): Result<Long> =
            Result.success(source.id)

        override suspend fun removeSource(sourceId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSources(): Result<List<MediaSourceInfo>> =
            Result.success(sources)

        override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
            sources.firstOrNull { it.id == sourceId }
                ?.let { Result.success(it) }
                ?: Result.failure(AppError.MediaSourceError.NotFound("Source id: $sourceId"))
    }
}
