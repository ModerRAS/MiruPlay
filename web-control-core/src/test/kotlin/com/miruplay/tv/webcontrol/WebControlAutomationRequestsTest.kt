package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.MAX_RSS_PROXY_PORT
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.model.MIN_RSS_PROXY_PORT
import com.miruplay.tv.model.RssSubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `login request validates and trims endpoint and username`() {
        val request = CloudDriveLoginRequest(
            endpointUrl = " https://cloud.example.test ",
            username = " miru ",
            password = " secret ",
        ).validated()

        assertEquals("https://cloud.example.test", request.endpointUrl)
        assertEquals("miru", request.username)
        assertEquals(" secret ", request.password)
    }

    @Test
    fun `login request rejects blank required fields`() {
        val failure = runCatching {
            CloudDriveLoginRequest(endpointUrl = "", username = "miru", password = "secret").validated()
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("请填写 CloudDrive2 地址、用户名和密码", failure?.message)
    }

    @Test
    fun `token request validates and trims endpoint and token`() {
        val request = CloudDriveTokenRequest(
            endpointUrl = " https://cloud.example.test ",
            token = " token ",
        ).validated()

        assertEquals("https://cloud.example.test", request.endpointUrl)
        assertEquals("token", request.token)
    }

    @Test
    fun `token request rejects blank required fields`() {
        val failure = runCatching {
            CloudDriveTokenRequest(endpointUrl = "https://cloud.example.test", token = " ").validated()
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("请填写 CloudDrive2 地址和 API Token", failure?.message)
    }

    @Test
    fun `token info maps to WebUI response`() {
        val response = CloudDriveTokenInfo(
            rootDir = "/CloudRoot",
            friendlyName = "Miru",
            allowList = true,
            allowCreateFolder = true,
            allowCreateFile = false,
            allowWrite = true,
            allowMove = false,
            allowAddOfflineDownload = true,
        ).toWebControlResponse()

        assertEquals("/CloudRoot", response.rootDir)
        assertEquals("Miru", response.friendlyName)
        assertEquals(true, response.allowList)
        assertEquals(true, response.allowCreateFolder)
        assertEquals(false, response.allowCreateFile)
        assertEquals(true, response.allowWrite)
        assertEquals(false, response.allowMove)
        assertEquals(true, response.allowAddOfflineDownload)
    }

    @Test
    fun `run summary maps to WebUI response`() {
        val response = CloudDriveRssRunSummary(
            submitted = 3,
            skipped = 2,
            failed = 1,
            organized = 4,
        ).toWebControlResponse()

        assertEquals(3, response.submitted)
        assertEquals(2, response.skipped)
        assertEquals(1, response.failed)
        assertEquals(4, response.organized)
    }
}
