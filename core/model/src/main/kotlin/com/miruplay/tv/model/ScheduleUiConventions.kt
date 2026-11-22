package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.joinUiText
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.core.common.uiText

// ── 设置页：新番时间表分组 ────────────────────────────────────────────────

fun settingsScheduleMenuSummary(configured: Boolean): UiText =
    if (configured) {
        uiText("settings_menu_schedule_configured", "已配置")
    } else {
        uiText("settings_menu_schedule_unconfigured", "未配置")
    }

fun settingsSchedulePanelTitleLabel(): UiText =
    uiText("settings_schedule_panel_title", "新番时间表")

fun settingsSchedulePanelDescription(): UiText =
    uiText(
        "settings_schedule_panel_desc",
        "填入时间表服务地址后，媒体库顶栏可打开每周新番表。地址只保存在本机。",
    )

fun settingsScheduleUrlFieldLabel(): UiText =
    uiText("settings_schedule_url_field", "时间表服务地址")

fun settingsScheduleSaveActionLabel(): UiText =
    uiText("settings_schedule_save", "保存地址")

fun settingsScheduleClearActionLabel(): UiText =
    uiText("settings_schedule_clear", "清除地址")

fun settingsScheduleSavedStatus(): UiText =
    uiText("settings_schedule_saved", "时间表地址已保存。")

fun settingsScheduleClearedStatus(): UiText =
    uiText("settings_schedule_cleared", "时间表地址已清除。")

fun settingsScheduleInvalidUrlStatus(): UiText =
    uiText("settings_schedule_invalid", "地址无效：需要以 http:// 或 https:// 开头的完整地址。")

fun settingsScheduleCurrentUnsetStatus(): UiText =
    uiText("settings_schedule_current_unset", "尚未配置时间表地址。")

fun settingsScheduleCurrentStatus(url: String): UiText =
    UiText("settings_schedule_current", listOf(url), "当前地址：$url")

fun settingsScheduleOpenScheduleActionLabel(): UiText =
    uiText("settings_schedule_open", "打开时间表")

// ── 时间表页 ────────────────────────────────────────────────────────────

fun libraryScheduleActionLabel(): UiText =
    uiText("library_schedule_action", "时间表")

fun scheduleScreenTitleLabel(): UiText =
    uiText("schedule_title", "新番时间表")

fun scheduleScreenSubtitleLabel(): UiText =
    uiText("schedule_subtitle", "每周播出时间 · 已换算到设备时区")

fun scheduleLoadingMessage(): UiText =
    uiText("schedule_loading", "正在获取时间表…")

fun scheduleUnconfiguredTitle(): UiText =
    uiText("schedule_unconfigured_title", "尚未配置时间表服务")

fun scheduleUnconfiguredDescription(): UiText =
    uiText(
        "schedule_unconfigured_desc",
        "到「设置 → 新番时间表」填入服务地址后，这里会显示每周播出时间。",
    )

fun scheduleGoSettingsActionLabel(): UiText =
    uiText("schedule_go_settings", "去设置")

fun scheduleEmptyTitle(): UiText =
    uiText("schedule_empty_title", "暂无播出数据")

fun scheduleEmptyDescription(): UiText =
    uiText("schedule_empty_desc", "服务端还没有发布本季时间表，稍后再试。")

fun scheduleErrorTitle(): UiText =
    uiText("schedule_error_title", "时间表获取失败")

fun scheduleErrorMessage(error: ScheduleFetchError): UiText =
    when (error) {
        ScheduleFetchError.InvalidUrl -> uiText(
            "schedule_error_invalid_url",
            "数据源地址无效，请到设置里重新填写。",
        )
        ScheduleFetchError.Network -> uiText(
            "schedule_error_network",
            "网络不可用，请检查网络后重试。",
        )
        ScheduleFetchError.Timeout -> uiText(
            "schedule_error_timeout",
            "请求超时，请稍后重试。",
        )
        is ScheduleFetchError.Http -> UiText(
            "schedule_error_http",
            listOf(error.code),
            "服务返回错误（HTTP ${error.code}）。",
        )
        ScheduleFetchError.InvalidData -> uiText(
            "schedule_error_invalid_data",
            "时间表数据格式无法识别，请确认数据源地址。",
        )
    }

fun scheduleRetryActionLabel(): UiText =
    uiText("schedule_retry", "重试")

fun scheduleRefreshActionLabel(): UiText =
    uiText("schedule_refresh", "刷新")

fun scheduleBackActionLabel(): UiText =
    uiText("schedule_back", "返回")

fun scheduleSeasonLabel(
    season: String?,
    seasonStart: String?,
    seasonEnd: String?,
): UiText? {
    val safeSeason = season?.takeIf { it.isNotBlank() }
    val start = seasonStart?.takeIf { it.isNotBlank() }
    val end = seasonEnd?.takeIf { it.isNotBlank() }
    return when {
        safeSeason != null && start != null && end != null -> UiText(
            "schedule_season_range",
            listOf(safeSeason, start, end),
            "$safeSeason · $start 至 $end",
        )
        safeSeason != null -> rawUiText(safeSeason)
        else -> null
    }
}

fun scheduleUpdatedAtLabel(localTimestamp: String): UiText =
    UiText("schedule_updated_at", listOf(localTimestamp), "更新于 $localTimestamp")

fun scheduleLocalZoneNote(zoneId: String): UiText =
    UiText("schedule_local_zone", listOf(zoneId), "设备时区：$zoneId")

fun scheduleTodayLabel(): UiText =
    uiText("schedule_today", "今天")

fun scheduleWeekdayLabel(weekday: Int): UiText = when (weekday) {
    1 -> uiText("schedule_weekday_mon", "周一")
    2 -> uiText("schedule_weekday_tue", "周二")
    3 -> uiText("schedule_weekday_wed", "周三")
    4 -> uiText("schedule_weekday_thu", "周四")
    5 -> uiText("schedule_weekday_fri", "周五")
    6 -> uiText("schedule_weekday_sat", "周六")
    else -> uiText("schedule_weekday_sun", "周日")
}

/** 「周一 22:00」——本地周几 + 本地时刻。 */
fun scheduleLocalTimeLabel(weekday: Int, localTime: String): UiText {
    val dayLabel = scheduleWeekdayLabel(weekday)
    return UiText(
        "schedule_local_time",
        listOf(dayLabel, localTime),
        "${dayLabel.fallback} $localTime",
    )
}

/** 服务端明确标记或缺失播出时刻时展示（此时不显示具体时间）。 */
fun scheduleTimeUnknownLabel(): UiText =
    uiText("schedule_time_unknown", "时间未知")

/** 来源之间存在时间冲突时的标记（服务端 disputed=true）。 */
fun scheduleTimeDisputedLabel(): UiText =
    uiText("schedule_time_disputed", "时间有争议")

/** 原始 JST 时刻，作为次要信息展示。 */
fun scheduleJstTimeLabel(jstWeekday: Int, jstTime: String): UiText {
    val dayLabel = scheduleWeekdayLabel(jstWeekday)
    return UiText(
        "schedule_jst_time",
        listOf(dayLabel, jstTime),
        "日本时间 ${dayLabel.fallback} $jstTime",
    )
}

fun scheduleCrossDayNextDayLabel(): UiText =
    uiText("schedule_cross_day_next", "次日")

fun scheduleCrossDayPreviousDayLabel(): UiText =
    uiText("schedule_cross_day_previous", "前日")

fun scheduleConfidenceLabel(confidence: ScheduleConfidence): UiText =
    when (confidence) {
        ScheduleConfidence.HIGH -> uiText("schedule_confidence_high", "置信度高")
        ScheduleConfidence.MEDIUM -> uiText("schedule_confidence_medium", "置信度中")
        ScheduleConfidence.LOW -> uiText("schedule_confidence_low", "置信度低")
        ScheduleConfidence.UNKNOWN -> uiText("schedule_confidence_unknown", "置信度未知")
    }

fun scheduleSourceCountLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("schedule_source_count", listOf(safe), "$safe 个来源")
}

fun scheduleMatchedLabel(): UiText =
    uiText("schedule_matched", "已匹配媒体库")

fun scheduleUnmatchedLabel(): UiText =
    uiText("schedule_unmatched", "未在媒体库")

fun scheduleOpenDetailHint(): UiText =
    uiText("schedule_open_detail_hint", "按确定打开详情")

fun scheduleMatchedSummary(matchedCount: Int, totalCount: Int): UiText {
    val matched = matchedCount.coerceAtLeast(0)
    val total = totalCount.coerceAtLeast(0)
    return UiText(
        "schedule_matched_summary",
        listOf(matched, total),
        "媒体库匹配 $matched/$total",
    )
}

fun scheduleCachedBadgeLabel(): UiText =
    uiText("schedule_cached_badge", "缓存")

/**
 * 回退到旧缓存时的提示条。文案必须与真实失败原因一致：
 * 只有真正的网络/超时失败才能说「网络不可用」，解析失败要说「数据格式无法识别」，
 * 否则会把 API 兼容性问题误导成网络问题（2026-11-22 线上 2.19.0 实测踩坑）。
 */
fun scheduleStaleBannerLabel(error: ScheduleFetchError?): UiText =
    when (error) {
        ScheduleFetchError.Timeout -> uiText(
            "schedule_stale_banner_timeout",
            "请求超时，正在显示上次缓存的数据。",
        )
        ScheduleFetchError.InvalidData -> uiText(
            "schedule_stale_banner_invalid_data",
            "时间表数据格式无法识别，正在显示上次缓存的数据。",
        )
        is ScheduleFetchError.Http -> UiText(
            "schedule_stale_banner_http",
            listOf(error.code),
            "时间表服务返回错误（HTTP ${error.code}），正在显示上次缓存的数据。",
        )
        ScheduleFetchError.InvalidUrl -> uiText(
            "schedule_stale_banner_other",
            "暂时无法获取时间表，正在显示上次缓存的数据。",
        )
        ScheduleFetchError.Network, null -> uiText(
            "schedule_stale_banner",
            "网络不可用，正在显示上次缓存的数据。",
        )
    }

fun scheduleSkippedItemsLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("schedule_skipped_items", listOf(safe), "$safe 条数据时间无法解析，已跳过。")
}

fun scheduleEmptyDayLabel(): UiText =
    uiText("schedule_empty_day", "本日无新番")

// 供时间表页头部拼接的多段文案（卷、更新时间、时区）。
fun scheduleHeaderMeta(
    seasonLabel: UiText?,
    updatedAtLabel: UiText?,
    localZoneLabel: UiText,
): UiText =
    joinUiText(listOfNotNull(seasonLabel, updatedAtLabel, localZoneLabel))
