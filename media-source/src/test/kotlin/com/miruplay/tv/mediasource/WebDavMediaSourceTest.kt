package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class WebDavMediaSourceTest {

    @Test
    fun `listFiles retries with anonymous auth after initial 401`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setHeader("Content-Type", "application/xml")
                    .setBody(
                        """
                        <?xml version="1.0" encoding="utf-8" ?>
                        <D:multistatus xmlns:D="DAV:">
                            <D:response>
                                <D:href>/dav/%E5%BD%B1%E9%9F%B3/%E7%94%B5%E8%A7%86%E5%89%A7/</D:href>
                                <D:propstat>
                                    <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                                </D:propstat>
                            </D:response>
                            <D:response>
                                <D:href>/dav/%E5%BD%B1%E9%9F%B3/%E7%94%B5%E8%A7%86%E5%89%A7/01.mkv</D:href>
                                <D:propstat>
                                    <D:prop>
                                        <D:getcontentlength>456</D:getcontentlength>
                                        <D:resourcetype></D:resourcetype>
                                    </D:prop>
                                </D:propstat>
                            </D:response>
                        </D:multistatus>
                        """.trimIndent()
                    )
            )
            val source = WebDavMediaSource(
                MediaSourceInfo(
                    name = "WebDAV",
                    type = MediaSourceType.WEBDAV,
                    connectionInfo = mapOf("url" to server.url("/dav/影音/电视剧").toString().trimEnd('/'))
                )
            )

            val result = source.listFiles("")

            assertTrue(result is Result.Success)
            val entries = result.getOrNull()
            assertNotNull(entries)
            assertEquals(1, entries!!.size)
            assertEquals("/01.mkv", entries.single().path)

            val firstRequest = server.takeRequest()
            val secondRequest = server.takeRequest()
            assertNull(firstRequest.getHeader("Authorization"))
            assertEquals("Basic YW5vbnltb3VzOg==", secondRequest.getHeader("Authorization"))
        }
    }

    @Test
    fun `parsePropfindResponse skips current directory and preserves child names`() {
        val source = WebDavMediaSource(
            MediaSourceInfo(
                name = "WebDAV",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "http://example.test")
            )
        )

        val entries = source.parsePropfindResponse(
            xml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:multistatus xmlns:D="DAV:">
                    <D:response>
                        <D:href>/%E7%95%AA%E5%89%A7/</D:href>
                        <D:propstat>
                            <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                        </D:propstat>
                    </D:response>
                    <D:response>
                        <D:href>/%E7%95%AA%E5%89%A7/01%20%5B1080P%5D.mp4</D:href>
                        <D:propstat>
                            <D:prop>
                                <D:getcontentlength>1234</D:getcontentlength>
                                <D:resourcetype></D:resourcetype>
                            </D:prop>
                        </D:propstat>
                    </D:response>
                    <D:response>
                        <D:href>/%E7%95%AA%E5%89%A7/01.trickplay/</D:href>
                        <D:propstat>
                            <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                        </D:propstat>
                    </D:response>
                </D:multistatus>
            """.trimIndent(),
            requestedPath = "/番剧"
        )

        assertFalse("Current directory should not be returned", entries.any { it.path == "/番剧" })
        assertTrue("Child video should be returned", entries.any { it.path == "/番剧/01 [1080P].mp4" })
        assertEquals("01 [1080P].mp4", entries.first { !it.isDirectory }.name)
        assertEquals(1234L, entries.first { !it.isDirectory }.size)
        assertEquals("01.trickplay", entries.first { it.isDirectory }.name)
    }

    @Test
    fun `parsePropfindResponse handles WebDAV base path`() {
        val source = WebDavMediaSource(
            MediaSourceInfo(
                name = "WebDAV",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "http://nas.local/dav")
            )
        )

        val entries = source.parsePropfindResponse(
            xml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:multistatus xmlns:D="DAV:">
                    <D:response>
                        <D:href>/dav/%E7%95%AA%E5%89%A7/</D:href>
                        <D:propstat>
                            <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                        </D:propstat>
                    </D:response>
                </D:multistatus>
            """.trimIndent(),
            requestedPath = ""
        )

        assertEquals("/番剧", entries.single().path)
        assertEquals("番剧", entries.single().name)
    }

    @Test
    fun `parsePropfindResponse uses shared WebDAV ordering and hidden filtering`() {
        val source = WebDavMediaSource(
            MediaSourceInfo(
                name = "WebDAV",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "http://nas.local/dav")
            )
        )

        val entries = source.parsePropfindResponse(
            xml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:multistatus xmlns:D="DAV:">
                    <D:response>
                        <D:href>/dav/%E7%95%AA%E5%89%A7/</D:href>
                        <D:propstat>
                            <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                        </D:propstat>
                    </D:response>
                    <D:response>
                        <D:href>/dav/%E7%95%AA%E5%89%A7/Episode%2001%20%231%3F.mkv</D:href>
                        <D:propstat>
                            <D:prop>
                                <D:getcontentlength>2048</D:getcontentlength>
                                <D:getcontenttype>video/x-matroska</D:getcontenttype>
                                <D:resourcetype></D:resourcetype>
                            </D:prop>
                        </D:propstat>
                    </D:response>
                    <D:response>
                        <D:href>/dav/%E7%95%AA%E5%89%A7/Season%2001/</D:href>
                        <D:propstat>
                            <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                        </D:propstat>
                    </D:response>
                    <D:response>
                        <D:href>/dav/%E7%95%AA%E5%89%A7/.DS_Store</D:href>
                        <D:propstat>
                            <D:prop>
                                <D:getcontentlength>4</D:getcontentlength>
                                <D:resourcetype></D:resourcetype>
                            </D:prop>
                        </D:propstat>
                    </D:response>
                </D:multistatus>
            """.trimIndent(),
            requestedPath = "/番剧"
        )

        assertEquals(listOf("Season 01", "Episode 01 #1?.mkv"), entries.map { it.name })
        assertTrue(entries.first().isDirectory)
        assertEquals("/番剧/Episode 01 #1?.mkv", entries.last().path)
        assertEquals(2048L, entries.last().size)
        assertEquals("video/x-matroska", entries.last().mimeType)
    }

    @Test
    fun `parsePropfindResponse handles mixed namespace propstats from CloudDrive`() {
        val source = WebDavMediaSource(
            MediaSourceInfo(
                name = "WebDAV",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf(
                    "url" to "http://nas.local/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB"
                )
            )
        )

        val entries = source.parsePropfindResponse(
            xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                    <D:response>
                        <D:href>/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/</D:href>
                        <D:propstat>
                            <D:prop>
                                <D:getcontenttype>httpd/unix-directory</D:getcontenttype>
                                <d:resourcetype xmlns:d="DAV:"><D:collection></D:collection></d:resourcetype>
                            </D:prop>
                            <D:status>HTTP/1.1 200 OK</D:status>
                        </D:propstat>
                        <D:propstat>
                            <D:prop><D:displayname></D:displayname><D:getcontentlength></D:getcontentlength></D:prop>
                            <D:status>HTTP/1.1 404 Not Found</D:status>
                        </D:propstat>
                    </D:response>
                    <D:response>
                        <D:href>/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/Dr.STONE%20%E6%96%B0%E7%9F%B3%E7%B4%80%20%E7%AC%AC%E5%9B%9B%E5%AD%A3/</D:href>
                        <D:propstat>
                            <D:prop>
                                <D:getcontenttype>httpd/unix-directory</D:getcontenttype>
                                <d:resourcetype xmlns:d="DAV:"><D:collection></D:collection></d:resourcetype>
                            </D:prop>
                            <D:status>HTTP/1.1 200 OK</D:status>
                        </D:propstat>
                    </D:response>
                </D:multistatus>
            """.trimIndent(),
            requestedPath = ""
        )

        assertEquals("/Dr.STONE 新石紀 第四季", entries.single().path)
        assertEquals("Dr.STONE 新石紀 第四季", entries.single().name)
        assertTrue(entries.single().isDirectory)
    }
}
