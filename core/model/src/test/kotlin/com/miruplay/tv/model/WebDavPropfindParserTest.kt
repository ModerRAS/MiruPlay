package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPropfindParserTest {
    @Test
    fun `parse returns children with directories first and hidden entries filtered`() {
        val entries = WebDavPropfindParser.parse(
            xml = propfindResponse("/dav/", includeHidden = true),
            baseUrl = "https://dav.example/dav",
            requestedPath = "",
        )

        assertEquals(listOf("Season 01", "Episode 01.mkv"), entries.map { it.name })
        assertTrue(entries.first().isDirectory)
        assertEquals("/Episode 01.mkv", entries.last().path)
        assertEquals(1234L, entries.last().size)
        assertEquals("video/x-matroska", entries.last().mimeType)
    }

    @Test
    fun `parse can include requested file for metadata lookup`() {
        val entries = WebDavPropfindParser.parse(
            xml = propfindFileResponse("/dav/Episode%2001.mkv"),
            baseUrl = "https://dav.example/dav",
            requestedPath = "/Episode 01.mkv",
            includeRequestedPath = true,
        )

        assertEquals(listOf("Episode 01.mkv"), entries.map { it.name })
        assertEquals(1234L, entries.single().size)
    }

    @Test
    fun `parse handles non ascii base paths`() {
        val entries = WebDavPropfindParser.parse(
            xml = propfindResponse("/媒体库/"),
            baseUrl = "https://dav.example/媒体库",
            requestedPath = "",
        )

        assertEquals(listOf("Season 01", "Episode 01.mkv"), entries.map { it.name })
        assertEquals("/Season 01", entries.first().path)
    }

    @Test
    fun `parse preserves encoded question marks in WebDAV file names`() {
        val entries = WebDavPropfindParser.parse(
            xml = propfindFileResponse("/dav/Episode%20%231%3F.mkv"),
            baseUrl = "https://dav.example/dav",
            requestedPath = "",
            includeRequestedPath = true,
        )

        assertEquals("Episode #1?.mkv", entries.single().name)
        assertEquals("/Episode #1?.mkv", entries.single().path)
    }

    @Test
    fun `parse preserves encoded question marks from absolute href URLs`() {
        val entries = WebDavPropfindParser.parse(
            xml = propfindFileResponse("https://dav.example/dav/Episode%20%231%3F.mkv?download=1"),
            baseUrl = "https://dav.example/dav",
            requestedPath = "",
            includeRequestedPath = true,
        )

        assertEquals("Episode #1?.mkv", entries.single().name)
        assertEquals("/Episode #1?.mkv", entries.single().path)
    }

    @Test
    fun `parse rejects doctype declarations`() {
        val result = runCatching {
            WebDavPropfindParser.parse(
                xml = """<?xml version="1.0"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><d:multistatus xmlns:d="DAV:"/>""",
                baseUrl = "https://dav.example/dav",
                requestedPath = "",
            )
        }

        assertTrue(result.isFailure)
    }

    private fun propfindResponse(rootHref: String, includeHidden: Boolean = false): String {
        val hidden = if (!includeHidden) "" else """
  <d:response>
    <d:href>${rootHref}.DS_Store</d:href>
    <d:propstat><d:prop>
      <d:getcontentlength>4</d:getcontentlength>
      <d:getcontenttype>application/octet-stream</d:getcontenttype>
    </d:prop></d:propstat>
  </d:response>"""
        return """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$rootHref</d:href>
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
  <d:response>
    <d:href>${rootHref}Season%2001/</d:href>
    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
  </d:response>$hidden
</d:multistatus>"""
    }

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
