package com.miruplay.tv.clouddrive

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GrpcCloudDriveClientTest {
    @Test
    fun `delegates login to provided cloud drive client`() = runBlocking {
        val delegate = RecordingCloudDriveClient()
        val client = GrpcCloudDriveClient(delegate)

        val result = client.login(
            endpointUrl = "http://127.0.0.1:19798",
            username = "alice",
            password = "secret",
        )

        assertTrue(result is Result.Success)
        assertEquals("delegated-token", (result as Result.Success).data.token)
        assertEquals(
            listOf(
                LoginCall(
                    endpointUrl = "http://127.0.0.1:19798",
                    username = "alice",
                    password = "secret",
                )
            ),
            delegate.loginCalls,
        )
    }

    @Test
    fun `delegates list folder call to provided cloud drive client`() = runBlocking {
        val delegate = RecordingCloudDriveClient()
        val client = GrpcCloudDriveClient(delegate)
        val endpoint = CloudDriveEndpoint(url = "http://127.0.0.1:19798", token = "api-token")

        val result = client.listFolder(
            endpoint = endpoint,
            path = "/Anime",
            forceRefresh = true,
        )

        assertTrue(result is Result.Success)
        val files = (result as Result.Success).data
        assertEquals(listOf("Episode 01.mkv"), files.map { it.name })
        assertEquals(
            listOf(
                ListFolderCall(
                    endpoint = endpoint,
                    path = "/Anime",
                    forceRefresh = true,
                )
            ),
            delegate.listFolderCalls,
        )
    }
}

private data class LoginCall(
    val endpointUrl: String,
    val username: String,
    val password: String,
)

private data class ListFolderCall(
    val endpoint: CloudDriveEndpoint,
    val path: String,
    val forceRefresh: Boolean,
)

private class RecordingCloudDriveClient : CloudDriveClient {
    val loginCalls = mutableListOf<LoginCall>()
    val listFolderCalls = mutableListOf<ListFolderCall>()

    override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> {
        loginCalls += LoginCall(endpointUrl, username, password)
        return Result.success(CloudDriveLoginResult(token = "delegated-token"))
    }

    override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
        Result.success(
            CloudDriveTokenInfo(
                rootDir = "/",
                friendlyName = "delegate",
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
    ): Result<Unit> = Result.success(Unit)

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
        listFolderCalls += ListFolderCall(endpoint, path, forceRefresh)
        return Result.success(
            listOf(
                CloudDriveFileInfo(
                    name = "Episode 01.mkv",
                    path = "/Anime/Episode 01.mkv",
                    isDirectory = false,
                    size = 1024L,
                )
            )
        )
    }

    override suspend fun createFolder(
        endpoint: CloudDriveEndpoint,
        parentPath: String,
        folderName: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun moveFiles(
        endpoint: CloudDriveEndpoint,
        paths: List<String>,
        destinationPath: String,
    ): Result<Unit> = Result.success(Unit)
}
