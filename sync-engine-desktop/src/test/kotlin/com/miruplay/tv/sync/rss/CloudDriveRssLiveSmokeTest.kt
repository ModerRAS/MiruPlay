package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudDriveRssLiveSmokeTest {
    @Test
    fun `parse options requires endpoint token rss and paths`() {
        val options = parseCloudDriveRssLiveSmokeOptions(
            arrayOf(
                "--endpoint",
                "http://127.0.0.1:19798",
                "--token",
                "api-token",
                "--rss-url",
                "https://example.test/rss.xml",
                "--inbox",
                "/Downloads",
                "--library",
                "/Library",
                "--filter",
                "Episode",
                "--max-preview",
                "5",
                "--proxy-enabled",
                "true",
                "--proxy-host",
                "127.0.0.1",
                "--proxy-port",
                "7890",
                "--report-path",
                "build/cloud-rss-smoke/report.json",
                "--submit",
                "true",
                "--submit-confirmation",
                "I_UNDERSTAND_THIS_SUBMITS_REAL_CLOUDDRIVE_DOWNLOADS",
                "--submit-limit",
                "2",
            )
        )

        assertEquals("http://127.0.0.1:19798", options.endpoint)
        assertEquals("api-token", options.token)
        assertEquals("https://example.test/rss.xml", options.rssUrl)
        assertEquals("/Downloads", options.inboxPath)
        assertEquals("/Library", options.libraryPath)
        assertEquals("Episode", options.filterRegex)
        assertEquals(5, options.maxPreviewItems)
        assertTrue(options.proxyEnabled)
        assertEquals("127.0.0.1", options.proxyHost)
        assertEquals(7890, options.proxyPort)
        assertEquals("build/cloud-rss-smoke/report.json", options.reportPath)
        assertTrue(options.submit)
        assertEquals("I_UNDERSTAND_THIS_SUBMITS_REAL_CLOUDDRIVE_DOWNLOADS", options.submitConfirmation)
        assertEquals(2, options.submitLimit)
    }

    @Test
    fun `dry run reports candidate RSS items without submitting downloads`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient(
            files = mapOf(
                "/Downloads" to listOf(CloudDriveFileInfo("Inbox item", "/Downloads/Inbox item", isDirectory = true)),
                "/Library" to emptyList(),
            )
        )
        val feedReader = FakeFeedReader(
            listOf(
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
                RssFeedItem(
                    title = "Episode 02",
                    guid = "guid-3",
                    link = null,
                    enclosureUrl = "https://example.test/episode-02.torrent",
                ),
            )
        )

        val result = runCloudDriveRssLiveSmoke(
            options = CloudDriveRssLiveSmokeOptions(
                endpoint = "http://127.0.0.1:19798",
                token = "api-token",
                rssUrl = "https://example.test/rss.xml",
                inboxPath = "Downloads",
                libraryPath = "Library",
                filterRegex = "Episode",
            ),
            cloudDriveClient = cloudDrive,
            feedReader = feedReader,
        )

        assertTrue(result is Result.Success)
        val report = (result as Result.Success).data
        assertEquals("desktop-token", report.friendlyName)
        assertEquals("/Downloads", report.inboxPath)
        assertEquals("/Library", report.libraryPath)
        assertEquals(1, report.inboxItemCount)
        assertEquals(3, report.feedItemCount)
        assertEquals(2, report.candidateCount)
        assertEquals(1, report.skippedByFilterCount)
        assertEquals(0, report.missingSubmissionCount)
        assertEquals(1, report.magnetCandidateCount)
        assertEquals(1, report.torrentCandidateCount)
        assertFalse(report.submitMode)
        assertEquals(0, report.submitAttemptedCount)
        assertEquals(0, report.submitSucceededCount)
        assertEquals(null, report.postSubmitInboxItemCount)
        assertEquals(0, cloudDrive.offlineSubmissions)
        assertEquals(listOf("/Downloads", "/Library"), cloudDrive.listedPaths)
        assertEquals(listOf("https://example.test/rss.xml"), feedReader.fetchedUrls)
    }

    @Test
    fun `live submit requires explicit confirmation`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient()
        val feedReader = FakeFeedReader(
            listOf(
                RssFeedItem(
                    title = "Episode 01",
                    guid = "guid-1",
                    link = "magnet:?xt=urn:btih:abc",
                    enclosureUrl = null,
                )
            )
        )

        val result = runCloudDriveRssLiveSmoke(
            options = CloudDriveRssLiveSmokeOptions(
                endpoint = "http://127.0.0.1:19798",
                token = "api-token",
                rssUrl = "https://example.test/rss.xml",
                inboxPath = "/Downloads",
                libraryPath = "/Library",
                submit = true,
                submitConfirmation = "yes",
            ),
            cloudDriveClient = cloudDrive,
            feedReader = feedReader,
        )

        assertTrue(result is Result.Error)
        assertEquals(0, cloudDrive.offlineSubmissions)
    }

    @Test
    fun `live submit sends limited candidate URLs and refreshes inbox evidence`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient(
            files = mapOf(
                "/Downloads" to listOf(CloudDriveFileInfo("Inbox item", "/Downloads/Inbox item", isDirectory = true)),
                "/Library" to emptyList(),
            )
        )
        val feedReader = FakeFeedReader(
            listOf(
                RssFeedItem(
                    title = "Episode 01",
                    guid = "guid-1",
                    link = "magnet:?xt=urn:btih:abc",
                    enclosureUrl = null,
                ),
                RssFeedItem(
                    title = "Episode 02",
                    guid = "guid-2",
                    link = "magnet:?xt=urn:btih:def",
                    enclosureUrl = null,
                ),
            )
        )

        val result = runCloudDriveRssLiveSmoke(
            options = CloudDriveRssLiveSmokeOptions(
                endpoint = "http://127.0.0.1:19798",
                token = "api-token",
                rssUrl = "https://example.test/rss.xml",
                inboxPath = "/Downloads",
                libraryPath = "/Library",
                submit = true,
                submitConfirmation = "I_UNDERSTAND_THIS_SUBMITS_REAL_CLOUDDRIVE_DOWNLOADS",
                submitLimit = 1,
            ),
            cloudDriveClient = cloudDrive,
            feedReader = feedReader,
        )

        assertTrue(result is Result.Success)
        val report = (result as Result.Success).data
        assertTrue(report.submitMode)
        assertEquals(1, report.submitAttemptedCount)
        assertEquals(1, report.submitSucceededCount)
        assertEquals(1, report.postSubmitInboxItemCount)
        assertEquals(1, cloudDrive.offlineSubmissions)
        assertEquals(listOf("magnet:?xt=urn:btih:abc"), cloudDrive.offlineUrls)
        assertEquals("/Downloads", cloudDrive.offlineTargetFolder)
        assertEquals(listOf("/Downloads", "/Library", "/Downloads"), cloudDrive.listedPaths)
    }

    @Test
    fun `dry run rejects library nested inside inbox`() = runBlocking {
        val result = runCloudDriveRssLiveSmoke(
            options = CloudDriveRssLiveSmokeOptions(
                endpoint = "http://127.0.0.1:19798",
                token = "api-token",
                rssUrl = "https://example.test/rss.xml",
                inboxPath = "/Downloads",
                libraryPath = "/Downloads/Library",
            ),
            cloudDriveClient = FakeCloudDriveClient(),
            feedReader = FakeFeedReader(emptyList()),
        )

        assertTrue(result is Result.Error)
    }

    @Test
    fun `report json captures dry run evidence without leaking token`() {
        val options = CloudDriveRssLiveSmokeOptions(
            endpoint = "http://127.0.0.1:19798",
            token = "secret-token",
            rssUrl = "https://example.test/rss.xml",
            inboxPath = "/Downloads",
            libraryPath = "/Library",
        )
        val report = CloudDriveRssLiveSmokeReport(
            friendlyName = "desktop-token",
            rootDir = "/",
            permissions = CloudDriveRssLiveSmokePermissions(
                allowList = true,
                allowCreateFolder = true,
                allowCreateFile = true,
                allowWrite = true,
                allowMove = true,
                allowAddOfflineDownload = true,
            ),
            inboxPath = "/Downloads",
            inboxItemCount = 2,
            libraryPath = "/Library",
            libraryItemCount = 3,
            rssUrl = "https://example.test/rss.xml",
            feedItemCount = 1,
            candidateCount = 1,
            skippedByFilterCount = 0,
            missingSubmissionCount = 0,
            magnetCandidateCount = 1,
            torrentCandidateCount = 0,
            otherCandidateCount = 0,
            submitMode = true,
            submitAttemptedCount = 1,
            submitSucceededCount = 1,
            postSubmitInboxItemCount = 4,
            previewItems = listOf(
                CloudDriveRssLiveSmokeItem(
                    title = "Episode 01",
                    guid = "guid-1",
                    submissionUrl = "magnet:?xt=urn:btih:abc",
                    status = CloudDriveRssLiveSmokeItemStatus.WOULD_SUBMIT,
                    submissionType = CloudDriveRssLiveSmokeSubmissionType.MAGNET,
                )
            ),
        )

        val json = buildCloudDriveRssLiveSmokeReportJson(options, report)

        assertFalse(json.contains("secret-token"))
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals("http://127.0.0.1:19798", root.getValue("endpoint").jsonPrimitive.content)
        assertEquals(1, root.getValue("candidateCount").jsonPrimitive.int)
        val liveSubmit = root.getValue("liveSubmit").jsonObject
        assertTrue(liveSubmit.getValue("enabled").jsonPrimitive.boolean)
        assertEquals(1, liveSubmit.getValue("attemptedCount").jsonPrimitive.int)
        assertEquals(4, liveSubmit.getValue("postSubmitInboxItemCount").jsonPrimitive.int)
        assertEquals("desktop-token", root.getValue("tokenInfo").jsonObject.getValue("friendlyName").jsonPrimitive.content)
        assertEquals("Episode 01", root.getValue("previewItems").jsonArray.single().jsonObject.getValue("title").jsonPrimitive.content)
    }

    private class FakeFeedReader(
        private val items: List<RssFeedItem>,
    ) : RssFeedReader {
        val fetchedUrls = mutableListOf<String>()

        override fun configureProxy(enabled: Boolean, host: String, port: Int) = Unit

        override suspend fun fetch(url: String): Result<List<RssFeedItem>> {
            fetchedUrls += url
            return Result.success(items)
        }
    }

    private class FakeCloudDriveClient(
        private val files: Map<String, List<CloudDriveFileInfo>> = emptyMap(),
    ) : CloudDriveClient {
        val listedPaths = mutableListOf<String>()
        val offlineUrls = mutableListOf<String>()
        var offlineTargetFolder: String = ""
        var offlineSubmissions = 0

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> =
            Result.success(CloudDriveLoginResult("login-token"))

        override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
            Result.success(
                CloudDriveTokenInfo(
                    rootDir = "/",
                    friendlyName = "desktop-token",
                    allowList = true,
                    allowCreateFolder = true,
                    allowCreateFile = true,
                    allowWrite = true,
                    allowMove = true,
                    allowAddOfflineDownload = true,
                )
            )

        override suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit> {
            offlineSubmissions += 1
            offlineUrls += urls
            offlineTargetFolder = targetFolder
            return Result.success(Unit)
        }

        override suspend fun uploadFile(
            endpoint: CloudDriveEndpoint,
            localFile: File,
            parentPath: String,
            remoteFileName: String,
        ): Result<String> = Result.success("$parentPath/$remoteFileName")

        override suspend fun listFolder(
            endpoint: CloudDriveEndpoint,
            path: String,
            forceRefresh: Boolean,
        ): Result<List<CloudDriveFileInfo>> {
            listedPaths += path
            return Result.success(files[path].orEmpty())
        }

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> =
            Result.success(Unit)
    }
}
