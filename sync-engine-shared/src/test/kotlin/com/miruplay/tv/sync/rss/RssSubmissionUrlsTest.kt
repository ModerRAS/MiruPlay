package com.miruplay.tv.sync.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssSubmissionUrlsTest {
    @Test
    fun `typeOf classifies magnet torrent other and blank urls`() {
        assertEquals(RssSubmissionUrlType.MAGNET, RssSubmissionUrls.typeOf("magnet:?xt=urn:btih:abc"))
        assertEquals(RssSubmissionUrlType.TORRENT, RssSubmissionUrls.typeOf("https://example.test/file.torrent?token=abc"))
        assertEquals(RssSubmissionUrlType.OTHER, RssSubmissionUrls.typeOf("https://example.test/watch/1"))
        assertEquals(RssSubmissionUrlType.NONE, RssSubmissionUrls.typeOf(" "))
        assertEquals(RssSubmissionUrlType.NONE, RssSubmissionUrls.typeOf(null))
    }

    @Test
    fun `select prefers offline download candidates before ordinary links`() {
        assertEquals(
            "https://example.test/file.torrent?token=abc",
            RssSubmissionUrls.select(
                link = "https://example.test/detail/1",
                enclosureUrl = "https://example.test/file.torrent?token=abc",
            ),
        )
        assertEquals(
            "magnet:?xt=urn:btih:abc",
            RssSubmissionUrls.select(
                link = "magnet:?xt=urn:btih:abc",
                enclosureUrl = "https://example.test/file.torrent",
            ),
        )
        assertEquals(
            "https://example.test/detail/1",
            RssSubmissionUrls.select(
                link = "https://example.test/detail/1",
                enclosureUrl = null,
            ),
        )
    }

    @Test
    fun `isTorrent delegates to shared URL type classification`() {
        assertTrue(RssSubmissionUrls.isTorrent("https://example.test/file.torrent#fragment"))
        assertFalse(RssSubmissionUrls.isTorrent("magnet:?xt=urn:btih:abc"))
        assertFalse(RssSubmissionUrls.isTorrent(null))
    }
}
