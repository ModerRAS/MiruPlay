package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.uiText

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

fun playbackBackLabel(): UiText = uiText("playback_back", "返回")

fun playbackBackToDetailsLabel(): UiText = uiText("playback_back_to_details", "返回详情")

fun playbackPlayLabel(): UiText = uiText("playback_play", "播放")

fun playbackPauseLabel(): UiText = uiText("playback_pause", "暂停")

fun playbackRetryLabel(): UiText = uiText("playback_retry", "重试")

fun playbackConfirmExitLabel(): UiText = uiText("playback_confirm_exit", "确定退出")

fun playbackStopLabel(): UiText = uiText("playback_stop", "停止")

fun playbackErrorTitle(): UiText = uiText("playback_error_title", "播放失败")

fun playbackChooseMediaLabel(): UiText = uiText("playback_choose_media", "选择媒体")

fun playbackWaitingForMediaLabel(): UiText = uiText("playback_waiting_for_media", "等待媒体")

fun playbackLocalSourceLabel(): UiText = uiText("playback_local_source", "本地播放")

fun playbackRemoteStreamLabel(): UiText = uiText("playback_remote_stream", "远程串流")

fun playbackExternalSubtitleLabel(): UiText = uiText("playback_external_subtitle", "字幕外载")

fun playbackSpeedMenuTitle(): UiText = uiText("playback_speed_menu_title", "播放速度")

fun playbackSubtitlesMenuTitle(): UiText = uiText("playback_subtitles_menu_title", "字幕")

fun playbackAudioMenuTitle(): UiText = uiText("playback_audio_menu_title", "音轨")

fun playbackSubtitleCountLabel(count: Int): UiText =
    if (count <= 0) {
        uiText("playback_subtitle_none", "无字幕")
    } else {
        UiText("playback_subtitle_count", listOf(count), "字幕 $count")
    }

fun playbackSubtitleOffLabel(): UiText = uiText("playback_subtitle_off", "关闭字幕")

fun playbackAudioTrackCountLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("playback_audio_track_count", listOf(safe), "音轨 $safe")
}

fun playbackSubtitleOptionLabel(track: SubtitleTrack, index: Int): UiText {
    if (track.title.isNotBlank()) {
        return UiText("playback_subtitle_track_title", listOf(track.title), track.title)
    }
    if (track.language.isNotBlank()) {
        return UiText("playback_subtitle_track_language", listOf(track.language), track.language)
    }
    val menu = playbackSubtitlesMenuTitle()
    return UiText("playback_subtitle_indexed", listOf(menu, index + 1), "${menu.fallback} ${index + 1}")
}

fun playbackAudioOptionLabel(title: String?, language: String, index: Int): UiText {
    if (!title.isNullOrBlank()) {
        return UiText("playback_audio_track_title", listOf(title), title)
    }
    if (language.isNotBlank()) {
        return UiText("playback_audio_track_language", listOf(language), language)
    }
    val menu = playbackAudioMenuTitle()
    return UiText("playback_audio_indexed", listOf(menu, index + 1), "${menu.fallback} ${index + 1}")
}

fun playbackSpeedValueLabel(speed: Float): String =
    "${playbackSpeedValue(speed)}x"

fun playbackSpeedChipLabel(speed: Float): UiText {
    val value = playbackSpeedValueLabel(speed)
    return UiText("playback_speed_chip", listOf(value), "倍速 $value")
}

fun playbackSeekBackLabel(seconds: Int): UiText =
    UiText("playback_seek_back", listOf(seconds), "快退 $seconds 秒")

fun playbackSeekForwardLabel(seconds: Int): UiText =
    UiText("playback_seek_forward", listOf(seconds), "快进 $seconds 秒")

fun playbackSeekBackCompactLabel(seconds: Int = PLAYBACK_SEEK_BACK_SECONDS): String =
    "-$seconds"

fun playbackSeekForwardCompactLabel(seconds: Int = PLAYBACK_SEEK_FORWARD_SECONDS): String =
    "+$seconds"

fun playbackUnknownDurationLabel(): String = "--:--"

fun playbackEndSettingsTitleLabel(): UiText = uiText("playback_end_settings_title", "播放结束")

fun playbackEndSettingsDescriptionLabel(): UiText =
    uiText("playback_end_settings_description", "选定剧集播完后，可以直接回到详情页，也可以自动切到下一集。")

fun preferredSubtitleLanguageSettingsTitleLabel(): UiText =
    uiText("subtitle_language_settings_title", "字幕语言优先级")

fun preferredSubtitleLanguageSettingsDescriptionLabel(): UiText =
    uiText("subtitle_language_settings_description", "有匹配字幕时自动优先选择；没有匹配时保留视频原本的默认字幕。")

fun subtitleBackgroundSettingsTitleLabel(): UiText =
    uiText("subtitle_background_settings_title", "字幕背景")

fun subtitleBackgroundSettingsDescriptionLabel(): UiText =
    uiText("subtitle_background_settings_description", "开启透明底后，只保留字幕文字和描边。")

fun subtitleBackgroundSettingsOptionLabel(transparent: Boolean): UiText =
    if (transparent) {
        uiText("subtitle_background_transparent", "透明底")
    } else {
        uiText("subtitle_background_black", "黑色底")
    }

fun subtitleBackgroundTransparentToggleLabel(): UiText =
    uiText("subtitle_background_toggle", "透明底")

fun perfMonitorSettingsTitleLabel(): UiText =
    uiText("perf_monitor_settings_title", "播放性能监测")

fun perfMonitorSettingsDescriptionLabel(): UiText =
    uiText(
        "perf_monitor_settings_description",
        "记录解码器掉帧、主线程卡顿、字幕渲染耗时并写入日志，用于定位卡顿原因；同时在播放画面左上角显示实时数据。",
    )

fun perfMonitorSettingsOptionLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("perf_monitor_enabled", "已开启")
    } else {
        uiText("perf_monitor_disabled", "已关闭")
    }

fun perfMonitorToggleLabel(): UiText = uiText("perf_monitor_toggle", "性能监测")

fun SubtitleLanguagePreference.displayLabel(): UiText =
    when (this) {
        SubtitleLanguagePreference.AUTO -> uiText("subtitle_language_auto", "自动")
        SubtitleLanguagePreference.CHINESE_SIMPLIFIED -> uiText("subtitle_language_zh_hans", "简体中文")
        SubtitleLanguagePreference.CHINESE_TRADITIONAL -> uiText("subtitle_language_zh_hant", "繁体中文")
        SubtitleLanguagePreference.CHINESE -> uiText("subtitle_language_zh", "中文")
        SubtitleLanguagePreference.ENGLISH -> uiText("subtitle_language_en", "英语")
        SubtitleLanguagePreference.JAPANESE -> uiText("subtitle_language_ja", "日语")
    }

fun pictureSettingsTitleLabel(): UiText =
    uiText("picture_settings_title", "画面 / Tone Mapping")

fun pictureSettingsDescriptionLabel(): UiText =
    uiText("picture_settings_description", "按 SDR、HDR10、HDR10+ 和 Dolby Vision 分别配置默认画面规则。")

fun pictureOsdMenuTitleLabel(): UiText =
    uiText("picture_osd_menu_title", "画面")

fun pictureSaveDefaultForFormatLabel(): UiText =
    uiText("picture_save_default_for_format", "保存为该格式默认")

fun pictureSessionOverrideLabel(): UiText =
    uiText("picture_session_override", "仅本次播放")

fun playbackBackendLabel(backend: PlaybackRenderBackend): UiText =
    when (backend.normalizeSupportedBackend()) {
        PlaybackRenderBackend.STANDARD_EXO,
        PlaybackRenderBackend.EXPERIMENTAL_GL,
        PlaybackRenderBackend.EXPERIMENTAL_LIBVLC -> uiText("playback_backend_standard_exo", "标准 Exo")
        PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID,
        PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> uiText("playback_backend_mpv", "实验 mpv 内嵌")
        PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER -> uiText("playback_backend_ijkplayer", "实验 ijkplayer")
    }

fun videoRenderRuleLabel(ruleKey: VideoRenderRuleKey): UiText =
    when (ruleKey) {
        VideoRenderRuleKey.SDR -> UiText("video_render_rule_sdr", listOf("SDR"), "SDR")
        VideoRenderRuleKey.HDR10 -> UiText("video_render_rule_hdr10", listOf("HDR10"), "HDR10")
        VideoRenderRuleKey.HDR10_PLUS -> UiText("video_render_rule_hdr10_plus", listOf("HDR10+"), "HDR10+")
        VideoRenderRuleKey.DOLBY_VISION -> UiText("video_render_rule_dolby_vision", listOf("Dolby Vision"), "Dolby Vision")
        VideoRenderRuleKey.UNKNOWN_HDR -> uiText("video_render_rule_unknown_hdr", "Unknown HDR")
    }

fun playbackEndReturnToDetailActionLabel(): UiText = playbackBackToDetailsLabel()

fun playbackEndPlayNextEpisodeActionLabel(): UiText =
    uiText("playback_end_play_next_episode_action", "继续下一集")

fun PlaybackEndAction.playbackEndActionLabel(): UiText =
    when (this) {
        PlaybackEndAction.RETURN_TO_DETAIL -> playbackEndReturnToDetailActionLabel()
        PlaybackEndAction.PLAY_NEXT_EPISODE -> playbackEndPlayNextEpisodeActionLabel()
    }

fun playbackEndReturnToDetailDetail(): UiText =
    uiText("playback_end_return_detail_detail", "播完后会停在详情页，方便手动挑下一集。")

fun playbackEndPlayNextEpisodeDetail(): UiText =
    uiText("playback_end_play_next_detail", "播完后会自动开始下一集，没有下一集时会回到详情页。")

fun playbackEndReturnToDetailSummary(): UiText = uiText("playback_end_return_detail_summary", "播完返回")

fun playbackEndPlayNextEpisodeSummary(): UiText = uiText("playback_end_play_next_summary", "自动下一集")

fun PlaybackEndAction.playbackEndMenuSummary(): UiText =
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
