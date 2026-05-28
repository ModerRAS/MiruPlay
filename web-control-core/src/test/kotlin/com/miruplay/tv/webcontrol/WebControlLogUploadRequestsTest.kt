package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.logging.MiruLogLevel
import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.repository.LocalLogSnapshot
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.LogUploadStatus
import com.miruplay.tv.repository.OtlpLogUploadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlLogUploadRequestsTest {
    @Test
    fun `get WebControl log upload reflects repository config status and token`() = runBlocking {
        val repository = FakeLogUploadRepository(
            config = OtlpLogUploadConfig(
                enabled = true,
                endpoint = "https://oo.example/api/default",
                streamName = "anime",
                lastUploadAt = 12L,
                lastUploadStatus = "ok",
            ),
            status = LogUploadStatus(
                pendingCount = 3,
                isUploading = true,
                lastUploadAt = 34L,
                lastUploadStatus = "uploading",
                tokenConfigured = false,
            ),
            tokenConfigured = true,
        )

        val dto = repository.getWebControlLogUpload()

        assertEquals(true, dto.config.enabled)
        assertEquals("https://oo.example/api/default", dto.config.endpoint)
        assertEquals("anime", dto.config.streamName)
        assertEquals(3, dto.status.pendingCount)
        assertEquals(true, dto.status.isUploading)
        assertEquals("uploading", dto.status.lastUploadStatus)
        assertEquals(true, dto.status.tokenConfigured)
        assertEquals(true, dto.tokenConfigured)
    }

    @Test
    fun `save WebControl log upload config trims endpoint and defaults stream`() = runBlocking {
        val repository = FakeLogUploadRepository()

        val dto = repository.saveWebControlLogUploadConfig(
            LogUploadConfigRequest(
                enabled = true,
                endpoint = " https://oo.example/api/default ",
                streamName = "   ",
            ),
        )

        assertEquals(1, repository.saveConfigCalls)
        assertEquals(true, repository.savedEnabled)
        assertEquals("https://oo.example/api/default", repository.savedEndpoint)
        assertEquals("miruplay", repository.savedStreamName)
        assertEquals("https://oo.example/api/default", dto.config.endpoint)
        assertEquals("miruplay", dto.config.streamName)
    }

    @Test
    fun `save WebControl log upload config rejects blank endpoint when enabled`() = runBlocking {
        val repository = FakeLogUploadRepository()

        val error = runCatching {
            repository.saveWebControlLogUploadConfig(
                LogUploadConfigRequest(
                    enabled = true,
                    endpoint = "  ",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("请填写 OpenObserve API 地址", error?.message)
        assertEquals(0, repository.saveConfigCalls)
    }

    @Test
    fun `save WebControl log upload token trims and rejects blank`() = runBlocking {
        val repository = FakeLogUploadRepository()

        val saved = repository.saveWebControlLogUploadToken(LogUploadTokenRequest(" token "))

        assertEquals(1, repository.saveTokenCalls)
        assertEquals("token", repository.savedToken)
        assertEquals(true, saved.tokenConfigured)

        val error = runCatching {
            repository.saveWebControlLogUploadToken(LogUploadTokenRequest(" "))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("请填写 OpenObserve Token", error?.message)
    }

    @Test
    fun `clear and run WebControl log upload delegate repository calls`() = runBlocking {
        val repository = FakeLogUploadRepository(tokenConfigured = true)

        val cleared = repository.clearWebControlLogUploadToken()
        assertEquals(1, repository.clearTokenCalls)
        assertFalse(cleared.tokenConfigured)

        val ran = repository.runWebControlLogUploadNow()
        assertEquals(1, repository.uploadCalls)
        assertEquals(0, ran.status.pendingCount)
    }

    @Test
    fun `local logs can be viewed and exported for WebControl`() = runBlocking {
        val repository = FakeLogUploadRepository(
            localLogs = listOf(
                logRecord("1", "first"),
                logRecord("2", "second"),
                logRecord("3", "third"),
            ),
        )

        val logs = repository.getWebControlLocalLogs(limit = 2)
        val download = repository.downloadWebControlLocalLogs(sinceTimestampMs = 2L, clock = { 123L })

        assertEquals(3, logs.totalCount)
        assertEquals(2, logs.returnedCount)
        assertEquals(1, logs.truncatedCount)
        assertEquals(listOf("second", "third"), logs.records.map { it.message })
        assertEquals("miruplay-logs-since-2-123.jsonl", download.fileName)
        assertFalse(download.content.decodeToString().contains("first"))
        assertTrue(download.content.decodeToString().contains("second"))
        assertTrue(download.contentType.contains("ndjson"))
    }

    private class FakeLogUploadRepository(
        config: OtlpLogUploadConfig = OtlpLogUploadConfig(),
        status: LogUploadStatus = LogUploadStatus(pendingCount = 2),
        private var tokenConfigured: Boolean = false,
        private val localLogs: List<MiruLogRecord> = emptyList(),
    ) : LogUploadRepository {
        private var currentConfig = config
        private val statusState = MutableStateFlow(status)

        var saveConfigCalls = 0
        var saveTokenCalls = 0
        var clearTokenCalls = 0
        var uploadCalls = 0
        var savedEnabled = false
        var savedEndpoint = ""
        var savedStreamName = ""
        var savedToken: String? = null

        override val status: Flow<LogUploadStatus> = statusState.asStateFlow()

        override fun observeConfig(): Flow<OtlpLogUploadConfig> =
            MutableStateFlow(currentConfig).asStateFlow()

        override fun getConfig(): OtlpLogUploadConfig = currentConfig

        override fun isTokenConfigured(): Boolean = tokenConfigured

        override suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String) {
            saveConfigCalls += 1
            savedEnabled = enabled
            savedEndpoint = endpoint
            savedStreamName = streamName
            currentConfig = currentConfig.copy(
                enabled = enabled,
                endpoint = endpoint,
                streamName = streamName,
            )
        }

        override suspend fun saveToken(token: String) {
            saveTokenCalls += 1
            savedToken = token
            tokenConfigured = token.isNotBlank()
            statusState.value = statusState.value.copy(tokenConfigured = tokenConfigured)
        }

        override suspend fun clearToken() {
            clearTokenCalls += 1
            tokenConfigured = false
            statusState.value = statusState.value.copy(tokenConfigured = false)
        }

        override suspend fun uploadPendingLogs(): LogUploadStatus {
            uploadCalls += 1
            val next = statusState.value.copy(
                pendingCount = 0,
                isUploading = false,
                tokenConfigured = tokenConfigured,
            )
            statusState.value = next
            return next
        }

        override suspend fun readLocalLogs(limit: Int): LocalLogSnapshot =
            LocalLogSnapshot(
                totalCount = localLogs.size,
                records = localLogs.takeLast(limit),
            )

        override suspend fun exportLocalLogs(sinceTimestampMs: Long?): String =
            localLogs
                .filter { record -> sinceTimestampMs == null || record.timestampMs >= sinceTimestampMs }
                .joinToString(separator = "\n", postfix = "\n") { it.message }
    }

    private fun logRecord(id: String, message: String): MiruLogRecord =
        MiruLogRecord(
            id = id,
            timestampMs = id.toLong(),
            level = MiruLogLevel.INFO,
            tag = "Test",
            message = message,
        )
}
