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
import com.miruplay.tv.repository.CloudDriveCredentialStore
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
    fun `repository WebUI automation summary includes subscriptions and token state`() = runBlocking {
        val config = CloudDriveAutomationConfig(endpointUrl = "https://cloud.example.test")
        val subscription = RssSubscriptionInfo(id = 7L, name = "Season", url = "https://rss.example.test/feed.xml")
        val repository = FakeCloudDriveAutomationRepository(
            config = config,
            subscriptions = mutableListOf(subscription),
        )
        val credentials = FakeCloudDriveCredentialStore(token = "token")

        val dto = repository.getWebControlCloudDriveAutomation(credentials)

        assertEquals(config, dto.config)
        assertEquals(listOf(subscription), dto.subscriptions)
        assertEquals(true, dto.tokenConfigured)
    }

    @Test
    fun `repository WebUI automation summary maps config read failures`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(
            configResult = Result.failure(AppError.NetworkError.ServerUnreachable("cloud")),
        )

        val failure = runCatching {
            repository.getWebControlCloudDriveAutomation(FakeCloudDriveCredentialStore())
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("读取 CloudDrive 设置失败: 无法连接服务器：cloud", failure?.message)
    }

    @Test
    fun `repository saves WebUI CloudDrive config and returns refreshed automation dto`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(
            config = CloudDriveAutomationConfig(lastRunAt = 77L),
        )

        val dto = repository.saveWebControlCloudDriveConfig(
            request = CloudDriveConfigRequest(
                endpointUrl = " https://cloud.example.test ",
                username = " miru ",
                webDavSourceId = 9L,
                inboxPath = " /Inbox ",
                libraryPath = " /Library ",
                intervalMinutes = 15,
                enabled = true,
            ),
            credentials = FakeCloudDriveCredentialStore(token = null),
        )

        assertEquals("https://cloud.example.test", repository.savedConfigs.single().endpointUrl)
        assertEquals("miru", repository.savedConfigs.single().username)
        assertEquals(9L, repository.savedConfigs.single().webDavSourceId)
        assertEquals("/Inbox", repository.savedConfigs.single().inboxPath)
        assertEquals("/Library", repository.savedConfigs.single().libraryPath)
        assertEquals(77L, repository.savedConfigs.single().lastRunAt)
        assertEquals(repository.savedConfigs.single(), dto.config)
        assertEquals(false, dto.tokenConfigured)
    }

    @Test
    fun `runner WebUI login validates request invokes engine and returns refreshed automation dto`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(
            config = CloudDriveAutomationConfig(username = "miru"),
        )
        val runner = FakeWebControlCloudDriveAutomationRunner()

        val dto = runner.loginWebControlCloudDrive(
            request = CloudDriveLoginRequest(
                endpointUrl = " https://cloud.example.test ",
                username = " miru ",
                password = " secret ",
            ),
            repository = repository,
            credentials = FakeCloudDriveCredentialStore(token = "token"),
        )

        assertEquals(listOf("https://cloud.example.test|miru| secret "), runner.loginCalls)
        assertEquals("miru", dto.config.username)
        assertEquals(true, dto.tokenConfigured)
    }

    @Test
    fun `runner WebUI login maps engine failures`() = runBlocking {
        val runner = FakeWebControlCloudDriveAutomationRunner(
            loginResult = Result.failure(AppError.NetworkError.ServerUnreachable("denied")),
        )

        val failure = runCatching {
            runner.loginWebControlCloudDrive(
                request = CloudDriveLoginRequest(
                    endpointUrl = "https://cloud.example.test",
                    username = "miru",
                    password = "secret",
                ),
                repository = FakeCloudDriveAutomationRepository(),
                credentials = FakeCloudDriveCredentialStore(),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("CloudDrive2 登录失败: 无法连接服务器：denied", failure?.message)
    }

    @Test
    fun `runner WebUI token request validates invokes engine and maps response`() = runBlocking {
        val runner = FakeWebControlCloudDriveAutomationRunner(
            tokenInfo = CloudDriveTokenInfo(
                rootDir = "/CloudRoot",
                friendlyName = "Miru",
                allowList = true,
                allowCreateFolder = false,
                allowCreateFile = false,
                allowWrite = false,
                allowMove = false,
                allowAddOfflineDownload = false,
            ),
        )

        val response = runner.saveWebControlCloudDriveToken(
            CloudDriveTokenRequest(
                endpointUrl = " https://cloud.example.test ",
                token = " token ",
            )
        )

        assertEquals(listOf("https://cloud.example.test|token"), runner.tokenCalls)
        assertEquals("/CloudRoot", response.rootDir)
        assertEquals("Miru", response.friendlyName)
        assertEquals(true, response.allowList)
    }

    @Test
    fun `runner WebUI run maps summary and invokes after-run hook`() = runBlocking {
        val summary = CloudDriveRssRunSummary(submitted = 3, skipped = 2, failed = 1, organized = 4)
        val runner = FakeWebControlCloudDriveAutomationRunner(runResult = Result.success(summary))
        val hookSummaries = mutableListOf<CloudDriveRssRunSummary>()

        val response = runner.runWebControlCloudDriveAutomationNow { hookSummaries += it }

        assertEquals(1, runner.runCalls)
        assertEquals(listOf(summary), hookSummaries)
        assertEquals(3, response.submitted)
        assertEquals(2, response.skipped)
        assertEquals(1, response.failed)
        assertEquals(4, response.organized)
    }

    @Test
    fun `runner WebUI run skips after-run hook on failure`() = runBlocking {
        val runner = FakeWebControlCloudDriveAutomationRunner(
            runResult = Result.failure(AppError.SyncError.WriteFailed(path = "rss", cause = "boom")),
        )
        var hookCalled = false

        val failure = runCatching {
            runner.runWebControlCloudDriveAutomationNow { hookCalled = true }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("CloudDrive/RSS 执行失败: 写入失败：boom", failure?.message)
        assertEquals(false, hookCalled)
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
        private var config: CloudDriveAutomationConfig = CloudDriveAutomationConfig(),
        private val configResult: Result<CloudDriveAutomationConfig>? = null,
        private val saveConfigResult: Result<Unit> = Result.success(Unit),
        private val subscriptions: MutableList<RssSubscriptionInfo> = mutableListOf(),
        private val nextSubscriptionId: Long = 1L,
        private val deleteResult: Result<Unit> = Result.success(Unit),
    ) : CloudDriveAutomationRepository {
        val savedSubscriptions = mutableListOf<RssSubscriptionInfo>()
        val savedConfigs = mutableListOf<CloudDriveAutomationConfig>()

        override fun observeConfig(): Flow<CloudDriveAutomationConfig> =
            flowOf(config)

        override suspend fun getConfig(): Result<CloudDriveAutomationConfig> =
            configResult ?: Result.success(config)

        override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> {
            savedConfigs += config
            this.config = config
            return saveConfigResult
        }

        override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> =
            Result.success(Unit)

        override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> =
            flowOf(subscriptions + savedSubscriptions)

        override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> =
            Result.success((subscriptions + savedSubscriptions).filter { it.enabled })

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

    private class FakeCloudDriveCredentialStore(
        token: String? = null,
        password: String? = null,
    ) : CloudDriveCredentialStore {
        override var cloudDriveToken: String? = token
        override var cloudDrivePassword: String? = password

        override fun clearCloudDriveCredentials() {
            cloudDriveToken = null
            cloudDrivePassword = null
        }
    }

    private class FakeWebControlCloudDriveAutomationRunner(
        private val loginResult: Result<Unit> = Result.success(Unit),
        private val tokenInfo: CloudDriveTokenInfo = CloudDriveTokenInfo(
            rootDir = "/",
            friendlyName = "",
            allowList = false,
            allowCreateFolder = false,
            allowCreateFile = false,
            allowWrite = false,
            allowMove = false,
            allowAddOfflineDownload = false,
        ),
        private val tokenResult: Result<CloudDriveTokenInfo> = Result.success(tokenInfo),
        private val runResult: Result<CloudDriveRssRunSummary> = Result.success(
            CloudDriveRssRunSummary(
                submitted = 0,
                skipped = 0,
                failed = 0,
                organized = 0,
            )
        ),
    ) : WebControlCloudDriveAutomationRunner {
        val loginCalls = mutableListOf<String>()
        val tokenCalls = mutableListOf<String>()
        var runCalls = 0

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> {
            loginCalls += "$endpointUrl|$username|$password"
            return loginResult
        }

        override suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
            tokenCalls += "$endpointUrl|$token"
            return tokenResult
        }

        override suspend fun runOnce(): Result<CloudDriveRssRunSummary> {
            runCalls += 1
            return runResult
        }
    }
}
