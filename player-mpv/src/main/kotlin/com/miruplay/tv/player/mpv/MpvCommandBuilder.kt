package com.miruplay.tv.player.mpv

import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.playbackSourceFromInputs
import java.io.File
import java.nio.file.Path

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
            add("--start=${PlaybackTimingConventions.formatMpvStartSeconds(source.startPosition)}")
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

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
}

fun MpvCommandBuilder.buildPreview(source: PlaybackSource): String =
    build(source).toMpvCommandPreview()

fun mpvCommandPreviewFromInputs(
    mpvPath: String,
    configDir: String,
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    fullscreen: Boolean,
    keepOpen: Boolean,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
    mediaSourceId: String = "desktop-compose",
    episodeId: String? = null,
    blankMediaMessage: String = "Choose a media URI or file path before launching playback.",
): String {
    val source = playbackSourceFromInputs(
        mediaPath = mediaPath,
        subtitlePath = subtitlePath,
        startSeconds = startSeconds,
        mediaSourceId = mediaSourceId,
        episodeId = episodeId,
        blankMediaMessage = blankMediaMessage,
    )
    val config = mpvRuntimeConfigFromInputs(
        mpvPath = mpvPath,
        configDir = configDir,
        fullscreen = fullscreen,
        keepOpen = keepOpen,
        rifeEnabled = rifeEnabled,
        rifeBackend = rifeBackend,
    )
    return MpvCommandBuilder(config).buildPreview(source)
}

fun List<String>.toMpvCommandPreview(): String =
    joinToString(" ") { it.toMpvPreviewArgument() }

fun String.toMpvPreviewArgument(): String =
    if (any { it.isWhitespace() }) "\"${replace("\"", "\\\"")}\"" else this
