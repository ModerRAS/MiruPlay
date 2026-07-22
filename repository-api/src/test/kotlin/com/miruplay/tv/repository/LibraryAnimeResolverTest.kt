package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAnimeResolverTest {
    @Test
    fun `loadAnime falls back to indexed poster groups`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/Episode 01.mkv",
                    animeName = "Frieren",
                    episodeNumber = 1,
                    plot = "A quiet journey.",
                ),
            ),
        )

        val anime = resolver.loadAnime().single()

        assertEquals("Frieren", anime.id)
        assertEquals("Frieren", anime.title)
        assertEquals(1, anime.episodeCount)
        assertEquals("A quiet journey.", anime.summary)
    }

    @Test
    fun `loadAnime prefers cached metadata`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/Episode 01.mkv",
                    animeName = "Frieren",
                    metadataId = "bgm-123",
                    metadataTitle = "Sousou no Frieren",
                ),
            ),
            cachedAnime = mapOf(
                "bgm-123" to Anime(
                    id = "bgm-123",
                    title = "Sousou no Frieren",
                    titleCn = "葬送的芙莉莲",
                    episodeCount = 28,
                ),
            ),
            mergeSameAnimeEnabled = true,
        )

        val anime = resolver.loadAnime().single()

        assertEquals("bgm-123", anime.id)
        assertEquals("葬送的芙莉莲", anime.titleCn)
        assertEquals(28, anime.episodeCount)
    }

    @Test
    fun `loadAnime prefers bound metadata id over stale title cache without merging`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/Episode 01.mkv",
                    animeName = "Frieren",
                    metadataId = "mlip:1:series-uuid",
                    metadataTitle = "Updated Frieren",
                ),
            ),
            cachedAnime = mapOf(
                "Frieren" to Anime(id = "Frieren", title = "Stale title", summary = "Old metadata"),
                "mlip:1:series-uuid" to Anime(id = "mlip:1:series-uuid", title = "Updated Frieren"),
            ),
        )

        val anime = resolver.loadAnime().single()

        assertEquals("mlip:1:series-uuid", anime.id)
        assertEquals("Updated Frieren", anime.title)
    }

    @Test
    fun `loadAnime prefers preserved MLIP key over legacy external override`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/Episode 01.mkv",
                    animeName = "Stale external title",
                    metadataSource = "Bangumi",
                    metadataId = "431767",
                    scrapeMessage = localMetadataOverrideMessage("mlip:1:series-uuid"),
                ),
            ),
            cachedAnime = mapOf(
                "431767" to Anime(id = "431767", title = "Stale external title"),
                "mlip:1:series-uuid" to Anime(id = "mlip:1:series-uuid", title = "Authoritative MLIP title"),
            ),
        )

        val anime = resolver.loadAnime().single()

        assertEquals("mlip:1:series-uuid", anime.id)
        assertEquals("Authoritative MLIP title", anime.title)
    }

    @Test
    fun `loadAnime uses anime name as cache key when index has no metadata id`() = runBlocking {
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
            cachedAnime = mapOf(
                "Frieren" to Anime(
                    id = "Frieren",
                    title = "Sousou no Frieren",
                    episodeCount = 28,
                ),
            ),
        )

        val anime = resolver.loadAnime().single()

        assertEquals("Sousou no Frieren", anime.title)
        assertEquals(28, anime.episodeCount)
    }

    @Test
    fun `loadDisplayAnime merges same anime when enabled`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val entries = listOf(
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Show/S1E01.mkv",
                animeName = "Show Season 1",
                episodeNumber = 1,
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
            ),
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Show/S2E01.mkv",
                animeName = "Show Season 2",
                episodeNumber = 1,
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
            ),
        )

        val split = resolver(sources = listOf(source), entries = entries, mergeSameAnimeEnabled = false).loadDisplayAnime()
        val merged = resolver(sources = listOf(source), entries = entries, mergeSameAnimeEnabled = true).loadDisplayAnime()

        assertEquals(2, split.size)
        assertEquals(listOf("bgm-1"), merged.map { it.id })
        assertEquals(2, merged.single().episodeCount)
    }

    @Test
    fun `loadAnimeDetail falls back to indexed anime and episodes`() = runBlocking {
        val source = MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 2L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(sourceId = 2L, path = "/Frieren/Episode 02.mkv", animeName = "Frieren", episodeNumber = 2),
                MediaIndexEntry(sourceId = 2L, path = "/Frieren/Episode 01.mkv", animeName = "Frieren", episodeNumber = 1),
            ),
        )

        val detail = resolver.loadAnimeDetail("Frieren")

        assertEquals("Frieren", detail?.anime?.id)
        assertEquals(2, detail?.anime?.episodeCount)
        assertEquals(listOf("2:/Frieren/Episode 01.mkv", "2:/Frieren/Episode 02.mkv"), detail?.episodes?.map { it.id })
        assertEquals("https://dav.example/anime/Frieren/Episode%2001.mkv", detail?.episodes?.first()?.filePath)
    }

    @Test
    fun `loadAnimeDetail globally orders extras merged from multiple sources`() = runBlocking {
        val second = MediaSourceInfoConventions.webDav(url = "https://dav2.example/anime", name = "DAV 2").copy(id = 2L)
        val first = MediaSourceInfoConventions.webDav(url = "https://dav1.example/anime", name = "DAV 1").copy(id = 1L)
        val shared = listOf(
            MediaIndexEntry(
                sourceId = 2L,
                path = "/Show/01.mkv",
                animeName = "Show Source 2",
                episodeNumber = 1,
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
            ),
            MediaIndexEntry(
                sourceId = 2L,
                path = "/Show/NCOP01.mkv",
                animeName = "Show Source 2",
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
                episodeTitle = "NCOP 01",
                extraKind = MediaExtraKind.NCOP,
                extraOrdinal = 1,
                extraSortOrder = 1,
            ),
            MediaIndexEntry(
                sourceId = 1L,
                path = "/Show/01.mkv",
                animeName = "Show Source 1",
                episodeNumber = 1,
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
            ),
            MediaIndexEntry(
                sourceId = 1L,
                path = "/Show/OVA.mkv",
                animeName = "Show Source 1",
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
                episodeTitle = "OVA",
                extraKind = MediaExtraKind.OVA,
                extraOrdinal = 1,
                extraSortOrder = 1,
            ),
        )
        val resolver = resolver(
            sources = listOf(second, first),
            entries = shared,
            cachedAnime = mapOf("bgm-1" to Anime(id = "bgm-1", title = "Shared Show")),
            mergeSameAnimeEnabled = true,
        )

        val detail = resolver.loadAnimeDetail("bgm-1")

        assertEquals(listOf("OVA", "NCOP 01"), detail?.extras?.map(Episode::title))
    }

    @Test
    fun `loadAnimeDetail prefers cached metadata and indexed episodes`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val resolver = resolver(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/Episode 01.mkv",
                    animeName = "Frieren",
                    episodeNumber = 1,
                    metadataId = "bgm-123",
                    metadataTitle = "Sousou no Frieren",
                ),
            ),
            cachedAnime = mapOf(
                "bgm-123" to Anime(
                    id = "bgm-123",
                    title = "Sousou no Frieren",
                    episodeCount = 28,
                    posterUrl = "https://img.example/frieren.jpg",
                ),
            ),
            mergeSameAnimeEnabled = true,
        )

        val detail = resolver.loadAnimeDetail("bgm-123")

        assertEquals("Sousou no Frieren", detail?.anime?.title)
        assertEquals(28, detail?.anime?.episodeCount)
        assertEquals(listOf("1:D:/Anime/Frieren/Episode 01.mkv"), detail?.episodes?.map { it.id })
    }

    @Test
    fun `loadAnimeDetail loads cached episodes without indexed group`() = runBlocking {
        val episode = Episode(
            id = "cached-episode",
            animeId = "cached",
            episodeNumber = 1,
            filePath = "D:/Anime/Cached/Episode 01.mkv",
            fileName = "Episode 01.mkv",
        )
        val resolver = resolver(
            cachedAnime = mapOf("cached" to Anime(id = "cached", title = "Cached", episodeCount = 1)),
            cachedEpisodes = mapOf("cached" to listOf(episode)),
        )

        val detail = resolver.loadAnimeDetail("cached")

        assertEquals("Cached", detail?.anime?.title)
        assertEquals(1, detail?.episodes?.size)
        assertEquals("cached#S1E1", detail?.episodes?.single()?.progressId)
        assertEquals(listOf("cached-episode"), detail?.episodes?.single()?.versions?.map { it.episodeId })
    }

    @Test
    fun `loadEpisodesForAnime falls back to indexed episodes with playable uri`() = runBlocking {
        val source = MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 2L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 2L, path = "/Frieren/Episode 02.mkv", animeName = "Frieren", episodeNumber = 2),
            MediaIndexEntry(sourceId = 2L, path = "/Frieren/Episode 01.mkv", animeName = "Frieren", episodeNumber = 1),
        )
        val resolver = resolver(sources = listOf(source), entries = entries)
        val group = resolver.loadIndexedGroups().single()

        val episodes = resolver.loadEpisodesForAnime(group.toAnime(), group)

        assertEquals(listOf("2:/Frieren/Episode 01.mkv", "2:/Frieren/Episode 02.mkv"), episodes.map { it.id })
        assertEquals("https://dav.example/anime/Frieren/Episode%2001.mkv", episodes.first().filePath)
    }

    private fun resolver(
        sources: List<MediaSourceInfo> = emptyList(),
        entries: List<MediaIndexEntry> = emptyList(),
        cachedAnime: Map<String, Anime> = emptyMap(),
        cachedEpisodes: Map<String, List<Episode>> = emptyMap(),
        mergeSameAnimeEnabled: Boolean = false,
    ): LibraryAnimeResolver =
        LibraryAnimeResolver(
            mediaSources = FakeMediaSourceRepository(sources),
            metadata = FakeMetadataRepository(cachedAnime, cachedEpisodes),
            index = FakeMediaIndexRepository(entries),
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
    ) : MetadataRepository {
        override suspend fun cacheMetadata(anime: Anime): Result<Unit> =
            Result.success(Unit)

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
            Result.success(cachedAnime[animeId])

        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
            Result.success(animeIds.mapNotNull(cachedAnime::get))

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(cachedEpisodes.values.flatten().firstOrNull { it.id == episodeId })

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(cachedEpisodes[animeId].orEmpty())

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> =
            Result.success(Unit)

        override suspend fun invalidateCache(animeId: String): Result<Unit> =
            Result.success(Unit)
    }
}
