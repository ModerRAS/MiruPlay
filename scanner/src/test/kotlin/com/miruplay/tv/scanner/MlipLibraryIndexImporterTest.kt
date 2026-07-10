package com.miruplay.tv.scanner

import android.database.sqlite.SQLiteDatabase
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaRecognitionMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.MlipMetadataMode
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaScrapeStatus
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.localMetadataOverrideKey
import com.miruplay.tv.repository.localMetadataOverrideMessage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MlipLibraryIndexImporterTest {
    @Test
    fun `media paths become rooted webdav index paths`() {
        assertEquals("/Series/01 [1080P].mkv", normalizeMlipMediaPath("Series/01 [1080P].mkv"))
        assertEquals("/Series/Season 2/01.mkv", normalizeMlipMediaPath("/Series\\Season 2/01.mkv"))
    }

    @Test
    fun `media paths reject traversal and absolute urls`() {
        assertNull(normalizeMlipMediaPath("../Series/01.mkv"))
        assertNull(normalizeMlipMediaPath("Series/../01.mkv"))
        assertNull(normalizeMlipMediaPath("https://dav.example.test/Series/01.mkv"))
    }

    @Test
    fun `artwork paths allow relative paths and remote urls`() {
        assertEquals("/Series/poster.jpg", normalizeMlipArtworkPath("Series/poster.jpg"))
        assertEquals("https://img.example.test/poster.jpg", normalizeMlipArtworkPath("https://img.example.test/poster.jpg"))
        assertNull(normalizeMlipArtworkPath("../poster.jpg"))
    }

    @Test
    fun `unsupported mlip version fails clearly`() = runBlocking {
        val databaseFile = File.createTempFile("mlip-test-", ".db")
        try {
            createMlipDatabase(databaseFile, userVersion = 2)

            val result = importDatabase(databaseFile)

            assertTrue(result is Result.Error)
            assertTrue((result as Result.Error).error is AppError.LibraryIndexError.UnsupportedVersion)
        } finally {
            databaseFile.delete()
        }
    }

    @Test
    fun `missing mlip tables fail clearly`() = runBlocking {
        val databaseFile = File.createTempFile("mlip-test-", ".db")
        try {
            createMlipDatabase(databaseFile, includeRequiredTables = false)

            val result = importDatabase(databaseFile)

            assertTrue(result is Result.Error)
            assertTrue((result as Result.Error).error is AppError.LibraryIndexError.InvalidSchema)
        } finally {
            databaseFile.delete()
        }
    }

    @Test
    fun `unsafe media paths fail clearly`() = runBlocking {
        val databaseFile = File.createTempFile("mlip-test-", ".db")
        try {
            createMlipDatabase(databaseFile, mediaPath = "../Series/01.mkv")

            val result = importDatabase(databaseFile)

            assertTrue(result is Result.Error)
            assertTrue((result as Result.Error).error is AppError.LibraryIndexError.InvalidSchema)
        } finally {
            databaseFile.delete()
        }
    }

    @Test
    fun `files only policy imports paths and skips library metadata cache`() = runBlocking {
        val databaseFile = File.createTempFile("mlip-test-", ".db")
        val source = mlipSource(metadataMode = MlipMetadataMode.FILES_ONLY)
        val indexRepository = RecordingIndexRepository()
        val metadataRepository = RecordingMetadataRepository()
        val mediaSource = FakeMediaSource(
            info = source,
            streams = mapOf("library.db" to { databaseFile.inputStream() }),
        )

        try {
            createMlipDatabase(databaseFile)

            val result = MlipLibraryIndexImporter(indexRepository, metadataRepository)
                .importLibrary(source, mediaSource)

            assertTrue(result.isSuccess())
            val entry = indexRepository.entries.single()
            assertEquals("/Series/01.mkv", entry.path)
            assertEquals("Series", entry.animeName)
            assertEquals(null, entry.metadataSource)
            assertEquals(null, entry.metadataId)
            assertEquals(null, entry.metadataTitle)
            assertEquals(MediaScrapeStatus.PENDING, entry.scrapeStatus)
            assertEquals("mlip:7:series-uuid", entry.localMetadataOverrideKey())
            assertTrue(metadataRepository.anime.isEmpty())
            assertTrue(metadataRepository.episodes.isEmpty())
            assertEquals(listOf("library.db"), mediaSource.openedPaths)
        } finally {
            databaseFile.delete()
        }
    }

    @Test
    fun `local metadata override survives mlip reimport`() = runBlocking {
        val databaseFile = File.createTempFile("mlip-test-", ".db")
        val source = mlipSource()
        val indexRepository = RecordingIndexRepository(
            initialEntries = listOf(
                MediaIndexEntry(
                    sourceId = 7L,
                    path = "/Series/01.mkv",
                    animeName = "葬送的芙莉莲",
                    metadataSource = "BANGUMI",
                    metadataId = "431767",
                    metadataTitle = "葬送的芙莉莲",
                    scrapeStatus = MediaScrapeStatus.SCRAPED,
                    scrapeMessage = localMetadataOverrideMessage("mlip:7:series-uuid"),
                    scrapedAt = 42L,
                )
            )
        )
        val metadataRepository = RecordingMetadataRepository()
        val mediaSource = FakeMediaSource(
            info = source,
            streams = mapOf("library.db" to { databaseFile.inputStream() }),
        )

        try {
            createMlipDatabase(databaseFile)

            val result = MlipLibraryIndexImporter(indexRepository, metadataRepository)
                .importLibrary(source, mediaSource)

            assertTrue(result.isSuccess())
            val entry = indexRepository.entries.single()
            assertEquals("BANGUMI", entry.metadataSource)
            assertEquals("431767", entry.metadataId)
            assertEquals("葬送的芙莉莲", entry.metadataTitle)
            assertEquals("mlip:7:series-uuid", entry.localMetadataOverrideKey())
            assertEquals(42L, entry.scrapedAt)
            assertTrue(metadataRepository.anime.isEmpty())
            assertTrue(metadataRepository.episodes.isEmpty())
            assertEquals(listOf("library.db"), mediaSource.openedPaths)
        } finally {
            databaseFile.delete()
        }
    }

    @Test
    fun `valid mlip database imports index metadata episodes and poster`() = runBlocking {
        val databaseFile = File.createTempFile("mlip-test-", ".db")
        val posterCacheDirectory = Files.createTempDirectory("mlip-poster-cache-").toFile()
        val source = MediaSourceInfo(
            id = 7L,
            name = "Anime DAV",
            type = MediaSourceType.WEBDAV,
        )
        val indexRepository = RecordingIndexRepository()
        val metadataRepository = RecordingMetadataRepository()
        val mediaSource = FakeMediaSource(
            info = source,
            streams = mapOf(
                "library.db" to { databaseFile.inputStream() },
                "/Series/poster.jpg" to { ByteArrayInputStream("poster".toByteArray()) },
            ),
        )

        try {
            createMlipDatabase(databaseFile)

            val result = MlipLibraryIndexImporter(indexRepository, metadataRepository)
                .importLibrary(source, mediaSource, posterCacheDirectory)

            assertTrue(result.isSuccess())
            val importResult = (result as Result.Success).data
            assertEquals(1, importResult.seriesCount)
            assertEquals(1, importResult.episodeCount)
            assertEquals(1, importResult.mediaFileCount)
            assertEquals(1, importResult.skippedFileCount)
            assertEquals(1, importResult.nonIntegerEpisodeCount)
            assertEquals(1, importResult.artworkCachedCount)
            assertEquals(listOf("library.db", "/Series/poster.jpg"), mediaSource.openedPaths)
            assertTrue("MLIP import should not list WebDAV directories", mediaSource.listedPaths.isEmpty())

            val entry = indexRepository.entries.single()
            assertEquals(7L, entry.sourceId)
            assertEquals("/Series/01.mkv", entry.path)
            assertEquals("中文标题", entry.animeName)
            assertEquals("第 1 集", entry.episodeTitle)
            assertEquals(1, entry.seasonNumber)
            assertEquals(1, entry.episodeNumber)
            assertEquals("MLIP", entry.metadataSource)
            assertEquals("mlip:7:series-uuid", entry.metadataId)
            assertEquals(1234L, entry.fileSize)
            assertEquals(1_700_000_000_000L, entry.lastModified)

            val anime = metadataRepository.anime.single()
            assertEquals("mlip:7:series-uuid", anime.id)
            assertEquals("Original Title", anime.title)
            assertEquals("中文标题", anime.titleCn)
            assertEquals("简介", anime.summary)
            assertEquals("2024-04-03", anime.airDate)
            assertEquals(listOf("科幻"), anime.genres)
            assertEquals(431767, anime.bangumiId)
            assertEquals(98765, anime.tmdbId)
            assertNotNull(anime.posterLocalPath)
            assertTrue(File(anime.posterLocalPath!!).exists())

            val episode = metadataRepository.episodes.single()
            assertEquals("7:/Series/01.mkv", episode.id)
            assertEquals("mlip:7:series-uuid", episode.animeId)
            assertEquals("第 1 集", episode.title)
            assertEquals("/Series/01.mkv", episode.filePath)
            assertEquals("01.mkv", episode.fileName)
            assertEquals(1_440_000L, episode.duration)
        } finally {
            databaseFile.delete()
            posterCacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun `legacy mlip database falls back to release year`() = runBlocking {
        val databaseFile = File.createTempFile("mlip-test-", ".db")
        val source = mlipSource()
        val metadataRepository = RecordingMetadataRepository()
        val mediaSource = FakeMediaSource(
            info = source,
            streams = mapOf("library.db" to { databaseFile.inputStream() }),
        )
        try {
            createMlipDatabase(databaseFile, includeReleaseDate = false)

            val result = MlipLibraryIndexImporter(RecordingIndexRepository(), metadataRepository)
                .importLibrary(source, mediaSource)

            assertTrue(result.isSuccess())
            assertEquals("2024", metadataRepository.anime.single().airDate)
        } finally {
            databaseFile.delete()
        }
    }

    private suspend fun importDatabase(databaseFile: File): Result<MlipImportResult> {
        val source = mlipSource()
        val mediaSource = FakeMediaSource(
            info = source,
            streams = mapOf("library.db" to { databaseFile.inputStream() }),
        )
        return MlipLibraryIndexImporter(RecordingIndexRepository(), RecordingMetadataRepository())
            .importLibrary(source, mediaSource)
    }

    private fun mlipSource(
        metadataMode: MlipMetadataMode = MlipMetadataMode.LIBRARY_DB_LOCAL_PRIORITY,
    ): MediaSourceInfo = MediaSourceInfo(
        id = 7L,
        name = "Anime DAV",
        type = MediaSourceType.WEBDAV,
        connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
            type = MediaSourceType.WEBDAV,
            location = "https://dav.example.test/anime",
            recognitionMode = MediaRecognitionMode.MLIP,
            mlipMetadataMode = metadataMode,
        ),
    )

    private fun createMlipDatabase(
        file: File,
        userVersion: Int = 1,
        includeRequiredTables: Boolean = true,
        includeReleaseDate: Boolean = true,
        mediaPath: String = "Series/01.mkv",
    ) {
        file.delete()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL("PRAGMA user_version = $userVersion")
            database.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            database.execSQL("INSERT INTO meta (key, value) VALUES ('protocol', 'MLIP'), ('schema', '1')")
            if (!includeRequiredTables) return
            database.execSQL("CREATE TABLE series (id INTEGER PRIMARY KEY, uuid TEXT NOT NULL, title TEXT NOT NULL, original_title TEXT, summary TEXT, year INTEGER, series_type INTEGER)")
            database.execSQL("CREATE TABLE episode (id INTEGER PRIMARY KEY, uuid TEXT NOT NULL, series_id INTEGER NOT NULL, season INTEGER, episode REAL, sort_order REAL, title TEXT, summary TEXT, runtime INTEGER)")
            database.execSQL("CREATE TABLE media_file (id INTEGER PRIMARY KEY, episode_id INTEGER NOT NULL, path TEXT NOT NULL, size INTEGER, modified_time INTEGER)")
            database.execSQL("CREATE TABLE series_artwork (id INTEGER PRIMARY KEY, series_id INTEGER NOT NULL, artwork_kind INTEGER NOT NULL, path TEXT NOT NULL)")
            database.execSQL("CREATE TABLE episode_artwork (id INTEGER PRIMARY KEY, episode_id INTEGER NOT NULL, artwork_kind INTEGER NOT NULL, path TEXT NOT NULL)")
            database.execSQL("CREATE TABLE genre (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
            database.execSQL("CREATE TABLE series_genre (series_id INTEGER NOT NULL, genre_id INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE series_external_id (series_id INTEGER NOT NULL, provider INTEGER NOT NULL, value TEXT NOT NULL)")
            database.execSQL("CREATE TABLE episode_external_id (episode_id INTEGER NOT NULL, provider INTEGER NOT NULL, value TEXT NOT NULL)")
            database.execSQL("CREATE TABLE capability (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            if (includeReleaseDate) {
                database.execSQL("CREATE TABLE series_release_date (series_id INTEGER PRIMARY KEY, air_date TEXT NOT NULL)")
                database.execSQL("INSERT INTO series_release_date (series_id, air_date) VALUES (1, '2024-04-03')")
            }
            database.execSQL("INSERT INTO series (id, uuid, title, original_title, summary, year, series_type) VALUES (1, 'series-uuid', '中文标题', 'Original Title', '简介', 2024, 1)")
            database.execSQL("INSERT INTO episode (id, uuid, series_id, season, episode, sort_order, title, summary, runtime) VALUES (10, 'episode-uuid-1', 1, 1, 1.0, 1.0, '第 1 集', '', 1440)")
            database.execSQL("INSERT INTO episode (id, uuid, series_id, season, episode, sort_order, title, summary, runtime) VALUES (11, 'episode-uuid-1-5', 1, 1, 1.5, 1.5, '第 1.5 集', '', 1440)")
            database.execSQL("INSERT INTO media_file (id, episode_id, path, size, modified_time) VALUES (100, 10, '$mediaPath', 1234, 1700000000)")
            database.execSQL("INSERT INTO media_file (id, episode_id, path, size, modified_time) VALUES (101, 11, 'Series/01.5.mkv', 1234, 1700000001)")
            database.execSQL("INSERT INTO genre (id, name) VALUES (1, '科幻')")
            database.execSQL("INSERT INTO series_genre (series_id, genre_id) VALUES (1, 1)")
            database.execSQL("INSERT INTO series_external_id (series_id, provider, value) VALUES (1, 1, '431767'), (1, 2, '98765')")
            database.execSQL("INSERT INTO series_artwork (id, series_id, artwork_kind, path) VALUES (1, 1, 1, 'Series/poster.jpg')")
        } finally {
            database.close()
        }
    }

    private class RecordingIndexRepository(
        initialEntries: List<MediaIndexEntry> = emptyList(),
    ) : MediaIndexRepository {
        val entries = initialEntries.toMutableList()

        override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> {
            this.entries.clear()
            this.entries.addAll(entries)
            return Result.success(Unit)
        }

        override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> = Result.success(Unit)
        override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> = Result.success(entries)
        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> = Result.success(entries.mapNotNull { it.animeName }.distinct())
        override suspend fun clearIndex(sourceId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = Result.success(Unit)
        override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> = Result.success(emptyList())
        override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = Result.success(Unit)
    }

    private class RecordingMetadataRepository : MetadataRepository {
        val anime = mutableListOf<Anime>()
        val episodes = mutableListOf<Episode>()

        override suspend fun cacheMetadata(anime: Anime): Result<Unit> {
            this.anime += anime
            return Result.success(Unit)
        }

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> = Result.success(anime.firstOrNull { it.id == animeId })
        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> = Result.success(anime.filter { it.id in animeIds })
        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> = Result.success(episodes.firstOrNull { it.id == episodeId })
        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> = Result.success(episodes.filter { it.animeId == animeId })
        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> {
            this.episodes += episodes
            return Result.success(Unit)
        }
        override suspend fun cacheDramaSeries(seriesId: String, series: DramaSeries): Result<Unit> = Result.success(Unit)
        override suspend fun invalidateCache(animeId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeMediaSource(
        override val info: MediaSourceInfo,
        private val streams: Map<String, () -> InputStream>,
    ) : MediaSource {
        override val id: String = "fake"
        override val capabilities: MediaCapabilities = MediaCapabilities()
        val openedPaths = mutableListOf<String>()
        val listedPaths = mutableListOf<String>()

        override suspend fun listFiles(path: String): Result<List<FileEntry>> {
            listedPaths += path
            return Result.success(emptyList())
        }

        override suspend fun openStream(path: String): Result<InputStream> {
            openedPaths += path
            return streams[path]?.let { Result.success(it()) }
                ?: Result.failure(AppError.MediaSourceError.NotFound(path))
        }

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.failure(AppError.MediaSourceError.NotFound(path))

        override suspend fun testConnection(): Result<Boolean> = Result.success(true)
        override suspend fun close() = Unit
    }
}
