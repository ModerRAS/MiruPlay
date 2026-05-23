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

    @Test
    fun `rss subscription form result reports blank url`() {
        val result = prepareRssSubscriptionForm(
            name = "Anime",
            url = "   ",
            filterRegex = "",
            enabled = true,
        )

        assertEquals(
            RssSubscriptionFormResult.Invalid(rssUrlRequiredStatus()),
            result,
        )
        assertEquals(false, result.shouldClearFormAfterSubmit)
    }

    @Test
    fun `rss subscription form result preserves selected matching subscription`() {
        val selected = RssSubscriptionInfo(
            id = 9L,
            name = "Old name",
            url = " https://example.test/feed.xml ",
            filterRegex = "old",
            enabled = false,
            lastCheckedAt = 1234L,
        )

        val result = prepareRssSubscriptionForm(
            name = " New name ",
            url = " https://example.test/feed.xml ",
            filterRegex = " 1080 ",
            enabled = true,
            selectedSubscription = selected,
        )

        assertEquals(
            RssSubscriptionFormResult.Ready(
                RssSubscriptionInfo(
                    id = 9L,
                    name = "New name",
                    url = "https://example.test/feed.xml",
                    filterRegex = "1080",
                    enabled = true,
                    lastCheckedAt = 1234L,
                ),
            ),
            result,
        )
        assertEquals(true, result.shouldClearFormAfterSubmit)
    }

    @Test
    fun `rss subscription form result creates new subscription for different url`() {
        val selected = RssSubscriptionInfo(
            id = 9L,
            name = "Old name",
            url = "https://example.test/old.xml",
            filterRegex = "old",
            enabled = false,
            lastCheckedAt = 1234L,
        )

        assertEquals(
            RssSubscriptionFormResult.Ready(
                RssSubscriptionInfo(
                    id = 0L,
                    name = "https://example.test/new.xml",
                    url = "https://example.test/new.xml",
                    filterRegex = null,
                    enabled = true,
                    lastCheckedAt = 0L,
                ),
            ),
            prepareRssSubscriptionForm(
                name = " ",
                url = " https://example.test/new.xml ",
                filterRegex = " ",
                enabled = true,
                selectedSubscription = selected,
            ),
        )
    }

    @Test
    fun `cloud drive login form trims endpoint and username`() {
        val result = validateCloudDriveLoginForm(
            endpointUrl = " http://127.0.0.1:19798 ",
            username = " miru ",
            password = " secret ",
        )

        assertEquals(
            CloudDriveLoginFormResult.Ready(
                CloudDriveLoginFormRequest(
                    endpointUrl = "http://127.0.0.1:19798",
                    username = "miru",
                    password = " secret ",
                ),
            ),
            result,
        )
    }

    @Test
    fun `cloud drive login form reports missing required fields`() {
        assertEquals(
            CloudDriveLoginFormResult.Invalid(cloudDriveLoginRequiredStatus()),
            validateCloudDriveLoginForm(endpointUrl = "", username = "miru", password = "secret"),
        )
        assertEquals(
            CloudDriveLoginFormResult.Invalid(cloudDriveLoginRequiredStatus()),
            validateCloudDriveLoginForm(endpointUrl = "http://cloud.test", username = " ", password = "secret"),
        )
        assertEquals(
            CloudDriveLoginFormResult.Invalid(cloudDriveLoginRequiredStatus()),
            validateCloudDriveLoginForm(endpointUrl = "http://cloud.test", username = "miru", password = ""),
        )
    }

    @Test
    fun `cloud drive api token form distinguishes endpoint and token errors`() {
        assertEquals(
            CloudDriveApiTokenFormResult.Invalid(cloudDriveTokenRequiredStatus()),
            validateCloudDriveApiTokenForm(endpointUrl = "", token = ""),
        )
        assertEquals(
            CloudDriveApiTokenFormResult.Invalid(cloudDriveEndpointRequiredStatus()),
            validateCloudDriveApiTokenForm(endpointUrl = " ", token = "api-token"),
        )
        assertEquals(
            CloudDriveApiTokenFormResult.Invalid(cloudDriveApiTokenRequiredStatus()),
            validateCloudDriveApiTokenForm(endpointUrl = "http://cloud.test", token = " "),
        )
        assertEquals(
            CloudDriveApiTokenFormResult.Invalid(cloudDriveTokenRequiredStatus()),
            validateCloudDriveApiTokenForm(
                endpointUrl = "http://cloud.test",
                token = " ",
                blankTokenStatus = cloudDriveTokenRequiredStatus(),
            ),
        )
    }

    @Test
    fun `cloud drive api token form trims ready values`() {
        val result = validateCloudDriveApiTokenForm(
            endpointUrl = " http://cloud.test ",
            token = " api-token ",
        )

        assertEquals(
            CloudDriveApiTokenFormResult.Ready(
                CloudDriveApiTokenFormRequest(
                    endpointUrl = "http://cloud.test",
                    token = "api-token",
                ),
            ),
            result,
        )
    }

    @Test
    fun `cloud drive directory picker form distinguishes endpoint and token errors`() {
        assertEquals(
            CloudDriveDirectoryPickerFormResult.Invalid(cloudDriveEndpointRequiredStatus()),
            validateCloudDriveDirectoryPickerForm(
                endpointUrl = " ",
                tokenInput = "token",
                savedToken = null,
            ),
        )
        assertEquals(
            CloudDriveDirectoryPickerFormResult.Invalid(cloudDriveTokenLoginRequiredStatus()),
            validateCloudDriveDirectoryPickerForm(
                endpointUrl = "http://cloud.test",
                tokenInput = " ",
                savedToken = "",
            ),
        )
    }

    @Test
    fun `cloud drive directory picker form prefers typed token then saved token`() {
        assertEquals(
            CloudDriveDirectoryPickerFormResult.Ready(
                CloudDriveDirectoryPickerRequest(
                    endpointUrl = "http://cloud.test",
                    token = "typed-token",
                ),
            ),
            validateCloudDriveDirectoryPickerForm(
                endpointUrl = " http://cloud.test ",
                tokenInput = " typed-token ",
                savedToken = "saved-token",
            ),
        )
        assertEquals(
            CloudDriveDirectoryPickerFormResult.Ready(
                CloudDriveDirectoryPickerRequest(
                    endpointUrl = "http://cloud.test",
                    token = "saved-token",
                ),
            ),
            validateCloudDriveDirectoryPickerForm(
                endpointUrl = "http://cloud.test",
                tokenInput = " ",
                savedToken = " saved-token ",
            ),
        )
    }
}
