package com.miruplay.tv.sync.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class RssTorrentSubmissionTest {
    @Test
    fun `rss item recognizes torrent links with query strings`() {
        val item = RssFeedItem(
            title = "Episode",
            guid = null,
            link = "https://example.test/item.torrent?token=abc",
            enclosureUrl = null
        )

        assertEquals("https://example.test/item.torrent?token=abc", item.submissionUrl)
        assertTrue(item.isTorrentSubmission)
    }

    @Test
    fun `torrent downloader builds safe staged file names`() {
        val name = CloudDriveRssNames.torrentFileName(
            title = """[ANi] Test: Show - 01 [1080P].torrent""",
            url = "https://example.test/download.torrent",
            keyPrefix = "abc123!@#"
        )

        assertEquals("abc123-[ANi] Test_ Show - 01 [1080P].torrent", name)
    }

    @Test
    fun `torrent parser converts torrent bytes to magnet link`() {
        val info = "d4:name8:Test.mkv12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrste"
        val torrent = "d8:announce14:http://tracker4:info${info}e"
        val expectedHash = MessageDigest.getInstance("SHA-1")
            .digest(info.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File.createTempFile("miruplay-rss-test", ".torrent")
        file.writeText(torrent)

        val result = try {
            TorrentMagnetParser.parse(file)
        } finally {
            file.delete()
        }

        assertTrue(result is com.miruplay.tv.core.common.Result.Success)
        assertEquals(
            "magnet:?xt=urn:btih:$expectedHash&dn=Test.mkv&tr=http%3A%2F%2Ftracker",
            (result as com.miruplay.tv.core.common.Result.Success).data
        )
    }
}
