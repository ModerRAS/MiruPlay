package com.miruplay.tv.webcontrol

import com.miruplay.tv.repository.LogUploadRepository
import kotlinx.coroutines.flow.first

private const val DEFAULT_LOG_UPLOAD_STREAM_NAME = "miruplay"

suspend fun LogUploadRepository.getWebControlLogUpload(): LogUploadDto {
    val tokenConfigured = isTokenConfigured()
    return LogUploadDto(
        config = OtlpLogUploadConfigDto.from(getConfig()),
        status = LogUploadStatusDto.from(status.first(), tokenConfigured),
        tokenConfigured = tokenConfigured,
    )
}

suspend fun LogUploadRepository.saveWebControlLogUploadConfig(
    request: LogUploadConfigRequest,
): LogUploadDto {
    if (request.enabled && request.endpoint.isBlank()) {
        throw IllegalArgumentException("请填写 OpenObserve API 地址")
    }
    saveConfig(
        enabled = request.enabled,
        endpoint = request.endpoint.trim(),
        streamName = request.streamName.trim().ifBlank { DEFAULT_LOG_UPLOAD_STREAM_NAME },
    )
    return getWebControlLogUpload()
}

suspend fun LogUploadRepository.saveWebControlLogUploadToken(
    request: LogUploadTokenRequest,
): LogUploadDto {
    if (request.token.isBlank()) {
        throw IllegalArgumentException("请填写 OpenObserve Token")
    }
    saveToken(request.token.trim())
    return getWebControlLogUpload()
}

suspend fun LogUploadRepository.clearWebControlLogUploadToken(): LogUploadDto {
    clearToken()
    return getWebControlLogUpload()
}

suspend fun LogUploadRepository.runWebControlLogUploadNow(): LogUploadDto {
    uploadPendingLogs()
    return getWebControlLogUpload()
}
