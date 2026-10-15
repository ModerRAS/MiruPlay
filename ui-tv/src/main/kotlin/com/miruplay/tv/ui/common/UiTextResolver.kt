package com.miruplay.tv.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.ui.tv.R

/** Compose 内解析：ViewModel/Model 层只传递 UiText，到渲染时才落到当前语言。 */
@Composable
fun UiText.resolve(): String {
    val id = uiTextResId(key)
    if (id == 0) return fallback
    return stringResource(id, *resolveArgs().map { it ?: "" }.toTypedArray())
}

/** 非 Compose 场景（Toast/通知/VM 插值）解析，需要 Context。 */
fun UiText.resolve(context: Context): String {
    val id = uiTextResId(key)
    if (id == 0) return fallback
    return context.resources.getString(id, *resolveArgs(context).map { it ?: "" }.toTypedArray())
}

private fun UiText.resolveArgs(context: Context? = null): Array<Any?> =
    args.map { arg ->
        when (arg) {
            is UiText -> if (context == null) arg.fallback else arg.resolve(context)
            else -> arg
        }
    }.toTypedArray()

/**
 * key → R 的显式映射（release 开了 shrinkResources，不能用 getIdentifier）。
 * 由 i18n 抽取同步维护；未知 key 返回 0，调用方回退到中文 fallback。
 */
internal fun uiTextResId(key: String): Int =
    when (key) {
        // when 分支即静态引用，release shrink 下安全。
        "common_em_dash" -> R.string.common_em_dash
        "playback_back" -> R.string.playback_back
        "playback_back_to_details" -> R.string.playback_back_to_details
        "playback_play" -> R.string.playback_play
        "playback_pause" -> R.string.playback_pause
        "playback_retry" -> R.string.playback_retry
        "playback_confirm_exit" -> R.string.playback_confirm_exit
        "playback_stop" -> R.string.playback_stop
        "playback_error_title" -> R.string.playback_error_title
        "playback_choose_media" -> R.string.playback_choose_media
        "playback_waiting_for_media" -> R.string.playback_waiting_for_media
        "playback_local_source" -> R.string.playback_local_source
        "playback_remote_stream" -> R.string.playback_remote_stream
        "playback_external_subtitle" -> R.string.playback_external_subtitle
        "playback_speed_menu_title" -> R.string.playback_speed_menu_title
        "playback_subtitles_menu_title" -> R.string.playback_subtitles_menu_title
        "playback_audio_menu_title" -> R.string.playback_audio_menu_title
        "playback_subtitle_none" -> R.string.playback_subtitle_none
        "playback_subtitle_count" -> R.string.playback_subtitle_count
        "playback_subtitle_off" -> R.string.playback_subtitle_off
        "playback_audio_track_count" -> R.string.playback_audio_track_count
        "playback_subtitle_track_title" -> R.string.playback_subtitle_track_title
        "playback_subtitle_track_language" -> R.string.playback_subtitle_track_language
        "playback_subtitle_indexed" -> R.string.playback_subtitle_indexed
        "playback_audio_track_title" -> R.string.playback_audio_track_title
        "playback_audio_track_language" -> R.string.playback_audio_track_language
        "playback_audio_indexed" -> R.string.playback_audio_indexed
        "playback_speed_chip" -> R.string.playback_speed_chip
        "playback_seek_back" -> R.string.playback_seek_back
        "playback_seek_forward" -> R.string.playback_seek_forward
        "playback_end_settings_title" -> R.string.playback_end_settings_title
        "playback_end_settings_description" -> R.string.playback_end_settings_description
        "subtitle_language_settings_title" -> R.string.subtitle_language_settings_title
        "subtitle_language_settings_description" -> R.string.subtitle_language_settings_description
        "subtitle_background_settings_title" -> R.string.subtitle_background_settings_title
        "subtitle_background_settings_description" -> R.string.subtitle_background_settings_description
        "subtitle_background_transparent" -> R.string.subtitle_background_transparent
        "subtitle_background_black" -> R.string.subtitle_background_black
        "subtitle_background_toggle" -> R.string.subtitle_background_toggle
        "perf_monitor_settings_title" -> R.string.perf_monitor_settings_title
        "perf_monitor_settings_description" -> R.string.perf_monitor_settings_description
        "perf_monitor_enabled" -> R.string.perf_monitor_enabled
        "perf_monitor_disabled" -> R.string.perf_monitor_disabled
        "perf_monitor_toggle" -> R.string.perf_monitor_toggle
        "subtitle_language_auto" -> R.string.subtitle_language_auto
        "subtitle_language_zh_hans" -> R.string.subtitle_language_zh_hans
        "subtitle_language_zh_hant" -> R.string.subtitle_language_zh_hant
        "subtitle_language_zh" -> R.string.subtitle_language_zh
        "subtitle_language_en" -> R.string.subtitle_language_en
        "subtitle_language_ja" -> R.string.subtitle_language_ja
        "picture_settings_title" -> R.string.picture_settings_title
        "picture_settings_description" -> R.string.picture_settings_description
        "picture_osd_menu_title" -> R.string.picture_osd_menu_title
        "picture_save_default_for_format" -> R.string.picture_save_default_for_format
        "picture_session_override" -> R.string.picture_session_override
        "playback_backend_standard_exo" -> R.string.playback_backend_standard_exo
        "playback_backend_mpv" -> R.string.playback_backend_mpv
        "playback_backend_ijkplayer" -> R.string.playback_backend_ijkplayer
        "playback_end_play_next_episode_action" -> R.string.playback_end_play_next_episode_action
        "playback_end_return_detail_detail" -> R.string.playback_end_return_detail_detail
        "playback_end_play_next_detail" -> R.string.playback_end_play_next_detail
        "playback_end_return_detail_summary" -> R.string.playback_end_return_detail_summary
        "playback_end_play_next_summary" -> R.string.playback_end_play_next_summary
        "video_render_rule_sdr" -> R.string.video_render_rule_sdr
        "video_render_rule_hdr10" -> R.string.video_render_rule_hdr10
        "video_render_rule_hdr10_plus" -> R.string.video_render_rule_hdr10_plus
        "video_render_rule_dolby_vision" -> R.string.video_render_rule_dolby_vision
        "video_render_rule_unknown_hdr" -> R.string.video_render_rule_unknown_hdr
        "tonemapping_bypass" -> R.string.tonemapping_bypass
        "tonemapping_balanced" -> R.string.tonemapping_balanced
        "tonemapping_punchy" -> R.string.tonemapping_punchy
        "tonemapping_soft" -> R.string.tonemapping_soft
        "tonemapping_peak_disabled" -> R.string.tonemapping_peak_disabled
        "tonemapping_peak_static" -> R.string.tonemapping_peak_static
        "tonemapping_peak_dynamic" -> R.string.tonemapping_peak_dynamic
        "tonemapping_peak_aggressive" -> R.string.tonemapping_peak_aggressive
        "tonemapping_curve_mobius" -> R.string.tonemapping_curve_mobius
        "tonemapping_curve_reinhard" -> R.string.tonemapping_curve_reinhard
        "tonemapping_summary" -> R.string.tonemapping_summary
        "subtitle_language_current_priority" -> R.string.subtitle_language_current_priority
        "subtitle_background_current" -> R.string.subtitle_background_current
        "perf_monitor_current" -> R.string.perf_monitor_current
        "picture_default_backend" -> R.string.picture_default_backend
        else -> 0
    }
