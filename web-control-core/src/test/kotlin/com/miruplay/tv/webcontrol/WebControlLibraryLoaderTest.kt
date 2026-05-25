package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebControlLibraryLoaderTest {
    @Test
    fun `library includes indexed poster groups without cached metadata`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val loader = loader(
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

        val library = loader.loadLibrary()

        assertEquals(listOf("Frieren"), library.allAnime.map { it.id })
        assertEquals("Frieren", library.allAnime.single().title)
        assertEquals(1, library.allAnime.single().episodeCount)
        assertEquals("A quiet journey.", library.allAnime.single().summary)
    }

    @Test
    fun `library and search prefer cached metadata for indexed groups`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val loader = loader(
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
                    titleCn = "葬送的芙莉莲",
                    episodeCount = 28,
                    posterUrl = "https://img.example/frieren.jpg",
                ),
            ),
            mergeSameAnimeEnabled = true,
        )

        val library = loader.loadLibrary()
        val search = loader.searchLibrary("芙莉莲")

        assertEquals("葬送的芙莉莲", library.allAnime.single().titleCn)
        assertEquals(28, library.allAnime.single().episodeCount)
        assertEquals(listOf("bgm-123"), search.allAnime.map { it.id })
    }

    @Test
    fun `library honors shared same anime merge keys`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val entries = listOf(
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Show/S1E01.mkv",
                animeName = "Show S1",
                episodeNumber = 1,
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
            ),
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Show/S2E01.mkv",
                animeName = "Show S2",
                episodeNumber = 1,
                metadataId = "bgm-1",
                metadataTitle = "Shared Show",
            ),
        )

        val split = loader(sources = listOf(source), entries = entries, mergeSameAnimeEnabled = false).loadLibrary()
        val merged = loader(sources = listOf(source), entries = entries, mergeSameAnimeEnabled = true).loadLibrary()

        assertEquals(2, split.allAnime.size)
        assertEquals(listOf("bgm-1"), merged.allAnime.map { it.id })
        assertEquals(2, merged.allAnime.single().episodeCount)
    }

    @Test
    fun `detail falls back to indexed episodes with shared playable uri`() = runBlocking {
        val source = MediaSourceInfoConventions.webDav(
            url = "https://dav.example/anime",
            name = "DAV",
        ).copy(id = 2L)
        val loader = loader(
            sources = listOf(source),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 2L,
                    path = "/Frieren/Episode 02.mkv",
                    animeName = "Frieren",
                    episodeNumber = 2,
                    metadataId = "frieren",
                    metadataTitle = "Frieren",
                ),
                MediaIndexEntry(
                    sourceId = 2L,
                    path = "/Frieren/Episode 01.mkv",
                    animeName = "Frieren",
                    episodeNumber = 1,
                    metadataId = "frieren",
                    metadataTitle = "Frieren",
                ),
            ),
            mergeSameAnimeEnabled = true,
        )

        val detail = loader.loadAnimeDetail("frieren")

        assertEquals("Frieren", detail.anime.title)
        assertEquals(listOf("2:/Frieren/Episode 01.mkv", "2:/Frieren/Episode 02.mkv"), detail.episodes.map { it.episode.id })
        assertEquals("https://dav.example/anime/Frieren/Episode%2001.mkv", detail.episodes.first().episode.filePath)
    }

    @Test
    fun `continue watching resolves indexed episode and poster fallback anime`() = runBlocking {
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val loader = loader(
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
                ProgressRecord(
                    episodeId = "1:D:/Anime/Frieren/Episode 01.mkv",
                    positionMs = 45_000L,
                    lastWatched = 99L,
                ),
            ),
        )

        val item = loader.loadContinueWatching().single()

        assertEquals("1:D:/Anime/Frieren/Episode 01.mkv", item.progressEpisodeId)
        assertNotNull(item.episode)
        assertEquals("Frieren", item.anime?.id)
        assertEquals(45_000L, item.positionMs)
    }

    @Test
    fun `find episode falls back to cached metadata when indexed lookup misses`() = runBlocking {
        val cached = Episode(
            id = "cached-episode",
            animeId = "cached",
            episodeNumber = 1,
            filePath = "D:/Anime/Cached/Episode 01.mkv",
            fileName = "Episode 01.mkv",
        )
        val loader = loader(
            cachedAnime = mapOf("cached" to Anime(id = "cached", title = "Cached")),
            cachedEpisodes = mapOf("cached" to listOf(cached)),
        )

        assertEquals(cached, loader.findEpisodeById("cached-episode"))
        assertNull(loader.findEpisodeById("missing"))
    }

    private fun loader(
        sources: List<MediaSourceInfo> = emptyList(),
        entries: List<MediaIndexEntry> = emptyList(),
        cachedAnime: Map<String, Anime> = emptyMap(),
        cachedEpisodes: Map<String, List<Episode>> = emptyMap(),
        progressRecords: List<ProgressRecord> = emptyList(),
        mergeSameAnimeEnabled: Boolean = false,
    ): WebControlLibraryLoader {
        val metadata = FakeMetadataRepository(cachedAnime, cachedEpisodes)
        return WebControlLibraryLoader(
            mediaSources = FakeMediaSourceRepository(sources),
            metadata = metadata,
            index = FakeMediaIndexRepository(entries),
            progress = FakeProgressRepository(progressRecords),
            mergeSameAnimeEnabled = { mergeSameAnimeEnabled },
        )
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

    private class FakeProgressRepository(
        private val records: List<ProgressRecord>,
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

        override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> =
            Result.success(records.take(limit))
    }
}
