package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class BangumiArchiveTest {
    @Test
    fun `downloadLatest uses configured HTTP proxy for latest and zip requests`() = runBlocking {
        val archiveZip = zipOf(
            "subject.jsonlines" to """{"id":440650,"type":2,"name":"Dr.STONE SCIENCE FUTURE","name_cn":"Dr.STONE 新石纪 第四季"}""",
        )

        MockWebServer().use { proxy ->
            proxy.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "browser_download_url": "http://github.com/bangumi/Archive/releases/download/test/dump.zip",
                      "content_type": "application/zip",
                      "digest": "sha256:${archiveZip.sha256()}",
                      "name": "dump-test.zip",
                      "size": ${archiveZip.size}
                    }
                    """.trimIndent()
                )
            )
            proxy.enqueue(MockResponse().setBody(Buffer().write(archiveZip)))

            val tempDir = createTempDirectory(prefix = "bangumi-archive-proxy-test-").toFile()
            val store = BangumiArchiveStore(
                directory = tempDir,
                client = BangumiArchiveClient(
                    latestUrl = "http://raw.githubusercontent.com/bangumi/Archive/master/aux/latest.json"
                ),
            )
            store.configureProxy(
                BangumiHttpProxyConfig(enabled = true, host = proxy.hostName, port = proxy.port)
            )

            val result = store.downloadLatest()

            assertTrue(result is Result.Success)
            assertTrue(store.subjectFile.readText().contains("Dr.STONE 新石纪 第四季"))
            assertEquals("raw.githubusercontent.com", proxy.takeRequest().headers["Host"])
            assertEquals("github.com", proxy.takeRequest().headers["Host"])
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `downloadLatest reads latest json validates zip and extracts subject jsonlines`() = runBlocking {
        val archiveZip = zipOf(
            "subject.jsonlines" to """
                {"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","score":8.8}
            """.trimIndent(),
            "episode.jsonlines" to """{"id":1,"subject_id":431767}""",
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "browser_download_url": "${server.url("/dump.zip")}",
                      "content_type": "application/zip",
                      "created_at": "2026-05-26T21:04:58Z",
                      "digest": "sha256:${archiveZip.sha256()}",
                      "name": "dump-2026-05-26.210457Z.zip",
                      "size": ${archiveZip.size}
                    }
                    """.trimIndent()
                )
            )
            server.enqueue(MockResponse().setBody(Buffer().write(archiveZip)))

            val tempDir = createTempDirectory(prefix = "bangumi-archive-test-").toFile()
            val store = BangumiArchiveStore(
                directory = tempDir,
                client = BangumiArchiveClient(latestUrl = server.url("/latest.json").toString()),
            )
            val progress = mutableListOf<Pair<Long, Long>>()

            val result = store.downloadLatest { bytesRead, totalBytes ->
                progress += bytesRead to totalBytes
            }

            assertTrue(result is Result.Success)
            val snapshot = (result as Result.Success).data
            assertTrue(snapshot.hasSubjectData)
            assertEquals("dump-2026-05-26.210457Z.zip", snapshot.latest?.name)
            assertTrue(store.subjectFile.readText().contains("葬送的芙莉莲"))
            assertEquals(archiveZip.size.toLong(), progress.last().first)
            assertEquals(archiveZip.size.toLong(), progress.last().second)
            assertEquals("/latest.json", server.takeRequest().path)
            assertEquals("/dump.zip", server.takeRequest().path)
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `downloadLatest skips zip when local archive is current`() = runBlocking {
        val latestJson = """
            {
              "browser_download_url": "http://example.test/dump.zip",
              "content_type": "application/zip",
              "created_at": "2026-05-26T21:04:58Z",
              "digest": "sha256:current",
              "name": "dump-2026-05-26.210457Z.zip",
              "size": 123
            }
        """.trimIndent()

        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(latestJson))

            val tempDir = createTempDirectory(prefix = "bangumi-archive-current-test-").toFile()
            File(tempDir, "latest.json").writeText(latestJson)
            File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME).writeText(
                """{"id":431767,"type":2,"name":"葬送のフリーレン"}"""
            )
            val staleDownload = File(tempDir, "stale.download").apply { writeText("stale") }
            val store = BangumiArchiveStore(
                directory = tempDir,
                client = BangumiArchiveClient(latestUrl = server.url("/latest.json").toString()),
            )
            val progress = mutableListOf<Pair<Long, Long>>()

            val result = store.downloadLatest { bytesRead, totalBytes ->
                progress += bytesRead to totalBytes
            }

            assertTrue(result is Result.Success)
            assertTrue(store.subjectFile.readText().contains("葬送のフリーレン"))
            assertTrue(progress.isEmpty())
            assertFalse(staleDownload.exists())
            assertEquals(1, server.requestCount)
            assertEquals("/latest.json", server.takeRequest().path)
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `downloadLatest replaces existing archive without retaining duplicate download artifacts`() = runBlocking {
        val archiveZip = zipOf(
            "episode.jsonlines" to """{"id":1,"subject_id":431767}""",
            "subject.jsonlines" to """{"id":440650,"type":2,"name":"Dr.STONE SCIENCE FUTURE","name_cn":"Dr.STONE 新石纪 第四季"}""",
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "browser_download_url": "${server.url("/dump-new.zip")}",
                      "content_type": "application/zip",
                      "created_at": "2026-05-27T21:04:58Z",
                      "digest": "sha256:${archiveZip.sha256()}",
                      "name": "dump-new.zip",
                      "size": ${archiveZip.size}
                    }
                    """.trimIndent()
                )
            )
            server.enqueue(MockResponse().setBody(Buffer().write(archiveZip)))

            val tempDir = createTempDirectory(prefix = "bangumi-archive-replace-test-").toFile()
            File(tempDir, "latest.json").writeText(
                """
                {
                  "browser_download_url": "http://example.test/dump-old.zip",
                  "content_type": "application/zip",
                  "created_at": "2026-05-20T21:04:58Z",
                  "digest": "sha256:old",
                  "name": "dump-old.zip",
                  "size": 123
                }
                """.trimIndent()
            )
            File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME).writeText(
                """{"id":431767,"type":2,"name":"葬送のフリーレン"}"""
            )
            val store = BangumiArchiveStore(
                directory = tempDir,
                client = BangumiArchiveClient(latestUrl = server.url("/latest.json").toString()),
            )

            val result = store.downloadLatest()

            assertTrue(result is Result.Success)
            assertTrue(store.subjectFile.readText().contains("Dr.STONE 新石纪 第四季"))
            assertFalse(File(tempDir, "dump-new.zip.download").exists())
            assertFalse(File(tempDir, "${BangumiArchiveStore.SUBJECT_FILE_NAME}.download").exists())
            assertEquals(
                listOf("latest.json", BangumiArchiveStore.SUBJECT_FILE_NAME),
                tempDir.listFiles().orEmpty().map { it.name }.sorted(),
            )
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `downloadLatest removes temporary files after failed update`() = runBlocking {
        val archiveZip = zipOf(
            "episode.jsonlines" to """{"id":1,"subject_id":431767}""",
        )

        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "browser_download_url": "${server.url("/dump.zip")}",
                      "content_type": "application/zip",
                      "digest": "sha256:${archiveZip.sha256()}",
                      "name": "dump-broken.zip",
                      "size": ${archiveZip.size}
                    }
                    """.trimIndent()
                )
            )
            server.enqueue(MockResponse().setBody(Buffer().write(archiveZip)))

            val tempDir = createTempDirectory(prefix = "bangumi-archive-failure-test-").toFile()
            val store = BangumiArchiveStore(
                directory = tempDir,
                client = BangumiArchiveClient(latestUrl = server.url("/latest.json").toString()),
            )

            val result = store.downloadLatest()

            assertTrue(result is Result.Error)
            assertFalse(File(tempDir, "${BangumiArchiveStore.SUBJECT_FILE_NAME}.download").exists())
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `importArchiveStream reads upload stream and extracts subject jsonlines`() = runBlocking {
        val archiveZip = zipOf(
            "archive/subject.jsonlines" to """{"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲"}""",
            "episode.jsonlines" to """{"id":1,"subject_id":431767}""",
        )
        val tempDir = createTempDirectory(prefix = "bangumi-archive-upload-stream-test-").toFile()
        val store = BangumiArchiveStore(directory = tempDir)

        val result = store.importArchiveStream(
            input = ByteArrayInputStream(archiveZip),
            originalName = "manual-dump.zip",
            contentLength = archiveZip.size.toLong(),
        )

        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data
        assertTrue(snapshot.hasSubjectData)
        assertEquals("manual-dump.zip", snapshot.latest?.name)
        assertTrue(store.subjectFile.readText().contains("葬送的芙莉莲"))
        assertFalse(File(tempDir, "${BangumiArchiveStore.SUBJECT_FILE_NAME}.raw-upload").exists())
        tempDir.deleteRecursively()
        Unit
    }

    @Test
    fun `importArchiveStream rejects oversized upload without reading request body`() = runBlocking {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-upload-limit-test-").toFile()
        val store = BangumiArchiveStore(directory = tempDir)
        val unreadableInput = object : InputStream() {
            override fun read(): Int = error("oversized upload should be rejected before reading")
        }

        val result = store.importArchiveStream(
            input = unreadableInput,
            originalName = "too-large.zip",
            contentLength = BangumiArchiveStore.MAX_ARCHIVE_IMPORT_BYTES + 1L,
        )

        assertTrue(result is Result.Error)
        assertFalse(store.subjectFile.exists())
        tempDir.deleteRecursively()
        Unit
    }

    @Test
    fun `subject search indexes anime rows and returns Bangumi ids`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-search-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """
            {"id":1,"type":1,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲"}
            {"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","infobox":"|别名=Frieren\n|英文名=Frieren: Beyond Journey's End","score":8.8,"rank":1,"date":"2023-09-29"}
            {"id":999,"type":2,"name":"Completely Different","name_cn":"完全不同","score":7.0}
            """.trimIndent()
        )

        val search = BangumiArchiveSubjectSearch(subjectFile)

        val result = search.search("Frieren")

        assertEquals("431767", result.single().animeId)
        assertEquals("Frieren", result.single().matchedTitle)
        assertTrue(result.single().confidence >= 0.62f)
        tempDir.deleteRecursively()
    }

    @Test
    fun `subject search indexes structured infobox aliases`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-structured-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """
            {"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","infobox":[{"key":"别名","value":[{"v":"Frieren"},{"v":"Sousou no Frieren"}]},{"key":"制作","value":"Madhouse"}],"score":8.8,"rank":1}
            {"id":999,"type":2,"name":"Completely Different","name_cn":"完全不同","score":7.0}
            """.trimIndent()
        )

        val search = BangumiArchiveSubjectSearch(subjectFile)

        val result = search.search("Sousou no Frieren")

        assertEquals("431767", result.single().animeId)
        assertEquals("Sousou no Frieren", result.single().matchedTitle)
        tempDir.deleteRecursively()
    }

    @Test
    fun `subject search prefers requested season over generic series title`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-season-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """
            {"id":266794,"type":2,"name":"Dr.STONE","name_cn":"石纪元","infobox":[{"key":"别名","value":[{"v":"Dr STONE 新石纪"}]}],"score":7.5,"rank":10}
            {"id":471578,"type":2,"name":"Dr.STONE SCIENCE FUTURE","name_cn":"石纪元 科学与未来","infobox":[{"key":"别名","value":[{"v":"Dr STONE 新石纪 第四季"}]}],"score":7.2,"rank":20}
            """.trimIndent()
        )

        val search = BangumiArchiveSubjectSearch(subjectFile)

        val result = search.search("Dr STONE 新石纪 第四季")

        assertEquals("471578", result.single().animeId)
        assertTrue(result.single().confidence >= 0.62f)
        tempDir.deleteRecursively()
    }


    @Test
    fun `subject search normalizes queries before matching`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-normalized-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """{"id":10,"type":2,"name":"Old","name_cn":"简体标题","score":8.0}"""
        )

        val search = BangumiArchiveSubjectSearch(
            subjectFile = subjectFile,
            normalizeQuery = { value -> if (value == "繁體標題") "简体标题" else value },
        )

        val result = search.search("繁體標題")

        assertEquals("10", result.single().animeId)
        assertEquals(1.0f, result.single().confidence)
        tempDir.deleteRecursively()
    }

    @Test
    fun `subject search supports exact numeric Bangumi subject id queries`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-id-search-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """
            {"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","score":8.8}
            {"id":999999,"type":2,"name":"Completely Different","name_cn":"完全不同","score":7.0}
            """.trimIndent()
        )

        val search = BangumiArchiveSubjectSearch(subjectFile)

        val result = search.search("431767")

        assertEquals(1, result.size)
        assertEquals("431767", result.single().animeId)
        assertEquals(1.0f, result.single().confidence)
        tempDir.deleteRecursively()
    }

    @Test
    fun `subject search creates lucene sidecar index directory`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-lucene-sidecar-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """{"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","score":8.8}"""
        )
        val luceneDirectory = File(tempDir, "lucene-v1")

        val search = BangumiArchiveSubjectSearch(subjectFile)

        val result = search.search("葬送的芙莉莲")

        assertEquals("431767", result.single().animeId)
        assertTrue("Lucene sidecar index directory should exist after searching", luceneDirectory.isDirectory)
        assertTrue(
            "Lucene sidecar index directory should contain index files",
            luceneDirectory.listFiles().orEmpty().isNotEmpty(),
        )
        tempDir.deleteRecursively()
    }

    @Test
    fun `subject search recovers when lucene sidecar path is invalid`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-lucene-recovery-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """{"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","score":8.8}"""
        )
        val luceneDirectory = File(tempDir, "lucene-v1")
        luceneDirectory.writeText("not-a-directory")

        val search = BangumiArchiveSubjectSearch(subjectFile)

        val result = search.search("葬送的芙莉莲")

        assertEquals("431767", result.single().animeId)
        assertTrue("Invalid Lucene sidecar path should be replaced by an index directory", luceneDirectory.isDirectory)
        assertTrue(
            "Recovered Lucene sidecar index directory should contain index files",
            luceneDirectory.listFiles().orEmpty().isNotEmpty(),
        )
        tempDir.deleteRecursively()
    }

    @Test
    fun `subject search normalizes punctuation in english and mixed titles`() {
        val tempDir = createTempDirectory(prefix = "bangumi-archive-punctuation-test-").toFile()
        val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
        subjectFile.writeText(
            """
            {"id":1,"type":2,"name":"Re:ZERO kara Hajimeru Isekai Seikatsu","name_cn":"Re:从零开始的异世界生活","score":8.4}
            {"id":2,"type":2,"name":"Fate/stay night","name_cn":"命运之夜","score":8.1}
            {"id":3,"type":2,"name":"Dr.STONE SCIENCE FUTURE","name_cn":"Dr.STONE 新石纪 第四季","score":8.0}
            """.trimIndent()
        )

        val search = BangumiArchiveSubjectSearch(subjectFile)

        assertEquals("1", search.search("Re ZERO").single().animeId)
        assertEquals("2", search.search("Fate stay night").single().animeId)
        assertEquals("3", search.search("Dr STONE 新石纪 第四季").single().animeId)
        tempDir.deleteRecursively()
    }
}

private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { "%02x".format(Locale.US, it.toInt() and 0xff) }
