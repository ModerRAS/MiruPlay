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
import com.miruplay.tv.model.cloudDriveApiTokenRequiredStatus
import com.miruplay.tv.model.cloudDriveEndpointRequiredStatus
import com.miruplay.tv.model.cloudDriveLoginRequiredStatus
import com.miruplay.tv.model.cloudDriveLoginStartedStatus
import com.miruplay.tv.model.cloudDriveLoginSucceededStatus
import com.miruplay.tv.model.cloudDriveTokenValidationStartedStatus
import com.miruplay.tv.model.cloudDriveTokenVerifiedStatus
import com.miruplay.tv.model.cloudDriveTokenLoginRequiredStatus
import com.miruplay.tv.model.cloudDriveCredentialsClearedStatus
import com.miruplay.tv.model.cloudDriveCredentialsSavedStatus
import com.miruplay.tv.model.cloudRssConfigSavedStatus
import com.miruplay.tv.model.cloudRssRunStartedStatus
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.model.rssSubscriptionDeletedStatus
import com.miruplay.tv.model.rssSubscriptionSavedStatus
import com.miruplay.tv.model.rssUrlRequiredStatus
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        val saved = (result as CloudDriveConfigActionResult.Saved).config
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
        assertEquals(cloudRssConfigSavedStatus(), result.status)
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

        assertEquals(
            CloudDriveConfigActionResult.Failed(AppError.NetworkError.ServerUnreachable("cloud").toUserMessage()),
            result,
        )
        assertEquals(emptyList<CloudDriveAutomationConfig>(), repository.savedConfigs)
    }

    @Test
    fun `save config maps save failure to user status`() = runBlocking {
        val error = AppError.SyncError.WriteFailed("cloud", "save failed")
        val repository = FakeCloudDriveAutomationRepository(saveConfigResult = Result.failure(error))
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

        assertEquals(CloudDriveConfigActionResult.Failed(error.toUserMessage()), result)
        assertEquals(1, repository.savedConfigs.size)
    }

    @Test
    fun `credentials save trims token and preserves nonblank password`() {
        val credentials = FakeCloudDriveCredentialStore()
        val coordinator = coordinator(credentials = credentials)

        val result = coordinator.saveCredentials(token = " token ", password = " secret ")

        assertEquals("token", credentials.cloudDriveToken)
        assertEquals(" secret ", credentials.cloudDrivePassword)
        assertEquals(
            CloudDriveCredentialActionResult.Saved(
                token = "token",
                password = " secret ",
                status = cloudDriveCredentialsSavedStatus(),
            ),
            result,
        )
    }

    @Test
    fun `credentials save clears blank token and blank password`() {
        val credentials = FakeCloudDriveCredentialStore(token = "old", password = "old")
        val coordinator = coordinator(credentials = credentials)

        val result = coordinator.saveCredentials(token = " ", password = "")

        assertNull(credentials.cloudDriveToken)
        assertNull(credentials.cloudDrivePassword)
        assertEquals(
            CloudDriveCredentialActionResult.Saved(
                token = null,
                password = null,
                status = cloudDriveCredentialsSavedStatus(),
            ),
            result,
        )
    }

    @Test
    fun `credentials clear removes token and password and returns shared status`() {
        val credentials = FakeCloudDriveCredentialStore(token = "old", password = "old")
        val coordinator = coordinator(credentials = credentials)

        val result = coordinator.clearCredentials()

        assertNull(credentials.cloudDriveToken)
        assertNull(credentials.cloudDrivePassword)
        assertEquals(CloudDriveCredentialActionResult.Cleared(cloudDriveCredentialsClearedStatus()), result)
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
    fun `login action validates form before delegating`() = runBlocking {
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(runner = runner)

        val result = coordinator.loginCloudDrive(
            endpointUrl = "https://cloud.example.test",
            username = " ",
            password = "secret",
            onStarted = { throw AssertionError("Invalid login form must not start runner") },
        )

        assertEquals(CloudDriveActionResult.Invalid(cloudDriveLoginRequiredStatus()), result)
        assertEquals(emptyList<String>(), runner.loginCalls)
    }

    @Test
    fun `login action trims form values and returns saved token`() = runBlocking {
        val credentials = FakeCloudDriveCredentialStore(token = "login-token")
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(credentials = credentials, runner = runner)

        var startedStatus = ""

        val result = coordinator.loginCloudDrive(
            endpointUrl = " https://cloud.example.test ",
            username = " miru ",
            password = "secret",
            onStarted = { status -> startedStatus = status },
        )

        assertEquals(CloudDriveActionResult.Success(cloudDriveLoginSucceededStatus(), token = "login-token"), result)
        assertEquals(cloudDriveLoginStartedStatus(), startedStatus)
        assertEquals(listOf("https://cloud.example.test|miru|secret"), runner.loginCalls)
    }

    @Test
    fun `login action maps runner failures to user status`() = runBlocking {
        val error = AppError.MediaSourceError.AuthenticationFailed("CloudDrive2")
        val coordinator = coordinator(
            runner = FakeCloudDriveRssAutomationRunner(loginResult = Result.failure(error)),
        )

        val result = coordinator.loginCloudDrive(
            endpointUrl = "https://cloud.example.test",
            username = "miru",
            password = "secret",
        )

        assertEquals(CloudDriveActionResult.Failed(error.toUserMessage()), result)
    }

    @Test
    fun `api token action validates form before delegating`() = runBlocking {
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(runner = runner)

        val blankToken = coordinator.verifyCloudDriveApiToken(
            endpointUrl = "https://cloud.example.test",
            token = " ",
            onStarted = { throw AssertionError("Invalid token form must not start runner") },
        )
        val blankEndpoint = coordinator.verifyCloudDriveApiToken(
            endpointUrl = " ",
            token = "api-token",
            onStarted = { throw AssertionError("Invalid endpoint form must not start runner") },
        )

        assertEquals(CloudDriveActionResult.Invalid(cloudDriveApiTokenRequiredStatus()), blankToken)
        assertEquals(CloudDriveActionResult.Invalid(cloudDriveEndpointRequiredStatus()), blankEndpoint)
        assertEquals(emptyList<String>(), runner.tokenCalls)
    }

    @Test
    fun `api token action trims token and returns verified status`() = runBlocking {
        val runner = FakeCloudDriveRssAutomationRunner(
            tokenInfo = CloudDriveTokenInfo(
                rootDir = "/Anime",
                friendlyName = "Miru",
                allowList = true,
                allowCreateFolder = false,
                allowCreateFile = false,
                allowWrite = false,
                allowMove = false,
                allowAddOfflineDownload = false,
            ),
        )
        val coordinator = coordinator(runner = runner)

        var startedStatus = ""

        val result = coordinator.verifyCloudDriveApiToken(
            endpointUrl = " https://cloud.example.test ",
            token = " api-token ",
            onStarted = { status -> startedStatus = status },
        )

        assertEquals(
            CloudDriveActionResult.Success(
                status = cloudDriveTokenVerifiedStatus(friendlyName = "Miru", rootDir = "/Anime"),
                token = "api-token",
                tokenInfo = CloudDriveTokenInfo(
                    rootDir = "/Anime",
                    friendlyName = "Miru",
                    allowList = true,
                    allowCreateFolder = false,
                    allowCreateFile = false,
                    allowWrite = false,
                    allowMove = false,
                    allowAddOfflineDownload = false,
                ),
            ),
            result,
        )
        assertEquals(cloudDriveTokenValidationStartedStatus(), startedStatus)
        assertEquals(listOf("https://cloud.example.test|api-token"), runner.tokenCalls)
    }

    @Test
    fun `api token action maps runner failures to user status`() = runBlocking {
        val error = AppError.MediaSourceError.Timeout("CloudDrive2")
        val coordinator = coordinator(
            runner = FakeCloudDriveRssAutomationRunner(tokenResult = Result.failure(error)),
        )

        val result = coordinator.verifyCloudDriveApiToken(
            endpointUrl = "https://cloud.example.test",
            token = "api-token",
        )

        assertEquals(CloudDriveActionResult.Failed(error.toUserMessage()), result)
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

    @Test
    fun `subscription action validates form before saving`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val coordinator = coordinator(repository = repository)

        val result = coordinator.saveRssSubscription(
            name = "Season",
            url = " ",
            filterRegex = "",
            enabled = true,
        )

        assertEquals(RssSubscriptionActionResult.Invalid(rssUrlRequiredStatus()), result)
        assertEquals(emptyList<RssSubscriptionInfo>(), repository.savedSubscriptions)
    }

    @Test
    fun `subscription action normalizes form values and returns saved status`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val coordinator = coordinator(repository = repository)

        val result = coordinator.saveRssSubscription(
            name = " Season ",
            url = " https://rss.example.test/feed.xml ",
            filterRegex = " 1080p ",
            enabled = true,
        ) as RssSubscriptionActionResult.Saved

        assertEquals("Season", result.subscription.name)
        assertEquals(1L, result.subscription.id)
        assertEquals("https://rss.example.test/feed.xml", result.subscription.url)
        assertEquals("1080p", result.subscription.filterRegex)
        assertEquals(rssSubscriptionSavedStatus("Season"), result.status)
        assertEquals(listOf(result.subscription.copy(id = 0L)), repository.savedSubscriptions)
    }

    @Test
    fun `subscription action preserves selected subscription identity when updating`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val coordinator = coordinator(repository = repository)
        val selected = RssSubscriptionInfo(
            id = 9L,
            name = "Old",
            url = "https://rss.example.test/feed.xml",
            filterRegex = "old",
            enabled = false,
            lastCheckedAt = 123L,
        )

        val result = coordinator.saveRssSubscription(
            name = "New",
            url = "https://rss.example.test/feed.xml",
            filterRegex = "",
            enabled = true,
            selectedSubscription = selected,
        ) as RssSubscriptionActionResult.Saved

        assertEquals(9L, result.subscription.id)
        assertEquals(123L, result.subscription.lastCheckedAt)
        assertEquals(true, result.subscription.enabled)
        assertEquals("New", result.subscription.name)
    }

    @Test
    fun `subscription action maps save and delete failures to user status`() = runBlocking {
        val saveError = AppError.SyncError.WriteFailed("rss", "save failed")
        val deleteError = AppError.SyncError.WriteFailed("rss", "delete failed")
        val saveCoordinator = coordinator(
            repository = FakeCloudDriveAutomationRepository(saveSubscriptionResult = Result.failure(saveError)),
        )
        val deleteCoordinator = coordinator(
            repository = FakeCloudDriveAutomationRepository(deleteSubscriptionResult = Result.failure(deleteError)),
        )

        val saveResult = saveCoordinator.saveRssSubscription(
            name = "Season",
            url = "https://rss.example.test/feed.xml",
            filterRegex = "",
            enabled = true,
        )
        val deleteResult = deleteCoordinator.deleteRssSubscription(7L)

        assertEquals(RssSubscriptionActionResult.Failed(saveError.toUserMessage()), saveResult)
        assertEquals(RssSubscriptionActionResult.Failed(deleteError.toUserMessage()), deleteResult)
    }

    @Test
    fun `subscription delete action returns deleted status`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val coordinator = coordinator(repository = repository)

        val result = coordinator.deleteRssSubscription(7L)

        assertEquals(RssSubscriptionActionResult.Deleted(rssSubscriptionDeletedStatus()), result)
        assertEquals(listOf(7L), repository.deletedSubscriptionIds)
    }

    @Test
    fun `run action reports started and completed statuses`() = runBlocking {
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(runner = runner)
        var startedStatus = ""

        val result = coordinator.runCloudDriveOnce(
            onStarted = { status -> startedStatus = status },
        ) as CloudDriveRunActionResult.Completed

        assertEquals(cloudRssRunStartedStatus(), startedStatus)
        assertEquals(CloudDriveRssRunSummary(submitted = 1, skipped = 2, failed = 0, organized = 3), result.summary)
        assertEquals(result.summary.completeStatus(), result.status)
        assertEquals(1, runner.runCalls)
    }

    @Test
    fun `run action maps runner failures to user status`() = runBlocking {
        val error = AppError.SyncError.WriteFailed("rss", "run failed")
        val coordinator = coordinator(
            runner = FakeCloudDriveRssAutomationRunner(runResult = Result.failure(error)),
        )

        val result = coordinator.runCloudDriveOnce()

        assertEquals(CloudDriveRunActionResult.Failed(error.toUserMessage()), result)
    }

    @Test
    fun `save config and run persists current form before executing`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val credentials = FakeCloudDriveCredentialStore(token = "api-token")
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(
            repository = repository,
            credentials = credentials,
            runner = runner,
        )
        var savedConfig: CloudDriveAutomationConfig? = null
        var startedStatus = ""

        val result = coordinator.saveConfigAndRunCloudDriveOnceWithCallbacks(
            endpointUrl = " https://cloud.example.test ",
            username = " miru ",
            password = "",
            webDavSourceId = 9L,
            inboxPath = " /Inbox ",
            libraryPath = " /Library ",
            intervalMinutes = 30,
            enabled = true,
            onConfigSaved = { saved -> savedConfig = saved.config },
            onStarted = { status -> startedStatus = status },
        ) as CloudDriveRunActionResult.Completed

        assertEquals("https://cloud.example.test", savedConfig?.endpointUrl)
        assertEquals("/Inbox", repository.savedConfigs.single().inboxPath)
        assertEquals(cloudRssRunStartedStatus(), startedStatus)
        assertEquals(CloudDriveRssRunSummary(submitted = 1, skipped = 2, failed = 0, organized = 3), result.summary)
        assertEquals(1, runner.runCalls)
        assertEquals(emptyList<String>(), runner.loginCalls)
    }

    @Test
    fun `save config and run logs in with form password before executing`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val credentials = FakeCloudDriveCredentialStore()
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(
            repository = repository,
            credentials = credentials,
            runner = runner,
        )

        val result = coordinator.saveConfigAndRunCloudDriveOnceWithCallbacks(
            endpointUrl = "https://cloud.example.test",
            username = "miru",
            password = "secret",
            webDavSourceId = null,
            inboxPath = "/Inbox",
            libraryPath = "/Library",
            intervalMinutes = 30,
            enabled = true,
        )

        assertTrue(result is CloudDriveRunActionResult.Completed)
        assertEquals(listOf("https://cloud.example.test|miru|secret"), runner.loginCalls)
        assertEquals(1, runner.runCalls)
    }

    @Test
    fun `save config and run requires saved token or password`() = runBlocking {
        val repository = FakeCloudDriveAutomationRepository()
        val runner = FakeCloudDriveRssAutomationRunner()
        val coordinator = coordinator(repository = repository, runner = runner)

        val result = coordinator.saveConfigAndRunCloudDriveOnceWithCallbacks(
            endpointUrl = "https://cloud.example.test",
            username = "miru",
            password = "",
            webDavSourceId = null,
            inboxPath = "/Inbox",
            libraryPath = "/Library",
            intervalMinutes = 30,
            enabled = true,
        )

        assertEquals(CloudDriveRunActionResult.Failed(cloudDriveTokenLoginRequiredStatus()), result)
        assertEquals(1, repository.savedConfigs.size)
        assertEquals(0, runner.runCalls)
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

    private class FakeCloudDriveRssAutomationRunner(
        private val loginResult: Result<Unit> = Result.success(Unit),
        private val tokenResult: Result<CloudDriveTokenInfo>? = null,
        private val runResult: Result<CloudDriveRssRunSummary>? = null,
        private val tokenInfo: CloudDriveTokenInfo = CloudDriveTokenInfo(
            rootDir = "/",
            friendlyName = "Miru",
            allowList = true,
            allowCreateFolder = false,
            allowCreateFile = false,
            allowWrite = false,
            allowMove = false,
            allowAddOfflineDownload = false,
        ),
    ) : CloudDriveRssAutomationRunner {
        val loginCalls = mutableListOf<String>()
        val tokenCalls = mutableListOf<String>()
        var runCalls = 0

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> {
            loginCalls += "$endpointUrl|$username|$password"
            return loginResult
        }

        override suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
            tokenCalls += "$endpointUrl|$token"
            return tokenResult ?: Result.success(tokenInfo)
        }

        override suspend fun runOnce(): Result<CloudDriveRssRunSummary> {
            runCalls += 1
            return runResult ?: Result.success(
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
