package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType

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
