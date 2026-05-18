package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.model.MediaSourceInfoConventions

internal fun playableUriFor(
    source: DesktopMediaSource?,
    bridge: DesktopPlaybackUriBridge,
    mediaPath: String,
): String {
    val path = mediaPath.trim()
    return if (source != null && MediaSourceInfoConventions.shouldBridgeForPlayback(source.info.type, path)) {
        bridge.playableUri(source, path)
    } else {
        path
    }
}

internal interface DesktopPlaybackUriBridge {
    fun playableUri(source: DesktopMediaSource, path: String): String
}
