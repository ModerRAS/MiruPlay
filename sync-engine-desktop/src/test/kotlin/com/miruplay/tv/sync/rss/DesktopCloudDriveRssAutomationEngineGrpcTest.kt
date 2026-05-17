package com.miruplay.tv.sync.rss

import clouddrive.CloudDriveFileSrvGrpc
import clouddrive.Clouddrive
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCloudDriveRssAutomationEngineGrpcTest {
    private var server: Server? = null

    @After
    fun tearDown() {
        server?.shutdownNow()
        server = null
    }

    @Test
    fun `runOnce submits RSS item through real GrpcCloudDriveClient`() = runBlocking {
        val cloudDriveService = FakeCloudDriveService()
        val endpointUrl = startServer(cloudDriveService)
        val repository = FakeAutomationRepository(
            config = CloudDriveAutomationConfig(
                endpointUrl = endpointUrl,
                inboxPath = "/Downloads",
                libraryPath = "/Library",
            ),
            subscriptions = listOf(
                RssSubscriptionInfo(
                    id = 9L,
                    name = "Anime",
                    url = "https://example.test/rss.xml",
                    filterRegex = "Episode",
                    enabled = true,
                )
            ),
        )
        val feedReader = FakeFeedReader(
            listOf(
                RssFeedItem(
                    title = "Episode 03",
                    guid = "guid-3",
                    link = "magnet:?xt=urn:btih:abcdef",
                    enclosureUrl = null,
                )
            )
        )
        val client = GrpcCloudDriveClient()
        val engine = DesktopCloudDriveRssAutomationEngine(
            repository = repository,
            credentials = FakeCredentials(token = "api-token"),
            feedFetcher = feedReader,
            cloudDriveClient = client,
            organizer = DesktopCloudDriveLibraryOrganizer(client),
        )

        val result = engine.runOnce()

        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        assertEquals(1, summary.submitted)
        assertEquals(0, summary.skipped)
        assertEquals(0, summary.failed)
        assertEquals(0, summary.organized)
        assertEquals(listOf("magnet:?xt=urn:btih:abcdef"), cloudDriveService.offlineUrls)
        assertEquals("/Downloads", cloudDriveService.offlineTargetFolder)
        assertEquals(listOf("/Downloads"), cloudDriveService.listFolderPaths)
        assertTrue(cloudDriveService.authorizationHeaders.all { it == "Bearer api-token" })
        assertEquals(listOf("guid-3"), repository.processed.map { it.itemKey })
        assertEquals(listOf("Episode 03"), repository.tasks.map { it.title })
        assertTrue(repository.lastCheckedAt > 0L)
        assertTrue(repository.lastRunAt > 0L)
    }

    private fun startServer(service: FakeCloudDriveService): String {
        server = ServerBuilder
            .forPort(0)
            .directExecutor()
            .addService(ServerInterceptors.intercept(service, CapturingAuthorizationInterceptor()))
            .build()
            .start()
        return "http://127.0.0.1:${server!!.port}"
    }

    private class FakeCloudDriveService : CloudDriveFileSrvGrpc.CloudDriveFileSrvImplBase() {
        val authorizationHeaders = mutableListOf<String>()
        val offlineUrls = mutableListOf<String>()
        var offlineTargetFolder: String = ""
        val listFolderPaths = mutableListOf<String>()

        override fun addOfflineFiles(
            request: Clouddrive.AddOfflineFileRequest,
            responseObserver: StreamObserver<Clouddrive.FileOperationResult>
        ) {
            authorizationHeaders += AUTHORIZATION_CONTEXT.get().orEmpty()
            offlineUrls += request.urls.split('\n').filter { it.isNotBlank() }
            offlineTargetFolder = request.toFolder
            responseObserver.respond(
                Clouddrive.FileOperationResult.newBuilder()
                    .setSuccess(true)
                    .build()
            )
        }

        override fun getSubFiles(
            request: Clouddrive.ListSubFileRequest,
            responseObserver: StreamObserver<Clouddrive.SubFilesReply>
        ) {
            authorizationHeaders += AUTHORIZATION_CONTEXT.get().orEmpty()
            listFolderPaths += request.path
            responseObserver.respond(Clouddrive.SubFilesReply.getDefaultInstance())
        }
    }

    private class CapturingAuthorizationInterceptor : ServerInterceptor {
        override fun <ReqT : Any, RespT : Any> interceptCall(
            call: ServerCall<ReqT, RespT>,
            headers: Metadata,
            next: ServerCallHandler<ReqT, RespT>
        ): ServerCall.Listener<ReqT> {
            val authorization = headers.get(AUTHORIZATION_HEADER).orEmpty()
            return Contexts.interceptCall(
                Context.current().withValue(AUTHORIZATION_CONTEXT, authorization),
                call,
                headers,
                next
            )
        }
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
        private val items: List<RssFeedItem>,
    ) : RssFeedReader {
        override fun configureProxy(enabled: Boolean, host: String, port: Int) = Unit

        override suspend fun fetch(url: String): Result<List<RssFeedItem>> =
            Result.success(items)
    }

    private companion object {
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        private val AUTHORIZATION_CONTEXT: Context.Key<String> = Context.key("authorization")

        private fun <T> StreamObserver<T>.respond(value: T) {
            onNext(value)
            onCompleted()
        }
    }
}
