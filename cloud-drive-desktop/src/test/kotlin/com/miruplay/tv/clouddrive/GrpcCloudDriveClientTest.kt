package com.miruplay.tv.clouddrive

import clouddrive.CloudDriveFileSrvGrpc
import clouddrive.Clouddrive
import com.miruplay.tv.core.common.Result
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrpcCloudDriveClientTest {
    private val client = GrpcCloudDriveClient()
    private var server: Server? = null

    @After
    fun tearDown() {
        server?.shutdownNow()
        server = null
    }

    @Test
    fun `login exchanges credentials for server token over generated grpc client`() = runBlocking {
        val service = FakeCloudDriveService(loginToken = "issued-token")
        val endpoint = startServer(service)

        val result = client.login(endpoint, username = "alice", password = "secret")

        assertTrue(result is Result.Success)
        assertEquals("issued-token", (result as Result.Success).data.token)
        assertEquals("alice", service.tokenRequests.single().userName)
        assertEquals("secret", service.tokenRequests.single().password)
    }

    @Test
    fun `getApiTokenInfo verifies token permissions from generated grpc client`() = runBlocking {
        val service = FakeCloudDriveService()
        val endpoint = startServer(service)

        val result = client.getApiTokenInfo(endpoint, "api-token")

        assertTrue(result is Result.Success)
        val tokenInfo = (result as Result.Success).data
        assertEquals("/anime", tokenInfo.rootDir)
        assertEquals("MiruPlay", tokenInfo.friendlyName)
        assertTrue(tokenInfo.allowList)
        assertTrue(tokenInfo.allowCreateFolder)
        assertTrue(tokenInfo.allowCreateFile)
        assertTrue(tokenInfo.allowWrite)
        assertTrue(tokenInfo.allowMove)
        assertTrue(tokenInfo.allowAddOfflineDownload)
        assertEquals(listOf("api-token"), service.apiTokenRequests)
    }

    @Test
    fun `listFolder sends bearer authorization and maps streamed files`() = runBlocking {
        val service = FakeCloudDriveService()
        val endpoint = startServer(service)

        val result = client.listFolder(
            endpoint = CloudDriveEndpoint(url = endpoint, token = "api-token"),
            path = "/Anime",
            forceRefresh = true
        )

        assertTrue(result is Result.Success)
        val files = (result as Result.Success).data
        assertEquals(listOf("Episode 01.mkv", "Season 2"), files.map { it.name })
        assertEquals(listOf("Bearer api-token"), service.authorizationHeaders)
        assertEquals("/Anime", service.listRequests.single().path)
        assertTrue(service.listRequests.single().forceRefresh)
    }

    @Test
    fun `listFolder retries with raw token when bearer token is rejected`() = runBlocking {
        val service = FakeCloudDriveService(rejectBearer = true)
        val endpoint = startServer(service)

        val result = client.listFolder(
            endpoint = CloudDriveEndpoint(url = endpoint, token = "api-token"),
            path = "/Anime"
        )

        assertTrue(result is Result.Success)
        assertEquals(listOf("Bearer api-token", "api-token"), service.authorizationHeaders)
    }

    private fun startServer(service: FakeCloudDriveService): String {
        val interceptedService = ServerInterceptors.intercept(
            service,
            CapturingAuthorizationInterceptor(service)
        )
        server = ServerBuilder
            .forPort(0)
            .directExecutor()
            .addService(interceptedService)
            .build()
            .start()
        return "http://127.0.0.1:${server!!.port}"
    }

    private class FakeCloudDriveService(
        private val loginToken: String = "login-token",
        private val rejectBearer: Boolean = false
    ) : CloudDriveFileSrvGrpc.CloudDriveFileSrvImplBase() {
        val tokenRequests = mutableListOf<Clouddrive.GetTokenRequest>()
        val apiTokenRequests = mutableListOf<String>()
        val listRequests = mutableListOf<Clouddrive.ListSubFileRequest>()
        val authorizationHeaders = mutableListOf<String?>()

        override fun getToken(
            request: Clouddrive.GetTokenRequest,
            responseObserver: StreamObserver<Clouddrive.JWTToken>
        ) {
            tokenRequests += request
            responseObserver.respond(
                Clouddrive.JWTToken.newBuilder()
                    .setSuccess(true)
                    .setToken(loginToken)
                    .build()
            )
        }

        override fun getApiTokenInfo(
            request: Clouddrive.StringValue,
            responseObserver: StreamObserver<Clouddrive.TokenInfo>
        ) {
            apiTokenRequests += request.value
            responseObserver.respond(
                Clouddrive.TokenInfo.newBuilder()
                    .setRootDir("/anime")
                    .setFriendlyName("MiruPlay")
                    .setPermissions(
                        Clouddrive.TokenPermissions.newBuilder()
                            .setAllowList(true)
                            .setAllowCreateFolder(true)
                            .setAllowCreateFile(true)
                            .setAllowWrite(true)
                            .setAllowMove(true)
                            .setAllowAddOfflineDownload(true)
                    )
                    .build()
            )
        }

        override fun getSubFiles(
            request: Clouddrive.ListSubFileRequest,
            responseObserver: StreamObserver<Clouddrive.SubFilesReply>
        ) {
            listRequests += request
            val authorization = AUTHORIZATION_CONTEXT.get()
            if (rejectBearer && authorization?.startsWith("Bearer ") == true) {
                responseObserver.onError(
                    Status.UNAUTHENTICATED
                        .withDescription("Invalid auth token")
                        .asRuntimeException()
                )
                return
            }
            responseObserver.respond(
                Clouddrive.SubFilesReply.newBuilder()
                    .addSubFiles(
                        Clouddrive.CloudDriveFile.newBuilder()
                            .setName("Episode 01.mkv")
                            .setFullPathName("/Anime/Episode 01.mkv")
                            .setSize(1024L)
                            .setIsDirectory(false)
                    )
                    .addSubFiles(
                        Clouddrive.CloudDriveFile.newBuilder()
                            .setName("Season 2")
                            .setFullPathName("/Anime/Season 2")
                            .setIsDirectory(true)
                    )
                    .build()
            )
        }
    }

    private class CapturingAuthorizationInterceptor(
        private val service: FakeCloudDriveService
    ) : ServerInterceptor {
        override fun <ReqT : Any, RespT : Any> interceptCall(
            call: ServerCall<ReqT, RespT>,
            headers: Metadata,
            next: ServerCallHandler<ReqT, RespT>
        ): ServerCall.Listener<ReqT> {
            val authorization = headers.get(AUTHORIZATION_HEADER)
            if (authorization != null) {
                service.authorizationHeaders += authorization
            }
            return Contexts.interceptCall(
                Context.current().withValue(AUTHORIZATION_CONTEXT, authorization),
                call,
                headers,
                next
            )
        }
    }

    private companion object {
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        private val AUTHORIZATION_CONTEXT: Context.Key<String> = Context.key("authorization")

        private fun <T> StreamObserver<T>.respond(value: T) {
            onNext(value)
            onCompleted()
        }
    }
}
