package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WebControlCloudDriveDirectoriesTest {
    @Test
    fun `directory browser state maps to WebUI dto`() {
        val dto = CloudDriveDirectoryBrowserState(
            path = "/CloudRoot/Inbox",
            displayPath = "CloudRoot / Inbox",
            parentPath = "/CloudRoot",
            entries = listOf(
                CloudDriveDirectoryEntry(
                    name = "Season 01",
                    path = "/CloudRoot/Inbox/Season 01",
                ),
                CloudDriveDirectoryEntry(
                    name = "Season 02",
                    path = "/CloudRoot/Inbox/Season 02",
                ),
            ),
        ).toWebControlDirectoryDto()

        assertEquals("/CloudRoot/Inbox", dto.path)
        assertEquals("CloudRoot / Inbox", dto.displayPath)
        assertEquals("/CloudRoot", dto.parentPath)
        assertEquals(2, dto.entries.size)
        assertEquals("Season 01", dto.entries[0].name)
        assertEquals("/CloudRoot/Inbox/Season 01", dto.entries[0].path)
        assertEquals(true, dto.entries[0].canRead)
        assertEquals("Season 02", dto.entries[1].name)
        assertEquals("/CloudRoot/Inbox/Season 02", dto.entries[1].path)
        assertEquals(true, dto.entries[1].canRead)
    }

    @Test
    fun `browse helper resolves fallback endpoint and token then maps directories`() = runBlocking {
        val client = FakeCloudDriveClient(
            rootDir = "/CloudRoot",
            files = listOf(
                CloudDriveFileInfo(name = "Inbox", path = "/CloudRoot/Inbox", isDirectory = true),
                CloudDriveFileInfo(name = "Episode.mkv", path = "/CloudRoot/Episode.mkv", isDirectory = false),
            ),
        )

        val dto = browseWebControlCloudDriveDirectory(
            client = client,
            endpointUrl = " ",
            fallbackEndpointUrl = { " https://cloud.example.test " },
            token = " token-1 ",
            path = "/CloudRoot",
        ).getOrNull()!!

        assertEquals("https://cloud.example.test", client.tokenInfoEndpoint)
        assertEquals("token-1", client.tokenInfoToken)
        assertEquals(CloudDriveEndpoint("https://cloud.example.test", "token-1"), client.listEndpoint)
        assertEquals("/CloudRoot", client.listPath)
        assertEquals("/CloudRoot", dto.path)
        assertEquals(1, dto.entries.size)
        assertEquals("Inbox", dto.entries.single().name)
    }

    @Test
    fun `browse helper requires endpoint and token`() = runBlocking {
        val client = FakeCloudDriveClient()

        assertEquals(
            "请先填写 CloudDrive2 地址",
            runCatching {
                browseWebControlCloudDriveDirectory(
                    client = client,
                    endpointUrl = "",
                    fallbackEndpointUrl = { "" },
                    token = "token",
                    path = "/",
                )
            }.exceptionOrNull()?.message,
        )
        assertEquals(
            "请先登录并保存密码，或验证并保存 Key。",
            runCatching {
                browseWebControlCloudDriveDirectory(
                    client = client,
                    endpointUrl = "https://cloud.example.test",
                    fallbackEndpointUrl = { "" },
                    token = " ",
                    path = "/",
                )
            }.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `browse helper does not load fallback endpoint when request supplies endpoint`() = runBlocking {
        val client = FakeCloudDriveClient(rootDir = "/CloudRoot")

        browseWebControlCloudDriveDirectory(
            client = client,
            endpointUrl = "https://request.example.test",
            fallbackEndpointUrl = { error("Fallback endpoint should not be loaded") },
            token = "token-1",
            path = "/CloudRoot",
        )

        assertEquals("https://request.example.test", client.tokenInfoEndpoint)
    }

    private class FakeCloudDriveClient(
        private val rootDir: String = "/CloudRoot",
        private val files: List<CloudDriveFileInfo> = emptyList(),
    ) : CloudDriveClient {
        var tokenInfoEndpoint: String? = null
        var tokenInfoToken: String? = null
        var listEndpoint: CloudDriveEndpoint? = null
        var listPath: String? = null

        override suspend fun login(
            endpointUrl: String,
            username: String,
            password: String,
        ): Result<CloudDriveLoginResult> =
            error("login should not be called")

        override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
            tokenInfoEndpoint = endpointUrl
            tokenInfoToken = token
            return Result.success(
                CloudDriveTokenInfo(
                    rootDir = rootDir,
                    friendlyName = "Test",
                    allowList = true,
                    allowCreateFolder = true,
                    allowCreateFile = true,
                    allowWrite = true,
                    allowMove = true,
                    allowAddOfflineDownload = true,
                ),
            )
        }

        override suspend fun addOfflineFiles(
            endpoint: CloudDriveEndpoint,
            urls: List<String>,
            targetFolder: String,
        ): Result<Unit> =
            error("addOfflineFiles should not be called")

        override suspend fun uploadFile(
            endpoint: CloudDriveEndpoint,
            localFile: File,
            parentPath: String,
            remoteFileName: String,
        ): Result<String> =
            error("uploadFile should not be called")

        override suspend fun listFolder(
            endpoint: CloudDriveEndpoint,
            path: String,
            forceRefresh: Boolean,
        ): Result<List<CloudDriveFileInfo>> {
            listEndpoint = endpoint
            listPath = path
            return Result.success(files)
        }

        override suspend fun createFolder(
            endpoint: CloudDriveEndpoint,
            parentPath: String,
            folderName: String,
        ): Result<Unit> =
            error("createFolder should not be called")

        override suspend fun moveFiles(
            endpoint: CloudDriveEndpoint,
            paths: List<String>,
            destinationPath: String,
        ): Result<Unit> =
            error("moveFiles should not be called")
    }
}
