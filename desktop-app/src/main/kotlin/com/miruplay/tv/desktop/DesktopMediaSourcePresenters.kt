package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopSmbMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopWebDavMediaSource
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord

internal fun sourceLabel(source: MediaSourceInfo): String =
    "${source.name} · ${source.type.name}"

internal fun webDavSourceInfo(
    url: String,
    username: String,
    password: String,
): MediaSourceInfo =
    MediaSourceInfo(
        name = url.substringAfter("://", url).trim('/').ifBlank { "WebDAV" },
        type = MediaSourceType.WEBDAV,
        connectionInfo = buildMap {
            put("url", url)
            if (username.isNotBlank()) put("username", username)
            if (password.isNotBlank()) put("password", password)
        },
        isConnected = true,
    )

internal fun smbSourceInfo(
    url: String,
    domain: String,
    username: String,
    password: String,
): MediaSourceInfo =
    DesktopSmbMediaSource.normalizeRoot(url).let { normalizedUrl ->
    MediaSourceInfo(
        name = normalizedUrl.removePrefix("smb://").trim('/').ifBlank { "SMB" },
        type = MediaSourceType.SMB,
        connectionInfo = buildMap {
            put("url", normalizedUrl)
            if (domain.isNotBlank()) put("domain", domain)
            if (username.isNotBlank()) put("username", username)
            if (password.isNotBlank()) put("password", password)
        },
        isConnected = true,
    )
    }

internal fun desktopSourceFromInfo(info: MediaSourceInfo): DesktopMediaSource =
    when (info.type) {
        MediaSourceType.LOCAL -> DesktopLocalMediaSource(info)
        MediaSourceType.WEBDAV -> desktopWebDavSourceFromInfo(info)
        MediaSourceType.SMB -> DesktopSmbMediaSource(info)
    }

internal fun desktopWebDavSourceFromInfo(info: MediaSourceInfo): DesktopWebDavMediaSource =
    DesktopWebDavMediaSource.create(
        name = info.name,
        url = requireNotNull(info.connectionInfo["url"]) { "WebDAV source requires connectionInfo[url]" },
        username = info.connectionInfo["username"].orEmpty(),
        password = info.connectionInfo["password"].orEmpty(),
    )

internal fun remoteParent(path: String): String? {
    val clean = path.trimEnd('/')
    if (clean.isBlank() || clean == "/") return null
    if (clean.startsWith("smb://", ignoreCase = true)) {
        val segments = clean.removePrefix("smb://").split('/').filter { it.isNotBlank() }
        if (segments.size <= 2) return null
        return "smb://${segments.dropLast(1).joinToString("/")}"
    }

    val parent = clean.trim('/').substringBeforeLast('/', "")
    return if (parent.isBlank()) "" else "/$parent"
}

internal fun List<MediaSourceInfo>.upsertSource(source: MediaSourceInfo): List<MediaSourceInfo> =
    map { if (it.id == source.id) source else it }.let { updated ->
        if (updated.none { it.id == source.id }) updated + source else updated
    }

internal fun recentDisplayName(record: ProgressRecord): String =
    record.episodeId.substringAfterLast('\\').substringAfterLast('/').ifBlank { record.episodeId }
