package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopSmbMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopWebDavMediaSource
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType

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
