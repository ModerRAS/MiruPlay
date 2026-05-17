package com.miruplay.tv.desktop

import com.miruplay.tv.repository.MetadataBatchConflict
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.MetadataBatchUpdate
import com.miruplay.tv.player.mpv.MpvRuntimeDiscovery
import java.nio.file.Path
import java.nio.file.Paths

internal object DesktopRuntimeDefaults {
    fun mpvPath(): String = runtimeRoot().resolve("mpv.exe").toString()

    fun configDirectory(): String = runtimeRoot().resolve("portable_config").toString()

    fun runtimeRoot(mpvPath: String, configDir: String): Path {
        val configPath = configDir.trim().takeIf { it.isNotBlank() }?.let(Paths::get)
        if (configPath?.fileName?.toString() == "portable_config") {
            return configPath.parent ?: configPath
        }
        return mpvPath.trim()
            .takeIf { it.isNotBlank() }
            ?.let(Paths::get)
            ?.parent
            ?: Paths.get("")
    }

    private fun runtimeRoot(): Path =
        MpvRuntimeDiscovery.findBundledRuntime()?.rootDirectory
            ?: MpvRuntimeDiscovery.defaultRuntimeRoot()
}

internal typealias DesktopBangumiBatchMatch = MetadataBatchMatch
internal typealias DesktopBangumiBatchUpdate = MetadataBatchUpdate
internal typealias DesktopBangumiBatchConflict = MetadataBatchConflict
internal typealias DesktopBangumiBatchPlan = MetadataBatchPlan
