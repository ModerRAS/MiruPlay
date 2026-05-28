package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
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
        assertEquals("葬送的芙莉莲", result.single().matchedTitle)
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
        assertEquals("葬送的芙莉莲", result.single().matchedTitle)
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
