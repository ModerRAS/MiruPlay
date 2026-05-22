package com.miruplay.tv.sync.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDriveRssNamesTest {
    @Test
    fun `folderSegment replaces CloudDrive path separators and reserved characters`() {
        assertEquals(
            "Show_Name_ 01_",
            CloudDriveRssNames.folderSegment("""Show/Name: 01?"""),
        )
        assertEquals("Unknown", CloudDriveRssNames.folderSegment("   "))
    }

    @Test
    fun `torrentFileName prefers torrent title and sanitizes prefix`() {
        assertEquals(
            "abcdef123456-Episode 01.torrent",
            CloudDriveRssNames.torrentFileName(
                title = "Episode 01.torrent",
                url = "https://example.test/download?id=1",
                keyPrefix = "abcdef1234567890!@#",
            ),
        )
    }

    @Test
    fun `torrentFileName falls back to URL path segment and appends extension`() {
        assertEquals(
            "episode 01.torrent",
            CloudDriveRssNames.torrentFileName(
                title = "Episode 01",
                url = "https://example.test/torrents/episode%2001",
                keyPrefix = "",
            ),
        )
    }

    @Test
    fun `torrentFileName sanitizes reserved filename characters and blank names`() {
        assertEquals(
            "rss-item.torrent",
            CloudDriveRssNames.torrentFileName(
                title = "",
                url = "https://example.test/",
                keyPrefix = "",
            ),
        )
        assertEquals(
            "bad_name.torrent",
            CloudDriveRssNames.torrentFileName(
                title = "bad/name.torrent",
                url = "https://example.test/fallback.torrent",
                keyPrefix = "",
            ),
        )
    }
}
