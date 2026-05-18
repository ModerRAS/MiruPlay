package com.miruplay.tv.player.mpv

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Windows mpv runtime configuration.
 *
 * The intended packaged layout is:
 *   mpv/
 *     mpv.exe
 *     portable_config/
 *       mpv.conf
 *       vs/MEMC_RIFE_*.vpy
 */
data class MpvRuntimeConfig(
    val mpvExecutable: Path,
    val configDirectory: Path? = null,
    val ipcServer: String? = null,
    val startFullscreen: Boolean = false,
    val forceWindow: Boolean = true,
    val keepOpen: Boolean = false,
    val terminal: Boolean = false,
    val rife: RifeInterpolationConfig? = null,
    val extraArguments: List<String> = emptyList(),
)

fun mpvRuntimeConfigFromInputs(
    mpvPath: String,
    configDir: String,
    fullscreen: Boolean,
    keepOpen: Boolean,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
): MpvRuntimeConfig =
    MpvRuntimeConfig(
        mpvExecutable = Paths.get(mpvPath.trim()),
        configDirectory = configDir.trim().takeIf { it.isNotBlank() }?.let(Paths::get),
        startFullscreen = fullscreen,
        keepOpen = keepOpen,
        rife = if (rifeEnabled) RifeInterpolationConfig(backend = rifeBackend) else null,
    )

data class RifeInterpolationConfig(
    val backend: RifeBackend = RifeBackend.NVIDIA,
    val scriptPath: Path? = null,
    val bufferedFrames: Int = 4,
    val concurrentFrames: String = "auto",
    val userData: String = "",
) {
    init {
        require(bufferedFrames > 0) { "bufferedFrames must be greater than zero" }
        require(concurrentFrames.isNotBlank()) { "concurrentFrames must not be blank" }
    }
}

enum class RifeBackend(val scriptName: String) {
    NVIDIA("MEMC_RIFE_NV.vpy"),
    DIRECTML("MEMC_RIFE_DML.vpy"),
    STANDARD("MEMC_RIFE_STD.vpy"),
}
