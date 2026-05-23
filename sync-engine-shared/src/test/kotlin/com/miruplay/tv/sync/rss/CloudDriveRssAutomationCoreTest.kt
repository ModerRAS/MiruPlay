package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudDriveRssAutomationCoreTest {
    @Test
    fun `runOnce propagates config read failures instead of replacing them with generic error`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("cloud-drive-config", "read failed")
        val repository = FakeAutomationRepository(configError = failure)
        val feedReader = FakeFeedReader()
        val core = core(repository = repository, feedReader = feedReader)

        val result = core.runOnce()

        assertEquals(Result.failure(failure), result)
        assertEquals(emptyList<String>(), feedReader.fetchedUrls)
    }

    @Test
    fun `runOnce propagates enabled subscription read failures`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("rss-subscriptions", "read failed")
        val repository = FakeAutomationRepository(enabledSubscriptionsError = failure)
        val feedReader = FakeFeedReader()
        val core = core(repository = repository, feedReader = feedReader)

        val result = core.runOnce()

        assertEquals(Result.failure(failure), result)
        assertEquals(emptyList<String>(), feedReader.fetchedUrls)
    }

    @Test
    fun `runOnce propagates processed item lookup failures before submitting item`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("rss-processed-items", "read failed")
        val repository = FakeAutomationRepository(
            subscriptions = listOf(subscription()),
            isItemProcessedError = failure,
        )
        val feedReader = FakeFeedReader(
            feeds = mapOf("https://example.test/rss.xml" to listOf(feedItem()))
        )
        val cloudDrive = FakeCloudDriveClient()
        val core = core(
            repository = repository,
            feedReader = feedReader,
            cloudDrive = cloudDrive,
        )

        val result = core.runOnce()

        assertEquals(Result.failure(failure), result)
        assertEquals(emptyList<String>(), cloudDrive.offlineUrls)
        assertEquals(emptyList<RssProcessedItemInfo>(), repository.processed)
    }

    @Test
    fun `runOnce propagates processed item save failures after CloudDrive accepts submission`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("rss-processed-items", "save failed")
        val repository = FakeAutomationRepository(
            subscriptions = listOf(subscription()),
            markItemProcessedError = failure,
        )
        val cloudDrive = FakeCloudDriveClient()
        val core = core(
            repository = repository,
            feedReader = FakeFeedReader(
                feeds = mapOf("https://example.test/rss.xml" to listOf(feedItem()))
            ),
            cloudDrive = cloudDrive,
        )

        val result = core.runOnce()

        assertEquals(Result.failure(failure), result)
        assertEquals(listOf("magnet:?xt=urn:btih:abc"), cloudDrive.offlineUrls)
        assertEquals(emptyList<RssDownloadTaskInfo>(), repository.tasks)
        assertEquals(0L, repository.lastRunAt)
    }

    @Test
    fun `runOnce propagates organizer failures instead of treating organized count as zero`() = runBlocking {
        val failure = AppError.NetworkError.ServerUnreachable("CloudDrive2")
        val repository = FakeAutomationRepository()
        val cloudDrive = FakeCloudDriveClient(listFolderFailure = failure)
        val core = core(repository = repository, cloudDrive = cloudDrive)

        val result = core.runOnce()

        assertEquals(Result.failure(failure), result)
        assertEquals(0L, repository.lastRunAt)
    }

    @Test
    fun `runIfDue propagates config read failures instead of skipping scheduler run`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("cloud-drive-config", "read failed")
        val core = core(repository = FakeAutomationRepository(configError = failure))

        val result = core.runIfDue()

        assertEquals(Result.failure(failure), result)
    }

    @Test
    fun `runOnce still reports per feed fetch failures as failed item summary`() = runBlocking {
        val repository = FakeAutomationRepository(subscriptions = listOf(subscription()))
        val core = core(
            repository = repository,
            feedReader = FakeFeedReader(
                feedErrors = mapOf("https://example.test/rss.xml" to AppError.NetworkError.ServerUnreachable("rss"))
            ),
        )

        val result = core.runOnce()

        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        assertEquals(0, summary.submitted)
        assertEquals(0, summary.skipped)
        assertEquals(1, summary.failed)
        assertEquals(0, summary.organized)
        assertEquals(emptyList<RssProcessedItemInfo>(), repository.processed)
        assertEquals(emptyList<RssDownloadTaskInfo>(), repository.tasks)
    }

    private fun core(
        repository: FakeAutomationRepository = FakeAutomationRepository(),
        credentials: FakeCredentials = FakeCredentials(token = "token"),
        feedReader: FakeFeedReader = FakeFeedReader(),
        cloudDrive: FakeCloudDriveClient = FakeCloudDriveClient(),
    ): CloudDriveRssAutomationCore =
        CloudDriveRssAutomationCore(
            repository = repository,
            credentials = credentials,
            cloudDriveClient = cloudDrive,
            feedFetcher = feedReader,
            organizer = CloudDriveLibraryOrganizer(cloudDrive),
        )

    private fun subscription(): RssSubscriptionInfo =
        RssSubscriptionInfo(
            id = 7L,
            name = "Anime",
            url = "https://example.test/rss.xml",
            enabled = true,
        )

    private fun feedItem(): RssFeedItem =
        RssFeedItem(
            title = "Episode 01",
            guid = "guid-1",
            link = "magnet:?xt=urn:btih:abc",
            enclosureUrl = null,
        )

    private class FakeAutomationRepository(
        private var config: CloudDriveAutomationConfig = CloudDriveAutomationConfig(
            endpointUrl = "http://127.0.0.1:19798",
            inboxPath = "/Downloads",
            libraryPath = "/Library",
        ),
        private val subscriptions: List<RssSubscriptionInfo> = emptyList(),
        private val configError: AppError? = null,
        private val enabledSubscriptionsError: AppError? = null,
        private val isItemProcessedError: AppError? = null,
        private val markItemProcessedError: AppError? = null,
        private val saveDownloadTaskError: AppError? = null,
        private val markSubscriptionCheckedError: AppError? = null,
        private val updateLastRunAtError: AppError? = null,
    ) : CloudDriveAutomationRepository {
        val processed = mutableListOf<RssProcessedItemInfo>()
        val tasks = mutableListOf<RssDownloadTaskInfo>()
        var lastCheckedAt: Long = 0L
        var lastRunAt: Long = 0L

        override fun observeConfig(): Flow<CloudDriveAutomationConfig> = flowOf(config)

        override suspend fun getConfig(): Result<CloudDriveAutomationConfig> {
            configError?.let { return Result.failure(it) }
            return Result.success(config)
        }

        override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> {
            this.config = config
            return Result.success(Unit)
        }

        override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> {
            updateLastRunAtError?.let { return Result.failure(it) }
            lastRunAt = timestamp
            config = config.copy(lastRunAt = timestamp)
            return Result.success(Unit)
        }

        override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> = flowOf(subscriptions)

        override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> {
            enabledSubscriptionsError?.let { return Result.failure(it) }
            return Result.success(subscriptions.filter { it.enabled })
        }

        override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> =
            Result.success(subscription.id)

        override suspend fun deleteSubscription(id: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit> {
            markSubscriptionCheckedError?.let { return Result.failure(it) }
            lastCheckedAt = timestamp
            return Result.success(Unit)
        }

        override suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean> {
            isItemProcessedError?.let { return Result.failure(it) }
            return Result.success(processed.any { it.subscriptionId == subscriptionId && it.itemKey == itemKey })
        }

        override suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit> {
            markItemProcessedError?.let { return Result.failure(it) }
            processed += item
            return Result.success(Unit)
        }

        override suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long> {
            saveDownloadTaskError?.let { return Result.failure(it) }
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
        private val feeds: Map<String, List<RssFeedItem>> = emptyMap(),
        private val feedErrors: Map<String, AppError> = emptyMap(),
    ) : RssFeedReader {
        val fetchedUrls = mutableListOf<String>()

        override fun configureProxy(enabled: Boolean, host: String, port: Int) = Unit

        override suspend fun fetch(url: String): Result<List<RssFeedItem>> {
            fetchedUrls += url
            feedErrors[url]?.let { return Result.failure(it) }
            return Result.success(feeds[url].orEmpty())
        }
    }

    private class FakeCloudDriveClient(
        private val listFolderFailure: AppError? = null,
    ) : CloudDriveClient {
        val offlineUrls = mutableListOf<String>()

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> =
            Result.success(CloudDriveLoginResult("token"))

        override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
            Result.success(
                CloudDriveTokenInfo(
                    rootDir = "/",
                    friendlyName = "test",
                    allowList = true,
                    allowCreateFolder = true,
                    allowCreateFile = true,
                    allowWrite = true,
                    allowMove = true,
                    allowAddOfflineDownload = true,
                )
            )

        override suspend fun addOfflineFiles(
            endpoint: CloudDriveEndpoint,
            urls: List<String>,
            targetFolder: String,
        ): Result<Unit> {
            offlineUrls += urls
            return Result.success(Unit)
        }

        override suspend fun uploadFile(
            endpoint: CloudDriveEndpoint,
            localFile: File,
            parentPath: String,
            remoteFileName: String,
        ): Result<String> =
            Result.success("$parentPath/$remoteFileName")

        override suspend fun listFolder(
            endpoint: CloudDriveEndpoint,
            path: String,
            forceRefresh: Boolean,
        ): Result<List<CloudDriveFileInfo>> {
            listFolderFailure?.let { return Result.failure(it) }
            return Result.success(emptyList())
        }

        override suspend fun createFolder(
            endpoint: CloudDriveEndpoint,
            parentPath: String,
            folderName: String,
        ): Result<Unit> =
            Result.success(Unit)

        override suspend fun moveFiles(
            endpoint: CloudDriveEndpoint,
            paths: List<String>,
            destinationPath: String,
        ): Result<Unit> =
            Result.success(Unit)
    }
}
