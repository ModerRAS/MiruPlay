package com.miruplay.tv.clouddrive

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudDriveLiveSmokeTest {
    @Test
    fun `parseCloudDriveLiveSmokeOptions accepts endpoint token path and report`() {
        val options = parseCloudDriveLiveSmokeOptions(
            arrayOf(
                "--endpoint",
                "http://127.0.0.1:19798",
                "--token",
                "secret",
                "--path",
                "/Downloads",
                "--report-path",
                "build/cloud-drive-smoke/report.json",
                "--max-preview",
                "2",
            )
        )

        assertEquals("http://127.0.0.1:19798", options.endpoint)
        assertEquals("secret", options.token)
        assertEquals("/Downloads", options.path)
        assertEquals("build/cloud-drive-smoke/report.json", options.reportPath)
        assertEquals(2, options.maxPreviewItems)
    }

    @Test
    fun `parseCloudDriveLiveSmokeOptions defaults path to root`() {
        val options = parseCloudDriveLiveSmokeOptions(
            arrayOf(
                "--endpoint",
                "http://127.0.0.1:19798",
                "--token",
                "secret",
            )
        )

        assertEquals("/", options.path)
        assertEquals(null, options.reportPath)
        assertEquals(10, options.maxPreviewItems)
    }

    @Test
    fun `parseCloudDriveLiveSmokeOptions rejects missing token`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseCloudDriveLiveSmokeOptions(arrayOf("--endpoint", "http://127.0.0.1:19798"))
        }
    }

    @Test
    fun `runCloudDriveLiveSmoke verifies token and lists folder evidence`() = runBlocking {
        val client = FakeCloudDriveClient(
            files = listOf(
                CloudDriveFileInfo("Season 1", "/Anime/Season 1", isDirectory = true),
                CloudDriveFileInfo("Episode 01.mkv", "/Anime/Episode 01.mkv", isDirectory = false, size = 2048L),
                CloudDriveFileInfo("Episode 02.mkv", "/Anime/Episode 02.mkv", isDirectory = false, size = 4096L),
            )
        )

        val result = runCloudDriveLiveSmoke(
            options = CloudDriveLiveSmokeOptions(
                endpoint = "http://127.0.0.1:19798",
                token = "api-token",
                path = "/Anime",
                maxPreviewItems = 2,
            ),
            client = client,
        )

        assertTrue(result is Result.Success)
        val report = (result as Result.Success).data
        assertEquals("http://127.0.0.1:19798", client.tokenInfoRequests.single())
        assertEquals(CloudDriveEndpoint("http://127.0.0.1:19798", "api-token"), client.listRequests.single().first)
        assertEquals("/Anime", client.listRequests.single().second)
        assertEquals("desktop-token", report.friendlyName)
        assertEquals("/Anime", report.rootDir)
        assertEquals("/Anime", report.path)
        assertEquals(3, report.itemCount)
        assertEquals(1, report.directoryCount)
        assertEquals(2, report.fileCount)
        assertEquals(2, report.previewItems.size)
        assertEquals("Season 1", report.previewItems.first().name)
        assertTrue(report.permissions.allowAddOfflineDownload)
    }

    @Test
    fun `report json captures live evidence without leaking token`() {
        val report = CloudDriveLiveSmokeReport(
            endpoint = "http://127.0.0.1:19798",
            path = "/Anime",
            friendlyName = "desktop-token",
            rootDir = "/Anime",
            permissions = CloudDriveLiveSmokePermissions(
                allowList = true,
                allowCreateFolder = true,
                allowCreateFile = true,
                allowWrite = true,
                allowMove = true,
                allowAddOfflineDownload = true,
            ),
            itemCount = 2,
            directoryCount = 1,
            fileCount = 1,
            previewItems = listOf(
                CloudDriveLiveSmokeItem(
                    name = "Episode 01.mkv",
                    path = "/Anime/Episode 01.mkv",
                    isDirectory = false,
                    size = 2048L,
                )
            ),
        )

        val json = buildCloudDriveLiveSmokeReportJson(report)

        assertFalse(json.contains("secret-token"))
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals("http://127.0.0.1:19798", root.getValue("endpoint").jsonPrimitive.content)
        assertEquals("/Anime", root.getValue("path").jsonPrimitive.content)
        assertEquals(2, root.getValue("itemCount").jsonPrimitive.int)
        assertEquals(1, root.getValue("directoryCount").jsonPrimitive.int)
        assertEquals(1, root.getValue("fileCount").jsonPrimitive.int)
        val tokenInfo = root.getValue("tokenInfo").jsonObject
        assertEquals("desktop-token", tokenInfo.getValue("friendlyName").jsonPrimitive.content)
        assertTrue(
            tokenInfo
                .getValue("permissions")
                .jsonObject
                .getValue("allowAddOfflineDownload")
                .jsonPrimitive
                .boolean
        )
        val preview = root.getValue("previewItems").jsonArray.single().jsonObject
        assertEquals("Episode 01.mkv", preview.getValue("name").jsonPrimitive.content)
        assertEquals("/Anime/Episode 01.mkv", preview.getValue("path").jsonPrimitive.content)
    }

    private class FakeCloudDriveClient(
        private val files: List<CloudDriveFileInfo>,
    ) : CloudDriveClient {
        val tokenInfoRequests = mutableListOf<String>()
        val listRequests = mutableListOf<Pair<CloudDriveEndpoint, String>>()

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> =
            Result.success(CloudDriveLoginResult("login-token"))

        override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
            tokenInfoRequests += endpointUrl
            return Result.success(
                CloudDriveTokenInfo(
                    rootDir = "/Anime",
                    friendlyName = "desktop-token",
                    allowList = true,
                    allowCreateFolder = true,
                    allowCreateFile = true,
                    allowWrite = true,
                    allowMove = true,
                    allowAddOfflineDownload = true,
                )
            )
        }

        override suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit> =
            Result.success(Unit)

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
            listRequests += endpoint to path
            return Result.success(files)
        }

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> =
            Result.success(Unit)
    }
}
