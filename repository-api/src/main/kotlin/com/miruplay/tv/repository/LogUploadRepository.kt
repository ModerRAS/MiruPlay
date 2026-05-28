package com.miruplay.tv.repository

import com.miruplay.tv.core.common.logging.MiruLogRecord
import kotlinx.coroutines.flow.Flow

const val DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME = "miruplay"
const val DEFAULT_LOCAL_LOG_READ_LIMIT = 200
const val MAX_LOCAL_LOG_READ_LIMIT = 1_000

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

data class LocalLogSnapshot(
    val totalCount: Int = 0,
    val records: List<MiruLogRecord> = emptyList(),
) {
    val returnedCount: Int
        get() = records.size

    val truncatedCount: Int
        get() = (totalCount - returnedCount).coerceAtLeast(0)
}

interface LogUploadRepository {
    val status: Flow<LogUploadStatus>
    fun observeConfig(): Flow<OtlpLogUploadConfig>
    fun getConfig(): OtlpLogUploadConfig
    fun isTokenConfigured(): Boolean
    suspend fun saveConfig(enabled: Boolean, endpoint: String, streamName: String)
    suspend fun saveToken(token: String)
    suspend fun clearToken()
    suspend fun uploadPendingLogs(): LogUploadStatus
    suspend fun readLocalLogs(limit: Int = DEFAULT_LOCAL_LOG_READ_LIMIT): LocalLogSnapshot =
        LocalLogSnapshot()
    suspend fun exportLocalLogs(sinceTimestampMs: Long? = null): String = ""
}
