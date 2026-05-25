package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType

class DesktopMediaSourceFactory : MediaSourceFactory {
    override fun create(info: MediaSourceInfo): Result<DesktopMediaSource> =
        Result.success(desktopSourceFromInfo(info))

    override fun supports(type: MediaSourceType): Boolean = true
}

fun desktopSourceFromInfo(info: MediaSourceInfo): DesktopMediaSource =
    when (info.type) {
        MediaSourceType.LOCAL -> desktopLocalSourceFromInfo(info)
        MediaSourceType.WEBDAV -> desktopWebDavSourceFromInfo(info)
        MediaSourceType.SMB -> desktopSmbSourceFromInfo(info)
    }

fun desktopLocalSourceFromInfo(info: MediaSourceInfo): DesktopLocalMediaSource =
    DesktopLocalMediaSource(info)

fun desktopWebDavSourceFromInfo(info: MediaSourceInfo): DesktopWebDavMediaSource =
    DesktopWebDavMediaSource(info)

fun desktopSmbSourceFromInfo(info: MediaSourceInfo): DesktopSmbMediaSource =
    DesktopSmbMediaSource(info)
