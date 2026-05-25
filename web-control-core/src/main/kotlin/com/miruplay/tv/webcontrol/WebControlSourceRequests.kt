package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceConnectionTestResult
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.mediaSourceConnectionFailedMessage
import com.miruplay.tv.repository.MediaSourceActionCoordinator
import com.miruplay.tv.repository.MediaSourceAddActionResult
import com.miruplay.tv.repository.MediaSourceAddFailurePhase
import com.miruplay.tv.repository.MediaSourceRepository

fun SourceRequest.toMediaSourceInfo(
    sourceId: Long = id,
    fallbackPassword: String? = null,
    isConnected: Boolean = false,
    lastScanned: Long = 0L,
): MediaSourceInfo {
    val sourceType = parseWebControlSourceType(type)
    val sourceLocation = sourceType.webControlSourceLocation(location)
    return MediaSourceInfo(
        id = sourceId,
        name = name.trim().ifBlank { sourceType.webControlDefaultSourceName() },
        type = sourceType,
        connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
            type = sourceType,
            location = sourceLocation,
            displayName = displayName.orEmpty(),
            username = username.orEmpty(),
            password = password?.takeIf { it.isNotBlank() } ?: fallbackPassword.orEmpty(),
        ),
        isConnected = isConnected,
        lastScanned = lastScanned,
    )
}

fun SourceTestRequest.toMediaSourceInfo(): MediaSourceInfo {
    val sourceType = parseWebControlSourceType(type)
    val sourceLocation = sourceType.webControlSourceLocation(location)
    return MediaSourceInfo(
        name = "test",
        type = sourceType,
        connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
            type = sourceType,
            location = sourceLocation,
            displayName = displayName.orEmpty(),
            username = username.orEmpty(),
            password = password.orEmpty(),
        ),
    )
}

suspend fun MediaSourceRepository.addWebControlSource(
    request: SourceRequest,
    testConnection: suspend (MediaSourceInfo) -> SourceTestResponse,
): MediaSourceInfo {
    val coordinator = MediaSourceActionCoordinator(this)
    return when (
        val result = coordinator.addSource(request.toMediaSourceInfo()) { source ->
            Result.success(testConnection(source).connected)
        }
    ) {
        is MediaSourceAddActionResult.Saved -> result.source.safeForApi()
        is MediaSourceAddActionResult.Failed -> {
            val prefix = when (result.phase) {
                MediaSourceAddFailurePhase.AddSource -> "添加媒体源失败"
                MediaSourceAddFailurePhase.UpdateConnectionState -> "更新媒体源失败"
            }
            throw IllegalStateException("$prefix: ${result.error.toUserMessage()}")
        }
    }
}

suspend fun MediaSourceRepository.updateWebControlSource(
    sourceId: Long,
    request: SourceRequest,
): MediaSourceInfo {
    requireWebControlSuccess(getSourceById(sourceId), "媒体源不存在")
    val source = request.toMediaSourceInfo(
        sourceId = sourceId,
    )
    return requireWebControlSuccess(
        MediaSourceActionCoordinator(this).updateSource(source),
        "更新媒体源失败",
    ).safeForApi()
}

suspend fun MediaSourceRepository.removeWebControlSource(sourceId: Long) {
    requireWebControlSuccess(
        MediaSourceActionCoordinator(this).removeSource(sourceId),
        "删除媒体源失败",
    )
}

suspend fun MediaSourceRepository.listWebControlSources(): List<MediaSourceInfo> =
    getSources().getOrNull().orEmpty().map { it.safeForApi() }

suspend fun MediaSourceRepository.scanAllWebControlSources(
    scanSource: suspend (MediaSourceInfo) -> Result<SourceScanResponse>,
): List<SourceScanResponse> =
    getSources().getOrNull().orEmpty().map { source ->
        when (val result = scanSource(source)) {
            is Result.Success -> result.data
            is Result.Error -> source.toWebControlSourceScanErrorResponse(result.error.toUserMessage())
        }
    }

suspend fun MediaSourceRepository.scanWebControlSource(
    sourceId: Long,
    scanSource: suspend (MediaSourceInfo) -> Result<SourceScanResponse>,
): SourceScanResponse {
    val source = requireWebControlSuccess(getSourceById(sourceId), "媒体源不存在")
    return when (val result = scanSource(source)) {
        is Result.Success -> result.data
        is Result.Error -> source.toWebControlSourceScanErrorResponse(result.error.toUserMessage())
    }
}

suspend fun MediaSourceRepository.scanAllWebControlSourcesFromScanResult(
    scanSource: suspend (MediaSourceInfo) -> Result<ScanResult>,
): List<SourceScanResponse> =
    scanAllWebControlSources { source ->
        Result.success(source.scanWebControlSourceWith(scanSource))
    }

suspend fun MediaSourceRepository.scanWebControlSourceFromScanResult(
    sourceId: Long,
    scanSource: suspend (MediaSourceInfo) -> Result<ScanResult>,
): SourceScanResponse =
    scanWebControlSource(sourceId) { source ->
        Result.success(source.scanWebControlSourceWith(scanSource))
    }

suspend fun MediaSourceInfo.scanWebControlSourceWith(
    scanSource: suspend (MediaSourceInfo) -> Result<ScanResult>,
): SourceScanResponse =
    when (val result = scanSource(this)) {
        is Result.Success -> result.data.toWebControlSourceScanResponse(id)
        is Result.Error -> toWebControlSourceScanErrorResponse(result.error.toUserMessage())
    }

fun parseWebControlSourceType(type: String): MediaSourceType =
    runCatching { MediaSourceType.valueOf(type.trim().uppercase()) }
        .getOrElse { throw IllegalArgumentException("不支持的媒体源类型: $type") }

fun MediaSourceType.webControlDefaultSourceName(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地媒体库"
        MediaSourceType.WEBDAV -> "WebDAV 媒体库"
        MediaSourceType.SMB -> "SMB 共享"
    }

fun Result<Boolean>.toWebControlSourceTestResponse(): SourceTestResponse =
    when (this) {
        is Result.Success -> SourceTestResponse(
            connected = data,
            message = if (data) "连接正常" else mediaSourceConnectionFailedMessage(),
        )
        is Result.Error -> SourceTestResponse(
            connected = false,
            message = error.toUserMessage(),
        )
    }

fun MediaSourceConnectionTestResult.toWebControlSourceTestResponse(): SourceTestResponse =
    when (this) {
        MediaSourceConnectionTestResult.Success -> SourceTestResponse(
            connected = true,
            message = "连接正常",
        )
        is MediaSourceConnectionTestResult.Failed -> SourceTestResponse(
            connected = false,
            message = message,
        )
    }

fun ScanResult.toWebControlSourceScanResponse(sourceId: Long): SourceScanResponse =
    toWebControlSourceScanResponse(
        sourceId = sourceId,
        animeName = animeName,
        episodesFound = episodesFound,
        newEpisodes = newEpisodes,
        updatedEpisodes = updatedEpisodes,
    )

fun toWebControlSourceScanResponse(
    sourceId: Long,
    animeName: String,
    episodesFound: Int,
    newEpisodes: Int,
    updatedEpisodes: Int,
): SourceScanResponse =
    SourceScanResponse(
        sourceId = sourceId,
        animeName = animeName.ifBlank { "Unknown" },
        episodesFound = episodesFound.coerceAtLeast(0),
        newEpisodes = newEpisodes.coerceAtLeast(0),
        updatedEpisodes = updatedEpisodes.coerceAtLeast(0),
    )

fun MediaSourceInfo.toWebControlSourceScanErrorResponse(message: String): SourceScanResponse =
    SourceScanResponse(
        sourceId = id,
        animeName = name.ifBlank { type.webControlDefaultSourceName() },
        episodesFound = 0,
        newEpisodes = 0,
        updatedEpisodes = 0,
        error = message.takeIf { it.isNotBlank() } ?: "扫描媒体源失败",
    )

private fun MediaSourceType.webControlSourceLocation(location: String): String =
    when (this) {
        MediaSourceType.SMB -> MediaSourceInfoConventions.normalizeSmbRoot(location)
        MediaSourceType.LOCAL, MediaSourceType.WEBDAV -> location
    }
