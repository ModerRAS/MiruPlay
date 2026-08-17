package com.miruplay.tv.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.data.dao.AnimeDao
import com.miruplay.tv.data.dao.DramaSeriesCacheDao
import com.miruplay.tv.data.dao.EpisodeDao
import com.miruplay.tv.data.db.MiruPlayDatabase
import com.miruplay.tv.data.entity.AnimeEntity
import com.miruplay.tv.data.entity.EpisodeEntity
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.repository.dramaSeriesCacheKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataRepositoryImplTest {

    private lateinit var database: MiruPlayDatabase
    private lateinit var repository: MetadataRepositoryImpl
    private lateinit var animeDao: AnimeDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var dramaSeriesCacheDao: DramaSeriesCacheDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MiruPlayDatabase::class.java
        ).build()
        animeDao = database.animeDao()
        episodeDao = database.episodeDao()
        dramaSeriesCacheDao = database.dramaSeriesCacheDao()
        repository = MetadataRepositoryImpl(
            animeDao = animeDao,
            episodeDao = episodeDao,
            dramaSeriesCacheDao = dramaSeriesCacheDao,
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createTestAnime(
        id: String = "test-anime-1",
        title: String = "Test Anime",
        genres: List<String> = listOf("Action", "Comedy"),
        posterUrl: String = "https://example.com/poster.jpg",
        posterLocalPath: String? = null,
    ): Anime = Anime(
        id = id,
        title = title,
        titleCn = "测试动漫",
        summary = "A test anime for unit testing",
        genres = genres,
        studio = "Test Studio",
        director = "Test Director",
        episodeCount = 12,
        airDate = "2024-01",
        rating = 8.5f,
        bangumiId = 12345,
        anilistId = 67890,
        tmdbId = 11111,
        posterUrl = posterUrl,
        posterLocalPath = posterLocalPath,
        fanartUrl = "https://example.com/fanart.jpg"
    )

    private fun createTestEpisode(
        id: String = "ep-1",
        animeId: String = "test-anime-1",
        seasonNumber: Int = 1,
        episodeNumber: Int = 1,
        filePath: String = "/storage/anime/ep01.mkv"
    ): Episode = Episode(
        id = id,
        animeId = animeId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = "Episode $episodeNumber",
        filePath = filePath,
        fileName = "ep${episodeNumber.toString().padStart(2, '0')}.mkv",
        duration = 1_440_000L
    )

    private fun insertEpisodeEntities(animeId: String, count: Int = 3) {
        val entities = (1..count).map { i ->
            EpisodeEntity(
                id = "ep-$animeId-$i",
                animeId = animeId,
                seasonNumber = 1,
                episodeNumber = i,
                title = "Episode $i",
                filePath = "/storage/$animeId/ep${i.toString().padStart(2, '0')}.mkv",
                fileName = "ep${i.toString().padStart(2, '0')}.mkv",
                duration = 1_440_000L
            )
        }
        runBlocking { episodeDao.insertAll(entities) }
    }

    // ── cacheMetadata ────────────────────────────────────────────

    @Test
    fun `cacheMetadata should insert anime entity`() = runBlocking {
        val anime = createTestAnime()
        val result = repository.cacheMetadata(anime)
        assertTrue("Expected Success", result.isSuccess())

        val cached = animeDao.getById(anime.id)
        assertNotNull("Anime entity should be in DB", cached)
        assertEquals(anime.title, cached!!.title)
        assertEquals(anime.titleCn, cached.titleCn)
        assertEquals(anime.rating, cached.rating)
    }

    @Test
    fun `cacheMetadata should upsert existing anime`() = runBlocking {
        val anime = createTestAnime()
        repository.cacheMetadata(anime)

        val updated = anime.copy(title = "Updated Title", rating = 9.0f)
        repository.cacheMetadata(updated)

        val cached = animeDao.getById(anime.id)
        assertEquals("Updated Title", cached!!.title)
        assertEquals(9.0f, cached.rating)
    }

    @Test
    fun `cacheMetadata should serialize genres as JSON`() = runBlocking {
        val anime = createTestAnime(genres = listOf("Action", "Sci-Fi", "Mecha"))
        repository.cacheMetadata(anime)

        val cached = animeDao.getById(anime.id)
        assertNotNull("Genres should be stored", cached!!.genres)
        assertTrue("Genres should contain Action", cached.genres!!.contains("Action"))
        assertTrue("Genres should contain Sci-Fi", cached.genres!!.contains("Sci-Fi"))
    }

    @Test
    fun `cacheMetadata should handle empty genres`() = runBlocking {
        val anime = createTestAnime(genres = emptyList())
        repository.cacheMetadata(anime)

        val cached = animeDao.getById(anime.id)
        assertNull("Genres should be null for empty list", cached!!.genres)
    }

    @Test
    fun `cacheMetadata should convert Int ids to String`() = runBlocking {
        val anime = createTestAnime()
        repository.cacheMetadata(anime)

        val cached = animeDao.getById(anime.id)
        assertEquals("12345", cached!!.bangumiId)
        assertEquals("67890", cached.anilistId)
        assertEquals("11111", cached.tmdbId)
    }

    @Test
    fun `cacheMetadata should handle null optional fields`() = runBlocking {
        val anime = Anime(
            id = "minimal-anime",
            title = "Minimal"
        )
        repository.cacheMetadata(anime)

        val cached = animeDao.getById("minimal-anime")
        assertNotNull(cached)
        assertNull(cached!!.titleCn)
        assertNull(cached.summary)
        assertNull(cached.bangumiId)
    }

    // ── getCachedMetadata ────────────────────────────────────────

    @Test
    fun `getCachedMetadata should return cached anime`() = runBlocking {
        val anime = createTestAnime()
        repository.cacheMetadata(anime)

        val result = repository.getCachedMetadata(anime.id)
        assertTrue("Expected Success", result.isSuccess())
        val cached = result.getOrNull()
        assertNotNull("Should return cached anime", cached)
        assertEquals(anime.title, cached!!.title)
        assertEquals(anime.genres, cached.genres)
        assertEquals(anime.bangumiId, cached.bangumiId)
    }

    @Test
    fun `getCachedMetadata should return null for missing anime`() = runBlocking {
        val result = repository.getCachedMetadata("nonexistent")
        assertTrue("Expected Success", result.isSuccess())
        assertNull("Should return null", result.getOrNull())
    }

    @Test
    fun `getCachedMetadata should return stale cached anime`() = runBlocking {
        val anime = createTestAnime()
        repository.cacheMetadata(anime)

        runBlocking {
            val oldEntity = animeDao.getById(anime.id)!!.copy(
                lastUpdated = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
            )
            animeDao.insert(oldEntity)
        }

        val result = repository.getCachedMetadata(anime.id)
        assertTrue("Expected Success", result.isSuccess())
        assertEquals(anime.id, result.getOrNull()?.id)
        assertEquals(anime.posterUrl, result.getOrNull()?.posterUrl)
    }

    @Test
    fun `getCachedMetadata should return anime with genre list`() = runBlocking {
        val anime = createTestAnime(genres = listOf("Action", "Comedy"))
        repository.cacheMetadata(anime)

        val cached = repository.getCachedMetadata(anime.id).getOrNull()
        assertNotNull(cached)
        assertEquals(listOf("Action", "Comedy"), cached!!.genres)
    }

    @Test
    fun `getCachedMetadata should handle String id conversion back to Int`() = runBlocking {
        val anime = createTestAnime()
        repository.cacheMetadata(anime)

        val cached = repository.getCachedMetadata(anime.id).getOrNull()
        assertEquals(12345, cached!!.bangumiId)
        assertEquals(67890, cached.anilistId)
        assertEquals(11111, cached.tmdbId)
    }

    @Test
    fun `getCachedMetadataByBangumiId should return poster binding`() = runBlocking {
        val anime = createTestAnime(
            id = "local-cache-key",
            posterUrl = "https://example.com/bangumi-poster.jpg",
            posterLocalPath = "/cache/poster"
        )
        repository.cacheMetadata(anime)

        val cached = repository.getCachedMetadataByBangumiId(12345).getOrNull()

        assertEquals("local-cache-key", cached?.id)
        assertEquals("https://example.com/bangumi-poster.jpg", cached?.posterUrl)
        assertEquals("/cache/poster", cached?.posterLocalPath)
    }

    @Test
    fun `getCachedMetadataByBangumiId should prefer binding with poster`() = runBlocking {
        repository.cacheMetadata(
            createTestAnime(
                id = "with-poster",
                posterUrl = "https://example.com/bangumi-poster.jpg",
                posterLocalPath = "/cache/poster"
            )
        )
        repository.cacheMetadata(
            createTestAnime(
                id = "without-poster",
                posterUrl = "",
                posterLocalPath = null
            )
        )

        val cached = repository.getCachedMetadataByBangumiId(12345).getOrNull()

        assertEquals("with-poster", cached?.id)
        assertEquals("https://example.com/bangumi-poster.jpg", cached?.posterUrl)
    }

    @Test
    fun `getCachedMetadata collection should return cached anime with episodes`() = runBlocking {
        repository.cacheMetadata(createTestAnime(id = "anime-a", title = "Anime A"))
        repository.cacheMetadata(createTestAnime(id = "anime-b", title = "Anime B"))
        insertEpisodeEntities("anime-a", count = 2)
        insertEpisodeEntities("anime-b", count = 1)

        val cached = repository.getCachedMetadata(listOf("anime-a", "anime-b", "missing")).getOrNull()!!

        assertEquals(2, cached.size)
        assertEquals(2, cached.first { it.id == "anime-a" }.episodeCount)
        assertEquals(1, cached.first { it.id == "anime-b" }.episodeCount)
    }

    @Test
    fun `getCachedMetadata collection should count logical episodes instead of files`() = runBlocking {
        repository.cacheMetadata(createTestAnime(id = "anime-a", title = "Anime A"))
        val episodes = listOf(
            createTestEpisode(id = "ep-1-web", animeId = "anime-a", episodeNumber = 1),
            createTestEpisode(id = "ep-1-bd", animeId = "anime-a", episodeNumber = 1),
            createTestEpisode(id = "ep-2", animeId = "anime-a", episodeNumber = 2),
            createTestEpisode(id = "s2-ep-1", animeId = "anime-a", seasonNumber = 2, episodeNumber = 1),
        )

        repository.cacheEpisodes("anime-a", episodes)
        val cached = repository.getCachedMetadata(listOf("anime-a")).getOrNull()!!.single()

        assertEquals(3, cached.episodeCount)
        assertEquals(3, animeDao.getById("anime-a")?.episodeCount)
        assertEquals(4, repository.getCachedEpisodes("anime-a").getOrNull()!!.size)
    }

    @Test
    fun `getCachedMetadata collection should keep stale anime`() = runBlocking {
        repository.cacheMetadata(createTestAnime(id = "fresh-anime"))
        repository.cacheMetadata(createTestAnime(id = "expired-anime"))
        val expiredEntity = animeDao.getById("expired-anime")!!.copy(
            lastUpdated = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
        )
        animeDao.insert(expiredEntity)

        val cached = repository.getCachedMetadata(listOf("fresh-anime", "expired-anime")).getOrNull()!!

        assertEquals(setOf("fresh-anime", "expired-anime"), cached.map { it.id }.toSet())
    }

    // ── getCachedEpisodes ────────────────────────────────────────

    @Test
    fun `getCachedEpisodes should return episodes sorted`() = runBlocking {
        repository.cacheMetadata(createTestAnime())
        insertEpisodeEntities("test-anime-1", count = 3)

        val result = repository.getCachedEpisodes("test-anime-1")
        assertTrue("Expected Success", result.isSuccess())
        val episodes = result.getOrNull()!!
        assertEquals(3, episodes.size)
        // Should be sorted by season_number ASC, episode_number ASC
        assertEquals(1, episodes[0].episodeNumber)
        assertEquals(2, episodes[1].episodeNumber)
        assertEquals(3, episodes[2].episodeNumber)
    }

    @Test
    fun `getCachedEpisodes should return empty for unknown anime`() = runBlocking {
        val result = repository.getCachedEpisodes("nonexistent")
        assertTrue("Expected Success", result.isSuccess())
        assertTrue("Should be empty", result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `getCachedEpisodes should return correct episode fields`() = runBlocking {
        repository.cacheMetadata(createTestAnime())

        val episode = createTestEpisode(
            id = "ep-specific",
            animeId = "test-anime-1",
            seasonNumber = 2,
            episodeNumber = 5
        )
        val entity = EpisodeEntity(
            id = episode.id,
            animeId = episode.animeId,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            title = "Special Episode",
            filePath = episode.filePath,
            fileName = episode.fileName,
            duration = 1_800_000L,
            thumbnailPath = "/thumbnails/ep5.jpg"
        )
        runBlocking { episodeDao.insertAll(listOf(entity)) }

        val episodes = repository.getCachedEpisodes("test-anime-1").getOrNull()!!
        assertEquals(1, episodes.size)
        val ep = episodes[0]
        assertEquals("ep-specific", ep.id)
        assertEquals("test-anime-1", ep.animeId)
        assertEquals(2, ep.seasonNumber)
        assertEquals(5, ep.episodeNumber)
        assertEquals("Special Episode", ep.title)
        assertEquals(1_800_000L, ep.duration)
        assertEquals("/thumbnails/ep5.jpg", ep.thumbnailPath)
    }

    @Test
    fun `cacheDramaSeries should persist provider neutral binding in dedicated cache`() = runBlocking {
        val series = DramaSeries(
            id = "qing-yu-nian",
            title = "庆余年 第一季",
            originalTitle = "Joy of Life",
            summary = "缓存简介",
            seasonCount = 1,
            episodeCount = 46,
            posterUrl = "poster-url",
            fanartUrl = "fanart-url",
            firstAirDate = "2024-01-01",
            metadataProviderRef = MetadataProviderRef(source = "TVMaze", id = "maze-321"),
        )

        val result = repository.cacheDramaSeries(series.id, series)

        assertTrue("Expected Success", result.isSuccess())
        val cached = repository.getCachedDramaSeries(series.id).getOrNull()
        assertEquals("TVMaze", cached?.metadataProviderRef?.source)
        assertEquals("maze-321", cached?.metadataProviderRef?.id)
        assertNull(cached?.tmdbId)
        assertEquals("缓存简介", cached?.summary)
        val legacy = animeDao.getById(dramaSeriesCacheKey(series.id))
        assertNotNull(legacy)
        assertNull(legacy?.tmdbId)
    }

    @Test
    fun `getCachedDramaSeries should fall back to legacy anime cache when dedicated cache is missing`() = runBlocking {
        animeDao.insert(
            AnimeEntity(
                id = dramaSeriesCacheKey("qing-yu-nian"),
                title = "庆余年 第一季",
                titleCn = "Joy of Life",
                summary = "旧缓存简介",
                episodeCount = 46,
                airDate = "2024-01-01",
                tmdbId = "321",
                posterUrl = "poster-url",
                fanartUrl = "fanart-url",
            ),
        )

        val cached = repository.getCachedDramaSeries("qing-yu-nian").getOrNull()

        assertEquals("庆余年 第一季", cached?.title)
        assertEquals("TMDB", cached?.metadataProviderRef?.source)
        assertEquals("321", cached?.metadataProviderRef?.id)
        assertEquals(321, cached?.tmdbId)
    }

    @Test
    fun `invalidateDramaSeriesCache should delete dedicated and legacy drama cache`() = runBlocking {
        repository.cacheDramaSeries(
            "qing-yu-nian",
            DramaSeries(
                id = "qing-yu-nian",
                title = "庆余年 第一季",
                metadataProviderRef = MetadataProviderRef(source = "TVMaze", id = "maze-321"),
            ),
        )

        val result = repository.invalidateDramaSeriesCache("qing-yu-nian")

        assertTrue("Invalidate should succeed", result.isSuccess())
        assertNull(dramaSeriesCacheDao.getBySeriesId("qing-yu-nian"))
        assertNull(animeDao.getById(dramaSeriesCacheKey("qing-yu-nian")))
    }

    // ── invalidateCache ──────────────────────────────────────────

    @Test
    fun `invalidateCache should delete anime and episodes`() = runBlocking {
        repository.cacheMetadata(createTestAnime())
        insertEpisodeEntities("test-anime-1", count = 3)

        val result = repository.invalidateCache("test-anime-1")
        assertTrue("Invalidate should succeed", result.isSuccess())

        val anime = animeDao.getById("test-anime-1")
        assertNull("Anime should be deleted", anime)

        val episodes = episodeDao.getByAnimeId("test-anime-1")
        assertTrue("Episodes should be deleted", episodes.isEmpty())
    }

    @Test
    fun `invalidateCache should succeed for non-existent anime`() = runBlocking {
        val result = repository.invalidateCache("nonexistent")
        assertTrue("Should succeed even if nothing to delete", result.isSuccess())
    }

    @Test
    fun `invalidateCache should not delete other anime data`() = runBlocking {
        repository.cacheMetadata(createTestAnime(id = "anime-a"))
        repository.cacheMetadata(createTestAnime(id = "anime-b"))
        insertEpisodeEntities("anime-a", count = 2)
        insertEpisodeEntities("anime-b", count = 2)

        repository.invalidateCache("anime-a")

        // Anime B should remain
        assertNotNull(animeDao.getById("anime-b"))
        assertEquals(2, episodeDao.getByAnimeId("anime-b").size)

        // Anime A should be gone
        assertNull(animeDao.getById("anime-a"))
        assertTrue(episodeDao.getByAnimeId("anime-a").isEmpty())
    }
}
