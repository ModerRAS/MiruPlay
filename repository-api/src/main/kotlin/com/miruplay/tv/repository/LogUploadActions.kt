package com.miruplay.tv.repository

import kotlinx.coroutines.flow.first

data class OtlpLogUploadActionSnapshot(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val streamName: String = DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME,
    val pendingCount: Int = 0,
    val isUploading: Boolean = false,
    val lastUploadAt: Long = 0L,
    val lastUploadStatus: String? = null,
    val tokenConfigured: Boolean = false,
) {
    val canRunNow: Boolean
        get() = !isUploading && enabled && endpoint.isNotBlank() && tokenConfigured
}

fun OtlpLogUploadActionSnapshot.canRunNow(tokenInput: String): Boolean =
    !isUploading && enabled && endpoint.isNotBlank() &&
        (tokenConfigured || tokenInput.isNotBlank())

fun normalizeOtlpLogUploadConfig(
    enabled: Boolean,
    endpoint: String,
    streamName: String,
): OtlpLogUploadConfig =
    OtlpLogUploadConfig(
        enabled = enabled,
        endpoint = endpoint.trim(),
        streamName = streamName.trim().ifBlank { DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME },
    )

fun OtlpLogUploadActionSnapshot.toConfig(): OtlpLogUploadConfig =
    normalizeOtlpLogUploadConfig(
        enabled = enabled,
        endpoint = endpoint,
        streamName = streamName,
    ).copy(
        lastUploadAt = lastUploadAt,
        lastUploadStatus = lastUploadStatus,
    )

fun otlpLogUploadActionSnapshot(
    config: OtlpLogUploadConfig,
    status: LogUploadStatus,
    tokenConfigured: Boolean = status.tokenConfigured,
): OtlpLogUploadActionSnapshot =
    OtlpLogUploadActionSnapshot(
        enabled = config.enabled,
        endpoint = config.endpoint,
        streamName = config.streamName,
    ).withRuntimeStatus(
        status = status,
        tokenConfigured = tokenConfigured,
    )

fun OtlpLogUploadActionSnapshot.withRuntimeStatus(
    status: LogUploadStatus,
    tokenConfigured: Boolean = status.tokenConfigured,
): OtlpLogUploadActionSnapshot =
    copy(
        pendingCount = status.pendingCount,
        isUploading = status.isUploading,
        lastUploadAt = status.lastUploadAt,
        lastUploadStatus = status.lastUploadStatus,
        tokenConfigured = tokenConfigured,
    )

class LogUploadActionCoordinator(
    private val repository: LogUploadRepository,
) {
    suspend fun current(): OtlpLogUploadActionSnapshot =
        snapshot()

    suspend fun saveConfig(
        enabled: Boolean,
        endpoint: String,
        streamName: String,
    ): OtlpLogUploadActionSnapshot {
        repository.saveConfig(
            enabled = enabled,
            endpoint = endpoint,
            streamName = streamName,
        )
        return snapshot()
    }

    suspend fun saveSettings(
        enabled: Boolean,
        endpoint: String,
        streamName: String,
        token: String,
    ): OtlpLogUploadActionSnapshot {
        repository.saveConfig(
            enabled = enabled,
            endpoint = endpoint,
            streamName = streamName,
        )
        val trimmedToken = token.trim()
        if (trimmedToken.isNotEmpty()) {
            repository.saveToken(trimmedToken)
        }
        return snapshot()
    }

    suspend fun saveToken(token: String): OtlpLogUploadActionSnapshot {
        repository.saveToken(token)
        return snapshot()
    }

    suspend fun clearToken(): OtlpLogUploadActionSnapshot {
        repository.clearToken()
        return snapshot()
    }

    suspend fun runNow(): OtlpLogUploadActionSnapshot {
        repository.uploadPendingLogs()
        return snapshot()
    }

    private suspend fun snapshot(): OtlpLogUploadActionSnapshot {
        val config = repository.getConfig()
        val status = repository.status.first()
        return otlpLogUploadActionSnapshot(
            config = config,
            status = status,
            tokenConfigured = status.tokenConfigured || repository.isTokenConfigured(),
        )
    }
}
