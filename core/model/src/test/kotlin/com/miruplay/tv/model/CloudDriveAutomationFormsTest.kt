package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudDriveAutomationFormsTest {
    @Test
    fun `cloud drive form values trim text clamp numeric fields and preserve existing fields`() {
        val current = CloudDriveAutomationConfig(
            endpointUrl = "old",
            username = "old-user",
            webDavSourceId = 1L,
            intervalMinutes = 60,
            enabled = true,
            lastRunAt = 123_456L,
        )

        val config = current.withAutomationFormValues(
            endpointUrl = "  http://cloud.test  ",
            username = "  miru  ",
            webDavSourceId = 7L,
            inboxPath = "  /downloads  ",
            libraryPath = "  /anime  ",
            intervalMinutes = 1,
            enabled = false,
            rssProxyEnabled = true,
            rssProxyHost = "  127.0.0.1  ",
            rssProxyPort = 70_000,
        )

        assertEquals("http://cloud.test", config.endpointUrl)
        assertEquals("miru", config.username)
        assertEquals(7L, config.webDavSourceId)
        assertEquals("/downloads", config.inboxPath)
        assertEquals("/anime", config.libraryPath)
        assertEquals(MIN_CLOUD_DRIVE_INTERVAL_MINUTES, config.intervalMinutes)
        assertEquals(false, config.enabled)
        assertEquals(123_456L, config.lastRunAt)
        assertEquals(true, config.rssProxyEnabled)
        assertEquals("127.0.0.1", config.rssProxyHost)
        assertEquals(MAX_RSS_PROXY_PORT, config.rssProxyPort)
    }

    @Test
    fun `cloud drive numeric parsers match Android settings bounds`() {
        assertEquals(MIN_CLOUD_DRIVE_INTERVAL_MINUTES, parseCloudDriveIntervalMinutes("1"))
        assertEquals(15, parseCloudDriveIntervalMinutes(" 15 "))
        assertEquals(DEFAULT_CLOUD_DRIVE_INTERVAL_MINUTES, parseCloudDriveIntervalMinutes("bad"))
        assertEquals(DEFAULT_RSS_PROXY_PORT, parseRssProxyPort("bad"))
        assertEquals(MIN_RSS_PROXY_PORT, parseRssProxyPort("-1"))
        assertEquals(1081, parseRssProxyPort(" 1081 "))
        assertEquals(MAX_RSS_PROXY_PORT, parseRssProxyPort("70000"))
    }

    @Test
    fun `rss subscription form trims fields and keeps selected identity`() {
        val subscription = buildRssSubscriptionFromForm(
            name = "  ",
            url = "  https://example.test/feed.xml  ",
            filterRegex = "  1080|简中  ",
            enabled = false,
            existingId = 42L,
            existingLastCheckedAt = 777L,
        )

        assertEquals(
            RssSubscriptionInfo(
                id = 42L,
                name = "https://example.test/feed.xml",
                url = "https://example.test/feed.xml",
                filterRegex = "1080|简中",
                enabled = false,
                lastCheckedAt = 777L,
            ),
            subscription,
        )
    }

    @Test
    fun `rss subscription form rejects blank url`() {
        assertNull(
            buildRssSubscriptionFromForm(
                name = "Anime",
                url = "   ",
                filterRegex = "",
                enabled = true,
            )
        )
    }
}
