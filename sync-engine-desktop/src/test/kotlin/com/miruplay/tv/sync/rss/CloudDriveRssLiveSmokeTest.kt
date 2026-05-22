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
                "--organize",
                "true",
                "--organize-confirmation",
                "I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES",
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
        assertTrue(options.organize)
        assertEquals("I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES", options.organizeConfirmation)
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
        assertEquals(0, report.submitPreparedTorrentCount)
        assertEquals(null, report.postSubmitInboxItemCount)
        assertFalse(report.organizeMode)
        assertEquals(0, report.organizeMovedCount)
        assertEquals(null, report.postOrganizeInboxItemCount)
        assertEquals(null, report.postOrganizeLibraryItemCount)
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
        assertEquals(emptyList<String>(), cloudDrive.offlineUrls)
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
        assertEquals(0, report.submitPreparedTorrentCount)
        assertEquals(1, report.postSubmitInboxItemCount)
        assertEquals(1, cloudDrive.offlineSubmissions)
        assertEquals(listOf("magnet:?xt=urn:btih:abc"), cloudDrive.offlineUrls)
        assertEquals("/Downloads", cloudDrive.offlineTargetFolder)
        assertEquals(listOf("/Downloads", "/Library", "/Downloads"), cloudDrive.listedPaths)
    }

    @Test
    fun `live submit prepares torrent candidate through shared staging flow`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient(
            files = mapOf(
                "/Downloads" to emptyList(),
                "/Library" to emptyList(),
            )
        )
        val feedReader = FakeFeedReader(
            listOf(
                RssFeedItem(
                    title = "Episode 01",
                    guid = "guid-1",
                    link = null,
                    enclosureUrl = "https://example.test/episode-01.torrent",
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
                submitConfirmation = "I_UNDERSTAND_THIS_SUBMITS_REAL_CLOUDDRIVE_DOWNLOADS",
                submitLimit = 1,
            ),
            cloudDriveClient = cloudDrive,
            feedReader = feedReader,
            submissionPreparer = CloudDriveRssSubmissionPreparer(
                cloudDriveClient = cloudDrive,
                torrentDownloader = FakeTorrentDownloader(torrentBytes()),
            ),
        )

        assertTrue(result is Result.Success)
        val report = (result as Result.Success).data
        assertEquals(1, report.submitAttemptedCount)
        assertEquals(1, report.submitSucceededCount)
        assertEquals(1, report.submitPreparedTorrentCount)
        assertEquals(1, cloudDrive.offlineSubmissions)
        assertEquals(listOf(expectedMagnet(torrentBytes())), cloudDrive.offlineUrls)
        assertEquals(listOf("/Downloads/.miruplay-torrents"), cloudDrive.createdFolders)
        assertEquals(listOf("/Downloads/.miruplay-torrents/episode-01.torrent"), cloudDrive.uploadedPaths)
    }

    @Test
    fun `organize requires explicit confirmation`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient(
            files = mapOf(
                "/Downloads" to listOf(
                    CloudDriveFileInfo("[Subs] Test Show - 01.mkv", "/Downloads/[Subs] Test Show - 01.mkv", isDirectory = false)
                ),
                "/Library" to emptyList(),
            )
        )

        val result = runCloudDriveRssLiveSmoke(
            options = CloudDriveRssLiveSmokeOptions(
                endpoint = "http://127.0.0.1:19798",
                token = "api-token",
                rssUrl = "https://example.test/rss.xml",
                inboxPath = "/Downloads",
                libraryPath = "/Library",
                organize = true,
                organizeConfirmation = "yes",
            ),
            cloudDriveClient = cloudDrive,
            feedReader = FakeFeedReader(emptyList()),
        )

        assertTrue(result is Result.Error)
        assertEquals(emptyList<Pair<List<String>, String>>(), cloudDrive.moves)
    }

    @Test
    fun `organize moves downloaded videos and refreshes folder evidence`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient(
            files = mapOf(
                "/Downloads" to listOf(
                    CloudDriveFileInfo("[Subs] Test Show - 01.mkv", "/Downloads/[Subs] Test Show - 01.mkv", isDirectory = false)
                ),
                "/Library" to emptyList(),
            )
        )

        val result = runCloudDriveRssLiveSmoke(
            options = CloudDriveRssLiveSmokeOptions(
                endpoint = "http://127.0.0.1:19798",
                token = "api-token",
                rssUrl = "https://example.test/rss.xml",
                inboxPath = "/Downloads",
                libraryPath = "/Library",
                organize = true,
                organizeConfirmation = "I_UNDERSTAND_THIS_MOVES_REAL_CLOUDDRIVE_FILES",
            ),
            cloudDriveClient = cloudDrive,
            feedReader = FakeFeedReader(emptyList()),
            organizer = CloudDriveLibraryOrganizer(
                cloudDriveClient = cloudDrive,
                classifier = CloudDriveVideoClassifier {
                    CloudDriveVideoClassification(showName = "Test Show", seasonNumber = 1)
                },
            ),
        )

        assertTrue(result is Result.Success)
        val report = (result as Result.Success).data
        assertTrue(report.organizeMode)
        assertEquals(1, report.organizeMovedCount)
        assertEquals(0, report.postOrganizeInboxItemCount)
        assertEquals(1, report.postOrganizeLibraryItemCount)
        assertEquals(listOf("/Library/Test Show", "/Library/Test Show/Season 1"), cloudDrive.createdFolders)
        assertEquals(
            listOf(listOf("/Downloads/[Subs] Test Show - 01.mkv") to "/Library/Test Show/Season 1"),
            cloudDrive.moves,
        )
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
            submitPreparedTorrentCount = 0,
            postSubmitInboxItemCount = 4,
            organizeMode = true,
            organizeMovedCount = 1,
            postOrganizeInboxItemCount = 0,
            postOrganizeLibraryItemCount = 5,
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
        assertEquals(0, liveSubmit.getValue("preparedTorrentCount").jsonPrimitive.int)
        assertEquals(4, liveSubmit.getValue("postSubmitInboxItemCount").jsonPrimitive.int)
        val organize = root.getValue("organize").jsonObject
        assertTrue(organize.getValue("enabled").jsonPrimitive.boolean)
        assertEquals(1, organize.getValue("movedCount").jsonPrimitive.int)
        assertEquals(0, organize.getValue("postOrganizeInboxItemCount").jsonPrimitive.int)
        assertEquals(5, organize.getValue("postOrganizeLibraryItemCount").jsonPrimitive.int)
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
        files: Map<String, List<CloudDriveFileInfo>> = emptyMap(),
    ) : CloudDriveClient {
        private val filesByPath = files
            .mapValues { (_, entries) -> entries.toMutableList() }
            .toMutableMap()
        val listedPaths = mutableListOf<String>()
        val offlineUrls = mutableListOf<String>()
        val createdFolders = mutableListOf<String>()
        val uploadedPaths = mutableListOf<String>()
        val moves = mutableListOf<Pair<List<String>, String>>()
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
        ): Result<String> {
            val uploadedPath = "$parentPath/$remoteFileName"
            uploadedPaths += uploadedPath
            return Result.success(uploadedPath)
        }

        override suspend fun listFolder(
            endpoint: CloudDriveEndpoint,
            path: String,
            forceRefresh: Boolean,
        ): Result<List<CloudDriveFileInfo>> {
            listedPaths += path
            return Result.success(filesByPath[path].orEmpty())
        }

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> {
            val folderPath = "$parentPath/$folderName"
            createdFolders += folderPath
            val parentEntries = filesByPath.getOrPut(parentPath) { mutableListOf() }
            if (parentEntries.none { it.path == folderPath }) {
                parentEntries += CloudDriveFileInfo(folderName, folderPath, isDirectory = true)
            }
            filesByPath.getOrPut(folderPath) { mutableListOf() }
            return Result.success(Unit)
        }

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> {
            moves += paths to destinationPath
            val destinationEntries = filesByPath.getOrPut(destinationPath) { mutableListOf() }
            paths.forEach { path ->
                val sourceParent = path.substringBeforeLast('/', missingDelimiterValue = "")
                    .ifBlank { "/" }
                val sourceEntries = filesByPath[sourceParent]
                val entry = sourceEntries
                    ?.firstOrNull { it.path == path }
                    ?: CloudDriveFileInfo(path.substringAfterLast('/'), path, isDirectory = false)
                sourceEntries?.removeAll { it.path == path }
                val movedPath = "$destinationPath/${entry.name}"
                destinationEntries += entry.copy(path = movedPath)
            }
            return Result.success(Unit)
        }
    }

    private class FakeTorrentDownloader(
        private val bytes: ByteArray,
    ) : RssTorrentDownloader {
        override fun configureProxy(enabled: Boolean, host: String, port: Int) = Unit

        override suspend fun download(url: String, title: String, keyPrefix: String): Result<DownloadedTorrentFile> {
            val file = File.createTempFile("miruplay-rss-smoke-test", ".torrent")
            file.writeBytes(bytes)
            return Result.success(DownloadedTorrentFile(file, "episode-01.torrent"))
        }
    }
}

private fun torrentBytes(): ByteArray {
    val info = "d4:name8:Test.mkv12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrste"
    return "d8:announce14:http://tracker4:info${info}e".toByteArray()
}

private fun expectedMagnet(torrentBytes: ByteArray): String {
    val info = "d4:name8:Test.mkv12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrste".toByteArray()
    val expectedHash = RssTextEncoding.sha1Hex(info)
    return "magnet:?xt=urn:btih:$expectedHash&dn=Test.mkv&tr=http%3A%2F%2Ftracker"
}
