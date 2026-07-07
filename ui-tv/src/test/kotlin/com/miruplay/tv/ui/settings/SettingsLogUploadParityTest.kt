package com.miruplay.tv.ui.settings

import com.miruplay.tv.model.settingsAndroidTvLogUploadMenuSummary
import com.miruplay.tv.model.settingsAndroidTvLogUploadStatusMessage
import com.miruplay.tv.model.settingsLogUploadStatusMessage
import com.miruplay.tv.repository.LogUploadActionCoordinator
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.LogUploadStatus
import com.miruplay.tv.repository.OtlpLogUploadConfig
import com.miruplay.tv.repository.OtlpLogUploadActionSnapshot
import com.miruplay.tv.repository.withRuntimeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogUploadParityTest {
    @Test
    fun `android tv log upload menu summary reflects runtime state`() {
        assertEquals("未启用", settingsAndroidTvLogUploadMenuSummary())
        assertEquals(
            "等待 Token",
            settingsAndroidTvLogUploadMenuSummary(enabled = true, tokenConfigured = false, isUploading = false),
        )
        assertEquals(
            "自动上报",
            settingsAndroidTvLogUploadMenuSummary(enabled = true, tokenConfigured = true, isUploading = false),
        )
        assertEquals(
            "上报中",
            settingsAndroidTvLogUploadMenuSummary(enabled = true, tokenConfigured = true, isUploading = true),
        )
    }

    @Test
    fun `android tv log upload status uses shared runtime behavior with tv entry copy`() {
        val snapshot = OtlpLogUploadActionSnapshot(
            pendingCount = 2,
            isUploading = true,
            tokenConfigured = false,
            lastUploadAt = 0L,
            lastUploadStatus = "后台自动上报中",
        )

        val status = androidTvLogUploadStatusMessage(snapshot)

        assertTrue(status.startsWith(settingsAndroidTvLogUploadStatusMessage()))
        assertFalse(status.contains("Web 控制端"))
        assertFalse(status.contains("Windows"))
        assertTrue(status.contains("待上报 2 条"))
        assertTrue(status.contains("上报中"))
        assertTrue(status.contains("未保存 Token"))
        assertTrue(status.contains("后台自动上报中"))
    }

    @Test
    fun `shared coordinator trims and persists save config values`() = runBlocking {
        val repository = FakeLogUploadRepository()
        val coordinator = LogUploadActionCoordinator(repository)

        val snapshot = coordinator.saveConfig(
            enabled = true,
            endpoint = " https://oo.example/api/default ",
            streamName = " anime ",
        )

        assertTrue(snapshot.enabled)
        assertEquals("https://oo.example/api/default", snapshot.endpoint)
        assertEquals("anime", snapshot.streamName)
        assertTrue(snapshot.tokenConfigured)
        assertTrue(snapshot.canRunNow)
    }

    @Test
    fun `shared coordinator accepts curl command from settings input`() = runBlocking {
        val repository = FakeLogUploadRepository(tokenConfigured = false)
        val coordinator = LogUploadActionCoordinator(repository)

        val snapshot = coordinator.saveConfig(
            enabled = true,
            endpoint = "curl -u user@example.com:secret -k https://openobserve.example.com/api/org/default/_json -d '{}'",
            streamName = "miruplay",
        )

        assertEquals("https://openobserve.example.com/api/org/default/_json", snapshot.endpoint)
        assertTrue(snapshot.tokenConfigured)
        assertTrue(snapshot.canRunNow)
    }

    @Test
    fun `shared coordinator run now updates pending count and status`() = runBlocking {
        val repository = FakeLogUploadRepository(
            status = LogUploadStatus(
                pendingCount = 4,
                isUploading = false,
                tokenConfigured = true,
                lastUploadStatus = null,
            ),
        )
        val coordinator = LogUploadActionCoordinator(repository)

        val snapshot = coordinator.runNow()
        val status = settingsLogUploadStatusMessage(
            pendingCount = snapshot.pendingCount,
            isUploading = snapshot.isUploading,
            tokenConfigured = snapshot.tokenConfigured,
            lastUploadAt = snapshot.lastUploadAt,
            lastUploadStatus = snapshot.lastUploadStatus,
        )

        assertEquals(1, repository.uploadCalls)
        assertEquals(0, snapshot.pendingCount)
        assertTrue(status.contains("待上报 0 条"))
        assertTrue(status.contains("Token 已保存"))
        assertTrue(status.contains("已上报 4 条日志"))
        assertTrue(snapshot.canRunNow)
        assertFalse(snapshot.isUploading)
    }

    @Test
    fun `runtime status refresh should not overwrite local draft config`() {
        val draft = OtlpLogUploadActionSnapshot(
            enabled = true,
            endpoint = "https://draft.example/api/default",
            streamName = "draft-stream",
        )
        val status = LogUploadStatus(
            pendingCount = 3,
            isUploading = true,
            lastUploadAt = 456L,
            lastUploadStatus = "后台自动上报中",
            tokenConfigured = true,
        )

        val updated = draft.withRuntimeStatus(status)

        assertEquals(true, updated.enabled)
        assertEquals("https://draft.example/api/default", updated.endpoint)
        assertEquals("draft-stream", updated.streamName)
        assertEquals(3, updated.pendingCount)
        assertTrue(updated.isUploading)
        assertEquals(456L, updated.lastUploadAt)
        assertEquals("后台自动上报中", updated.lastUploadStatus)
        assertTrue(updated.tokenConfigured)
    }

    private class FakeLogUploadRepository(
        config: OtlpLogUploadConfig = OtlpLogUploadConfig(
            enabled = true,
            endpoint = "https://oo.example/api/default",
            streamName = "miruplay",
        ),
        status: LogUploadStatus = LogUploadStatus(
            pendingCount = 0,
            isUploading = false,
            tokenConfigured = true,
        ),
        tokenConfigured: Boolean = true,
    ) : LogUploadRepository {
        private val configState = MutableStateFlow(config)
        private val statusState = MutableStateFlow(status)
        private var tokenSaved = tokenConfigured
        var uploadCalls: Int = 0

        override val status: Flow<LogUploadStatus> = statusState.asStateFlow()

        override fun observeConfig(): Flow<OtlpLogUploadConfig> = configState.asStateFlow()

        override fun getConfig(): OtlpLogUploadConfig = configState.value

        override fun isTokenConfigured(): Boolean = tokenSaved

        override suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String) {
            configState.value = configState.value.copy(
                enabled = enabled,
                endpoint = endpoint.trim(),
                streamName = streamName.trim().ifBlank { "miruplay" },
            )
            statusState.value = statusState.value.copy(tokenConfigured = tokenSaved)
        }

        override suspend fun saveToken(token: String) {
            tokenSaved = token.isNotBlank()
            statusState.value = statusState.value.copy(tokenConfigured = tokenSaved)
        }

        override suspend fun clearToken() {
            tokenSaved = false
            statusState.value = statusState.value.copy(tokenConfigured = false)
        }

        override suspend fun uploadPendingLogs(): LogUploadStatus {
            uploadCalls += 1
            val pending = statusState.value.pendingCount
            val next = statusState.value.copy(
                pendingCount = 0,
                isUploading = false,
                tokenConfigured = tokenSaved,
                lastUploadStatus = "已上报 $pending 条日志",
            )
            statusState.value = next
            return next
        }
    }
}
