package com.miruplay.tv.player.mpv

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val MPV_RUNTIME_PROPERTY = "miruplay.mpv.runtime"
private const val MPV_RUNTIME_ENV = "MIRUPLAY_MPV_RUNTIME"

data class MpvRuntimeLayout(
    val rootDirectory: Path,
    val executable: Path,
    val configDirectory: Path,
) {
    fun rifeScript(backend: RifeBackend): Path =
        configDirectory.resolve("vs").resolve(backend.scriptName)

    fun availableRifeBackends(): Set<RifeBackend> =
        RifeBackend.entries
            .filter { backend -> Files.isRegularFile(rifeScript(backend)) }
            .toSet()
}

object MpvRuntimeDiscovery {
    fun findBundledRuntime(
        appHome: Path? = currentApplicationHome(),
        workingDirectory: Path = Paths.get("").toAbsolutePath().normalize(),
    ): MpvRuntimeLayout? =
        candidateRoots(appHome, workingDirectory)
            .map(::layoutFor)
            .firstOrNull { layout ->
                Files.isRegularFile(layout.executable) && Files.isDirectory(layout.configDirectory)
            }

    fun defaultRuntimeRoot(
        appHome: Path? = currentApplicationHome(),
        workingDirectory: Path = Paths.get("").toAbsolutePath().normalize(),
    ): Path =
        candidateRoots(appHome, workingDirectory)
            .firstOrNull()
            ?: workingDirectory.resolve("runtime").resolve("mpv").normalize()

    fun defaultLayout(
        appHome: Path? = currentApplicationHome(),
        workingDirectory: Path = Paths.get("").toAbsolutePath().normalize(),
    ): MpvRuntimeLayout =
        findBundledRuntime(appHome, workingDirectory)
            ?: layoutFor(defaultRuntimeRoot(appHome, workingDirectory))

    fun inferRootFromInputs(mpvPath: String, configDir: String): Path {
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

    fun layoutFor(rootDirectory: Path): MpvRuntimeLayout {
        val root = rootDirectory.toAbsolutePath().normalize()
        return MpvRuntimeLayout(
            rootDirectory = root,
            executable = root.resolve("mpv.exe"),
            configDirectory = root.resolve("portable_config"),
        )
    }

    fun candidateRoots(
        appHome: Path? = currentApplicationHome(),
        workingDirectory: Path = Paths.get("").toAbsolutePath().normalize(),
    ): List<Path> {
        val configured = listOfNotNull(
            System.getProperty(MPV_RUNTIME_PROPERTY)?.takeIf { it.isNotBlank() }?.let(Paths::get),
            System.getenv(MPV_RUNTIME_ENV)?.takeIf { it.isNotBlank() }?.let(Paths::get),
        )
        val discovered = listOfNotNull(
            appHome?.resolve("runtime")?.resolve("mpv"),
            appHome?.resolve("app")?.resolve("runtime")?.resolve("mpv"),
            workingDirectory.resolve("runtime").resolve("mpv"),
            workingDirectory.resolve("mpv"),
        )
        return (configured + discovered)
            .map { it.toAbsolutePath().normalize() }
            .distinct()
    }

    private fun currentApplicationHome(): Path? =
        runCatching {
            val location = Paths.get(
                MpvRuntimeDiscovery::class.java.protectionDomain.codeSource.location.toURI()
            ).toAbsolutePath().normalize()
            if (Files.isRegularFile(location)) {
                location.parent?.parent
            } else {
                location
            }
        }.getOrNull()
}
