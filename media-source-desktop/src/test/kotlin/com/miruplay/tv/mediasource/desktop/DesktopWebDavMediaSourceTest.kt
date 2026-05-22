package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class DesktopWebDavMediaSourceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listFiles sends propfind and parses directories before files`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(propfindResponse(server.url("/dav/").encodedPath))
        )
        val source = DesktopWebDavMediaSource.create("DAV", server.url("/dav").toString())

        val result = source.listFiles("")

        assertTrue(result is Result.Success)
        val entries = (result as Result.Success).data
        assertEquals(listOf("Season 01", "Episode 01.mkv"), entries.map { it.name })
        assertTrue(entries.first().isDirectory)
        assertEquals("video/x-matroska", entries.last().mimeType)
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    @Test
    fun `openStream sends basic auth and reads response body`() = runBlocking {
        server.enqueue(MockResponse().setBody("payload"))
        val source = DesktopWebDavMediaSource.create(
            name = "DAV",
            url = server.url("/dav").toString(),
            username = "user",
            password = "pass",
        )

        val result = source.openStream("/Episode 01.mkv")

        assertTrue(result is Result.Success)
        (result as Result.Success).data.use { stream ->
            assertEquals("payload", stream.readBytes().decodeToString())
        }
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "Basic ${Base64.getEncoder().encodeToString("user:pass".toByteArray())}",
            request.getHeader("Authorization"),
        )
    }

    @Test
    fun `openStream with range sends HTTP range header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(206).setBody("2345"))
        val source = DesktopWebDavMediaSource.create("DAV", server.url("/dav").toString())

        val result = source.openStream("/Episode 01.mkv", DesktopStreamRange(2, 5))

        assertTrue(result is Result.Success)
        (result as Result.Success).data.use { stream ->
            assertEquals("2345", stream.readBytes().decodeToString())
        }
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("bytes=2-5", request.getHeader("Range"))
    }

    @Test
    fun `normalizeUrl encodes each path segment with spaces and unicode`() {
        val source = DesktopWebDavMediaSource.create("DAV", server.url("/dav").toString())

        val normalized = source.normalizeUrl("/孤独摇滚/Episode 01.mkv")

        assertTrue(normalized.endsWith("/dav/%E5%AD%A4%E7%8B%AC%E6%91%87%E6%BB%9A/Episode%2001.mkv"))
    }

    @Test
    fun `getMetadata maps requested file response to FileMetadata`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(propfindFileResponse(server.url("/dav/Episode%2001.mkv").encodedPath))
        )
        val source = DesktopWebDavMediaSource.create("DAV", server.url("/dav").toString())

        val result = source.getMetadata("/Episode 01.mkv")

        assertTrue(result is Result.Success)
        val metadata = (result as Result.Success).data
        assertEquals("Episode 01.mkv", metadata.name)
        assertEquals(1234L, metadata.size)
        assertEquals("video/x-matroska", metadata.mimeType)
    }

    private fun propfindResponse(rootHref: String): String = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$rootHref</d:href>
    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
  </d:response>
  <d:response>
    <d:href>${rootHref}Season%2001/</d:href>
    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
  </d:response>
  <d:response>
    <d:href>${rootHref}Episode%2001.mkv</d:href>
    <d:propstat><d:prop>
      <d:getcontentlength>1234</d:getcontentlength>
      <d:getcontenttype>video/x-matroska</d:getcontenttype>
      <d:getlastmodified>Wed, 01 Jan 2025 12:00:00 GMT</d:getlastmodified>
    </d:prop></d:propstat>
  </d:response>
</d:multistatus>"""

    private fun propfindFileResponse(fileHref: String): String = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$fileHref</d:href>
    <d:propstat><d:prop>
      <d:getcontentlength>1234</d:getcontentlength>
      <d:getcontenttype>video/x-matroska</d:getcontenttype>
      <d:getlastmodified>Wed, 01 Jan 2025 12:00:00 GMT</d:getlastmodified>
    </d:prop></d:propstat>
  </d:response>
</d:multistatus>"""
}
