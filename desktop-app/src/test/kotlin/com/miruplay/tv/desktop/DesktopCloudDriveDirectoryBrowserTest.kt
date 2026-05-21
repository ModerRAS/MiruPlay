package com.miruplay.tv.desktop

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCloudDriveDirectoryBrowserTest {
    @Test
    fun `prepare verifies token and scopes initial path to token root`() = runBlocking {
        val client = FakeCloudDriveClient(rootDir = "/CloudRoot")

        val result = prepareDesktopCloudDriveDirectoryBrowser(
            client = client,
            target = DesktopCloudDriveDirectoryTarget.INBOX,
            endpointUrl = " http://127.0.0.1:19798 ",
            token = " api-token ",
            initialPath = "/Outside/Inbox",
        )

        val state = (result as Result.Success).data
        assertEquals(listOf("http://127.0.0.1:19798" to "api-token"), client.tokenInfoRequests)
        assertTrue(state.open)
        assertEquals(DesktopCloudDriveDirectoryTarget.INBOX, state.target)
        assertEquals("http://127.0.0.1:19798", state.endpointUrl)
        assertEquals("api-token", state.token)
        assertEquals("/CloudRoot", state.rootPath)
        assertEquals("/CloudRoot", state.path)
        assertEquals("/CloudRoot", state.displayPath)
        assertNull(state.parentPath)
        assertTrue(state.isLoading)
    }

    @Test
    fun `load lists scoped directory and keeps visible folders only`() = runBlocking {
        val client = FakeCloudDriveClient(
            files = listOf(
                CloudDriveFileInfo("Episode 01.mkv", "/CloudRoot/Anime/Episode 01.mkv", isDirectory = false),
                CloudDriveFileInfo(".cache", "/CloudRoot/Anime/.cache", isDirectory = true),
                CloudDriveFileInfo("Season B", "/CloudRoot/Anime/Season B", isDirectory = true),
                CloudDriveFileInfo("season a", "/CloudRoot/Anime/season a", isDirectory = true),
            ),
        )
        val state = DesktopCloudDriveDirectoryBrowserState(
            open = true,
            target = DesktopCloudDriveDirectoryTarget.LIBRARY,
            endpointUrl = "http://127.0.0.1:19798",
            token = "api-token",
            rootPath = "/CloudRoot",
        )

        val result = loadDesktopCloudDriveDirectory(
            client = client,
            state = state,
            requestedPath = "/CloudRoot/Anime",
        )

        val loaded = (result as Result.Success).data
        assertEquals(listOf(CloudDriveEndpoint("http://127.0.0.1:19798", "api-token") to "/CloudRoot/Anime"), client.listRequests)
        assertEquals("/CloudRoot/Anime", loaded.path)
        assertEquals("/CloudRoot", loaded.parentPath)
        assertEquals(listOf("season a", "Season B"), loaded.entries.map { it.name })
        assertEquals(listOf("/CloudRoot/Anime/season a", "/CloudRoot/Anime/Season B"), loaded.entries.map { it.path })
        assertEquals(false, loaded.isLoading)
        assertNull(loaded.message)
    }

    @Test
    fun `load clamps outside requests before listing`() = runBlocking {
        val client = FakeCloudDriveClient()
        val state = DesktopCloudDriveDirectoryBrowserState(
            open = true,
            endpointUrl = "http://127.0.0.1:19798",
            token = "api-token",
            rootPath = "/CloudRoot",
        )

        val result = loadDesktopCloudDriveDirectory(client, state, "/Outside")

        val loaded = (result as Result.Success).data
        assertEquals("/CloudRoot", loaded.path)
        assertEquals(listOf(CloudDriveEndpoint("http://127.0.0.1:19798", "api-token") to "/CloudRoot"), client.listRequests)
    }

    @Test
    fun `load returns listing errors without mutating them`() = runBlocking {
        val failure = AppError.MediaSourceError.AuthenticationFailed("CloudDrive2")
        val client = FakeCloudDriveClient(listFailure = failure)
        val state = DesktopCloudDriveDirectoryBrowserState(
            open = true,
            endpointUrl = "http://127.0.0.1:19798",
            token = "api-token",
            rootPath = "/CloudRoot",
        )

        val result = loadDesktopCloudDriveDirectory(client, state, "/CloudRoot")

        assertEquals(failure, (result as Result.Error).error)
    }

    @Test
    fun `select normalizes selected desktop CloudDrive path`() {
        val selection = selectDesktopCloudDriveDirectory(
            target = DesktopCloudDriveDirectoryTarget.LIBRARY,
            path = "Anime\\Season 1\\",
        )

        assertEquals(DesktopCloudDriveDirectoryTarget.LIBRARY, selection.target)
        assertEquals("/Anime/Season 1", selection.path)
        assertEquals("已选择 选择媒体库目录：/Anime/Season 1", selection.status)
    }

    private class FakeCloudDriveClient(
        private val rootDir: String = "/CloudRoot",
        private val files: List<CloudDriveFileInfo> = emptyList(),
        private val listFailure: AppError? = null,
    ) : CloudDriveClient {
        val tokenInfoRequests = mutableListOf<Pair<String, String>>()
        val listRequests = mutableListOf<Pair<CloudDriveEndpoint, String>>()

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> =
            Result.success(CloudDriveLoginResult("token"))

        override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
            tokenInfoRequests += endpointUrl to token
            return Result.success(
                CloudDriveTokenInfo(
                    rootDir = rootDir,
                    friendlyName = "Smoke",
                    allowList = true,
                    allowCreateFolder = true,
                    allowCreateFile = true,
                    allowWrite = true,
                    allowMove = true,
                    allowAddOfflineDownload = true,
                ),
            )
        }

        override suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit> =
            Result.success(Unit)

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
            listRequests += endpoint to path
            return listFailure?.let { Result.failure(it) } ?: Result.success(files)
        }

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> =
            Result.success(Unit)
    }
}
