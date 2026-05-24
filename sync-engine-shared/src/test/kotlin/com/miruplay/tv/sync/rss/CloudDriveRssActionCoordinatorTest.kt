package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
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
import org.junit.Test

class CloudDriveRssActionCoordinatorTest {
    @Test
    fun `save config normalizes form values and returns saved config`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(
            config = CloudDriveAutomationConfig(lastRunAt = 77L),
        )
        val coordinator = coordinator(repository = repository)

        val result = coordinator.saveConfig(
            endpointUrl = " https://cloud.example.test ",
            username = " miru ",
            webDavSourceId = 9L,
            inboxPath = " /Inbox ",
            libraryPath = " /Library ",
            intervalMinutes = 1,
            enabled = true,
            rssProxyEnabled = true,
            rssProxyHost = " 127.0.0.1 ",
            rssProxyPort = 70_000,
        )

        val saved = (result as Result.Success).data
        assertEquals(saved, repository.savedConfigs.single())
        assertEquals("https://cloud.example.test", saved.endpointUrl)
        assertEquals("miru", saved.username)
        assertEquals(9L, saved.webDavSourceId)
        assertEquals("/Inbox", saved.inboxPath)
        assertEquals("/Library", saved.libraryPath)
        assertEquals(MIN_CLOUD_DRIVE_INTERVAL_MINUTES, saved.intervalMinutes)
        assertEquals(true, saved.enabled)
        assertEquals(77L, saved.lastRunAt)
        assertEquals(true, saved.rssProxyEnabled)
        assertEquals("127.0.0.1", saved.rssProxyHost)
        assertEquals(65_535, saved.rssProxyPort)
    }

    @Test
    fun `save config returns config read failure without saving`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository(
            getConfigResult = Result.failure(AppError.NetworkError.ServerUnreachable("cloud")),
        )
        val coordinator = coordinator(repository = repository)

        val result = coordinator.saveConfig(
            endpointUrl = "https://cloud.example.test",
            username = "miru",
            webDavSourceId = null,
            inboxPath = "/Inbox",
            libraryPath = "/Library",
            intervalMinutes = 30,
            enabled = true,
        )

        assertEquals(true, result is Result.Error)
        assertEquals(emptyList<CloudDriveAutomationConfig>(), repository.savedConfigs)
    }

    @Test
    fun `credentials save trims token and preserves nonblank password`() {
        val credentials = FakeCloudDriveCredentialStore()
        val coordinator = coordinator(credentials = credentials)

        coordinator.saveCredentials(token = " token ", password = " secret ")

        assertEquals("token", credentials.cloudDriveToken)
        assertEquals(" secret ", credentials.cloudDrivePassword)
    }

    @Test
    fun `credentials save clears blank token and blank password`() {
        val credentials = FakeCloudDriveCredentialStore(token = "old", password = "old")
        val coordinator = coordinator(credentials = credentials)

        coordinator.saveCredentials(token = " ", password = "")

        assertNull(credentials.cloudDriveToken)
        assertNull(credentials.cloudDrivePassword)
    }

    @Test
    fun `login verify token and run delegate to runner`() = runBlocking {
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(runner = runner)

        coordinator.login("https://cloud.example.test", "miru", "secret")
        coordinator.verifyApiToken("https://cloud.example.test", "token")
        coordinator.runOnce()

        assertEquals(listOf("https://cloud.example.test|miru|secret"), runner.loginCalls)
        assertEquals(listOf("https://cloud.example.test|token"), runner.tokenCalls)
        assertEquals(1, runner.runCalls)
    }

    @Test
    fun `subscription save and delete delegate to repository`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val coordinator = coordinator(repository = repository)
        val subscription = RssSubscriptionInfo(id = 7L, name = "Season", url = "https://rss.example.test/feed.xml")

        val saved = coordinator.saveSubscription(subscription)
        val deleted = coordinator.deleteSubscription(7L)

        assertEquals(true, saved is Result.Success)
        assertEquals(true, deleted is Result.Success)
        assertEquals(listOf(subscription), repository.savedSubscriptions)
        assertEquals(listOf(7L), repository.deletedSubscriptionIds)
    }

    private fun coordinator(
        repository: FakeCloudDriveAutomationRepository = FakeCloudDriveAutomationRepository(),
        credentials: FakeCloudDriveCredentialStore = FakeCloudDriveCredentialStore(),
        runner: FakeCloudDriveRssAutomationRunner = FakeCloudDriveRssAutomationRunner(),
    ): CloudDriveRssActionCoordinator =
        CloudDriveRssActionCoordinator(
            repository = repository,
            credentials = credentials,
            runner = runner,
        )

    private class FakeCloudDriveAutomationRepository(
        private var config: CloudDriveAutomationConfig = CloudDriveAutomationConfig(),
        private val getConfigResult: Result<CloudDriveAutomationConfig>? = null,
        private val saveConfigResult: Result<Unit> = Result.success(Unit),
        private val saveSubscriptionResult: Result<Long> = Result.success(1L),
        private val deleteSubscriptionResult: Result<Unit> = Result.success(Unit),
    ) : CloudDriveAutomationRepository {
        val savedConfigs = mutableListOf<CloudDriveAutomationConfig>()
        val savedSubscriptions = mutableListOf<RssSubscriptionInfo>()
        val deletedSubscriptionIds = mutableListOf<Long>()

        override fun observeConfig(): Flow<CloudDriveAutomationConfig> =
            flowOf(config)

        override suspend fun getConfig(): Result<CloudDriveAutomationConfig> =
            getConfigResult ?: Result.success(config)

        override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> {
            savedConfigs += config
            this.config = config
            return saveConfigResult
        }

        override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> =
            Result.success(Unit)

        override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> =
            flowOf(savedSubscriptions)

        override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> =
            Result.success(savedSubscriptions.filter { it.enabled })

        override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> {
            savedSubscriptions += subscription
            return saveSubscriptionResult
        }

        override suspend fun deleteSubscription(id: Long): Result<Unit> {
            deletedSubscriptionIds += id
            return deleteSubscriptionResult
        }

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

    private class FakeCloudDriveRssAutomationRunner : CloudDriveRssAutomationRunner {
        val loginCalls = mutableListOf<String>()
        val tokenCalls = mutableListOf<String>()
        var runCalls = 0

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> {
            loginCalls += "$endpointUrl|$username|$password"
            return Result.success(Unit)
        }

        override suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
            tokenCalls += "$endpointUrl|$token"
            return Result.success(
                CloudDriveTokenInfo(
                    rootDir = "/",
                    friendlyName = "Miru",
                    allowList = true,
                    allowCreateFolder = false,
                    allowCreateFile = false,
                    allowWrite = false,
                    allowMove = false,
                    allowAddOfflineDownload = false,
                )
            )
        }

        override suspend fun runOnce(): Result<CloudDriveRssRunSummary> {
            runCalls += 1
            return Result.success(
                CloudDriveRssRunSummary(
                    submitted = 1,
                    skipped = 2,
                    failed = 0,
                    organized = 3,
                )
            )
        }
    }
}
