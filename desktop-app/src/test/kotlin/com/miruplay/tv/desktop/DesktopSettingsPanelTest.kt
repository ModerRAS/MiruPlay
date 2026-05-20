package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.RssSubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSettingsPanelTest {
    @Test
    fun `source settings tiles summarize source types active source and index`() {
        val activeSource = MediaSourceInfoConventions.local(name = "Local Anime", rootPath = "D:/Anime")
        val tiles = sourceSettingsTiles(
            sources = listOf(
                activeSource,
                MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime"),
                MediaSourceInfoConventions.smb(url = "smb://nas.local/anime"),
            ),
            activeSourceLabel = desktopActiveSourceLabel(activeSource),
            indexedItemCount = 42,
        )

        assertEquals(listOf("媒体源", "当前源", "海报墙索引"), tiles.map { it.label })
        assertEquals("3 个", tiles[0].value)
        assertTrue(tiles[0].detail.contains("本地 1"))
        assertTrue(tiles[0].detail.contains("WebDAV 1"))
        assertTrue(tiles[0].detail.contains("SMB 1"))
        assertEquals("Local Anime · 本地", tiles[1].value)
        assertEquals("42 条", tiles[2].value)
    }

    @Test
    fun `desktop source status labels use TV facing type labels`() {
        val linkedSource = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/anime",
        ).copy(id = 42L, name = "Cloud WebDAV")

        assertEquals("未选择", desktopActiveSourceLabel(null))
        assertEquals("Cloud WebDAV · WebDAV", desktopLinkedSourceLabel(listOf(linkedSource), 42L))
        assertEquals("缺失媒体源 #99", desktopLinkedSourceLabel(listOf(linkedSource), 99L))
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

    @Test
    fun `cloud rss overview tiles summarize endpoint subscriptions and scheduler`() {
        val subscription = RssSubscriptionInfo(
            id = 7L,
            name = "Bangumi Feed",
            url = "https://rss.example.test/feeds/very/long/path/season-one.xml",
            filterRegex = "S01",
            enabled = true,
        )

        val tiles = cloudRssOverviewTiles(
            endpointUrl = "http://127.0.0.1:19798/clouddrive/very/long/endpoint",
            subscriptions = listOf(subscription),
            enabled = true,
            linkedSourceLabel = "Cloud WebDAV · WebDAV",
            schedulerStatus = "Scheduler running every 30 minutes with a very long diagnostic status line.",
        )

        assertEquals(listOf("CloudDrive2", "RSS 订阅", "同步后扫描"), tiles.map { it.label })
        assertEquals("已启用", tiles[0].value)
        assertEquals("1 个", tiles[1].value)
        assertEquals("Cloud WebDAV · WebDAV", tiles[2].value)
        assertTrue(tiles[0].detail.length <= 58)
        assertTrue(tiles[1].detail.contains("ON"))
        assertTrue(tiles[1].detail.contains("Bangumi Feed"))
        assertTrue(tiles[2].detail.length <= 58)
    }

    @Test
    fun `cloud rss card previews compact long paths and subscriptions`() {
        val pathPreview = cloudRssPathPairPreview(
            inboxPath = "/Downloads/CloudDrive2/rss/inbox/very/deep/path",
            libraryPath = "/Library/Anime/Season One/Very Long Destination",
            maxLength = 46,
        )
        val subscriptionPreview = rssSubscriptionPreview(
            RssSubscriptionInfo(
                name = "Disabled Feed",
                url = "https://rss.example.test/feeds/disabled.xml",
                enabled = false,
            ),
            maxLength = 42,
        )

        assertTrue(pathPreview.length <= 46)
        assertTrue(pathPreview.contains("..."))
        assertTrue(pathPreview.contains("->"))
        assertTrue(subscriptionPreview.length <= 42)
        assertTrue(subscriptionPreview.startsWith("OFF"))
        assertTrue(subscriptionPreview.contains("..."))
    }

    @Test
    fun `rss subscription directional keys move between saved subscriptions`() {
        val subscriptions = listOf(
            RssSubscriptionInfo(id = 10L, name = "Season A", url = "https://rss.example.test/a.xml"),
            RssSubscriptionInfo(id = 11L, name = "Season B", url = "https://rss.example.test/b.xml"),
            RssSubscriptionInfo(id = 12L, name = "Season C", url = "https://rss.example.test/c.xml"),
        )

        assertEquals(11L, subscriptions.rssSubscriptionNavigationTarget(10L, Key.DirectionDown)?.id)
        assertEquals(10L, subscriptions.rssSubscriptionNavigationTarget(11L, Key.DirectionUp)?.id)
        assertEquals(10L, subscriptions.rssSubscriptionNavigationTarget(null, Key.DirectionDown)?.id)
        assertEquals(12L, subscriptions.rssSubscriptionNavigationTarget(null, Key.DirectionUp)?.id)
        assertNull(subscriptions.rssSubscriptionNavigationTarget(12L, Key.DirectionDown))
        assertNull(subscriptions.rssSubscriptionNavigationTarget(10L, Key.DirectionUp))
        assertNull(subscriptions.rssSubscriptionNavigationTarget(10L, Key.DirectionRight))
    }
}
