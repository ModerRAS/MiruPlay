package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaRecognitionMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaScrapeStatus
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.scraper.EpisodeMetadata
import com.miruplay.tv.scraper.MetadataScraper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest

class ScanCoordinatorTest {

    @Test
    fun `scanSource imports mlip source without directory traversal or scraping`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 27L,
            name = "MLIP WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = MediaSourceType.WEBDAV,
                location = "http://example.test/dav",
                recognitionMode = MediaRecognitionMode.MLIP,
            ),
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf("" to listOf(FileEntry("should-not-list.mkv", "/should-not-list.mkv", false))),
            streamErrors = mapOf("library.db" to AppError.MediaSourceError.NotFound("library.db")),
        )
        val mediaRepository = SingleSourceRepository(sourceInfo)
        val scraper = RecordingBangumiScraper()
        val coordinator = ScanCoordinator(
            mediaRepository = mediaRepository,
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = RecordingIndexRepository(),
            metadataRepository = RecordingMetadataRepository(),
            filenameMetadataParser = EmptyFilenameMetadataParser,
            metadataScrapers = setOf(scraper),
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Missing library.db should fail explicitly", result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.LibraryIndexError.Missing)
        assertEquals(listOf(""), mediaSource.listedPaths)
        assertEquals(null, scraper.normalizedName)
        assertEquals(null, mediaRepository.updatedSource)
    }

    @Test
    fun `scanSource starts WebDAV traversal at empty path and indexes remote files`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 7L,
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "番剧", path = "/番剧", isDirectory = true)
                ),
                "/番剧" to listOf(
                    FileEntry(name = "01 [1080P].mp4", path = "/番剧/01 [1080P].mp4", isDirectory = false, size = 1234),
                    FileEntry(name = "02 #OVA?.mkv", path = "/番剧/02 #OVA?.mkv", isDirectory = false, size = 1234),
                    FileEntry(name = "01.trickplay", path = "/番剧/01.trickplay", isDirectory = true)
                )
            )
        )
        val indexRepository = RecordingIndexRepository()
        val metadataRepository = RecordingMetadataRepository()
        val mediaRepository = SingleSourceRepository(sourceInfo)
        val coordinator = ScanCoordinator(
            mediaRepository = mediaRepository,
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = metadataRepository,
            filenameMetadataParser = EmptyFilenameMetadataParser
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals(listOf("", "/番剧"), mediaSource.listedPaths)
        assertEquals(2, result.getOrNull()?.episodesFound)
        assertEquals(
            listOf("/番剧/01 [1080P].mp4", "/番剧/02 #OVA?.mkv"),
            indexRepository.entries.map { it.path }
        )
        assertEquals(listOf("番剧", "番剧"), indexRepository.entries.map { it.animeName })
        assertEquals("7:/番剧/01 [1080P].mp4", metadataRepository.episodes.first().id)
        assertEquals(
            "http://example.test/dav/%E7%95%AA%E5%89%A7/01%20%5B1080P%5D.mp4",
            metadataRepository.episodes.first().filePath
        )
        assertEquals(
            "http://example.test/dav/%E7%95%AA%E5%89%A7/02%20%23OVA%3F.mkv",
            metadataRepository.episodes.last().filePath
        )
        assertTrue("Scan should update source lastScanned", mediaRepository.updatedSource?.lastScanned ?: 0L > 0L)
        assertEquals(true, mediaRepository.updatedSource?.isConnected)
    }

    @Test
    fun `scanSource uses WebDAV root folder as context for flat show source`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 8L,
            name = "DrStone WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf(
                "url" to "http://example.test/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/Dr.STONE%20%E6%96%B0%E7%9F%B3%E7%B4%80%20%E7%AC%AC%E5%9B%9B%E5%AD%A3"
            )
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(
                        name = "25 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4",
                        path = "/25 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val indexRepository = RecordingIndexRepository()
        val scraper = RecordingBangumiScraper()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = RecordingMetadataRepository(),
            filenameMetadataParser = EmptyFilenameMetadataParser,
            metadataScrapers = setOf(scraper)
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals("Dr STONE 新石紀", indexRepository.entries.single().animeName)
        assertEquals(4, indexRepository.entries.single().seasonNumber)
        assertEquals(25, indexRepository.entries.single().episodeNumber)
        assertEquals("Dr STONE 新石紀", scraper.normalizedName)
        assertTrue(scraper.aliasCandidates.contains("Dr STONE 新石紀 第四季"))
    }

    @Test
    fun `scanSource keeps webdav root context when media source returns relative child paths`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 18L,
            name = "DrStone WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf(
                "url" to "http://example.test/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/Dr.STONE%20%E6%96%B0%E7%9F%B3%E7%B4%80%20%E7%AC%AC%E5%9B%9B%E5%AD%A3"
            )
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(
                        name = "25 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4",
                        path = "/25 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val indexRepository = RecordingIndexRepository()
        val scraper = RecordingBangumiScraper()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = RecordingMetadataRepository(),
            filenameMetadataParser = EmptyFilenameMetadataParser,
            metadataScrapers = setOf(scraper)
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals("Dr STONE 新石紀", indexRepository.entries.single().animeName)
        assertEquals(4, indexRepository.entries.single().seasonNumber)
        assertEquals(25, indexRepository.entries.single().episodeNumber)
        assertTrue("Season-aware root should be offered to Bangumi", scraper.aliasCandidates.contains("Dr STONE 新石紀 第四季"))
    }

    @Test
    fun `scanSource keeps filename episode when path parser misreads dotted season folder`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 19L,
            name = "Drama WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf(
                "url" to "http://example.test/dav/%E5%BD%B1%E9%9F%B3/%E7%94%B5%E8%A7%86%E5%89%A7/%E5%8C%BB%E9%A6%86%E7%AC%91%E4%BC%A0"
            )
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "医馆笑传.S01", path = "/医馆笑传.S01", isDirectory = true)
                ),
                "/医馆笑传.S01" to listOf(
                    FileEntry(
                        name = "医馆笑传.S01E02.mp4",
                        path = "/医馆笑传.S01/医馆笑传.S01E02.mp4",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val indexRepository = RecordingIndexRepository()
        val metadataRepository = RecordingMetadataRepository()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = metadataRepository,
            filenameMetadataParser = MappingFilenameParser(
                mapOf(
                    "医馆笑传/医馆笑传.S01/医馆笑传.S01E02.mp4" to FilenameParseResult(
                        title = "医馆笑传.S01",
                        episode = 1
                    )
                )
            )
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals("医馆笑传", indexRepository.entries.single().animeName)
        assertEquals(1, indexRepository.entries.single().seasonNumber)
        assertEquals(2, indexRepository.entries.single().episodeNumber)
        assertEquals(2, metadataRepository.episodes.single().episodeNumber)
    }

    @Test
    fun `scanSource recognizes flat show season folder and generates local nfo files`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-scan").toFile().canonicalFile
        try {
            val showDir = File(root, "异世界悠闲农家 第2季").apply { mkdirs() }
            val video = File(showDir, "01 [1080P].mp4").apply { writeText("fake") }
            val sourceInfo = MediaSourceInfo(
                id = 9L,
                name = "Local",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to root.absolutePath)
            )
            val mediaSource = FakeMediaSource(
                listings = mapOf(
                    root.absolutePath to listOf(
                        FileEntry(name = showDir.name, path = showDir.absolutePath, isDirectory = true)
                    ),
                    showDir.absolutePath to listOf(
                        FileEntry(
                            name = video.name,
                            path = video.absolutePath,
                            isDirectory = false,
                            size = video.length()
                        )
                    )
                )
            )
            val indexRepository = RecordingIndexRepository()
            val metadataRepository = RecordingMetadataRepository()
            val coordinator = ScanCoordinator(
                mediaRepository = SingleSourceRepository(sourceInfo),
                mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
                indexRepository = indexRepository,
                metadataRepository = metadataRepository,
                filenameMetadataParser = EmptyFilenameMetadataParser
            )

            val result = coordinator.scanSource(sourceInfo.id)

            assertTrue("Scan should succeed", result.isSuccess())
            assertEquals(root.name, result.getOrNull()?.animeName)
            assertEquals("异世界悠闲农家", indexRepository.entries.single().animeName)
            assertEquals(2, indexRepository.entries.single().seasonNumber)
            assertEquals(1, indexRepository.entries.single().episodeNumber)
            assertEquals("异世界悠闲农家", metadataRepository.episodes.single().animeId)
            assertEquals(2, metadataRepository.episodes.single().seasonNumber)
            assertTrue(File(showDir, "tvshow.nfo").exists())
            assertTrue(File(showDir, "01 [1080P].nfo").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `scanSource skips local paths that only share the root prefix`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-scan").toFile().canonicalFile
        try {
            val outsidePath = "${root.absolutePath}-outside"
            val sourceInfo = MediaSourceInfo(
                id = 10L,
                name = "Local",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to root.absolutePath)
            )
            val mediaSource = FakeMediaSource(
                listings = mapOf(
                    root.absolutePath to listOf(
                        FileEntry(name = "outside", path = outsidePath, isDirectory = true)
                    ),
                    outsidePath to listOf(
                        FileEntry(
                            name = "01 [1080P].mp4",
                            path = "$outsidePath/01 [1080P].mp4",
                            isDirectory = false,
                            size = 1234
                        )
                    )
                )
            )
            val indexRepository = RecordingIndexRepository()
            val coordinator = ScanCoordinator(
                mediaRepository = SingleSourceRepository(sourceInfo),
                mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
                indexRepository = indexRepository,
                metadataRepository = RecordingMetadataRepository(),
                filenameMetadataParser = EmptyFilenameMetadataParser
            )

            val result = coordinator.scanSource(sourceInfo.id)

            assertTrue("Scan should succeed", result.isSuccess())
            assertEquals(0, result.getOrNull()?.episodesFound)
            assertTrue(indexRepository.entries.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `scanSource uses filename parser result for indexed anime and episode`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 11L,
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "raw", path = "/raw", isDirectory = true)
                ),
                "/raw" to listOf(
                    FileEntry(
                        name = "weird-upload-name.mkv",
                        path = "/raw/weird-upload-name.mkv",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val indexRepository = RecordingIndexRepository()
        val metadataRepository = RecordingMetadataRepository()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = metadataRepository,
            filenameMetadataParser = StaticFilenameParser(
                FilenameParseResult(
                    title = "葬送的芙莉莲",
                    season = 2,
                    episode = 3
                )
            )
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals("葬送的芙莉莲", indexRepository.entries.single().animeName)
        assertEquals(2, indexRepository.entries.single().seasonNumber)
        assertEquals(3, indexRepository.entries.single().episodeNumber)
        assertEquals("葬送的芙莉莲", metadataRepository.episodes.single().animeId)
        assertEquals(2, metadataRepository.episodes.single().seasonNumber)
        assertEquals(3, metadataRepository.episodes.single().episodeNumber)
    }

    @Test
    fun `scanSource counts duplicate physical versions as one logical episode`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 46L,
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav"),
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "Show WEB S01E01.mkv", path = "/Show WEB S01E01.mkv", isDirectory = false),
                    FileEntry(name = "Show BD S01E01.mkv", path = "/Show BD S01E01.mkv", isDirectory = false),
                ),
            ),
        )
        val metadataRepository = RecordingMetadataRepository()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = RecordingIndexRepository(),
            metadataRepository = metadataRepository,
            filenameMetadataParser = StaticFilenameParser(
                FilenameParseResult(title = "Show", season = 1, episode = 1),
            ),
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals(2, metadataRepository.episodes.size)
        assertEquals(1, metadataRepository.anime.single().episodeCount)
    }

    @Test
    fun `scanSource returns error when root directory listing fails`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 45L,
            name = "Broken WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )
        val expectedError = AppError.NetworkError.HttpError(401, "Unauthorized")
        val mediaSource = FakeMediaSource(
            listings = emptyMap(),
            listErrors = mapOf("" to expectedError),
        )
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = RecordingIndexRepository(),
            metadataRepository = RecordingMetadataRepository(),
            filenameMetadataParser = EmptyFilenameMetadataParser,
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue(result is Result.Error)
        assertEquals(expectedError, (result as Result.Error).error)
        assertEquals(listOf(""), mediaSource.listedPaths)
    }

    @Test
    fun `scanSource passes parser title candidates to Bangumi alias search`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 12L,
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "葬送的芙莉莲 第2季", path = "/葬送的芙莉莲 第2季", isDirectory = true)
                ),
                "/葬送的芙莉莲 第2季" to listOf(
                    FileEntry(
                        name = "Frieren S2E03.mkv",
                        path = "/葬送的芙莉莲 第2季/Frieren S2E03.mkv",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val scraper = RecordingBangumiScraper()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = RecordingIndexRepository(),
            metadataRepository = RecordingMetadataRepository(),
            filenameMetadataParser = MappingFilenameParser(
                mapOf(
                    "Frieren S2E03" to FilenameParseResult(
                        title = "Frieren",
                        season = 2,
                        episode = 3
                    ),
                    "葬送的芙莉莲 第2季" to FilenameParseResult(
                        title = "葬送的芙莉莲",
                        season = 2
                    )
                )
            ),
            metadataScrapers = setOf(scraper)
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals("葬送的芙莉莲", scraper.normalizedName)
        assertTrue(scraper.aliasCandidates.contains("葬送的芙莉莲"))
        assertTrue(scraper.aliasCandidates.contains("Frieren"))
    }

    @Test
    fun `scanSource includes leading token suffix in Bangumi alias candidates`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 13L,
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(
                        name = "銀魂 3 年 Z 班銀八老師 05.mkv",
                        path = "/銀魂 3 年 Z 班銀八老師 05.mkv",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val scraper = RecordingBangumiScraper()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = RecordingIndexRepository(),
            metadataRepository = RecordingMetadataRepository(),
            filenameMetadataParser = StaticFilenameParser(
                FilenameParseResult(
                    title = "銀魂 3 年 Z 班銀八老師",
                    episode = 5
                )
            ),
            metadataScrapers = setOf(scraper)
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals("銀魂 3 年 Z 班銀八老師", scraper.normalizedName)
        assertTrue(scraper.aliasCandidates.contains("3 年 Z 班銀八老師"))
    }

    @Test
    fun `scanSource skips online metadata when source disables it`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 14L,
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf(
                "url" to "http://example.test/dav",
                "disableOnlineMetadata" to "true"
            )
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "Fixture Alpha", path = "/Fixture Alpha", isDirectory = true)
                ),
                "/Fixture Alpha" to listOf(
                    FileEntry(
                        name = "Fixture Alpha - S01E01.mkv",
                        path = "/Fixture Alpha/Fixture Alpha - S01E01.mkv",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val scraper = RecordingBangumiScraper()
        val indexRepository = RecordingIndexRepository()
        val metadataRepository = RecordingMetadataRepository()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = metadataRepository,
            filenameMetadataParser = EmptyFilenameMetadataParser,
            metadataScrapers = setOf(scraper)
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals(1, indexRepository.entries.size)
        assertEquals("Fixture Alpha", indexRepository.entries.single().animeName)
        assertEquals(1, metadataRepository.episodes.size)
        assertTrue("Bangumi alias lookup should be skipped", scraper.normalizedName == null)
        assertTrue("Bangumi fallback candidates should be skipped", scraper.aliasCandidates.isEmpty())
    }

    @Test
    fun `scanSource associates same stem external subtitles`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 49L,
            name = "Subtitle WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav", "disableOnlineMetadata" to "true"),
        )
        val videoPath = "/Show/Show - S01E01.mkv"
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(FileEntry(name = "Show", path = "/Show", isDirectory = true)),
                "/Show" to listOf(
                    FileEntry(name = "Show - S01E01.mkv", path = videoPath, isDirectory = false, size = 1234),
                    FileEntry(name = "Show - S01E01.ass", path = "/Show/Show - S01E01.ass", isDirectory = false, size = 100),
                    FileEntry(name = "Show - S01E01.zh-CN.srt", path = "/Show/Show - S01E01.zh-CN.srt", isDirectory = false, size = 100),
                    FileEntry(name = "Show - S01E010.ass", path = "/Show/Show - S01E010.ass", isDirectory = false, size = 100),
                ),
            ),
        )
        val indexRepository = RecordingIndexRepository()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = RecordingMetadataRepository(),
            filenameMetadataParser = EmptyFilenameMetadataParser,
            metadataScrapers = setOf(RecordingBangumiScraper()),
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals(
            listOf("/Show/Show - S01E01.ass", "/Show/Show - S01E01.zh-CN.srt"),
            indexRepository.entries.single().externalSubtitlePaths,
        )
    }

    @Test
    fun `scanSource skips Bangumi cache reuse and lookup for drama sources`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 48L,
            name = "Drama WebDAV",
            type = MediaSourceType.WEBDAV,
            contentMode = MediaContentMode.DRAMA,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "医馆笑传", path = "/医馆笑传", isDirectory = true)
                ),
                "/医馆笑传" to listOf(
                    FileEntry(
                        name = "医馆笑传.S01E01.mkv",
                        path = "/医馆笑传/医馆笑传.S01E01.mkv",
                        isDirectory = false,
                        size = 1234
                    )
                )
            )
        )
        val scraper = RecordingBangumiScraper()
        val metadataRepository = RecordingMetadataRepository(
            cachedAnime = mutableMapOf(
                "医馆笑传" to Anime(
                    id = "医馆笑传",
                    title = "错误动漫标题",
                    titleCn = "错误动漫标题",
                    bangumiId = 431767,
                    posterUrl = "http://example.test/poster.jpg",
                )
            ),
            cachedEpisodes = mutableMapOf(
                "医馆笑传" to listOf(
                    Episode(
                        id = "cached-1",
                        animeId = "医馆笑传",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        title = "错误动漫分集标题",
                        filePath = "cached",
                        fileName = "cached.mkv",
                        bangumiEpisodeId = 99,
                    )
                )
            )
        )
        val indexRepository = RecordingIndexRepository()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = metadataRepository,
            filenameMetadataParser = EmptyFilenameMetadataParser,
            metadataScrapers = setOf(scraper)
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertTrue("Bangumi alias lookup should be skipped for drama", scraper.normalizedName == null)
        assertTrue("Bangumi fallback candidates should be skipped for drama", scraper.aliasCandidates.isEmpty())
        val entry = indexRepository.entries.single()
        assertEquals(null, entry.metadataSource)
        assertEquals(null, entry.metadataId)
        assertEquals(null, entry.metadataTitle)
        assertEquals(MediaScrapeStatus.PENDING, entry.scrapeStatus)
        assertEquals("Drama metadata is resolved from TMDB detail flow", entry.scrapeMessage)
        assertEquals("", metadataRepository.episodes.single().title)
        assertEquals(null, metadataRepository.episodes.single().bangumiEpisodeId)
        assertTrue("Drama scan should not cache anime metadata rows", metadataRepository.anime.isEmpty())
    }

    @Test
    fun `scanSource downloads Bangumi poster through configured proxy`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setBody("poster-bytes"))
            val sourceInfo = MediaSourceInfo(
                id = 44L,
                name = "Poster Proxy",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "http://example.test/dav")
            )
            val mediaSource = FakeMediaSource(
                listings = mapOf(
                    "" to listOf(
                        FileEntry(name = "01.mkv", path = "/01.mkv", isDirectory = false, size = 1234)
                    )
                )
            )
            val metadataRepository = RecordingMetadataRepository()
            val posterCacheDirectory = Files.createTempDirectory("miruplay-poster-proxy-test-").toFile()
            try {
                val coordinator = ScanCoordinator(
                    mediaRepository = SingleSourceRepository(sourceInfo),
                    mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
                    indexRepository = RecordingIndexRepository(),
                    metadataRepository = metadataRepository,
                    filenameMetadataParser = EmptyFilenameMetadataParser,
                    metadataScrapers = setOf(PosterBangumiScraper("http://image.example/poster.jpg")),
                    cloudDriveRepository = StaticCloudDriveAutomationRepository(
                        CloudDriveAutomationConfig(
                            rssProxyEnabled = true,
                            rssProxyHost = proxy.hostName,
                            rssProxyPort = proxy.port,
                        )
                    ),
                )

                val result = coordinator.scanSource(sourceInfo.id, posterCacheDirectory = posterCacheDirectory)

                assertTrue("Scan should succeed", result.isSuccess())
                val imageRequest = proxy.takeRequest()
                assertEquals("image.example", imageRequest.headers["Host"])
                assertTrue(imageRequest.requestLine.contains("http://image.example/poster.jpg"))
                val cachedPoster = metadataRepository.anime.single().posterLocalPath
                assertTrue(!cachedPoster.isNullOrBlank())
                assertEquals("poster-bytes", File(cachedPoster!!).readText())
            } finally {
                posterCacheDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun `scanSource reuses cached Bangumi poster without redownloading`() = runBlocking {
        MockWebServer().use { imageServer ->
            val posterUrl = imageServer.url("/poster.jpg").toString()
            val sourceInfo = MediaSourceInfo(
                id = 45L,
                name = "Poster Cache",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "http://example.test/dav")
            )
            val mediaSource = FakeMediaSource(
                listings = mapOf(
                    "" to listOf(
                        FileEntry(name = "01.mkv", path = "/01.mkv", isDirectory = false, size = 1234)
                    )
                )
            )
            val metadataRepository = RecordingMetadataRepository()
            val posterCacheDirectory = Files.createTempDirectory("miruplay-poster-cache-test-").toFile()
            try {
                posterCacheDirectory.mkdirs()
                val cachedPoster = File(posterCacheDirectory, sha256Hex(posterUrl))
                cachedPoster.writeText("cached-poster")
                val coordinator = ScanCoordinator(
                    mediaRepository = SingleSourceRepository(sourceInfo),
                    mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
                    indexRepository = RecordingIndexRepository(),
                    metadataRepository = metadataRepository,
                    filenameMetadataParser = EmptyFilenameMetadataParser,
                    metadataScrapers = setOf(PosterBangumiScraper(posterUrl)),
                )

                val result = coordinator.scanSource(sourceInfo.id, posterCacheDirectory = posterCacheDirectory)

                assertTrue("Scan should succeed", result.isSuccess())
                assertEquals(0, imageServer.requestCount)
                assertEquals(cachedPoster.absolutePath, metadataRepository.anime.single().posterLocalPath)
                assertEquals("cached-poster", cachedPoster.readText())
            } finally {
                posterCacheDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun `scanSource reuses cached Bangumi poster metadata without API lookup`() = runBlocking {
        MockWebServer().use { imageServer ->
            val posterUrl = imageServer.url("/poster.jpg").toString()
            val sourceInfo = MediaSourceInfo(
                id = 46L,
                name = "Cached Metadata",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "http://example.test/dav")
            )
            val mediaSource = FakeMediaSource(
                listings = mapOf(
                    "" to listOf(
                        FileEntry(name = "Cached Show", path = "/Cached Show", isDirectory = true)
                    ),
                    "/Cached Show" to listOf(
                        FileEntry(name = "Cached Show - 01.mkv", path = "/Cached Show/Cached Show - 01.mkv", isDirectory = false, size = 1234)
                    )
                )
            )
            val posterCacheDirectory = Files.createTempDirectory("miruplay-poster-metadata-test-").toFile()
            try {
                posterCacheDirectory.mkdirs()
                val cachedPoster = File(posterCacheDirectory, sha256Hex(posterUrl))
                cachedPoster.writeText("cached-poster")
                val scraper = RecordingBangumiScraper()
                val metadataRepository = RecordingMetadataRepository(
                    cachedAnime = mutableMapOf(
                        "Cached Show" to Anime(
                            id = "Cached Show",
                            title = "Cached Show",
                            posterUrl = posterUrl,
                            posterLocalPath = cachedPoster.absolutePath,
                            bangumiId = 431767,
                        )
                    )
                )
                val indexRepository = RecordingIndexRepository()
                val coordinator = ScanCoordinator(
                    mediaRepository = SingleSourceRepository(sourceInfo),
                    mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
                    indexRepository = indexRepository,
                    metadataRepository = metadataRepository,
                    filenameMetadataParser = EmptyFilenameMetadataParser,
                    metadataScrapers = setOf(scraper),
                )

                val result = coordinator.scanSource(sourceInfo.id, posterCacheDirectory = posterCacheDirectory)

                assertTrue("Scan should succeed", result.isSuccess())
                assertTrue("Bangumi alias lookup should be skipped", scraper.normalizedName == null)
                assertTrue("Bangumi fallback candidates should be skipped", scraper.aliasCandidates.isEmpty())
                assertEquals(0, imageServer.requestCount)
                assertEquals("431767", indexRepository.entries.single { !it.isDirectory }.metadataId)
                assertEquals(cachedPoster.absolutePath, metadataRepository.anime.last().posterLocalPath)
            } finally {
                posterCacheDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun `scanSource reuses poster metadata bound to cached Bangumi id`() = runBlocking {
        MockWebServer().use { imageServer ->
            val posterUrl = imageServer.url("/poster.jpg").toString()
            val sourceInfo = MediaSourceInfo(
                id = 47L,
                name = "Cached Bangumi Binding",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "http://example.test/dav")
            )
            val mediaSource = FakeMediaSource(
                listings = mapOf(
                    "" to listOf(
                        FileEntry(name = "Cached Show", path = "/Cached Show", isDirectory = true)
                    ),
                    "/Cached Show" to listOf(
                        FileEntry(name = "Cached Show - 01.mkv", path = "/Cached Show/Cached Show - 01.mkv", isDirectory = false, size = 1234)
                    )
                )
            )
            val posterCacheDirectory = Files.createTempDirectory("miruplay-poster-binding-test-").toFile()
            try {
                posterCacheDirectory.mkdirs()
                val cachedPoster = File(posterCacheDirectory, sha256Hex(posterUrl))
                cachedPoster.writeText("cached-poster")
                val scraper = RecordingBangumiScraper()
                val metadataRepository = RecordingMetadataRepository(
                    cachedAnime = mutableMapOf(
                        "Cached Show" to Anime(
                            id = "Cached Show",
                            title = "Cached Show",
                            bangumiId = 431767,
                        ),
                        "bangumi:431767" to Anime(
                            id = "bangumi:431767",
                            title = "Cached Show",
                            posterUrl = posterUrl,
                            posterLocalPath = cachedPoster.absolutePath,
                            bangumiId = 431767,
                        )
                    )
                )
                val coordinator = ScanCoordinator(
                    mediaRepository = SingleSourceRepository(sourceInfo),
                    mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
                    indexRepository = RecordingIndexRepository(),
                    metadataRepository = metadataRepository,
                    filenameMetadataParser = EmptyFilenameMetadataParser,
                    metadataScrapers = setOf(scraper),
                )

                val result = coordinator.scanSource(sourceInfo.id, posterCacheDirectory = posterCacheDirectory)

                assertTrue("Scan should succeed", result.isSuccess())
                assertTrue("Bangumi alias lookup should be skipped", scraper.normalizedName == null)
                assertEquals(0, imageServer.requestCount)
                val cached = metadataRepository.anime.last()
                assertEquals("Cached Show", cached.id)
                assertEquals(431767, cached.bangumiId)
                assertEquals(posterUrl, cached.posterUrl)
                assertEquals(cachedPoster.absolutePath, cached.posterLocalPath)
            } finally {
                posterCacheDirectory.deleteRecursively()
            }
        }
    }

    private class SingleSourceRepository(
        private val source: MediaSourceInfo
    ) : MediaSourceRepository {
        var updatedSource: MediaSourceInfo? = null

        override suspend fun addSource(source: MediaSourceInfo): Result<Long> = Result.success(source.id)
        override suspend fun removeSource(sourceId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun getSources(): Result<List<MediaSourceInfo>> = Result.success(listOf(source))
        override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> {
            updatedSource = source
            return Result.success(Unit)
        }
        override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
            if (sourceId == source.id) Result.success(source) else Result.failure(AppError.MediaSourceError.NotFound(sourceId.toString()))
    }

    private class SingleMediaSourceFactory(
        private val source: MediaSource
    ) : MediaSourceFactory {
        override fun create(info: MediaSourceInfo): Result<MediaSource> = Result.success(source)
        override fun supports(type: MediaSourceType): Boolean = true
    }

    private class RecordingIndexRepository : MediaIndexRepository {
        val entries = mutableListOf<MediaIndexEntry>()

        override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> {
            this.entries.clear()
            this.entries.addAll(entries)
            return Result.success(Unit)
        }

        override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> = Result.success(entries)
        override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> {
            entries.removeAll { it.sourceId == sourceId && it.path == entry.path }
            entries.add(entry.copy(sourceId = sourceId))
            return Result.success(Unit)
        }
        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> = Result.success(entries.mapNotNull { it.animeName }.distinct())
        override suspend fun clearIndex(sourceId: Long): Result<Unit> {
            entries.clear()
            return Result.success(Unit)
        }
        override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = Result.success(Unit)
        override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> = Result.success(emptyList())
        override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = Result.success(Unit)
    }

    private class RecordingMetadataRepository(
        private val cachedAnime: MutableMap<String, Anime> = mutableMapOf(),
        private val cachedEpisodes: MutableMap<String, List<Episode>> = mutableMapOf(),
    ) : MetadataRepository {
        val anime = mutableListOf<Anime>()
        val episodes = mutableListOf<Episode>()

        override suspend fun cacheMetadata(anime: Anime): Result<Unit> {
            this.anime.add(anime)
            cachedAnime[anime.id] = anime
            return Result.success(Unit)
        }
        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> = Result.success(cachedAnime[animeId])
        override suspend fun getCachedMetadataByBangumiId(bangumiId: Int): Result<Anime?> =
            Result.success(
                cachedAnime.values.firstOrNull {
                    it.bangumiId == bangumiId && (!it.posterUrl.isNullOrBlank() || !it.posterLocalPath.isNullOrBlank())
                } ?: cachedAnime.values.firstOrNull { it.bangumiId == bangumiId }
            )
        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
            Result.success(animeIds.mapNotNull(cachedAnime::get))
        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> = Result.success(episodes.firstOrNull { it.id == episodeId })
        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(cachedEpisodes[animeId] ?: episodes)
        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> {
            this.episodes.clear()
            this.episodes.addAll(episodes)
            cachedEpisodes[animeId] = episodes
            return Result.success(Unit)
        }
        override suspend fun invalidateCache(animeId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeMediaSource(
        private val listings: Map<String, List<FileEntry>>,
        private val listErrors: Map<String, AppError> = emptyMap(),
        private val streamErrors: Map<String, AppError> = emptyMap(),
    ) : MediaSource {
        override val id: String = "fake"
        override lateinit var info: MediaSourceInfo
        override val capabilities: MediaCapabilities = MediaCapabilities()
        val listedPaths = mutableListOf<String>()

        override suspend fun listFiles(path: String): Result<List<FileEntry>> {
            listedPaths.add(path)
            listErrors[path]?.let { return Result.failure(it) }
            return Result.success(listings[path].orEmpty())
        }

        override suspend fun openStream(path: String): Result<InputStream> {
            streamErrors[path]?.let { return Result.failure(it) }
            return Result.success(ByteArrayInputStream(ByteArray(0)))
        }

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.failure(AppError.MediaSourceError.NotFound(path))

        override suspend fun testConnection(): Result<Boolean> = Result.success(true)
        override suspend fun close() = Unit
    }

    private object EmptyFilenameMetadataParser : FilenameMetadataParser {
        override fun parse(filename: String, maxLength: Int): FilenameParseResult = FilenameParseResult()
    }

    private class StaticFilenameParser(
        private val result: FilenameParseResult
    ) : FilenameMetadataParser {
        override fun parse(filename: String, maxLength: Int): FilenameParseResult = result
    }

    private class MappingFilenameParser(
        private val results: Map<String, FilenameParseResult>
    ) : FilenameMetadataParser {
        override fun parse(filename: String, maxLength: Int): FilenameParseResult =
            results[filename] ?: FilenameParseResult()
    }

    private class RecordingBangumiScraper : MetadataScraper {
        override val sourceName: String = "Bangumi"
        var normalizedName: String? = null
        var aliasCandidates: List<String> = emptyList()

        override suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
            Result.success(emptyList())

        override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
            Result.failure(AppError.ScrapingError.NoMatchFound(animeId))

        override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
            Result.success(emptyList())

        override suspend fun searchByAlias(
            normalizedName: String,
            candidates: List<String>
        ): Result<ScraperResult?> {
            this.normalizedName = normalizedName
            this.aliasCandidates = candidates
            return Result.success(null)
        }
    }

    private class PosterBangumiScraper(
        private val posterUrl: String
    ) : MetadataScraper {
        override val sourceName: String = "Bangumi"

        override suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
            Result.success(listOf(match()))

        override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
            Result.success(
                Anime(
                    id = animeId,
                    title = "Poster Proxy",
                    posterUrl = posterUrl,
                    bangumiId = animeId.toIntOrNull(),
                )
            )

        override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
            Result.success(emptyList())

        override suspend fun searchByAlias(
            normalizedName: String,
            candidates: List<String>
        ): Result<ScraperResult?> = Result.success(match())

        private fun match(): ScraperResult =
            ScraperResult(
                animeId = "431767",
                title = "Poster Proxy",
                titleCn = "Poster Proxy",
                matchedTitle = "Poster Proxy",
                confidence = 1f,
                source = ScraperSource.BANGUMI,
            )
    }

    private class StaticCloudDriveAutomationRepository(
        private val config: CloudDriveAutomationConfig
    ) : CloudDriveAutomationRepository {
        override fun observeConfig(): Flow<CloudDriveAutomationConfig> = flowOf(config)
        override suspend fun getConfig(): Result<CloudDriveAutomationConfig> = Result.success(config)
        override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> = Result.success(Unit)
        override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> = Result.success(Unit)
        override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> = flowOf(emptyList())
        override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> = Result.success(emptyList())
        override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> = Result.success(0L)
        override suspend fun deleteSubscription(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit> = Result.success(Unit)
        override suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean> = Result.success(false)
        override suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit> = Result.success(Unit)
        override suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long> = Result.success(0L)
    }

    private companion object {
        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
