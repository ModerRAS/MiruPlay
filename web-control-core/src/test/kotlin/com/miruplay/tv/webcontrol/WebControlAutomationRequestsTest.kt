package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.MAX_RSS_PROXY_PORT
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.model.MIN_RSS_PROXY_PORT
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
    fun `repository saves WebUI RSS subscription and returns persisted id`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(nextSubscriptionId = 42L)

        val saved = repository.saveWebControlRssSubscription(
            RssSubscriptionRequest(
                name = " Season ",
                url = " https://rss.example.test/feed.xml ",
                filterRegex = " 1080p ",
                enabled = false,
            )
        )

        assertEquals(42L, saved.id)
        assertEquals("Season", saved.name)
        assertEquals("https://rss.example.test/feed.xml", saved.url)
        assertEquals("1080p", saved.filterRegex)
        assertEquals(false, saved.enabled)
        assertEquals(saved, repository.savedSubscriptions.single().withSavedId(42L))
    }

    @Test
    fun `repository update keeps requested RSS subscription id`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(nextSubscriptionId = 99L)

        val saved = repository.updateWebControlRssSubscription(
            id = 7L,
            request = RssSubscriptionRequest(
                name = "Season",
                url = "https://rss.example.test/feed.xml",
            ),
        )

        assertEquals(7L, saved.id)
        assertEquals(7L, repository.savedSubscriptions.single().id)
    }

    @Test
    fun `repository save WebUI RSS subscription rejects blank url`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()

        val failure = runCatching {
            repository.saveWebControlRssSubscription(RssSubscriptionRequest(name = "Season", url = " "))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("请填写 RSS 地址", failure?.message)
        assertEquals(emptyList<RssSubscriptionInfo>(), repository.savedSubscriptions)
    }

    @Test
    fun `repository delete WebUI RSS subscription maps repository errors`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(
            deleteResult = Result.failure(AppError.SyncError.WriteFailed(path = "rss", cause = "boom")),
        )

        val failure = runCatching {
            repository.deleteWebControlRssSubscription(7L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("删除 RSS 订阅失败: 写入失败：boom", failure?.message)
    }

    @Test
    fun `automation config maps to WebUI dto`() {
        val config = CloudDriveAutomationConfig(
            endpointUrl = "https://cloud.example.test",
            username = "miru",
        )
        val subscriptions = listOf(
            RssSubscriptionInfo(
                id = 7L,
                name = "Season",
                url = "https://rss.example.test/feed.xml",
            ),
        )

        val dto = config.toWebControlAutomationDto(
            subscriptions = subscriptions,
            tokenConfigured = true,
        )

        assertEquals(config, dto.config)
        assertEquals(subscriptions, dto.subscriptions)
        assertEquals(true, dto.tokenConfigured)
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

    private class FakeCloudDriveAutomationRepository(
        private val nextSubscriptionId: Long = 1L,
        private val deleteResult: Result<Unit> = Result.success(Unit),
    ) : CloudDriveAutomationRepository {
        val savedSubscriptions = mutableListOf<RssSubscriptionInfo>()

        override fun observeConfig(): Flow<CloudDriveAutomationConfig> =
            flowOf(CloudDriveAutomationConfig())

        override suspend fun getConfig(): Result<CloudDriveAutomationConfig> =
            Result.success(CloudDriveAutomationConfig())

        override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> =
            Result.success(Unit)

        override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> =
            Result.success(Unit)

        override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> =
            flowOf(savedSubscriptions)

        override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> =
            Result.success(savedSubscriptions.filter { it.enabled })

        override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> {
            savedSubscriptions += subscription
            return Result.success(nextSubscriptionId)
        }

        override suspend fun deleteSubscription(id: Long): Result<Unit> =
            deleteResult

        override suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean> =
            Result.success(false)

        override suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit> =
            Result.success(Unit)

        override suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long> =
            Result.success(1L)
    }
}
