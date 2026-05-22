package com.miruplay.tv.player.mpv

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

const val DEFAULT_MPV_IPC_SERVER = "miruplay-mpv"

fun defaultMpvIpcServer(
    osName: String = System.getProperty("os.name").orEmpty(),
    tempDirectory: String = System.getProperty("java.io.tmpdir").orEmpty(),
    uniqueId: String = UUID.randomUUID().toString(),
): String {
    val serverName = "$DEFAULT_MPV_IPC_SERVER-${uniqueId.toMpvIpcSuffix()}"
    return if (osName.contains("Windows", ignoreCase = true)) {
        "\\\\.\\pipe\\$serverName"
    } else {
        Paths.get(tempDirectory.ifBlank { "." }, "$serverName.sock").toString()
    }
}

private fun String.toMpvIpcSuffix(): String =
    filter(Char::isLetterOrDigit).takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().filter(Char::isLetterOrDigit)

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
        ipcServer = defaultMpvIpcServer(),
        startFullscreen = fullscreen,
        keepOpen = keepOpen,
        rife = if (rifeEnabled) RifeInterpolationConfig(backend = rifeBackend) else null,
    )

fun MpvRuntimeConfig.validateLaunchRuntime(): Result<MpvRuntimeVerification?> {
    if (!Files.isRegularFile(mpvExecutable)) {
        return Result.failure(
            AppError.PlaybackError.StreamError(missingMpvExecutableMessage(mpvExecutable))
        )
    }

    val requestedRife = rife ?: return Result.success(null)
    requestedRife.scriptPath?.let { scriptPath ->
        val normalizedScript = scriptPath.toAbsolutePath().normalize()
        return if (Files.isRegularFile(normalizedScript)) {
            Result.success(null)
        } else {
            Result.failure(
                AppError.PlaybackError.StreamError(missingRifeScriptMessage(normalizedScript))
            )
        }
    }

    val configDirectory = configDirectory
        ?: return Result.failure(
            AppError.PlaybackError.StreamError(missingRifeConfigDirectoryMessage())
        )

    val verification = MpvRuntimeVerifier.verify(
        MpvRuntimeLayout(
            rootDirectory = configDirectory.parent ?: mpvExecutable.parent ?: configDirectory,
            executable = mpvExecutable,
            configDirectory = configDirectory,
        )
    )
    if (requestedRife.backend !in verification.availableRifeBackends) {
        return Result.failure(
            AppError.PlaybackError.StreamError(
                missingRifeScriptMessage(verification.layout.rifeScript(requestedRife.backend))
            )
        )
    }

    return Result.success(verification)
}

private fun missingMpvExecutableMessage(path: Path): String =
    "mpv executable not found: $path. Choose the bundled runtime path, install mpv, or run Check runtime before launching."

private fun missingRifeScriptMessage(path: Path): String =
    "RIFE is enabled but script was not found: $path. Pick an installed backend, prepare the bundled runtime, or turn RIFE off."

private fun missingRifeConfigDirectoryMessage(): String =
    "RIFE is enabled but configDirectory is empty. Set portable_config, choose a runtime root, or turn RIFE off."

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
