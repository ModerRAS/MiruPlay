package com.miruplay.tv.player.mpv

import com.miruplay.tv.model.PlaybackSource
import java.io.File
import java.nio.file.Path
import java.util.Locale

class MpvCommandBuilder(
    private val config: MpvRuntimeConfig,
) {
    fun build(source: PlaybackSource): List<String> = buildList {
        add(config.mpvExecutable.toString())

        config.configDirectory?.let { add("--config-dir=$it") }
        config.ipcServer?.takeIf { it.isNotBlank() }?.let { add("--input-ipc-server=$it") }

        add("--terminal=${yesNo(config.terminal)}")
        add("--force-window=${yesNo(config.forceWindow)}")
        add("--keep-open=${yesNo(config.keepOpen)}")

        if (config.startFullscreen) {
            add("--fs")
        }

        config.rife?.let { add("--vf-append=${it.toVapourSynthFilter(config.configDirectory)}") }

        source.subtitleTracks
            .filter { it.isExternal && it.path.isNotBlank() }
            .forEach { subtitle -> add("--sub-file=${subtitle.path}") }

        if (source.startPosition > 0) {
            add("--start=${formatStartSeconds(source.startPosition)}")
        }

        addAll(config.extraArguments)
        add(source.uri)
    }

    private fun RifeInterpolationConfig.toVapourSynthFilter(configDirectory: Path?): String {
        val script = scriptArgument(configDirectory)
        return "vapoursynth=$script:$bufferedFrames:$concurrentFrames:$userData"
    }

    private fun RifeInterpolationConfig.scriptArgument(configDirectory: Path?): String {
        val explicitScript = scriptPath
        if (explicitScript != null) {
            return configRelativeOrFixedLength(explicitScript, configDirectory)
        }

        require(configDirectory != null) {
            "configDirectory is required when using a bundled RIFE backend without scriptPath"
        }
        return "~~home/vs/${backend.scriptName}"
    }

    private fun configRelativeOrFixedLength(scriptPath: Path, configDirectory: Path?): String {
        val normalizedScript = scriptPath.toAbsolutePath().normalize()
        val normalizedConfig = configDirectory?.toAbsolutePath()?.normalize()
        if (normalizedConfig != null && normalizedScript.startsWith(normalizedConfig)) {
            val relative = normalizedConfig.relativize(normalizedScript)
                .joinToString("/") { it.toString() }
                .replace(File.separatorChar, '/')
            return "~~home/$relative"
        }
        return fixedLengthQuote(normalizedScript.toString())
    }

    private fun fixedLengthQuote(value: String): String {
        val byteLength = value.toByteArray(Charsets.UTF_8).size
        return "%$byteLength%$value"
    }

    private fun formatStartSeconds(positionMs: Long): String {
        if (positionMs % 1_000L == 0L) {
            return (positionMs / 1_000L).toString()
        }
        return String.format(Locale.US, "%.3f", positionMs / 1_000.0)
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
}
