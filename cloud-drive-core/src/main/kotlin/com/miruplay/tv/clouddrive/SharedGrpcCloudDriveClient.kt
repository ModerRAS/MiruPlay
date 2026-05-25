package com.miruplay.tv.clouddrive

import clouddrive.CloudDriveFileSrvGrpc
import clouddrive.Clouddrive
import com.google.protobuf.ByteString
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class SharedGrpcCloudDriveClient : CloudDriveClient {
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
        targetFolder: String,
    ): Result<Unit> = withAuthenticatedStub(endpoint) { stub ->
        val offlineRequest = CloudDriveRequests.offlineFiles(urls, targetFolder)
        val response = stub.addOfflineFiles(
            Clouddrive.AddOfflineFileRequest.newBuilder()
                .setUrls(offlineRequest.urls)
                .setToFolder(offlineRequest.targetFolder)
                .setCheckFolderAfterSecs(offlineRequest.checkFolderAfterSeconds)
                .build()
        )
        response.asUnitResult("提交离线下载失败")
    }

    override suspend fun uploadFile(
        endpoint: CloudDriveEndpoint,
        localFile: File,
        parentPath: String,
        remoteFileName: String,
    ): Result<String> = withAuthenticatedStub(endpoint) { stub ->
        if (!localFile.isFile) {
            return@withAuthenticatedStub Result.failure(AppError.MediaSourceError.NotFound(localFile.absolutePath))
        }
        val uploadTarget = CloudDriveRequests.uploadTarget(parentPath, remoteFileName)
        val response = stub.createFile(
            Clouddrive.CreateFileRequest.newBuilder()
                .setParentPath(uploadTarget.parentPath)
                .setFileName(uploadTarget.remoteFileName)
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
        Result.success(uploadTarget.remotePath)
    }

    override suspend fun listFolder(
        endpoint: CloudDriveEndpoint,
        path: String,
        forceRefresh: Boolean,
    ): Result<List<CloudDriveFileInfo>> = withAuthenticatedStub(endpoint) { stub ->
        val request = Clouddrive.ListSubFileRequest.newBuilder()
            .setPath(path)
            .setForceRefresh(forceRefresh)
            .build()
        val files = mutableListOf<CloudDriveFileInfo>()
        val replies = stub.getSubFiles(request)
        while (replies.hasNext()) {
            replies.next().subFilesList.forEach { file ->
                files += CloudDriveRequests.fileInfo(
                    name = file.name,
                    fullPathName = file.fullPathName,
                    isDirectory = file.isDirectory,
                    size = file.size,
                )
            }
        }
        Result.success(files)
    }

    override suspend fun createFolder(
        endpoint: CloudDriveEndpoint,
        parentPath: String,
        folderName: String,
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
        destinationPath: String,
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
        block: (CloudDriveFileSrvGrpc.CloudDriveFileSrvBlockingStub) -> Result<T>,
    ): Result<T> {
        val token = when (val tokenResult = CloudDriveProtocol.requireToken(endpoint)) {
            is Result.Success -> tokenResult.data
            is Result.Error -> return tokenResult
        }
        val authorizations = CloudDriveProtocol.authorizationCandidates(token)
        val bearerResult = withAuthorization(endpoint.url, authorizations.first(), block)
        return if (CloudDriveProtocol.shouldRetryWithRawToken(bearerResult)) {
            withAuthorization(endpoint.url, authorizations.last(), block)
        } else {
            bearerResult
        }
    }

    private suspend fun <T> withAuthorization(
        endpointUrl: String,
        authorization: String,
        block: (CloudDriveFileSrvGrpc.CloudDriveFileSrvBlockingStub) -> Result<T>,
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
        block: (ManagedChannel) -> Result<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        val channel = try {
            buildChannel(endpointUrl)
        } catch (_: Exception) {
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
        val address = CloudDriveProtocol.parseEndpointAddress(endpointUrl)
        return OkHttpChannelBuilder.forAddress(address.host, address.port)
            .apply {
                if (address.useTransportSecurity) {
                    useTransportSecurity()
                } else {
                    usePlaintext()
                }
            }
            .build()
    }

    private fun Clouddrive.FileOperationResult.asUnitResult(fallback: String): Result<Unit> =
        CloudDriveProtocol.operationResult(success, errorMessage, fallback)

    private companion object {
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
