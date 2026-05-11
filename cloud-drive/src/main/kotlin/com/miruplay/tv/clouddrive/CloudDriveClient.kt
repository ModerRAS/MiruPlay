package com.miruplay.tv.clouddrive

import clouddrive.CloudDriveFileSrvGrpc
import clouddrive.Clouddrive
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
    val allowMove: Boolean,
    val allowAddOfflineDownload: Boolean
)

interface CloudDriveClient {
    suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult>
    suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo>
    suspend fun addOfflineFiles(endpoint: CloudDriveEndpoint, urls: List<String>, targetFolder: String): Result<Unit>
    suspend fun listFolder(endpoint: CloudDriveEndpoint, path: String, forceRefresh: Boolean = false): Result<List<CloudDriveFileInfo>>
    suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit>
    suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit>
}

@Singleton
class GrpcCloudDriveClient @Inject constructor() : CloudDriveClient {
    override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> =
        withGrpc(endpointUrl) { channel ->
            val stub = CloudDriveFileSrvGrpc.newBlockingStub(channel)
            val response = stub.getToken(
                Clouddrive.GetTokenRequest.newBuilder()
                    .setUserName(username)
                    .setPassword(password)
                    .build()
            )
            if (!response.success) {
                Result.failure(AppError.MediaSourceError.AuthenticationFailed(response.errorMessage.ifBlank { "CloudDrive2" }))
            } else {
                Result.success(CloudDriveLoginResult(response.token))
            }
        }

    override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
        withGrpc(endpointUrl) { channel ->
            val response = CloudDriveFileSrvGrpc.newBlockingStub(channel).getApiTokenInfo(
                Clouddrive.StringValue.newBuilder()
                    .setValue(token)
                    .build()
            )
            Result.success(
                CloudDriveTokenInfo(
                    rootDir = response.rootDir,
                    friendlyName = response.friendlyName,
                    allowList = response.permissions.allowList,
                    allowCreateFolder = response.permissions.allowCreateFolder,
                    allowMove = response.permissions.allowMove,
                    allowAddOfflineDownload = response.permissions.allowAddOfflineDownload
                )
            )
        }

    override suspend fun addOfflineFiles(
        endpoint: CloudDriveEndpoint,
        urls: List<String>,
        targetFolder: String
    ): Result<Unit> = withAuthenticatedStub(endpoint) { stub ->
        val response = stub.addOfflineFiles(
            Clouddrive.AddOfflineFileRequest.newBuilder()
                .setUrls(urls.joinToString("\n"))
                .setToFolder(targetFolder)
                .setCheckFolderAfterSecs(30L)
                .build()
        )
        response.asUnitResult("提交离线下载失败")
    }

    override suspend fun listFolder(
        endpoint: CloudDriveEndpoint,
        path: String,
        forceRefresh: Boolean
    ): Result<List<CloudDriveFileInfo>> = withAuthenticatedStub(endpoint) { stub ->
        val request = Clouddrive.ListSubFileRequest.newBuilder()
            .setPath(path)
            .setForceRefresh(forceRefresh)
            .build()
        val files = mutableListOf<CloudDriveFileInfo>()
        val replies = stub.getSubFiles(request)
        while (replies.hasNext()) {
            replies.next().subFilesList.forEach { file ->
                files += CloudDriveFileInfo(
                    name = file.name.ifBlank { file.fullPathName.substringAfterLast('/') },
                    path = file.fullPathName,
                    isDirectory = file.isDirectory,
                    size = file.size
                )
            }
        }
        Result.success(files)
    }

    override suspend fun createFolder(
        endpoint: CloudDriveEndpoint,
        parentPath: String,
        folderName: String
    ): Result<Unit> = withAuthenticatedStub(endpoint) { stub ->
        val response = stub.createFolder(
            Clouddrive.CreateFolderRequest.newBuilder()
                .setParentPath(parentPath)
                .setFolderName(folderName)
                .build()
        )
        response.result.asUnitResult("创建目录失败")
    }

    override suspend fun moveFiles(
        endpoint: CloudDriveEndpoint,
        paths: List<String>,
        destinationPath: String
    ): Result<Unit> = withAuthenticatedStub(endpoint) { stub ->
        if (paths.isEmpty()) return@withAuthenticatedStub Result.success(Unit)
        val response = stub.moveFile(
            Clouddrive.MoveFileRequest.newBuilder()
                .addAllTheFilePaths(paths)
                .setDestPath(destinationPath)
                .setConflictPolicy(Clouddrive.MoveFileRequest.ConflictPolicy.Rename)
                .setHandleConflictRecursively(true)
                .build()
        )
        response.asUnitResult("移动文件失败")
    }

    private suspend fun <T> withAuthenticatedStub(
        endpoint: CloudDriveEndpoint,
        block: (CloudDriveFileSrvGrpc.CloudDriveFileSrvBlockingStub) -> Result<T>
    ): Result<T> {
        val token = endpoint.token?.takeIf { it.isNotBlank() }
            ?: return Result.failure(AppError.MediaSourceError.AuthenticationFailed("CloudDrive2"))
        return withGrpc(endpoint.url) { channel ->
            val metadata = Metadata().apply {
                put(AUTHORIZATION_HEADER, "Bearer $token")
            }
            val intercepted = ClientInterceptors.intercept(
                channel,
                MetadataUtils.newAttachHeadersInterceptor(metadata)
            )
            val stub = CloudDriveFileSrvGrpc.newBlockingStub(intercepted)
            block(stub)
        }
    }

    private suspend fun <T> withGrpc(
        endpointUrl: String,
        block: (ManagedChannel) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val channel = try {
            buildChannel(endpointUrl)
        } catch (e: Exception) {
            return@withContext Result.failure(AppError.NetworkError.ServerUnreachable(endpointUrl))
        }
        try {
            block(channel)
        } catch (e: Exception) {
            Result.failure(AppError.NetworkError.ServerUnreachable(e.message ?: endpointUrl))
        } finally {
            channel.shutdownNow()
            channel.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    private fun buildChannel(endpointUrl: String): ManagedChannel {
        val uri = URI(endpointUrl)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "CloudDrive endpoint must be http(s)" }
        val host = uri.host ?: error("CloudDrive endpoint host is missing")
        val port = when {
            uri.port > 0 -> uri.port
            scheme == "https" -> 443
            else -> 80
        }
        return OkHttpChannelBuilder.forAddress(host, port)
            .apply {
                if (scheme == "https") {
                    useTransportSecurity()
                } else {
                    usePlaintext()
                }
            }
            .build()
    }

    private fun Clouddrive.FileOperationResult.asUnitResult(fallback: String): Result<Unit> =
        if (success) {
            Result.success(Unit)
        } else {
            Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", errorMessage.ifBlank { fallback }))
        }

    companion object {
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
