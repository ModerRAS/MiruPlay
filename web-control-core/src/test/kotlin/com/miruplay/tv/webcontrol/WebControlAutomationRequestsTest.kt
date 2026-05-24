package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MAX_RSS_PROXY_PORT
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.model.MIN_RSS_PROXY_PORT
import com.miruplay.tv.model.RssSubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebControlAutomationRequestsTest {
    @Test
    fun `cloud drive config request trims values clamps ranges and preserves last run`() {
        val current = CloudDriveAutomationConfig(lastRunAt = 123_456L)

        val config = CloudDriveConfigRequest(
            endpointUrl = " https://cloud.example.test ",
            username = " miru ",
            webDavSourceId = 0L,
            inboxPath = " /Inbox ",
            libraryPath = " /Library ",
            intervalMinutes = 1,
            enabled = true,
            rssProxyEnabled = true,
            rssProxyHost = " 127.0.0.1 ",
            rssProxyPort = 70_000,
        ).toAutomationConfig(current)

        assertEquals("https://cloud.example.test", config.endpointUrl)
        assertEquals("miru", config.username)
        assertNull(config.webDavSourceId)
        assertEquals("/Inbox", config.inboxPath)
        assertEquals("/Library", config.libraryPath)
        assertEquals(MIN_CLOUD_DRIVE_INTERVAL_MINUTES, config.intervalMinutes)
        assertEquals(true, config.enabled)
        assertEquals(123_456L, config.lastRunAt)
        assertEquals(true, config.rssProxyEnabled)
        assertEquals("127.0.0.1", config.rssProxyHost)
        assertEquals(MAX_RSS_PROXY_PORT, config.rssProxyPort)
    }

    @Test
    fun `cloud drive config request keeps positive source id and clamps low proxy port`() {
        val config = CloudDriveConfigRequest(
            endpointUrl = "",
            webDavSourceId = 42L,
            inboxPath = "",
            libraryPath = "",
            rssProxyPort = -1,
        ).toAutomationConfig(CloudDriveAutomationConfig())

        assertEquals(42L, config.webDavSourceId)
        assertEquals(MIN_RSS_PROXY_PORT, config.rssProxyPort)
    }

    @Test
    fun `rss subscription request trims fields and falls back to url name`() {
        val subscription = RssSubscriptionRequest(
            id = 7L,
            name = " ",
            url = " https://rss.example.test/feed.xml ",
            filterRegex = " 1080p ",
            enabled = false,
        ).toSubscription(existingLastCheckedAt = 99L)

        requireNotNull(subscription)
        assertEquals(7L, subscription.id)
        assertEquals("https://rss.example.test/feed.xml", subscription.name)
        assertEquals("https://rss.example.test/feed.xml", subscription.url)
        assertEquals("1080p", subscription.filterRegex)
        assertEquals(false, subscription.enabled)
        assertEquals(99L, subscription.lastCheckedAt)
    }

    @Test
    fun `rss subscription request rejects blank url`() {
        assertNull(
            RssSubscriptionRequest(
                name = "Season",
                url = " ",
            ).toSubscription(),
        )
    }

    @Test
    fun `saved id keeps existing id when updating`() {
        assertEquals(7L, RssSubscriptionInfo(id = 7L).withSavedId(9L).id)
        assertEquals(9L, RssSubscriptionInfo(id = 0L).withSavedId(9L).id)
    }
}
