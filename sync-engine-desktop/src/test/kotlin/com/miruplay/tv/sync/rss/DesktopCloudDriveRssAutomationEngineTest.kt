package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCloudDriveRssAutomationEngineTest {
    @Test
    fun `login stores CloudDrive token and password`() = runBlocking {
        val credentials = FakeCredentials(token = null)
        val engine = DesktopCloudDriveRssAutomationEngine(
            repository = FakeAutomationRepository(CloudDriveAutomationConfig(), emptyList()),
            credentials = credentials,
            cloudDriveClient = FakeCloudDriveClient(loginToken = "login-token"),
        )

        val result = engine.login(
            endpointUrl = "http://127.0.0.1:19798",
            username = "miru",
            password = "secret",
        )

        assertTrue(result is Result.Success)
        assertEquals("login-token", credentials.cloudDriveToken)
        assertEquals("secret", credentials.cloudDrivePassword)
    }

    @Test
    fun `saveApiToken validates token before persisting`() = runBlocking {
        val credentials = FakeCredentials(token = null)
        val engine = DesktopCloudDriveRssAutomationEngine(
            repository = FakeAutomationRepository(CloudDriveAutomationConfig(), emptyList()),
            credentials = credentials,
            cloudDriveClient = FakeCloudDriveClient(tokenFriendlyName = "desktop-token"),
        )

        val result = engine.saveApiToken(
            endpointUrl = "http://127.0.0.1:19798",
            token = "api-token",
        )

        assertTrue(result is Result.Success)
        assertEquals("api-token", credentials.cloudDriveToken)
        assertEquals("desktop-token", (result as Result.Success).data.friendlyName)
    }

    @Test
    fun `runOnce submits new RSS items and persists task state`() = runBlocking {
        val repository = FakeAutomationRepository(
            config = CloudDriveAutomationConfig(
                endpointUrl = "http://127.0.0.1:19798",
                inboxPath = "/Downloads",
                libraryPath = "/Library",
            ),
            subscriptions = listOf(
                RssSubscriptionInfo(
                    id = 7L,
                    name = "Anime",
                    url = "https://example.test/rss.xml",
                    filterRegex = "Episode",
                    enabled = true,
                )
            ),
        )
        val credentials = FakeCredentials(token = "token")
        val feedReader = FakeFeedReader(
            mapOf(
                "https://example.test/rss.xml" to listOf(
                    RssFeedItem(
                        title = "Episode 01",
                        guid = "guid-1",
                        link = "magnet:?xt=urn:btih:abc",
                        enclosureUrl = null,
                    ),
                    RssFeedItem(
                        title = "Preview",
                        guid = "guid-2",
                        link = "magnet:?xt=urn:btih:def",
                        enclosureUrl = null,
                    ),
                )
            )
        )
        val cloudDrive = FakeCloudDriveClient()
        val engine = DesktopCloudDriveRssAutomationEngine(
            repository = repository,
            credentials = credentials,
            feedFetcher = feedReader,
            cloudDriveClient = cloudDrive,
            organizer = CloudDriveLibraryOrganizer(cloudDrive),
        )

        val result = engine.runOnce()

        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        assertEquals(1, summary.submitted)
        assertEquals(1, summary.skipped)
        assertEquals(0, summary.failed)
        assertEquals(listOf("magnet:?xt=urn:btih:abc"), cloudDrive.offlineUrls)
        assertEquals("/Downloads", cloudDrive.offlineTargetFolder)
        assertEquals(listOf("guid-1"), repository.processed.map { it.itemKey })
        assertEquals(listOf("Episode 01"), repository.tasks.map { it.title })
        assertTrue(repository.lastCheckedAt > 0L)
        assertTrue(repository.lastRunAt > 0L)
    }

    @Test
    fun `runOnce rejects root inbox and does not fetch feeds`() = runBlocking {
        val repository = FakeAutomationRepository(
            config = CloudDriveAutomationConfig(
                endpointUrl = "http://127.0.0.1:19798",
                inboxPath = "/",
                libraryPath = "/Library",
            ),
            subscriptions = listOf(RssSubscriptionInfo(id = 1L, name = "Anime", url = "https://example.test/rss.xml")),
        )
        val feedReader = FakeFeedReader(emptyMap())
        val engine = DesktopCloudDriveRssAutomationEngine(
            repository = repository,
            credentials = FakeCredentials(token = "token"),
            feedFetcher = feedReader,
            cloudDriveClient = FakeCloudDriveClient(),
        )

        val result = engine.runOnce()

        assertTrue(result is Result.Error)
        assertEquals(0, feedReader.fetchedUrls.size)
    }

    @Test
    fun `scheduler triggers due sync and exposes running state`() = runBlocking {
        val repository = FakeAutomationRepository(
            config = CloudDriveAutomationConfig(
                endpointUrl = "http://127.0.0.1:19798",
                inboxPath = "/Downloads",
                libraryPath = "/Library",
                enabled = true,
            ),
            subscriptions = listOf(RssSubscriptionInfo(id = 3L, name = "Anime", url = "https://example.test/rss.xml")),
        )
        val feedReader = FakeFeedReader(
            mapOf(
                "https://example.test/rss.xml" to listOf(
                    RssFeedItem(
                        title = "Episode 02",
                        guid = "guid-2",
                        link = "magnet:?xt=urn:btih:def",
                        enclosureUrl = null,
                    )
                )
            )
        )
        val cloudDrive = FakeCloudDriveClient()
        val engine = DesktopCloudDriveRssAutomationEngine(
            repository = repository,
            credentials = FakeCredentials(token = "token"),
            feedFetcher = feedReader,
            cloudDriveClient = cloudDrive,
            organizer = CloudDriveLibraryOrganizer(cloudDrive),
        )
        val scheduler = DesktopCloudDriveRssScheduler(engine, this, checkIntervalMillis = 10L)

        assertTrue(scheduler.start())
        val state = withTimeout(1_000L) {
            scheduler.state.first { it.lastSummary != null }
        }
        scheduler.stop()

        assertTrue(state.running)
        assertTrue(state.lastRunCompletedAt > 0L)
        assertEquals(1, state.lastSummary?.submitted)
        assertEquals("magnet:?xt=urn:btih:def", cloudDrive.offlineUrls.single())
        withTimeout(1_000L) {
            scheduler.state.first { !it.running }
        }
        Unit
    }

    private class FakeAutomationRepository(
        private var config: CloudDriveAutomationConfig,
        private val subscriptions: List<RssSubscriptionInfo>,
    ) : CloudDriveAutomationRepository {
        val processed = mutableListOf<RssProcessedItemInfo>()
        val tasks = mutableListOf<RssDownloadTaskInfo>()
        var lastCheckedAt: Long = 0L
        var lastRunAt: Long = 0L

        override fun observeConfig(): Flow<CloudDriveAutomationConfig> = flowOf(config)

        override suspend fun getConfig(): Result<CloudDriveAutomationConfig> = Result.success(config)

        override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> {
            this.config = config
            return Result.success(Unit)
        }

        override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> {
            lastRunAt = timestamp
            config = config.copy(lastRunAt = timestamp)
            return Result.success(Unit)
        }

        override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> = flowOf(subscriptions)

        override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> =
            Result.success(subscriptions.filter { it.enabled })

        override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> =
            Result.success(subscription.id)

        override suspend fun deleteSubscription(id: Long): Result<Unit> = Result.success(Unit)

        override suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit> {
            lastCheckedAt = timestamp
            return Result.success(Unit)
        }

        override suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean> =
            Result.success(processed.any { it.subscriptionId == subscriptionId && it.itemKey == itemKey })

        override suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit> {
            processed += item
            return Result.success(Unit)
        }

        override suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long> {
            tasks += task.copy(id = tasks.size + 1L)
            return Result.success(tasks.last().id)
        }
    }

    private class FakeCredentials(token: String?) : CloudDriveCredentialStore {
        override var cloudDriveToken: String? = token
        override var cloudDrivePassword: String? = null

        override fun clearCloudDriveCredentials() {
            cloudDriveToken = null
            cloudDrivePassword = null
        }
    }

    private class FakeFeedReader(
        private val feeds: Map<String, List<RssFeedItem>>,
    ) : RssFeedReader {
        val fetchedUrls = mutableListOf<String>()

        override fun configureProxy(enabled: Boolean, host: String, port: Int) = Unit

        override suspend fun fetch(url: String): Result<List<RssFeedItem>> {
            fetchedUrls += url
            return Result.success(feeds[url].orEmpty())
        }
    }

    private class FakeCloudDriveClient(
        private val loginToken: String = "token",
        private val tokenFriendlyName: String = "test",
    ) : CloudDriveClient {
        val offlineUrls = mutableListOf<String>()
        var offlineTargetFolder: String = ""

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> =
            Result.success(CloudDriveLoginResult(loginToken))

        override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
            Result.success(
                CloudDriveTokenInfo(
                    rootDir = "/",
                    friendlyName = tokenFriendlyName,
                    allowList = true,
                    allowCreateFolder = true,
                    allowCreateFile = true,
                    allowWrite = true,
                    allowMove = true,
                    allowAddOfflineDownload = true,
                )
            )

        override suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit> {
            offlineUrls += urls
            offlineTargetFolder = targetFolder
            return Result.success(Unit)
        }

        override suspend fun uploadFile(endpoint: CloudDriveEndpoint, localFile: java.io.File, parentPath: String, remoteFileName: String): Result<String> =
            Result.success("$parentPath/$remoteFileName")

        override suspend fun listFolder(endpoint: CloudDriveEndpoint, path: String, forceRefresh: Boolean): Result<List<CloudDriveFileInfo>> =
            Result.success(emptyList())

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> =
            Result.success(Unit)
    }
}
