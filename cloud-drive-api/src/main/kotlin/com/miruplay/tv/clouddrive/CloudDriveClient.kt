package com.miruplay.tv.clouddrive

import com.miruplay.tv.core.common.Result
import java.io.File

data class CloudDriveEndpoint(
    val url: String,
    val token: String? = null
)

data class CloudDriveFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L
)

data class CloudDriveLoginResult(
    val token: String
)

data class CloudDriveTokenInfo(
    val rootDir: String,
    val friendlyName: String,
    val allowList: Boolean,
    val allowCreateFolder: Boolean,
    val allowCreateFile: Boolean,
    val allowWrite: Boolean,
    val allowMove: Boolean,
    val allowAddOfflineDownload: Boolean
)

interface CloudDriveClient {
    suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult>
    suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo>
    suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit>
    suspend fun uploadFile(endpoint: CloudDriveEndpoint, localFile: File, parentPath: String, remoteFileName: String): Result<String>
    suspend fun listFolder(endpoint: CloudDriveEndpoint, path: String, forceRefresh: Boolean = false): Result<List<CloudDriveFileInfo>>
    suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit>
    suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit>
}
