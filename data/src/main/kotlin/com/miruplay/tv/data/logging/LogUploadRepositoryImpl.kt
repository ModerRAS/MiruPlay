package com.miruplay.tv.data.logging

import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.LogUploadStatus
import com.miruplay.tv.repository.OtlpLogUploadConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class LogUploadRepositoryImpl @Inject constructor(
    private val preferences: LogUploadPreferencesManager,
    private val credentials: AppCredentialStore,
    private val localLogStore: LocalLogStore,
    private val uploader: OtlpLogUploader
) : LogUploadRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(currentStatus(isUploading = false))
    override val status: Flow<LogUploadStatus> = _status.asStateFlow()

    init {
        MiruLog.setSink(localLogStore)
        scope.launch {
            localLogStore.pendingCount.collect {
                refreshStatus()
            }
        }
        scope.launch {
            preferences.config.collect {
                refreshStatus()
            }
        }
    }

    override fun observeConfig(): Flow<OtlpLogUploadConfig> = preferences.config

    override fun getConfig(): OtlpLogUploadConfig = preferences.getConfig()

    override fun isTokenConfigured(): Boolean = !credentials.otlpAccessToken.isNullOrBlank()

    override suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String) {
        preferences.save(enabled, endpoint, streamName)
        refreshStatus()
    }

    override suspend fun saveToken(token: String) {
        credentials.otlpAccessToken = token.trim()
        refreshStatus()
    }

    override suspend fun clearToken() {
        credentials.clearOtlpAccessToken()
        refreshStatus()
    }

    override suspend fun uploadPendingLogs(): LogUploadStatus = withContext(Dispatchers.IO) {
        val config = preferences.getConfig()
        val token = credentials.otlpAccessToken.orEmpty()
        if (!config.enabled) return@withContext currentStatus(isUploading = false)
        if (config.endpoint.isBlank()) return@withContext updateStatus("请填写 OTLP 服务器地址")
        if (token.isBlank()) return@withContext updateStatus("请填写 OTLP Token")

        val records = localLogStore.readBatch(MAX_UPLOAD_BATCH)
        if (records.isEmpty()) return@withContext updateStatus("没有待上报日志")

        _status.value = currentStatus(isUploading = true)
        val result = runCatching {
            uploader.upload(config.endpoint, token, config.streamName, records)
        }.getOrElse { error ->
            OtlpLogUploader.UploadResult.Failed(error.message ?: error::class.simpleName.orEmpty())
        }

        when (result) {
            is OtlpLogUploader.UploadResult.Success -> {
                localLogStore.removeUploaded(records.map { it.id }.toSet())
                updateStatus("已上报 ${result.uploadedCount} 条日志")
            }
            is OtlpLogUploader.UploadResult.Failed -> {
                updateStatus("上报失败：${result.message}")
            }
        }
    }

    private fun updateStatus(message: String): LogUploadStatus {
        val now = System.currentTimeMillis()
        preferences.setUploadStatus(now, message)
        return currentStatus(isUploading = false).also { _status.value = it }
    }

    private fun refreshStatus() {
        _status.value = currentStatus(isUploading = false)
    }

    private fun currentStatus(isUploading: Boolean): LogUploadStatus {
        val config = preferences.getConfig()
        return LogUploadStatus(
            pendingCount = localLogStore.pendingCount(),
            isUploading = isUploading,
            lastUploadAt = config.lastUploadAt,
            lastUploadStatus = config.lastUploadStatus,
            tokenConfigured = isTokenConfigured()
        )
    }

    companion object {
        private const val MAX_UPLOAD_BATCH = 200
    }
}
