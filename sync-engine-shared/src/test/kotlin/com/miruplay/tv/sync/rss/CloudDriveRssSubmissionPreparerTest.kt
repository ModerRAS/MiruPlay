package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CloudDriveRssSubmissionPreparerTest {
    @Test
    fun `prepare keeps magnet submissions unchanged`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient()
        val preparer = CloudDriveRssSubmissionPreparer(cloudDrive, FakeTorrentDownloader(torrentBytes()))

        val result = preparer.prepare(
            endpoint = CloudDriveEndpoint("http://127.0.0.1:19798", "token"),
            item = RssFeedItem("Episode 01", "guid-1", "magnet:?xt=urn:btih:abc", null),
            itemKey = "guid-1",
            submissionUrl = "magnet:?xt=urn:btih:abc",
            inboxPath = "/Downloads",
        )

        assertTrue(result is Result.Success)
        val prepared = (result as Result.Success).data
        assertEquals("magnet:?xt=urn:btih:abc", prepared.originalUrl)
        assertEquals("magnet:?xt=urn:btih:abc", prepared.submissionUrl)
        assertEquals(null, prepared.stagedTorrentPath)
        assertEquals(emptyList<String>(), cloudDrive.listedPaths)
        assertEquals(emptyList<String>(), cloudDrive.uploadedPaths)
    }

    @Test
    fun `prepare converts torrent to magnet and stages original torrent in CloudDrive`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient(
            entriesByPath = mapOf("/Downloads" to emptyList())
        )
        val torrentBytes = torrentBytes()
        val preparer = CloudDriveRssSubmissionPreparer(cloudDrive, FakeTorrentDownloader(torrentBytes))

        val result = preparer.prepare(
            endpoint = CloudDriveEndpoint("http://127.0.0.1:19798", "token"),
            item = RssFeedItem("Episode 01", "guid-1", null, "https://example.test/episode-01.torrent"),
            itemKey = "guid-1",
            submissionUrl = "https://example.test/episode-01.torrent",
            inboxPath = "/Downloads",
        )

        assertTrue(result is Result.Success)
        val prepared = (result as Result.Success).data
        assertEquals("https://example.test/episode-01.torrent", prepared.originalUrl)
        assertEquals(expectedMagnet(torrentBytes), prepared.submissionUrl)
        assertEquals("/Downloads/.miruplay-torrents/episode-01.torrent", prepared.stagedTorrentPath)
        assertEquals(listOf("/Downloads"), cloudDrive.listedPaths)
        assertEquals(listOf("/Downloads/.miruplay-torrents"), cloudDrive.createdFolders)
        assertEquals(listOf("/Downloads/.miruplay-torrents/episode-01.torrent"), cloudDrive.uploadedPaths)
    }

    private class FakeTorrentDownloader(
        private val bytes: ByteArray,
    ) : RssTorrentDownloader {
        override fun configureProxy(enabled: Boolean, host: String, port: Int) = Unit

        override suspend fun download(url: String, title: String, keyPrefix: String): Result<DownloadedTorrentFile> {
            val file = File.createTempFile("miruplay-rss-test", ".torrent")
            file.writeBytes(bytes)
            return Result.success(DownloadedTorrentFile(file, "episode-01.torrent"))
        }
    }

    private class FakeCloudDriveClient(
        private val entriesByPath: Map<String, List<CloudDriveFileInfo>> = emptyMap(),
    ) : CloudDriveClient {
        val listedPaths = mutableListOf<String>()
        val createdFolders = mutableListOf<String>()
        val uploadedPaths = mutableListOf<String>()

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

        override suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun uploadFile(endpoint: CloudDriveEndpoint, localFile: File, parentPath: String, remoteFileName: String): Result<String> {
            val uploadedPath = "$parentPath/$remoteFileName"
            uploadedPaths += uploadedPath
            return Result.success(uploadedPath)
        }

        override suspend fun listFolder(endpoint: CloudDriveEndpoint, path: String, forceRefresh: Boolean): Result<List<CloudDriveFileInfo>> {
            listedPaths += path
            return Result.success(entriesByPath[path].orEmpty())
        }

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> {
            createdFolders += "$parentPath/$folderName"
            return Result.success(Unit)
        }

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> =
            Result.success(Unit)
    }
}

internal fun torrentBytes(): ByteArray {
    val info = "d4:name8:Test.mkv12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrste"
    return "d8:announce14:http://tracker4:info${info}e".toByteArray()
}

internal fun expectedMagnet(torrentBytes: ByteArray): String {
    val info = "d4:name8:Test.mkv12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrste".toByteArray()
    val expectedHash = MessageDigest.getInstance("SHA-1")
        .digest(info)
        .joinToString("") { "%02x".format(it) }
    return "magnet:?xt=urn:btih:$expectedHash&dn=Test.mkv&tr=http%3A%2F%2Ftracker"
}
