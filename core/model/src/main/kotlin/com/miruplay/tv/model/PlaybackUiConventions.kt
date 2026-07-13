package com.miruplay.tv.model

import kotlin.math.roundToLong

const val PLAYBACK_SEEK_BACK_SECONDS = 10

const val PLAYBACK_SEEK_FORWARD_SECONDS = 30

const val PLAYBACK_SPEED_MIN = 0.25f

const val PLAYBACK_SPEED_NORMAL = 1.0f

const val PLAYBACK_SPEED_MAX = 3.0f

private val playbackSpeedOptionValues: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

fun playbackSpeedOptions(): List<Float> =
    playbackSpeedOptionValues

fun coercePlaybackSpeed(speed: Float): Float =
    if (speed.isFinite()) {
        speed.coerceIn(PLAYBACK_SPEED_MIN, PLAYBACK_SPEED_MAX)
    } else {
        PLAYBACK_SPEED_NORMAL
    }

fun playbackBackLabel(): String = "返回"

fun playbackBackToDetailsLabel(): String = "返回详情"

fun playbackPlayLabel(): String = "播放"

fun playbackPauseLabel(): String = "暂停"

fun playbackRetryLabel(): String = "重试"

fun playbackConfirmExitLabel(): String = "确定退出"

fun playbackStopLabel(): String = "停止"

fun playbackErrorTitle(): String = "播放失败"

fun playbackChooseMediaLabel(): String = "选择媒体"

fun playbackWaitingForMediaLabel(): String = "等待媒体"

fun playbackLocalSourceLabel(): String = "本地播放"

fun playbackRemoteStreamLabel(): String = "远程串流"

fun playbackExternalSubtitleLabel(): String = "字幕外载"

fun playbackSpeedMenuTitle(): String = "播放速度"

fun playbackSubtitlesMenuTitle(): String = "字幕"

fun playbackAudioMenuTitle(): String = "音轨"

fun playbackSubtitleCountLabel(count: Int): String =
    if (count <= 0) "无字幕" else "字幕 $count"

fun playbackSubtitleOffLabel(): String = "关闭字幕"

fun playbackAudioTrackCountLabel(count: Int): String =
    "音轨 ${count.coerceAtLeast(0)}"

fun playbackSubtitleOptionLabel(track: SubtitleTrack, index: Int): String =
    track.title.ifBlank { track.language.ifBlank { playbackSubtitlesMenuTitle() + " ${index + 1}" } }

fun playbackAudioOptionLabel(title: String?, language: String, index: Int): String =
    title?.takeIf { it.isNotBlank() }
        ?: language.ifBlank { playbackAudioMenuTitle() + " ${index + 1}" }

fun playbackSpeedValueLabel(speed: Float): String =
    "${playbackSpeedValue(speed)}x"

fun playbackSpeedChipLabel(speed: Float): String =
    "倍速 ${playbackSpeedValueLabel(speed)}"

fun playbackSeekBackLabel(seconds: Int): String =
    "快退 $seconds 秒"

fun playbackSeekForwardLabel(seconds: Int): String =
    "快进 $seconds 秒"

fun playbackSeekBackCompactLabel(seconds: Int = PLAYBACK_SEEK_BACK_SECONDS): String =
    "-$seconds"

fun playbackSeekForwardCompactLabel(seconds: Int = PLAYBACK_SEEK_FORWARD_SECONDS): String =
    "+$seconds"

fun playbackUnknownDurationLabel(): String = "--:--"

fun playbackSettingsTitleLabel(): String = "播放设置"

fun playbackRuntimeTitleLabel(): String = "运行时"

fun playbackDiagnosticsTitleLabel(): String = "mpv 诊断"

fun playbackMediaPathFieldLabel(): String = "媒体 URI 或文件路径"

fun playbackStartSecondsFieldLabel(): String = "起播秒数"

fun playbackSubtitlePathFieldLabel(): String = "外挂字幕路径"

fun playbackFullscreenToggleLabel(): String = "全屏"

fun playbackKeepOpenToggleLabel(): String = "播完保留窗口"

fun playbackRifeToggleLabel(): String = "RIFE"

fun playbackMpvExecutableFieldLabel(): String = "mpv.exe"

fun playbackPortableConfigFieldLabel(): String = "portable_config"

fun playbackCheckRuntimeActionLabel(): String = "检查运行时"

fun playbackBlankMediaMessage(): String = "请先选择媒体，再启动 mpv。"

fun playbackCommandPreviewErrorMessage(): String = "无法生成 mpv 命令。"

fun playbackMpvRuntimeStateLabel(isPlayerActive: Boolean): String =
    if (isPlayerActive) "mpv 播放中" else "mpv 待命"

fun playbackRifeStateLabel(
    enabled: Boolean,
    backendName: String,
): String =
    if (enabled) "RIFE $backendName" else "RIFE 关闭"

fun playbackMediaTitle(mediaPath: String): String {
    val trimmed = mediaPath.trim()
    if (trimmed.isBlank()) return playbackChooseMediaLabel()
    return MediaPathConventions.stem(trimmed).takeIf { it.isNotBlank() }
        ?: trimmed.substringAfterLast('/').substringAfterLast('\\').ifBlank { trimmed }
}

fun mpvPlaybackSourceLine(
    mediaPath: String,
    rifeEnabled: Boolean,
    rifeBackendName: String,
    isPlayerActive: Boolean,
): String {
    val source = when {
        mediaPath.isBlank() -> playbackWaitingForMediaLabel()
        mediaPath.startsWith("http://", ignoreCase = true) || mediaPath.startsWith("https://", ignoreCase = true) ->
            playbackRemoteStreamLabel()
        else -> playbackLocalSourceLabel()
    }
    return "$source · ${playbackMpvRuntimeStateLabel(isPlayerActive)} · ${playbackRifeStateLabel(rifeEnabled, rifeBackendName)}"
}

fun playbackStartPositionLabel(startSeconds: String): String {
    val startMs = startSeconds.trim()
        .toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?.let { (it * 1000.0).roundToLong() }
        ?: 0L
    return formatPlaybackPosition(startMs)
}

data class PlaybackUiLabels(
    val mediaPath: String,
    val startSeconds: String,
    val subtitlePath: String,
    val fullscreen: String,
    val keepOpen: String,
    val rife: String,
)

fun playbackUiLabels(): PlaybackUiLabels =
    PlaybackUiLabels(
        mediaPath = playbackMediaPathFieldLabel(),
        startSeconds = playbackStartSecondsFieldLabel(),
        subtitlePath = playbackSubtitlePathFieldLabel(),
        fullscreen = playbackFullscreenToggleLabel(),
        keepOpen = playbackKeepOpenToggleLabel(),
        rife = playbackRifeToggleLabel(),
    )

fun mpvPlaybackStatusText(status: String): String {
    val trimmed = status.trim()
    return when {
        trimmed.isBlank() -> "mpv 待命。"
        trimmed == "mpv is idle." -> "mpv 待命。"
        trimmed.startsWith("mpv launched: pid ") -> "mpv 已启动：pid ${trimmed.removePrefix("mpv launched: pid ")}"
        trimmed == "Unable to launch mpv." -> "无法启动 mpv。"
        trimmed == "No mpv process is active." -> "没有正在运行的 mpv 进程。"
        trimmed == "mpv pause toggled." -> "已切换暂停状态。"
        trimmed == "mpv resumed." -> "已继续播放。"
        trimmed == "mpv paused." -> "已暂停播放。"
        trimmed.startsWith("mpv seeked back ") && trimmed.endsWith("s.") ->
            "已后退 ${trimmed.removePrefix("mpv seeked back ").removeSuffix("s.")} 秒。"
        trimmed.startsWith("mpv seeked forward ") && trimmed.endsWith("s.") ->
            "已快进 ${trimmed.removePrefix("mpv seeked forward ").removeSuffix("s.")} 秒。"
        trimmed.startsWith("mpv speed set to ") && trimmed.endsWith(".") ->
            "播放速度已设为 ${trimmed.removePrefix("mpv speed set to ").removeSuffix(".")}。"
        trimmed == "mpv stopped." -> "mpv 已停止。"
        trimmed == "mpv exited." -> "mpv 已退出。"
        trimmed.startsWith("mpv playback completed at ") && trimmed.endsWith(".") ->
            "播放已完成：${trimmed.removePrefix("mpv playback completed at ").removeSuffix(".")}。"
        trimmed.startsWith("mpv position synced at ") && trimmed.endsWith(".") ->
            "播放进度已同步至 ${trimmed.removePrefix("mpv position synced at ").removeSuffix(".")}。"
        trimmed.startsWith("播放出错：mpv executable not found: ") ->
            trimmed.removePrefix("播放出错：").localizedMissingMpvExecutableMessage(prefix = "播放出错：")
        trimmed.startsWith("播放出错：RIFE is enabled but script was not found: ") ->
            trimmed.removePrefix("播放出错：").localizedMissingRifeScriptMessage(prefix = "播放出错：")
        trimmed == "播放出错：RIFE is enabled but configDirectory is empty. Set portable_config, choose a runtime root, or turn RIFE off." ->
            "播放出错：已开启 RIFE，但 portable_config 为空。请设置 portable_config、选择运行时目录，或关闭 RIFE。"
        trimmed == "Choose a media URI or file path before launching mpv." -> "请先选择媒体，再启动 mpv。"
        trimmed == "Unable to build mpv command." -> "无法生成 mpv 命令。"
        else -> trimmed
    }
}

fun playbackRuntimeStatusText(status: String): String =
    status.trim()
        .takeIf { it.isNotBlank() }
        ?.lineSequence()
        ?.joinToString(separator = "\n") { line -> playbackRuntimeStatusLine(line.trim()) }
        ?: "尚未检查运行时。"

fun playbackEndSettingsTitleLabel(): String = "播放结束"

fun playbackEndSettingsDescriptionLabel(): String =
    "选定剧集播完后，可以直接回到详情页，也可以自动切到下一集。"

fun pictureSettingsTitleLabel(): String =
    "画面 / Tone Mapping"

fun pictureSettingsDescriptionLabel(): String =
    "按 SDR、HDR10、HDR10+ 和 Dolby Vision 分别配置默认画面规则。"

fun pictureOsdMenuTitleLabel(): String =
    "画面"

fun pictureSaveDefaultForFormatLabel(): String =
    "保存为该格式默认"

fun pictureSessionOverrideLabel(): String =
    "仅本次播放"

fun playbackBackendLabel(backend: PlaybackRenderBackend): String =
    when (backend.normalizeSupportedBackend()) {
        PlaybackRenderBackend.STANDARD_EXO -> "标准 Exo"
        PlaybackRenderBackend.EXPERIMENTAL_GL -> "旧实验 GL"
        PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> "实验 mpv 内嵌"
        PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> "实验 mpv 内嵌"
        PlaybackRenderBackend.EXPERIMENTAL_LIBVLC -> "标准 Exo"
    }

fun videoRenderRuleLabel(ruleKey: VideoRenderRuleKey): String =
    when (ruleKey) {
        VideoRenderRuleKey.SDR -> "SDR"
        VideoRenderRuleKey.HDR10 -> "HDR10"
        VideoRenderRuleKey.HDR10_PLUS -> "HDR10+"
        VideoRenderRuleKey.DOLBY_VISION -> "Dolby Vision"
        VideoRenderRuleKey.UNKNOWN_HDR -> "Unknown HDR"
    }

fun playbackEndReturnToDetailActionLabel(): String = playbackBackToDetailsLabel()

fun playbackEndPlayNextEpisodeActionLabel(): String = "继续下一集"

fun PlaybackEndAction.playbackEndActionLabel(): String =
    when (this) {
        PlaybackEndAction.RETURN_TO_DETAIL -> playbackEndReturnToDetailActionLabel()
        PlaybackEndAction.PLAY_NEXT_EPISODE -> playbackEndPlayNextEpisodeActionLabel()
    }

fun playbackEndReturnToDetailDetail(): String =
    "播完后会停在详情页，方便手动挑下一集。"

fun playbackEndPlayNextEpisodeDetail(): String =
    "播完后会自动开始下一集，没有下一集时会回到详情页。"

fun playbackEndReturnToDetailSummary(): String = "播完返回"

fun playbackEndPlayNextEpisodeSummary(): String = "自动下一集"

fun PlaybackEndAction.playbackEndMenuSummary(): String =
    when (this) {
        PlaybackEndAction.RETURN_TO_DETAIL -> playbackEndReturnToDetailSummary()
        PlaybackEndAction.PLAY_NEXT_EPISODE -> playbackEndPlayNextEpisodeSummary()
    }

private fun playbackSpeedValue(speed: Float): String =
    if (speed % 1f == 0f) {
        speed.toInt().toString()
    } else {
        "%.2f".format(speed).trimEnd('0')
    }

private fun playbackRuntimeStatusLine(line: String): String {
    val marker = " Manifest: present."
    val hasManifest = line.endsWith(marker)
    val body = if (hasManifest) line.removeSuffix(marker) else line
    val suffix = if (hasManifest) "清单：已发现。" else ""
    return when {
        body.startsWith("Bundled mpv runtime is ready. RIFE: ") ->
            "内置 mpv 运行时已就绪。RIFE：${
                body.removePrefix("Bundled mpv runtime is ready. RIFE: ").removeSuffix(".").localizedRifeBackends()
            }。$suffix"
        body == "mpv runtime is playable. RIFE scripts are missing; leave RIFE off or prepare a RIFE backend." ->
            "mpv 运行时可播放。缺少 RIFE 脚本；请关闭 RIFE 或准备 RIFE 后端。$suffix"
        body.startsWith("mpv runtime is playable. Runtime manifest entries are missing or invalid: ") ->
            "mpv 运行时可播放，但运行时清单声明的条目缺失或无效：${
                body.removePrefix("mpv runtime is playable. Runtime manifest entries are missing or invalid: ").removeSuffix(".")
            }。$suffix"
        body.startsWith("mpv runtime is playable. Missing optional files: ") ->
            "mpv 运行时可播放。缺少可选文件：${
                body.removePrefix("mpv runtime is playable. Missing optional files: ").removeSuffix(".")
            }。$suffix"
        body.startsWith("mpv runtime is incomplete. Missing: ") ->
            "mpv 运行时不完整。缺少：${
                body.removePrefix("mpv runtime is incomplete. Missing: ").removeSuffix(".")
            }。$suffix"
        body.startsWith("Runtime check failed: ") ->
            "运行时检查失败：${body.removePrefix("Runtime check failed: ")}"
        body == "Runtime manifest" -> "运行时清单"
        body.startsWith("Verified at: ") -> "验证时间：${body.removePrefix("Verified at: ")}"
        body.startsWith("Source: ") -> "来源：${body.removePrefix("Source: ")}"
        body.startsWith("Overlay source: ") -> "叠加包来源：${body.removePrefix("Overlay source: ")}"
        body.startsWith("Runtime root: ") -> "运行时目录：${body.removePrefix("Runtime root: ")}"
        body.startsWith("Required RIFE: ") -> "要求的 RIFE：${body.removePrefix("Required RIFE: ").localizedRifeBackends()}"
        body.startsWith("Manifest files: ") -> "清单文件：${body.removePrefix("Manifest files: ")}"
        else -> line
    }
}

private fun String.localizedRifeBackends(): String =
    if (equals("none", ignoreCase = true)) "无" else this

private fun String.localizedMissingMpvExecutableMessage(prefix: String = ""): String {
    val path = removePrefix("mpv executable not found: ")
        .removeSuffix(". Choose the bundled runtime path, install mpv, or run Check runtime before launching.")
    return "${prefix}找不到 mpv.exe：$path。请选择内置运行时路径、安装 mpv，或先检查运行时。"
}

private fun String.localizedMissingRifeScriptMessage(prefix: String = ""): String {
    val path = removePrefix("RIFE is enabled but script was not found: ")
        .removeSuffix(". Pick an installed backend, prepare the bundled runtime, or turn RIFE off.")
    return "${prefix}已开启 RIFE，但找不到脚本：$path。请选择已安装后端、准备内置运行时，或关闭 RIFE。"
}
