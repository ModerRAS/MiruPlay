package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssMediaSourceTest {
    private fun feedXml(base: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss xmlns:anime="https://resources.ani.rip" version="2.0"><channel>
        <item><title><![CDATA[[ANi] 野生的大魔王出現了！ - 16 [1080P][Baha].mp4]]></title>
        <link>${base}ep16.mp4?d=mp4</link>
        <guid isPermaLink="false">${base}ep16.mp4?d=mp4</guid>
        <category><![CDATA[2835ee8b-7d70-5ab7-8097-e2cb6ef58fd9]]></category>
        <pubDate>Sat, 17 Oct 2026 13:31:10 GMT</pubDate>
        <anime:size>457.2 MB</anime:size></item>
        <item><title><![CDATA[[ANi] 佐佐木與文鳥小嗶 Season 2 - 02 [1080P][Baha].mp4]]></title>
        <link>${base}ep02.mp4?d=mp4</link>
        <pubDate>Sat, 17 Oct 2026 13:01:25 GMT</pubDate>
        <anime:size>14.0 MB</anime:size></item>
        </channel></rss>
    """.trimIndent()

    private fun serverWithFeed(): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/feed.xml")) {
                    return MockResponse().setResponseCode(200).setBody(feedXml(server.url("/").toString()))
                }
                if (path.startsWith("/ep")) {
                    if (request.getHeader("Range") != null) {
                        return MockResponse()
                            .setResponseCode(206)
                            .setHeader("Content-Range", "bytes 0-0/100")
                            .setBody("x")
                    }
                    return MockResponse().setResponseCode(200).setBody("fake-video-bytes")
                }
                return MockResponse().setResponseCode(404)
            }
        }
        return server
    }

    private fun source(feedUrl: String): RssMediaSource =
        RssMediaSource(
            MediaSourceInfo(
                name = "RSS",
                type = MediaSourceType.RSS,
                connectionInfo = mapOf("url" to feedUrl),
            ),
        )

    @Test
    fun `listFiles exposes series directories then episode urls`() = runBlocking {
        serverWithFeed().use { server ->
            val media = source(server.url("/feed.xml").toString())

            val root = (media.listFiles("") as Result.Success).data
            assertEquals(2, root.size)
            assertTrue(root.all { it.isDirectory })
            val names = root.map { it.name }.sorted()
            assertEquals(listOf("佐佐木與文鳥小嗶", "野生的大魔王出現了！"), names)

            val seriesKey = root.first { it.name == "野生的大魔王出現了！" }.path
            val episodes = (media.listFiles(seriesKey) as Result.Success).data
            assertEquals(1, episodes.size)
            val episode = episodes.single()
            assertTrue(episode.path.startsWith(server.url("/").toString()))
            assertTrue(episode.path.endsWith("ep16.mp4?d=mp4"))
            assertEquals(479408947L, episode.size)
        }
    }

    @Test
    fun `missing category falls back to series title grouping`() = runBlocking {
        serverWithFeed().use { server ->
            val media = source(server.url("/feed.xml").toString())
            val root = (media.listFiles("") as Result.Success).data
            val fallback = root.first { it.name == "佐佐木與文鳥小嗶" }
            val episodes = (media.listFiles(fallback.path) as Result.Success).data
            assertEquals(1, episodes.size)
        }
    }

    @Test
    fun `testConnection verifies feed and detects range support`() = runBlocking {
        serverWithFeed().use { server ->
            val media = source(server.url("/feed.xml").toString())
            val result = media.testConnection()
            assertTrue(result is Result.Success)
            assertTrue(media.capabilities.supportsRange)
        }
    }

    @Test
    fun `testConnection fails when feed is unreachable`() = runBlocking {
        serverWithFeed().use { server ->
            val media = source(server.url("/missing.xml").toString())
            val result = media.testConnection()
            assertTrue(result is Result.Error)
        }
    }

    @Test
    fun `openStream reads direct url bytes`() = runBlocking {
        serverWithFeed().use { server ->
            val media = source(server.url("/feed.xml").toString())
            val stream = (media.openStream(server.url("/ep16.mp4").toString()) as Result.Success).data
            val bytes = stream.use { it.readBytes() }.toString(Charsets.UTF_8)
            assertEquals("fake-video-bytes", bytes)
        }
    }
}
