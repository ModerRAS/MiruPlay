package com.miruplay.tv.desktop

import com.miruplay.tv.repository.MetadataBatchConflict
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.MetadataBatchUpdate
import java.nio.file.Paths

internal object DesktopRuntimeDefaults {
    fun mpvPath(): String = Paths.get("runtime", "mpv", "mpv.exe").toString()

    fun configDirectory(): String = Paths.get("runtime", "mpv", "portable_config").toString()
}

internal typealias DesktopBangumiBatchMatch = MetadataBatchMatch
internal typealias DesktopBangumiBatchUpdate = MetadataBatchUpdate
internal typealias DesktopBangumiBatchConflict = MetadataBatchConflict
internal typealias DesktopBangumiBatchPlan = MetadataBatchPlan
