package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.ProgressRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryEpisodeResolverTest {
    @Test
    fun `find episode returns cached episode before indexed lookup`() = runBlocking {
        val cached = Episode(
            id = "cached-1",
            animeId = "cached-show",
            episodeNumber = 1,
            filePath = "D:/Anime/Cached/01.mkv",
            fileName = "01.mkv",
        )
        val resolver = resolver(
            cachedAnime = mapOf("cached-show" to Anime(id = "cached-show", title = "Cached")),
            cachedEpisodes = mapOf("cached-show" to listOf(cached)),
            entries = listOf(
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Indexed/01.mkv", animeName = "Indexed"),
            ),
        )

        assertEquals(cached, resolver.findEpisodeById("cached-1"))
    }

    @Test
    fun `find episode resolves indexed source path with shared playable uri`() = runBlocking {
        val source = MediaSourceInfoConventions.webDav(
            url = "https://dav.example/anime",
            name = "DAV",
        ).copy(id = 2L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 2L,
                    path = "/Frieren/Episode 01.mkv",
                    animeName = "Frieren",
                    metadataId = "431767",
                    metadataTitle = "Frieren",
                    episodeNumber = 1,
                ),
            ),
            mergeSameAnimeEnabled = true,
        )

        val episode = resolver.findEpisodeById("2:/Frieren/Episode 01.mkv")

        assertEquals("2:/Frieren/Episode 01.mkv", episode?.id)
        assertEquals("431767", episode?.animeId)
        assertEquals("https://dav.example/anime/Frieren/Episode%2001.mkv", episode?.filePath)
    }

    @Test
    fun `find episode uses canonical group numbering for unnumbered indexed paths`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val targetPath = "D:/Anime/Show/Unknown B.mkv"
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/Unknown A.mkv", animeName = "Show", seasonNumber = 1),
            MediaIndexEntry(
                sourceId = 1L,
                path = targetPath,
                animeName = "Show",
                seasonNumber = 1,
                episodeNumber = 0,
                episodeTitle = "Unnumbered B",
            ),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/S01E02.mkv", animeName = "Show", seasonNumber = 1, episodeNumber = 2),
        )
        val episodeResolver = resolver(sources = listOf(source), entries = entries)
        val animeResolver = LibraryAnimeResolver(
            mediaSources = FakeMediaSourceRepository(listOf(source)),
            metadata = FakeMetadataRepository(emptyMap(), emptyMap()),
            index = FakeMediaIndexRepository(entries),
        )
        val expectedCached = entries.toCachedIndexedEpisodes(source, "Show").single { it.id == "1:$targetPath" }
        val expectedDetail = animeResolver.loadAnimeDetail("Show")?.episodes?.single { it.id == "1:$targetPath" }

        val first = episodeResolver.findEpisodeById("1:$targetPath")
        val second = episodeResolver.findEpisodeById("1:$targetPath")

        assertEquals(1, first?.seasonNumber)
        assertEquals(3, first?.episodeNumber)
        assertEquals(expectedCached.seasonNumber to expectedCached.episodeNumber, first?.seasonNumber to first?.episodeNumber)
        assertEquals(expectedDetail?.seasonNumber to expectedDetail?.episodeNumber, first?.seasonNumber to first?.episodeNumber)
        assertEquals("Unnumbered B", first?.title)
        assertEquals(targetPath, first?.filePath)
        assertEquals("Unknown B.mkv", first?.fileName)
        assertEquals(first, second)
    }

    @Test
    fun `continue watching skips completed records and attaches progress fields`() = runBlocking {
        val partial = Episode(
            id = "partial",
            animeId = "show",
            episodeNumber = 1,
            duration = 100_000L,
            filePath = "D:/Anime/Show/01.mkv",
            fileName = "01.mkv",
        )
        val complete = partial.copy(id = "complete", episodeNumber = 2)
        val resolver = resolver(
            cachedAnime = mapOf("show" to Anime(id = "show", title = "Show")),
            cachedEpisodes = mapOf("show" to listOf(partial, complete)),
            progressRecords = listOf(
                ProgressRecord("partial", positionMs = 40_000L, lastWatched = 10L, playCount = 1),
                ProgressRecord("complete", positionMs = 95_000L, lastWatched = 20L, playCount = 1),
            ),
        )

        val items = resolver.loadContinueWatchingEpisodes()

        assertEquals(listOf("partial"), items.map { it.progress.episodeId })
        assertEquals(40_000L, items.single().episode.watchedPosition)
        assertEquals(10L, items.single().episode.lastWatchedTimestamp)
        assertEquals(1, items.single().episode.playCount)
    }

    @Test
    fun `continue watching resolves logical episode progress`() = runBlocking {
        val web = Episode(
            id = "web-1",
            animeId = "show",
            episodeNumber = 1,
            duration = 100_000L,
            filePath = "D:/Anime/Show/WEB/01.mkv",
            fileName = "01.mkv",
        )
        val bd = web.copy(
            id = "bd-1",
            filePath = "D:/Anime/Show/BD/01.mkv",
        )
        val resolver = resolver(
            cachedAnime = mapOf("show" to Anime(id = "show", title = "Show")),
            cachedEpisodes = mapOf("show" to listOf(web, bd)),
            progressRecords = listOf(
                ProgressRecord("show#S1E1", positionMs = 40_000L, lastWatched = 10L),
            ),
        )

        val item = resolver.loadContinueWatchingEpisodes().single()

        assertEquals("show#S1E1", item.progress.episodeId)
        assertEquals("show#S1E1", item.episode.progressId)
        assertEquals(2, item.episode.versions.size)
        assertEquals(40_000L, item.episode.watchedPosition)
    }

    @Test
    fun `continue watching deduplicates legacy and logical progress`() = runBlocking {
        val episode = Episode(
            id = "physical-1",
            animeId = "show",
            episodeNumber = 1,
            duration = 100_000L,
            filePath = "D:/Anime/Show/01.mkv",
            fileName = "01.mkv",
        )
        val resolver = resolver(
            cachedAnime = mapOf("show" to Anime(id = "show", title = "Show")),
            cachedEpisodes = mapOf("show" to listOf(episode)),
            progressRecords = listOf(
                ProgressRecord("show#S1E1", positionMs = 50_000L, lastWatched = 20L),
                ProgressRecord("physical-1", positionMs = 40_000L, lastWatched = 10L),
            ),
        )

        val item = resolver.loadContinueWatchingEpisodes().single()

        assertEquals("show#S1E1", item.progress.episodeId)
        assertEquals("show#S1E1", item.episode.progressId)
        assertEquals(50_000L, item.episode.watchedPosition)
    }

    @Test
    fun `continue watching keeps completed logical episode hidden when older progress is partial`() = runBlocking {
        val episode = Episode(
            id = "physical-1",
            animeId = "show",
            episodeNumber = 1,
            duration = 100_000L,
            filePath = "D:/Anime/Show/01.mkv",
            fileName = "01.mkv",
        )
        val resolver = resolver(
            cachedAnime = mapOf("show" to Anime(id = "show", title = "Show")),
            cachedEpisodes = mapOf("show" to listOf(episode)),
            progressRecords = listOf(
                ProgressRecord("physical-1", positionMs = 95_000L, lastWatched = 20L),
                ProgressRecord("show#S1E1", positionMs = 40_000L, lastWatched = 10L),
            ),
        )

        assertTrue(resolver.loadContinueWatchingEpisodes().isEmpty())
    }

    @Test
    fun `continue watching expands candidates when duplicate progress consumes limit`() = runBlocking {
        val episodes = (1..2).map { number ->
            Episode(
                id = "physical-$number",
                animeId = "show",
                episodeNumber = number,
                duration = 100_000L,
                filePath = "D:/Anime/Show/$number.mkv",
                fileName = "$number.mkv",
            )
        }
        val progressLimits = mutableListOf<Int>()
        val resolver = resolver(
            cachedAnime = mapOf("show" to Anime(id = "show", title = "Show")),
            cachedEpisodes = mapOf("show" to episodes),
            progressRecords = listOf(
                ProgressRecord("physical-1", positionMs = 30_000L, lastWatched = 30L),
                ProgressRecord("show#S1E1", positionMs = 20_000L, lastWatched = 20L),
                ProgressRecord("physical-2", positionMs = 10_000L, lastWatched = 10L),
            ),
            progressLimits = progressLimits,
        )

        val items = resolver.loadContinueWatchingEpisodes(limit = 2)

        assertEquals(listOf("show#S1E1", "show#S1E2"), items.map { it.episode.progressId })
        assertEquals(listOf(2, 4), progressLimits)
    }

    @Test
    fun `continue watching bounds progress and caches logical episode lookups`() = runBlocking {
        val episodes = (1..3).map { number ->
            Episode(
                id = "physical-$number",
                animeId = "show",
                episodeNumber = number,
                duration = 100_000L,
                filePath = "D:/Anime/Show/$number.mkv",
                fileName = "$number.mkv",
            )
        }
        val metadataRequests = mutableListOf<String>()
        val episodeListRequests = mutableListOf<String>()
        val progressLimits = mutableListOf<Int>()
        val resolver = resolver(
            cachedAnime = mapOf("show" to Anime(id = "show", title = "Show")),
            cachedEpisodes = mapOf("show" to episodes),
            progressRecords = episodes.mapIndexed { index, episode ->
                ProgressRecord(episode.id, positionMs = 10_000L, lastWatched = 100L - index)
            },
            metadataRequests = metadataRequests,
            episodeListRequests = episodeListRequests,
            progressLimits = progressLimits,
        )

        val items = resolver.loadContinueWatchingEpisodes(limit = 2)

        assertEquals(2, items.size)
        assertEquals(listOf(2), progressLimits)
        assertEquals(listOf("show"), episodeListRequests)
        assertEquals(listOf("show"), metadataRequests)
    }

    @Test
    fun `continue watching resolves indexed records`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/Episode 01.mkv",
                    animeName = "Frieren",
                    episodeNumber = 1,
                ),
            ),
            progressRecords = listOf(
                ProgressRecord("1:D:/Anime/Frieren/Episode 01.mkv", positionMs = 45_000L, lastWatched = 99L),
            ),
        )

        val items = resolver.loadContinueWatchingEpisodes()

        assertEquals("1:D:/Anime/Frieren/Episode 01.mkv", items.single().progress.episodeId)
        assertEquals("1:D:/Anime/Frieren/Episode 01.mkv", items.single().episode.id)
        assertEquals("Frieren", items.single().episode.animeId)
        assertEquals("Frieren", items.single().anime?.id)
        assertEquals(45_000L, items.single().episode.watchedPosition)
    }

    @Test
    fun `continue watching ignores indexed series extras`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val path = "D:/Anime/Frieren/NCOP01.mkv"
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = path,
                    animeName = "Frieren",
                    extraKind = MediaExtraKind.NCOP,
                    extraOrdinal = 1,
                ),
            ),
            progressRecords = listOf(
                ProgressRecord("1:$path", positionMs = 45_000L, lastWatched = 99L),
            ),
        )

        assertNull(resolver.findEpisodeById("1:$path"))
        assertTrue(resolver.loadContinueWatchingEpisodes().isEmpty())
    }

    @Test
    fun `continue watching result preserves progress errors`() = runBlocking {
        val error = AppError.MediaSourceError.NotFound("progress-store")
        val resolver = resolver(
            progressError = error,
        )

        val result = resolver.loadContinueWatchingEpisodesResult()

        assertEquals(Result.failure(error), result)
        assertTrue(resolver.loadContinueWatchingEpisodes().isEmpty())
    }

    private fun resolver(
        sources: List<MediaSourceInfo> = emptyList(),
        entries: List<MediaIndexEntry> = emptyList(),
        cachedAnime: Map<String, Anime> = emptyMap(),
        cachedEpisodes: Map<String, List<Episode>> = emptyMap(),
        progressRecords: List<ProgressRecord> = emptyList(),
        progressError: AppError? = null,
        mergeSameAnimeEnabled: Boolean = false,
        metadataRequests: MutableList<String>? = null,
        episodeListRequests: MutableList<String>? = null,
        progressLimits: MutableList<Int>? = null,
    ): LibraryEpisodeResolver =
        LibraryEpisodeResolver(
            mediaSources = FakeMediaSourceRepository(sources),
            metadata = FakeMetadataRepository(
                cachedAnime,
                cachedEpisodes,
                metadataRequests,
                episodeListRequests,
            ),
            index = FakeMediaIndexRepository(entries),
            progress = FakeProgressRepository(progressRecords, progressError, progressLimits),
            mergeSameAnimeEnabled = { mergeSameAnimeEnabled },
        )

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
                    entry.sourceId == sourceId && (
                        query.isBlank() ||
                            entry.path.contains(query, ignoreCase = true) ||
                            entry.animeName?.contains(query, ignoreCase = true) == true
                    )
                },
            )

        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
            Result.success(entries.filter { it.sourceId == sourceId }.mapNotNull { it.animeName }.distinct())

        override suspend fun clearIndex(sourceId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
            Result.success(Unit)

        override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> =
            Result.success(emptyList())

        override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeMetadataRepository(
        private val cachedAnime: Map<String, Anime>,
        private val cachedEpisodes: Map<String, List<Episode>>,
        private val metadataRequests: MutableList<String>? = null,
        private val episodeListRequests: MutableList<String>? = null,
    ) : MetadataRepository {
        override suspend fun cacheMetadata(anime: Anime): Result<Unit> =
            Result.success(Unit)

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> {
            metadataRequests?.add(animeId)
            return Result.success(cachedAnime[animeId])
        }

        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
            Result.success(animeIds.mapNotNull(cachedAnime::get))

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(cachedEpisodes.values.flatten().firstOrNull { it.id == episodeId })

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> {
            episodeListRequests?.add(animeId)
            return Result.success(cachedEpisodes[animeId].orEmpty())
        }

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> =
            Result.success(Unit)

        override suspend fun invalidateCache(animeId: String): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeProgressRepository(
        private val records: List<ProgressRecord>,
        private val error: AppError?,
        private val requestedLimits: MutableList<Int>? = null,
    ) : PlaybackProgressRepository {
        override suspend fun saveProgress(
            episodeId: String,
            positionMs: Long,
            lastWatched: Long,
            incrementPlayCount: Boolean,
        ): Result<Unit> =
            Result.success(Unit)

        override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> =
            Result.success(records.firstOrNull { it.episodeId == episodeId })

        override suspend fun getAllProgress(): Result<List<ProgressRecord>> =
            Result.success(records)

        override suspend fun deleteProgress(episodeId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> {
            requestedLimits?.add(limit)
            return error?.let { Result.failure(it) } ?: Result.success(records.take(limit))
        }
    }
}
