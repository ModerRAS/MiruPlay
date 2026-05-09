package com.miruplay.tv.mediasource

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavMediaSourceTest {

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
}
