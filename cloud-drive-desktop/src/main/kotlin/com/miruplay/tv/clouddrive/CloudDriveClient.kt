package com.miruplay.tv.clouddrive

import clouddrive.CloudDriveFileSrvGrpc
import clouddrive.Clouddrive
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class GrpcCloudDriveClient : CloudDriveClient {
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
                    allowCreateFile = response.permissions.allowCreateFile,
                    allowWrite = response.permissions.allowWrite,
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

    override suspend fun uploadFile(
        endpoint: CloudDriveEndpoint,
        localFile: File,
        parentPath: String,
        remoteFileName: String
    ): Result<String> = withAuthenticatedStub(endpoint) { stub ->
        if (!localFile.isFile) {
            return@withAuthenticatedStub Result.failure(AppError.MediaSourceError.NotFound(localFile.absolutePath))
        }
        val normalizedParent = normalizeCloudPath(parentPath)
        val response = stub.createFile(
            Clouddrive.CreateFileRequest.newBuilder()
                .setParentPath(normalizedParent)
                .setFileName(remoteFileName)
                .build()
        )
        val fileHandle = response.fileHandle
        try {
            val bytes = localFile.readBytes()
            stub.writeToFile(
                Clouddrive.WriteFileRequest.newBuilder()
                    .setFileHandle(fileHandle)
                    .setStartPos(0L)
                    .setLength(bytes.size.toLong())
                    .setBuffer(ByteString.copyFrom(bytes))
                    .setCloseFile(true)
                    .build()
            )
        } catch (e: Exception) {
            runCatching {
                stub.closeFile(Clouddrive.CloseFileRequest.newBuilder().setFileHandle(fileHandle).build())
            }
            throw e
        }
        Result.success(joinCloudPath(normalizedParent, remoteFileName))
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
        val bearerResult = withAuthorization(endpoint.url, "Bearer $token", block)
        return if (bearerResult.isUnauthenticated()) {
            withAuthorization(endpoint.url, token, block)
        } else {
            bearerResult
        }
    }

    private suspend fun <T> withAuthorization(
        endpointUrl: String,
        authorization: String,
        block: (CloudDriveFileSrvGrpc.CloudDriveFileSrvBlockingStub) -> Result<T>
    ): Result<T> =
        withGrpc(endpointUrl) { channel ->
            val metadata = Metadata().apply {
                put(AUTHORIZATION_HEADER, authorization)
            }
            val stub = CloudDriveFileSrvGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
            block(stub)
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

    private fun normalizeCloudPath(path: String): String {
        val normalized = path.trim().replace('\\', '/').trimEnd('/')
        return when {
            normalized.isBlank() -> "/"
            normalized.startsWith('/') -> normalized
            else -> "/$normalized"
        }
    }

    private fun joinCloudPath(parentPath: String, fileName: String): String =
        "${normalizeCloudPath(parentPath).trimEnd('/')}/$fileName"

    private fun Clouddrive.FileOperationResult.asUnitResult(fallback: String): Result<Unit> =
        if (success) {
            Result.success(Unit)
        } else {
            Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", errorMessage.ifBlank { fallback }))
        }

    private fun Result<*>.isUnauthenticated(): Boolean {
        val error = (this as? Result.Error)?.error ?: return false
        val detail = when (error) {
            is AppError.NetworkError.ServerUnreachable -> error.url
            is AppError.MediaSourceError.AuthenticationFailed -> error.source
            else -> error.toString()
        }
        return detail.contains("UNAUTHENTICATED", ignoreCase = true) ||
            detail.contains("Invalid auth token", ignoreCase = true)
    }

    companion object {
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
