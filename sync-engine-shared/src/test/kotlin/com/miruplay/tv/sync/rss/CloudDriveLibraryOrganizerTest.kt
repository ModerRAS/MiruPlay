package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudDriveLibraryOrganizerTest {
    @Test
    fun `organizer uses injected classifier for move destination`() = runBlocking {
        val cloudDrive = FakeCloudDriveClient(
            entriesByPath = mapOf(
                "/Downloads" to listOf(
                    CloudDriveFileInfo(
                        name = "raw-download.mkv",
                        path = "/Downloads/raw-download.mkv",
                        isDirectory = false,
                    )
                ),
                "/Library" to emptyList(),
                "/Library/Classifier Show" to emptyList(),
            )
        )
        val organizer = CloudDriveLibraryOrganizer(
            cloudDriveClient = cloudDrive,
            classifier = CloudDriveVideoClassifier { file ->
                assertEquals("raw-download.mkv", file.name)
                CloudDriveVideoClassification(showName = "Classifier Show", seasonNumber = 2)
            },
        )

        val result = organizer.organize(
            endpoint = CloudDriveEndpoint("http://127.0.0.1:19798", "token"),
            inboxPath = "/Downloads",
            libraryPath = "/Library",
        )

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data)
        assertEquals(listOf("/Library/Classifier Show", "/Library/Classifier Show/Season 2"), cloudDrive.createdFolders)
        assertEquals(listOf(listOf("/Downloads/raw-download.mkv") to "/Library/Classifier Show/Season 2"), cloudDrive.moves)
    }

    @Test
    fun `organizer propagates nested listing failure instead of ignoring subtree`() = runBlocking {
        val failure = AppError.NetworkError.ServerUnreachable("CloudDrive2")
        val cloudDrive = FakeCloudDriveClient(
            entriesByPath = mapOf(
                "/Downloads" to listOf(
                    CloudDriveFileInfo(
                        name = "Nested",
                        path = "/Downloads/Nested",
                        isDirectory = true,
                    )
                ),
            ),
            listFailures = mapOf("/Downloads/Nested" to failure),
        )
        val organizer = CloudDriveLibraryOrganizer(cloudDrive)

        val result = organizer.organize(
            endpoint = CloudDriveEndpoint("http://127.0.0.1:19798", "token"),
            inboxPath = "/Downloads",
            libraryPath = "/Library",
        )

        assertEquals(Result.failure(failure), result)
        assertEquals(emptyList<Pair<List<String>, String>>(), cloudDrive.moves)
    }

    @Test
    fun `organizer propagates folder creation failure before moving video`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("CloudDrive2", "create failed")
        val cloudDrive = FakeCloudDriveClient(
            entriesByPath = mapOf(
                "/Downloads" to listOf(
                    CloudDriveFileInfo(
                        name = "raw-download.mkv",
                        path = "/Downloads/raw-download.mkv",
                        isDirectory = false,
                    )
                ),
                "/Library" to emptyList(),
            ),
            createFailures = mapOf("/Library/raw-download" to failure),
        )
        val organizer = CloudDriveLibraryOrganizer(cloudDrive)

        val result = organizer.organize(
            endpoint = CloudDriveEndpoint("http://127.0.0.1:19798", "token"),
            inboxPath = "/Downloads",
            libraryPath = "/Library",
        )

        assertEquals(Result.failure(failure), result)
        assertEquals(emptyList<Pair<List<String>, String>>(), cloudDrive.moves)
    }

    @Test
    fun `organizer propagates move failure instead of reporting zero moves`() = runBlocking {
        val failure = AppError.SyncError.WriteFailed("CloudDrive2", "move failed")
        val cloudDrive = FakeCloudDriveClient(
            entriesByPath = mapOf(
                "/Downloads" to listOf(
                    CloudDriveFileInfo(
                        name = "raw-download.mkv",
                        path = "/Downloads/raw-download.mkv",
                        isDirectory = false,
                    )
                ),
                "/Library" to listOf(
                    CloudDriveFileInfo(
                        name = "raw-download",
                        path = "/Library/raw-download",
                        isDirectory = true,
                    )
                ),
                "/Library/raw-download" to listOf(
                    CloudDriveFileInfo(
                        name = "Season 1",
                        path = "/Library/raw-download/Season 1",
                        isDirectory = true,
                    )
                ),
            ),
            moveFailure = failure,
        )
        val organizer = CloudDriveLibraryOrganizer(cloudDrive)

        val result = organizer.organize(
            endpoint = CloudDriveEndpoint("http://127.0.0.1:19798", "token"),
            inboxPath = "/Downloads",
            libraryPath = "/Library",
        )

        assertEquals(Result.failure(failure), result)
        assertEquals(listOf(listOf("/Downloads/raw-download.mkv") to "/Library/raw-download/Season 1"), cloudDrive.moves)
    }

    private class FakeCloudDriveClient(
        private val entriesByPath: Map<String, List<CloudDriveFileInfo>>,
        private val listFailures: Map<String, AppError> = emptyMap(),
        private val createFailures: Map<String, AppError> = emptyMap(),
        private val moveFailure: AppError? = null,
    ) : CloudDriveClient {
        val createdFolders = mutableListOf<String>()
        val moves = mutableListOf<Pair<List<String>, String>>()

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

        override suspend fun uploadFile(endpoint: CloudDriveEndpoint, localFile: File, parentPath: String, remoteFileName: String): Result<String> =
            Result.success("$parentPath/$remoteFileName")

        override suspend fun listFolder(endpoint: CloudDriveEndpoint, path: String, forceRefresh: Boolean): Result<List<CloudDriveFileInfo>> {
            listFailures[path]?.let { return Result.failure(it) }
            return Result.success(entriesByPath[path].orEmpty())
        }

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> {
            val path = "$parentPath/$folderName"
            createdFolders += path
            createFailures[path]?.let { return Result.failure(it) }
            return Result.success(Unit)
        }

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> {
            moves += paths to destinationPath
            moveFailure?.let { return Result.failure(it) }
            return Result.success(Unit)
        }
    }
}
