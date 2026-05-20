package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfoConventions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSettingsPanelTest {
    @Test
    fun `source settings tiles summarize source types active source and index`() {
        val tiles = sourceSettingsTiles(
            sources = listOf(
                MediaSourceInfoConventions.local(name = "Local Anime", rootPath = "D:/Anime"),
                MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime"),
                MediaSourceInfoConventions.smb(url = "smb://nas.local/anime"),
            ),
            activeSourceLabel = "Local Anime · LOCAL",
            indexedItemCount = 42,
        )

        assertEquals(listOf("媒体源", "当前源", "海报墙索引"), tiles.map { it.label })
        assertEquals("3 个", tiles[0].value)
        assertTrue(tiles[0].detail.contains("本地 1"))
        assertTrue(tiles[0].detail.contains("WebDAV 1"))
        assertTrue(tiles[0].detail.contains("SMB 1"))
        assertEquals("Local Anime · LOCAL", tiles[1].value)
        assertEquals("42 条", tiles[2].value)
    }

    @Test
    fun `playback settings tiles expose RIFE recents and selected media`() {
        val tiles = playbackSettingsTiles(
            playbackSummary = "RIFE DIRECTML",
            recentCount = 5,
            selectedMediaTitle = "Fixture Alpha",
        )

        assertEquals("RIFE DIRECTML", tiles[0].value)
        assertEquals("5 条", tiles[1].value)
        assertEquals("Fixture Alpha", tiles[2].value)
    }

    @Test
    fun `scan and metadata settings tiles keep TV settings content concrete`() {
        val scanTiles = scanSettingsTiles(
            indexedItemCount = 11,
            linkedSourceLabel = "SMB Share · SMB",
            libraryStatus = "Scan complete: 11 videos, 4 directories.",
        )
        val metadataTiles = metadataSettingsTiles(
            selectedMediaTitle = "Fixture Beta",
            metadataSummary = "已匹配：Fixture Beta",
            indexedItemCount = 11,
        )

        assertEquals("11 条", scanTiles[0].value)
        assertEquals("SMB Share · SMB", scanTiles[1].value)
        assertTrue(scanTiles[2].value.contains("Scan complete"))
        assertEquals("Fixture Beta", metadataTiles[0].value)
        assertEquals("已匹配：Fixture Beta", metadataTiles[1].value)
        assertEquals("11 条索引", metadataTiles[2].value)
    }
}
