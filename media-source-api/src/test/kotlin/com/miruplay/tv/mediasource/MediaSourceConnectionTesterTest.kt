package com.miruplay.tv.mediasource

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class MediaSourceConnectionTesterTest {
    @Test
    fun `testConnection returns success and closes source`() = runBlocking {
        val source = FakeMediaSource(testResult = Result.success(true), info = sourceInfo())
        val factory = FakeMediaSourceFactory(sourceResult = Result.success(source))

        val result = factory.testConnection(sourceInfo())

        assertEquals(MediaSourceConnectionTestResult.Success, result)
        assertTrue(factory.createdSources.single().connectionInfo.containsKey(MediaSourceInfoConventions.CONNECTION_URL))
        assertTrue(source.closed)
    }

    @Test
    fun `testConnection returns failed message for false connection`() = runBlocking {
        val source = FakeMediaSource(testResult = Result.success(false), info = sourceInfo())
        val factory = FakeMediaSourceFactory(sourceResult = Result.success(source))

        val result = factory.testConnection(sourceInfo())

        assertEquals(MediaSourceConnectionTestResult.Failed("无法连接到服务器"), result)
        assertTrue(source.closed)
    }

    @Test
    fun `testConnection returns source creation error`() = runBlocking {
        val factory = FakeMediaSourceFactory(
            sourceResult = Result.failure(AppError.NetworkError.ServerUnreachable("create failed"))
        )

        val result = factory.testConnection(sourceInfo())

        assertEquals(MediaSourceConnectionTestResult.Failed("无法连接服务器：create failed"), result)
    }

    @Test
    fun `testConnection returns media source test error and closes source`() = runBlocking {
        val source = FakeMediaSource(
            testResult = Result.failure(AppError.NetworkError.ServerUnreachable("test failed")),
            info = sourceInfo(),
        )
        val factory = FakeMediaSourceFactory(sourceResult = Result.success(source))

        val result = factory.testConnection(sourceInfo())

        assertEquals(MediaSourceConnectionTestResult.Failed("无法连接服务器：test failed"), result)
        assertTrue(source.closed)
    }

    @Test
    fun `testConnection builds transient source info from form inputs`() = runBlocking {
        val source = FakeMediaSource(testResult = Result.success(true), info = sourceInfo())
        val factory = FakeMediaSourceFactory(sourceResult = Result.success(source))

        val result = factory.testConnection(
            type = MediaSourceType.SMB,
            location = "smb://nas/anime",
            username = "alice",
            password = "secret",
            domain = "WORKGROUP",
        )

        assertEquals(MediaSourceConnectionTestResult.Success, result)
        val info = factory.createdSources.single()
        assertEquals(MediaSourceType.SMB, info.type)
        assertEquals("smb://nas/anime", info.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
        assertEquals("alice", info.connectionInfo[MediaSourceInfoConventions.CONNECTION_USERNAME])
        assertEquals("secret", info.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
        assertEquals("WORKGROUP", info.connectionInfo[MediaSourceInfoConventions.CONNECTION_DOMAIN])
    }

    @Test
    fun `testConnectionState maps success to boolean`() = runBlocking {
        val successFactory = FakeMediaSourceFactory(Result.success(FakeMediaSource(Result.success(true), sourceInfo())))
        val failedFactory = FakeMediaSourceFactory(Result.success(FakeMediaSource(Result.success(false), sourceInfo())))

        assertTrue(successFactory.testConnectionState(sourceInfo()))
        assertFalse(failedFactory.testConnectionState(sourceInfo()))
    }

    private class FakeMediaSourceFactory(
        private val sourceResult: Result<MediaSource>,
    ) : MediaSourceFactory {
        val createdSources = mutableListOf<MediaSourceInfo>()

        override fun create(info: MediaSourceInfo): Result<MediaSource> {
            createdSources += info
            return sourceResult
        }

        override fun supports(type: MediaSourceType): Boolean = true
    }

    private class FakeMediaSource(
        private val testResult: Result<Boolean>,
        override val info: MediaSourceInfo,
    ) : MediaSource {
        var closed = false

        override val id: String = "fake"
        override val capabilities: MediaCapabilities = MediaCapabilities()

        override suspend fun listFiles(path: String): Result<List<FileEntry>> =
            Result.success(emptyList())

        override suspend fun openStream(path: String): Result<InputStream> =
            Result.success(ByteArrayInputStream(ByteArray(0)))

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.success(FileMetadata(path = path, name = path, isDirectory = false))

        override suspend fun testConnection(): Result<Boolean> =
            testResult

        override suspend fun close() {
            closed = true
        }
    }

    private fun sourceInfo(): MediaSourceInfo =
        MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/anime",
            username = "alice",
            password = "secret",
        )
}
