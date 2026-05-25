package com.miruplay.tv.repository

import kotlinx.coroutines.flow.Flow

const val DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME = "miruplay"

data class OtlpLogUploadConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val streamName: String = DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME,
    val lastUploadAt: Long = 0L,
    val lastUploadStatus: String? = null
) {
    val isReady: Boolean
        get() = enabled && endpoint.isNotBlank()
}

data class LogUploadStatus(
    val pendingCount: Int = 0,
    val isUploading: Boolean = false,
    val lastUploadAt: Long = 0L,
    val lastUploadStatus: String? = null,
    val tokenConfigured: Boolean = false
)

interface LogUploadRepository {
    val status: Flow<LogUploadStatus>
    fun observeConfig(): Flow<OtlpLogUploadConfig>
    fun getConfig(): OtlpLogUploadConfig
    fun isTokenConfigured(): Boolean
    suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String)
    suspend fun saveToken(token: String)
    suspend fun clearToken()
    suspend fun uploadPendingLogs(): LogUploadStatus
}
