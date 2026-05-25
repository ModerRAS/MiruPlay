package com.miruplay.tv.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUploadActionCoordinatorTest {
    @Test
    fun `otlp snapshot helper merges config and status`() {
        val snapshot = otlpLogUploadActionSnapshot(
            config = OtlpLogUploadConfig(
                enabled = true,
                endpoint = "https://oo.example/api/default",
                streamName = "miruplay",
            ),
            status = LogUploadStatus(
                pendingCount = 6,
                isUploading = true,
                lastUploadAt = 88L,
                lastUploadStatus = "上报中",
                tokenConfigured = false,
            ),
            tokenConfigured = true,
        )

        assertEquals(true, snapshot.enabled)
        assertEquals("https://oo.example/api/default", snapshot.endpoint)
        assertEquals("miruplay", snapshot.streamName)
        assertEquals(6, snapshot.pendingCount)
        assertEquals(true, snapshot.isUploading)
        assertEquals(88L, snapshot.lastUploadAt)
        assertEquals("上报中", snapshot.lastUploadStatus)
        assertEquals(true, snapshot.tokenConfigured)
    }

    @Test
    fun `with runtime status keeps draft config fields`() {
        val draft = OtlpLogUploadActionSnapshot(
            enabled = true,
            endpoint = "https://draft-endpoint.example",
            streamName = "draft-stream",
            pendingCount = 1,
            isUploading = false,
            tokenConfigured = false,
        )

        val updated = draft.withRuntimeStatus(
            status = LogUploadStatus(
                pendingCount = 9,
                isUploading = true,
                lastUploadAt = 777L,
                lastUploadStatus = "后台上报中",
                tokenConfigured = true,
            ),
        )

        assertEquals(true, updated.enabled)
        assertEquals("https://draft-endpoint.example", updated.endpoint)
        assertEquals("draft-stream", updated.streamName)
        assertEquals(9, updated.pendingCount)
        assertEquals(true, updated.isUploading)
        assertEquals(777L, updated.lastUploadAt)
        assertEquals("后台上报中", updated.lastUploadStatus)
        assertEquals(true, updated.tokenConfigured)
    }

    @Test
    fun `current snapshot merges config status and token state`() = runBlocking {
        val repository = FakeLogUploadRepository(
            currentConfig = OtlpLogUploadConfig(enabled = true, endpoint = "https://oo.example/api/default", streamName = "miruplay"),
            status = LogUploadStatus(
                pendingCount = 3,
                isUploading = false,
                lastUploadAt = 1234L,
                lastUploadStatus = "已上报 3 条日志",
                tokenConfigured = false,
            ),
            tokenConfigured = true,
        )
        val coordinator = LogUploadActionCoordinator(repository)

        val snapshot = coordinator.current()

        assertEquals(true, snapshot.enabled)
        assertEquals("https://oo.example/api/default", snapshot.endpoint)
        assertEquals("miruplay", snapshot.streamName)
        assertEquals(3, snapshot.pendingCount)
        assertEquals(false, snapshot.isUploading)
        assertEquals(1234L, snapshot.lastUploadAt)
        assertEquals("已上报 3 条日志", snapshot.lastUploadStatus)
        assertEquals(true, snapshot.tokenConfigured)
        assertTrue(snapshot.canRunNow)
    }

    @Test
    fun `save config forwards parameters and updates snapshot`() = runBlocking {
        val repository = FakeLogUploadRepository()
        val coordinator = LogUploadActionCoordinator(repository)

        val snapshot = coordinator.saveConfig(
            enabled = true,
            endpoint = " https://openobserve.example.com/api/default ",
            streamName = " anime ",
        )

        assertEquals(
            OtlpLogUploadConfig(
                enabled = true,
                endpoint = " https://openobserve.example.com/api/default ",
                streamName = " anime ",
            ),
            repository.currentConfig,
        )
        assertEquals(true, snapshot.enabled)
        assertEquals(" https://openobserve.example.com/api/default ", snapshot.endpoint)
        assertEquals(" anime ", snapshot.streamName)
    }

    @Test
    fun `save and clear token refresh token configured state`() = runBlocking {
        val repository = FakeLogUploadRepository()
        val coordinator = LogUploadActionCoordinator(repository)

        val saved = coordinator.saveToken("token")
        assertEquals("token", repository.savedToken)
        assertTrue(saved.tokenConfigured)

        val cleared = coordinator.clearToken()
        assertEquals(true, repository.tokenCleared)
        assertFalse(cleared.tokenConfigured)
    }

    @Test
    fun `run now triggers upload and returns latest status`() = runBlocking {
        val repository = FakeLogUploadRepository(
            currentConfig = OtlpLogUploadConfig(enabled = true, endpoint = "https://oo.example/api/default", streamName = "miruplay"),
            status = LogUploadStatus(pendingCount = 2, tokenConfigured = true),
            tokenConfigured = true,
        )
        val coordinator = LogUploadActionCoordinator(repository)

        val snapshot = coordinator.runNow()

        assertEquals(1, repository.uploadCalls)
        assertEquals(0, snapshot.pendingCount)
        assertEquals("已上报 2 条日志", snapshot.lastUploadStatus)
        assertTrue(snapshot.canRunNow)
    }

    private class FakeLogUploadRepository(
        var currentConfig: OtlpLogUploadConfig = OtlpLogUploadConfig(),
        status: LogUploadStatus = LogUploadStatus(),
        private var tokenConfigured: Boolean = false,
    ) : LogUploadRepository {
        private val statusState = MutableStateFlow(status)
        override val status: Flow<LogUploadStatus> = statusState.asStateFlow()
        var savedToken: String? = null
        var tokenCleared: Boolean = false
        var uploadCalls: Int = 0

        override fun observeConfig(): Flow<OtlpLogUploadConfig> =
            MutableStateFlow(currentConfig).asStateFlow()

        override fun getConfig(): OtlpLogUploadConfig = currentConfig

        override fun isTokenConfigured(): Boolean = tokenConfigured

        override suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String) {
            currentConfig = OtlpLogUploadConfig(
                enabled = enabled,
                endpoint = endpoint,
                streamName = streamName,
                lastUploadAt = currentConfig.lastUploadAt,
                lastUploadStatus = currentConfig.lastUploadStatus,
            )
        }

        override suspend fun saveToken(token: String) {
            savedToken = token
            tokenConfigured = token.isNotBlank()
            statusState.value = statusState.value.copy(tokenConfigured = tokenConfigured)
        }

        override suspend fun clearToken() {
            tokenCleared = true
            tokenConfigured = false
            statusState.value = statusState.value.copy(tokenConfigured = false)
        }

        override suspend fun uploadPendingLogs(): LogUploadStatus {
            uploadCalls += 1
            val current = statusState.value
            val uploadedCount = current.pendingCount
            val next = current.copy(
                pendingCount = 0,
                isUploading = false,
                lastUploadStatus = "已上报 $uploadedCount 条日志",
                tokenConfigured = tokenConfigured,
            )
            statusState.value = next
            return next
        }
    }
}
