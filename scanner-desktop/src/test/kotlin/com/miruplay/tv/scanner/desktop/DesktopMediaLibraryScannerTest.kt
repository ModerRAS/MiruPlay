package com.miruplay.tv.scanner.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.nio.file.Files

class DesktopMediaLibraryScannerTest {
    @Test
    fun `scan indexes video files and directories with inferred metadata`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-scan")
        try {
            val show = Files.createDirectory(root.resolve("Frieren"))
            Files.writeString(show.resolve("[Group] Frieren - S01E02 [1080p].mkv"), "video")
            Files.writeString(show.resolve("cover.jpg"), "image")

            val source = DesktopLocalMediaSource.create("Local", root)
            val scanner = DesktopMediaLibraryScanner()

            val result = scanner.scan(sourceId = 42L, source = source)

            assertTrue(result is Result.Success)
            val report = (result as Result.Success).data
            assertEquals(1, report.filesIndexed)
            assertEquals(2, report.directoriesVisited)

            val video = report.entries.single { !it.isDirectory }
            assertEquals(42L, video.sourceId)
            assertEquals("Frieren", video.animeName)
            assertEquals(1, video.seasonNumber)
            assertEquals(2, video.episodeNumber)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scan prefers sibling episode nfo metadata over filename inference`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-scan")
        try {
            val show = Files.createDirectory(root.resolve("Filename Show"))
            Files.writeString(show.resolve("badly named episode.mkv"), "video")
            Files.writeString(
                show.resolve("badly named episode.nfo"),
                """
                <episodedetails>
                    <title>Nfo Episode Title</title>
                    <showtitle>Nfo Show</showtitle>
                    <plot>Nfo episode plot.</plot>
                    <season>2</season>
                    <episode>7</episode>
                </episodedetails>
                """.trimIndent(),
            )

            val source = DesktopLocalMediaSource.create("Local", root)
            val scanner = DesktopMediaLibraryScanner()

            val report = (scanner.scan(sourceId = 9L, source = source) as Result.Success).data

            val video = report.entries.single { !it.isDirectory }
            assertEquals("Nfo Show", video.animeName)
            assertEquals("Nfo Episode Title", video.episodeTitle)
            assertEquals("Nfo episode plot.", video.plot)
            assertEquals(2, video.seasonNumber)
            assertEquals(7, video.episodeNumber)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scan uses tvshow nfo title when episode nfo is missing`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-scan")
        try {
            val show = Files.createDirectory(root.resolve("Filename Show"))
            Files.writeString(show.resolve("tvshow.nfo"), """
                <tvshow>
                    <title>Directory Nfo Show</title>
                    <originaltitle>Original Directory Title</originaltitle>
                </tvshow>
                """.trimIndent())
            Files.writeString(show.resolve("Bad Name - 03.mkv"), "video")

            val source = DesktopLocalMediaSource.create("Local", root)
            val scanner = DesktopMediaLibraryScanner()

            val report = (scanner.scan(sourceId = 10L, source = source) as Result.Success).data

            val video = report.entries.single { !it.isDirectory }
            assertEquals("Directory Nfo Show", video.animeName)
            assertEquals(3, video.episodeNumber)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scan honors max depth`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-scan")
        try {
            val show = Files.createDirectory(root.resolve("Show"))
            val season = Files.createDirectory(show.resolve("Season 01"))
            Files.writeString(season.resolve("Show - 01.mkv"), "video")

            val source = DesktopLocalMediaSource.create("Local", root)
            val scanner = DesktopMediaLibraryScanner(DesktopScanConfig(maxDepth = 1))

            val report = (scanner.scan(sourceId = 1L, source = source) as Result.Success).data

            assertTrue(report.entries.none { it.path.endsWith("Show - 01.mkv") })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scan supports uri style remote entry paths`() = runBlocking {
        val source = StubDesktopMediaSource(
            mapOf(
                "" to listOf(
                    FileEntry(
                        name = "Show",
                        path = "smb://nas/share/Show/",
                        isDirectory = true,
                    )
                ),
                "smb://nas/share/Show/" to listOf(
                    FileEntry(
                        name = "Remote Show - S01E03.mkv",
                        path = "smb://nas/share/Show/Remote Show - S01E03.mkv",
                        isDirectory = false,
                        size = 1L,
                    )
                ),
            )
        )
        val scanner = DesktopMediaLibraryScanner()

        val report = (scanner.scan(sourceId = 11L, source = source) as Result.Success).data

        val video = report.entries.single { !it.isDirectory }
        assertEquals("Remote Show", video.animeName)
        assertEquals(1, video.seasonNumber)
        assertEquals(3, video.episodeNumber)
    }

    @Test
    fun `scan can combine parser results from folder and filename`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-scan")
        try {
            val show = Files.createDirectory(root.resolve("葬送的芙莉莲 第2季"))
            Files.writeString(show.resolve("03 [1080P].mkv"), "video")

            val source = DesktopLocalMediaSource.create("Local", root)
            val scanner = DesktopMediaLibraryScanner(
                filenameMetadataParser = MappingFilenameParser(
                    mapOf(
                        "葬送的芙莉莲 第2季" to FilenameParseResult(
                            title = "葬送的芙莉莲",
                            season = 2
                        ),
                        "03 [1080P]" to FilenameParseResult(episode = 3)
                    )
                )
            )

            val report = (scanner.scan(sourceId = 12L, source = source) as Result.Success).data

            val video = report.entries.single { !it.isDirectory }
            assertEquals("葬送的芙莉莲", video.animeName)
            assertEquals(2, video.seasonNumber)
            assertEquals(3, video.episodeNumber)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scan feeds full path to parser for numbered files`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-scan")
        try {
            val show = Files.createDirectory(root.resolve("葬送的芙莉莲 第2季"))
            val video = show.resolve("03.mkv")
            Files.writeString(video, "video")

            val source = DesktopLocalMediaSource.create("Local", root)
            val parser = MappingFilenameParser(
                mapOf(
                    video.toString().replace('\\', '/') to FilenameParseResult(
                        title = "葬送的芙莉莲",
                        season = 2,
                        episode = 3
                    )
                )
            )
            val scanner = DesktopMediaLibraryScanner(filenameMetadataParser = parser)

            val report = (scanner.scan(sourceId = 13L, source = source) as Result.Success).data

            val indexed = report.entries.single { !it.isDirectory }
            assertEquals("葬送的芙莉莲", indexed.animeName)
            assertEquals(2, indexed.seasonNumber)
            assertEquals(3, indexed.episodeNumber)
            assertTrue(parser.requests.contains(video.toString().replace('\\', '/')))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `nfo reader resolves sibling nfo paths across local and remote formats`() {
        val reader = DesktopNfoMetadataReader()

        assertEquals("""D:\Anime\Show\Episode 01.nfo""", reader.siblingNfoPath("""D:\Anime\Show\Episode 01.mkv"""))
        assertEquals("/mnt/anime/Show/Episode 01.nfo", reader.siblingNfoPath("/mnt/anime/Show/Episode 01.mp4"))
        assertEquals("smb://nas/share/Show/Episode 01.nfo", reader.siblingNfoPath("smb://nas/share/Show/Episode 01.mkv"))
        assertEquals("""D:\Anime\Show\tvshow.nfo""", reader.childPath("""D:\Anime\Show""", "tvshow.nfo"))
        assertEquals("/mnt/anime/Show/tvshow.nfo", reader.childPath("/mnt/anime/Show", "tvshow.nfo"))
        assertEquals("smb://nas/share/Show/tvshow.nfo", reader.childPath("smb://nas/share/Show", "tvshow.nfo"))
    }

    private class StubDesktopMediaSource(
        private val entriesByPath: Map<String, List<FileEntry>>,
    ) : MediaSource {
        override val id: String = "stub"
        override val info: MediaSourceInfo = MediaSourceInfo(
            name = "Stub",
            type = MediaSourceType.SMB,
            isConnected = true,
        )
        override val capabilities: MediaCapabilities = MediaCapabilities(supportsList = true)

        override suspend fun listFiles(path: String): Result<List<FileEntry>> =
            Result.success(entriesByPath[path].orEmpty())

        override suspend fun openStream(path: String): Result<InputStream> =
            Result.failure(AppError.MediaSourceError.NotFound(path))

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.failure(AppError.MediaSourceError.NotFound(path))

        override suspend fun testConnection(): Result<Boolean> =
            Result.success(true)

        override suspend fun close() = Unit
    }

    private class MappingFilenameParser(
        private val results: Map<String, FilenameParseResult>
    ) : FilenameMetadataParser {
        val requests = mutableListOf<String>()

        override fun parse(filename: String, maxLength: Int): FilenameParseResult {
            requests += filename
            return results[filename] ?: FilenameParseResult()
        }
    }
}
