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
import org.junit.Assert.assertNull
import org.junit.Test

class NextPlaybackSourceResolverTest {
    @Test
    fun `buildNextPlaybackSource uses shared ordering resume and playable uri`() = runBlocking {
        val current = episode(id = "ep1", number = 1)
        val next = episode(id = "ep2", number = 2)
        val episodes = listOf(
            episode(id = "ep3", number = 3),
            next,
            current,
        )

        val source = buildNextPlaybackSource(
            currentEpisodeId = current.id,
            loadCurrentEpisode = { id -> current.takeIf { it.id == id } },
            loadEpisodes = { animeId -> episodes.filter { it.animeId == animeId } },
            loadProgress = { id ->
                ProgressRecord(
                    episodeId = id,
                    positionMs = 30_000L,
                    lastWatched = 1L,
                )
            },
            resolvePlayableUri = { episode -> "https://play.example/${episode.id}.mkv" },
        )

        assertEquals(
            PlaybackSource(
                uri = "https://play.example/ep2.mkv",
                mediaSourceId = "anime-1",
                startPosition = 30_000L,
                episodeId = "ep2",
            ),
            source,
        )
    }

    @Test
    fun `buildNextPlaybackSource resets completed progress`() = runBlocking {
        val current = episode(id = "ep1", number = 1)
        val next = episode(id = "ep2", number = 2, duration = 100_000L)

        val source = buildNextPlaybackSource(
            currentEpisodeId = current.id,
            loadCurrentEpisode = { current },
            loadEpisodes = { listOf(current, next) },
            loadProgress = { id ->
                ProgressRecord(
                    episodeId = id,
                    positionMs = 95_000L,
                    lastWatched = 1L,
                    playCount = 1,
                )
            },
        )

        assertEquals(
            PlaybackSource(
                uri = "D:/Anime/ep2.mkv",
                mediaSourceId = "anime-1",
                startPosition = 0L,
                episodeId = "ep2",
            ),
            source,
        )
    }

    @Test
    fun `buildNextPlaybackSource accepts playback source input`() = runBlocking {
        val current = episode(id = "ep1", number = 1)
        val next = episode(id = "ep2", number = 2)

        val source = buildNextPlaybackSource(
            currentSource = PlaybackSource(
                uri = "D:/Anime/ep1.mkv",
                mediaSourceId = "anime-1",
                episodeId = current.id,
            ),
            loadCurrentEpisode = { current },
            loadEpisodes = { listOf(current, next) },
            loadProgress = { null },
        )

        assertEquals(
            PlaybackSource(
                uri = "D:/Anime/ep2.mkv",
                mediaSourceId = "anime-1",
                startPosition = 0L,
                episodeId = "ep2",
            ),
            source,
        )
    }

    @Test
    fun `resolver builds repository backed source with shared playable uri`() = runBlocking {
        val current = episode(id = "41:/Show/Episode 01.mkv", number = 1, filePath = "/Show/Episode 01.mkv")
        val next = episode(id = "41:/Show/Episode 02.mkv", number = 2, filePath = "/Show/Episode 02.mkv")
        val resolver = NextPlaybackSourceResolver(
            metadata = FakeMetadataRepository(mapOf("anime-1" to listOf(current, next))),
            progress = FakeProgressRepository(
                ProgressRecord(
                    episodeId = next.id,
                    positionMs = 42_000L,
                    lastWatched = 1L,
                ),
            ),
            mediaSources = FakeMediaSourceRepository(
                listOf(MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 41L)),
            ),
        )

        val source = resolver.build(current.id)

        assertEquals(
            PlaybackSource(
                uri = "https://dav.example/anime/Show/Episode%2002.mkv",
                mediaSourceId = "anime-1",
                startPosition = 42_000L,
                episodeId = next.id,
            ),
            source,
        )
    }

    @Test
    fun `resolver accepts platform playback uri override`() = runBlocking {
        val current = episode(id = "41:/Show/Episode 01.mkv", number = 1, filePath = "/Show/Episode 01.mkv")
        val next = episode(id = "41:/Show/Episode 02.mkv", number = 2, filePath = "/Show/Episode 02.mkv")
        val resolver = NextPlaybackSourceResolver(
            metadata = FakeMetadataRepository(mapOf("anime-1" to listOf(current, next))),
            progress = FakeProgressRepository(null),
            mediaSources = FakeMediaSourceRepository(
                listOf(MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 41L)),
            ),
            playbackUriForEpisode = { episode -> episode.filePath },
        )

        val source = resolver.build(current.id)

        assertEquals(
            PlaybackSource(
                uri = "/Show/Episode 02.mkv",
                mediaSourceId = "anime-1",
                startPosition = 0L,
                episodeId = next.id,
            ),
            source,
        )
    }

    @Test
    fun `buildNextPlaybackSource returns null without episode id`() = runBlocking {
        val source = buildNextPlaybackSource(
            currentEpisodeId = null,
            loadCurrentEpisode = { error("Current episode should not load without an id") },
            loadEpisodes = { emptyList() },
            loadProgress = { null },
        )

        assertNull(source)
    }

    @Test
    fun `buildNextPlaybackSource returns null when current episode or next episode is missing`() = runBlocking {
        assertNull(
            buildNextPlaybackSource(
                currentEpisodeId = "missing",
                loadCurrentEpisode = { null },
                loadEpisodes = { error("Episode list should not load without current episode") },
                loadProgress = { null },
            )
        )

        val current = episode(id = "ep1", number = 1)

        assertNull(
            buildNextPlaybackSource(
                currentEpisodeId = current.id,
                loadCurrentEpisode = { current },
                loadEpisodes = { listOf(current) },
                loadProgress = { error("Progress should not load without a next episode") },
            )
        )
    }

    private fun episode(
        id: String,
        number: Int,
        duration: Long = 0L,
        filePath: String = "D:/Anime/$id.mkv",
    ): Episode =
        Episode(
            id = id,
            animeId = "anime-1",
            episodeNumber = number,
            filePath = filePath,
            fileName = filePath.substringAfterLast('/').substringAfterLast('\\'),
            duration = duration,
        )

    private class FakeMetadataRepository(
        private val episodesByAnime: Map<String, List<Episode>>,
    ) : MetadataRepository {
        override suspend fun cacheMetadata(anime: com.miruplay.tv.model.Anime): Result<Unit> =
            Result.success(Unit)

        override suspend fun getCachedMetadata(animeId: String): Result<com.miruplay.tv.model.Anime?> =
            Result.success(null)

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(episodesByAnime.values.flatten().firstOrNull { it.id == episodeId })

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(episodesByAnime[animeId].orEmpty())

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> =
            Result.success(Unit)

        override suspend fun invalidateCache(animeId: String): Result<Unit> =
            Result.success(Unit)
    }

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
