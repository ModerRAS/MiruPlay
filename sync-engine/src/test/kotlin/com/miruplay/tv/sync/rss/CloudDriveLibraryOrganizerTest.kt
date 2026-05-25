package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CloudDriveLibraryOrganizerTest {
    @Test
    fun `organizer uses filename parser when moving downloaded files`() = runBlocking {
        val cloudDriveClient = FakeCloudDriveClient(
            files = listOf(
                CloudDriveFileInfo(
                    name = "raw-download.mp4",
                    path = "/115open/下载/Ani/raw-download.mp4",
                    isDirectory = false
                )
            )
        )
        val parser = RecordingFilenameParser(
            FilenameParseResult(
                title = "百鬼夜行抄",
                season = 1,
                episode = 5
            )
        )
        val organizer = CloudDriveLibraryOrganizer(cloudDriveClient, AndroidCloudDriveVideoClassifier(parser))

        val result = organizer.organize(
            endpoint = CloudDriveEndpoint("http://cloud.test", "token"),
            inboxPath = "/115open/下载/Ani",
            libraryPath = "/115open/影音/动漫"
        )

        assertEquals(1, (result as Result.Success).data)
        assertEquals("raw-download", parser.lastFilename)
        assertEquals(
            listOf("/115open/影音/动漫/百鬼夜行抄/Season 1"),
            cloudDriveClient.moveDestinations
        )
    }

    private class RecordingFilenameParser(
        private val result: FilenameParseResult
    ) : FilenameMetadataParser {
        var lastFilename: String? = null

        override fun parse(filename: String, maxLength: Int): FilenameParseResult {
            lastFilename = filename
            return result
        }
    }

    private class FakeCloudDriveClient(
        private val files: List<CloudDriveFileInfo>
    ) : CloudDriveClient {
        val moveDestinations = mutableListOf<String>()

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
                    allowAddOfflineDownload = true
                )
            )

        override suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun uploadFile(
            endpoint: CloudDriveEndpoint,
            localFile: File,
            parentPath: String,
            remoteFileName: String
        ): Result<String> = Result.success("$parentPath/$remoteFileName")

        override suspend fun listFolder(
            endpoint: CloudDriveEndpoint,
            path: String,
            forceRefresh: Boolean
        ): Result<List<CloudDriveFileInfo>> =
            Result.success(if (path == "/115open/下载/Ani") files else emptyList())

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> {
            moveDestinations += destinationPath
            return Result.success(Unit)
        }
    }
}
