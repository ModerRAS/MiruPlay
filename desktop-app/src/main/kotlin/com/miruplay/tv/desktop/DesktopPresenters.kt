package com.miruplay.tv.desktop

import com.miruplay.tv.repository.MetadataBatchConflict
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.MetadataBatchUpdate
import com.miruplay.tv.player.mpv.MpvRuntimeDiscovery
import java.nio.file.Path

internal object DesktopRuntimeDefaults {
    fun mpvPath(): String = layout().executable.toString()

    fun configDirectory(): String = layout().configDirectory.toString()

    fun runtimeRoot(mpvPath: String, configDir: String): Path =
        MpvRuntimeDiscovery.inferRootFromInputs(mpvPath, configDir)

    private fun layout() = MpvRuntimeDiscovery.defaultLayout()
}

internal typealias DesktopBangumiBatchMatch = MetadataBatchMatch
internal typealias DesktopBangumiBatchUpdate = MetadataBatchUpdate
internal typealias DesktopBangumiBatchConflict = MetadataBatchConflict
internal typealias DesktopBangumiBatchPlan = MetadataBatchPlan
