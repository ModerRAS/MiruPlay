package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType

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

fun parseWebControlSourceType(type: String): MediaSourceType =
    runCatching { MediaSourceType.valueOf(type.trim().uppercase()) }
        .getOrElse { throw IllegalArgumentException("不支持的媒体源类型: $type") }

fun MediaSourceType.webControlDefaultSourceName(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地媒体库"
        MediaSourceType.WEBDAV -> "WebDAV 媒体库"
        MediaSourceType.SMB -> "SMB 共享"
    }

private fun MediaSourceType.webControlSourceLocation(location: String): String =
    when (this) {
        MediaSourceType.SMB -> MediaSourceInfoConventions.normalizeSmbRoot(location)
        MediaSourceType.LOCAL, MediaSourceType.WEBDAV -> location
    }
