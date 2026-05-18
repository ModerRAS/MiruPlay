package com.miruplay.tv.desktop

import com.miruplay.tv.player.mpv.MpvRuntimeDiscovery
import java.nio.file.Path

internal object DesktopRuntimeDefaults {
    fun mpvPath(): String = layout().executable.toString()

    fun configDirectory(): String = layout().configDirectory.toString()

    fun runtimeRoot(mpvPath: String, configDir: String): Path =
        MpvRuntimeDiscovery.inferRootFromInputs(mpvPath, configDir)

    private fun layout() = MpvRuntimeDiscovery.defaultLayout()
}
