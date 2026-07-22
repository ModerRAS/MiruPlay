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
                progressId = "anime-1#S1E2",
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
                progressId = "anime-1#S1E2",
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
                progressId = "anime-1#S1E2",
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
                progressId = "anime-1#S1E2",
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
                progressId = "anime-1#S1E2",
            ),
            source,
        )
    }

    @Test
    fun `auto next skips duplicate current versions and keeps nearest path`() = runBlocking {
        val currentWeb = episode(
            id = "41:/Show/WEB/Episode 01.mkv",
            number = 1,
            filePath = "/Show/WEB/Episode 01.mkv",
        )
        val currentBd = episode(
            id = "41:/Show/BD/Episode 01.mkv",
            number = 1,
            filePath = "/Show/BD/Episode 01.mkv",
        )
        val nextWeb = episode(
            id = "41:/Show/WEB/Episode 02.mkv",
            number = 2,
            filePath = "/Show/WEB/Episode 02.mkv",
        )
        val nextBd = episode(
            id = "41:/Show/BD/Episode 02.mkv",
            number = 2,
            filePath = "/Show/BD/Episode 02.mkv",
        )

        val source = buildNextPlaybackSource(
            currentSource = PlaybackSource(
                uri = currentWeb.filePath,
                mediaSourceId = currentWeb.animeId,
                episodeId = currentWeb.id,
                progressId = "anime-1#S1E1",
            ),
            loadCurrentEpisode = { currentWeb },
            loadEpisodes = { listOf(currentWeb, currentBd, nextBd, nextWeb) },
            loadProgress = { null },
        )

        assertEquals(nextWeb.id, source?.episodeId)
        assertEquals("anime-1#S1E2", source?.progressId)
    }

    @Test
    fun `auto next uses logical anime queue across merged physical anime ids`() = runBlocking {
        val current = episode(id = "part-a-1", number = 1).copy(animeId = "part-a")
        val next = episode(id = "part-b-2", number = 2).copy(animeId = "part-b")

        val source = buildNextPlaybackSource(
            currentSource = PlaybackSource(
                uri = current.filePath,
                mediaSourceId = "merged-show",
                episodeId = current.id,
                progressId = "merged-show#S1E1",
            ),
            loadCurrentEpisode = { current },
            loadEpisodes = { animeId ->
                assertEquals("merged-show", animeId)
                listOf(current, next)
            },
            loadProgress = { null },
        )

        assertEquals(next.id, source?.episodeId)
        assertEquals("merged-show#S1E2", source?.progressId)
    }

    @Test
    fun `resolver auto next supports indexed episodes without metadata cache`() = runBlocking {
        val source = MediaSourceInfoConventions.webDav(
            url = "https://dav.example/anime",
            name = "DAV",
        ).copy(id = 41L)
        val currentPath = "/Show/WEB/Episode 01.mkv"
        val nextPath = "/Show/WEB/Episode 02.mkv"
        val resolver = NextPlaybackSourceResolver(
            metadata = FakeMetadataRepository(emptyMap()),
            progress = FakeProgressRepository(null),
            mediaSources = FakeMediaSourceRepository(listOf(source)),
            index = FakeMediaIndexRepository(
                listOf(
                    MediaIndexEntry(sourceId = 41L, path = currentPath, animeName = "Show", episodeNumber = 1),
                    MediaIndexEntry(sourceId = 41L, path = nextPath, animeName = "Show", episodeNumber = 2),
                ),
            ),
        )

        val next = resolver.build(
            PlaybackSource(
                uri = "https://dav.example/anime/Show/WEB/Episode%2001.mkv",
                mediaSourceId = "Show",
                episodeId = "41:$currentPath",
                progressId = "Show#S1E1",
            ),
        )

        assertEquals("41:$nextPath", next?.episodeId)
        assertEquals("Show#S1E2", next?.progressId)
    }

    @Test
    fun `resolver compares database paths when current uri is remote`() = runBlocking {
        val currentWeb = episode(
            id = "41:/Show/WEB/Episode 01.mkv",
            number = 1,
            filePath = "/Show/WEB/Episode 01.mkv",
        )
        val currentBd = episode(
            id = "41:/Show/BD/Episode 01.mkv",
            number = 1,
            filePath = "/Show/BD/Episode 01.mkv",
        )
        val nextWeb = episode(
            id = "41:/Show/WEB/Episode 02.mkv",
            number = 2,
            filePath = "/Show/WEB/Episode 02.mkv",
        )
        val nextBd = episode(
            id = "41:/Show/BD/Episode 02.mkv",
            number = 2,
            filePath = "/Show/BD/Episode 02.mkv",
        )
        val resolver = NextPlaybackSourceResolver(
            metadata = FakeMetadataRepository(
                mapOf("anime-1" to listOf(currentWeb, currentBd, nextBd, nextWeb)),
            ),
            progress = FakeProgressRepository(null),
            mediaSources = FakeMediaSourceRepository(emptyList()),
            playbackUriForEpisode = { it.filePath },
        )

        val source = resolver.build(
            PlaybackSource(
                uri = "https://dav.example/anime/Show/WEB/Episode%2001.mkv",
                mediaSourceId = currentWeb.animeId,
                episodeId = currentWeb.id,
                progressId = "anime-1#S1E1",
            ),
        )

        assertEquals(nextWeb.id, source?.episodeId)
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

        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<com.miruplay.tv.model.Anime>> =
            Result.success(emptyList())

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

    private class FakeMediaIndexRepository(
        private val entries: List<MediaIndexEntry>,
    ) : MediaIndexRepository {
        override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
            Result.success(Unit)

        override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> =
            Result.success(Unit)

        override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> =
            Result.success(
                entries.filter { entry ->
                    entry.sourceId == sourceId &&
                        (query.isBlank() || entry.animeName?.contains(query, ignoreCase = true) == true ||
                            entry.path.contains(query, ignoreCase = true))
                },
            )

        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
            Result.success(entries.filter { it.sourceId == sourceId }.mapNotNull { it.animeName }.distinct())

        override suspend fun clearIndex(sourceId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
            Result.success(Unit)
        override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> =
            Result.success(emptyList())
        override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = Result.success(Unit)
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
