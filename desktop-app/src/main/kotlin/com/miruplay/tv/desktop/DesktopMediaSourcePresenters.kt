package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopSmbMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopWebDavMediaSource
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.ProgressRecord

internal fun sourceLabel(source: MediaSourceInfo): String =
    "${source.name} · ${source.type.name}"

internal fun webDavSourceInfo(
    url: String,
    username: String,
    password: String,
): MediaSourceInfo =
    MediaSourceInfoConventions.webDav(
        url = url,
        username = username,
        password = password,
        isConnected = true,
    )

internal fun smbSourceInfo(
    url: String,
    domain: String,
    username: String,
    password: String,
): MediaSourceInfo =
    MediaSourceInfoConventions.smb(
        url = url,
        domain = domain,
        username = username,
        password = password,
        isConnected = true,
    )

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
    return MediaPathConventions.remoteParent(path)
}

internal fun List<MediaSourceInfo>.upsertSource(source: MediaSourceInfo): List<MediaSourceInfo> =
    map { if (it.id == source.id) source else it }.let { updated ->
        if (updated.none { it.id == source.id }) updated + source else updated
    }

internal fun recentDisplayName(record: ProgressRecord): String =
    MediaPathConventions.fileName(record.episodeId).ifBlank { record.episodeId }
