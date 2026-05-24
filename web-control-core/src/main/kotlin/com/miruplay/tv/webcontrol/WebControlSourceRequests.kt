package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.connectionPasswordOrNull
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
    val source = request.toMediaSourceInfo()
    val sourceId = requireWebControlSuccess(addSource(source), "添加媒体源失败")
    val persisted = source.copy(id = sourceId)
    val connected = testConnection(persisted).connected
    val savedSource = persisted.copy(isConnected = connected)
    requireWebControlSuccess(updateSource(savedSource), "更新媒体源失败")
    return savedSource.safeForApi()
}

suspend fun MediaSourceRepository.updateWebControlSource(
    sourceId: Long,
    request: SourceRequest,
): MediaSourceInfo {
    val existing = requireWebControlSuccess(getSourceById(sourceId), "媒体源不存在")
    val source = request.toMediaSourceInfo(
        sourceId = sourceId,
        fallbackPassword = existing.connectionPasswordOrNull(),
        isConnected = existing.isConnected,
        lastScanned = existing.lastScanned,
    )
    requireWebControlSuccess(updateSource(source), "更新媒体源失败")
    return source.safeForApi()
}

suspend fun MediaSourceRepository.removeWebControlSource(sourceId: Long) {
    requireWebControlSuccess(removeSource(sourceId), "删除媒体源失败")
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
            message = if (data) "连接正常" else "无法连接",
        )
        is Result.Error -> SourceTestResponse(
            connected = false,
            message = error.toUserMessage(),
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

private fun MediaSourceType.webControlSourceLocation(location: String): String =
    when (this) {
        MediaSourceType.SMB -> MediaSourceInfoConventions.normalizeSmbRoot(location)
        MediaSourceType.LOCAL, MediaSourceType.WEBDAV -> location
    }
