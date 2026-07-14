package com.miruplay.tv.model

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

fun playbackEndSettingsTitleLabel(): String = "播放结束"

fun playbackEndSettingsDescriptionLabel(): String =
    "选定剧集播完后，可以直接回到详情页，也可以自动切到下一集。"

fun preferredSubtitleLanguageSettingsTitleLabel(): String = "字幕语言优先级"

fun preferredSubtitleLanguageSettingsDescriptionLabel(): String =
    "有匹配字幕时自动优先选择；没有匹配时保留视频原本的默认字幕。"

fun SubtitleLanguagePreference.displayLabel(): String =
    when (this) {
        SubtitleLanguagePreference.AUTO -> "自动"
        SubtitleLanguagePreference.CHINESE_SIMPLIFIED -> "简体中文"
        SubtitleLanguagePreference.CHINESE_TRADITIONAL -> "繁体中文"
        SubtitleLanguagePreference.CHINESE -> "中文"
        SubtitleLanguagePreference.ENGLISH -> "英语"
        SubtitleLanguagePreference.JAPANESE -> "日语"
    }

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
