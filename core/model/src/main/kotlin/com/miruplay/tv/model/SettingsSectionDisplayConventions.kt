package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.joinUiText
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.core.common.uiText

enum class MiruPlaySettingsSection(
    val androidTvTitle: UiText,
    val androidTvDescription: UiText,
) {
    WEB_UI(
        androidTvTitle = uiText("settings_section_webui_title", "WebUI"),
        androidTvDescription = uiText("settings_section_webui_desc", "访问地址与二维码"),
    ),
    SOURCES(
        androidTvTitle = uiText("settings_section_sources_title", "媒体源"),
        androidTvDescription = uiText("settings_section_sources_desc", "本地、WebDAV、SMB"),
    ),
    PLAYBACK(
        androidTvTitle = uiText("settings_section_playback_title", "播放"),
        androidTvDescription = uiText("settings_section_playback_desc", "播完动作"),
    ),
    AUDIO_DSP(
        androidTvTitle = uiText("settings_section_audio_dsp_title", "音频 DSP"),
        androidTvDescription = uiText("settings_section_audio_dsp_desc", "PEQ、扫频测量与校准"),
    ),
    CLOUD_DRIVE(
        androidTvTitle = uiText("settings_section_clouddrive_title", "CloudDrive"),
        androidTvDescription = uiText("settings_section_clouddrive_desc", "RSS 离线下载与入库"),
    ),
    PROXY(
        androidTvTitle = uiText("settings_section_proxy_title", "代理配置"),
        androidTvDescription = uiText("settings_section_proxy_desc", "Bangumi、Archive 与 RSS 出站代理"),
    ),
    SCAN(
        androidTvTitle = uiText("settings_section_scan_title", "扫描"),
        androidTvDescription = uiText("settings_section_scan_desc", "媒体库更新策略"),
    ),
    LOG_UPLOAD(
        androidTvTitle = uiText("settings_section_log_upload_title", "日志上报"),
        androidTvDescription = uiText("settings_section_log_upload_desc", "OpenObserve JSON"),
    ),
    APP_UPDATE(
        androidTvTitle = uiText("settings_section_app_update_title", "更新"),
        androidTvDescription = uiText("settings_section_app_update_desc", "GitHub APK"),
    ),
    METADATA(
        androidTvTitle = uiText("settings_section_metadata_title", "元数据"),
        androidTvDescription = uiText("settings_section_metadata_desc", "Bangumi / TMDB Token"),
    ),
    ABOUT(
        androidTvTitle = uiText("settings_section_about_title", "关于"),
        androidTvDescription = uiText("settings_section_about_desc", "版本与应用信息"),
    ),
}

val androidTvSettingsSectionOrder: List<MiruPlaySettingsSection> =
    listOf(
        MiruPlaySettingsSection.WEB_UI,
        MiruPlaySettingsSection.SOURCES,
        MiruPlaySettingsSection.PLAYBACK,
        MiruPlaySettingsSection.AUDIO_DSP,
        MiruPlaySettingsSection.CLOUD_DRIVE,
        MiruPlaySettingsSection.PROXY,
        MiruPlaySettingsSection.SCAN,
        MiruPlaySettingsSection.LOG_UPLOAD,
        MiruPlaySettingsSection.APP_UPDATE,
        MiruPlaySettingsSection.METADATA,
        MiruPlaySettingsSection.ABOUT,
    )

fun MiruPlaySettingsSection.stepInSettingsOrder(
    order: List<MiruPlaySettingsSection>,
    delta: Int,
): MiruPlaySettingsSection? {
    val nextIndex = order.indexOf(this) + delta
    return order.getOrNull(nextIndex)
}

fun MiruPlaySettingsSection.stepAndroidTvSettingsSection(delta: Int): MiruPlaySettingsSection? =
    stepInSettingsOrder(androidTvSettingsSectionOrder, delta)

fun settingsWebUiMenuSummary(addressCount: Int): UiText =
    if (addressCount > 0) {
        UiText("settings_menu_webui_some", listOf(addressCount), "$addressCount 个地址")
    } else {
        uiText("settings_menu_webui_none", "等待网络")
    }

fun settingsSourcesMenuSummary(sourceCount: Int): UiText {
    val safe = sourceCount.coerceAtLeast(0)
    return UiText("settings_menu_sources", listOf(safe), "$safe 个源")
}

fun settingsCloudDriveMenuSummary(enabled: Boolean, rssCount: Int): UiText =
    if (enabled) {
        val safe = rssCount.coerceAtLeast(0)
        UiText("settings_menu_clouddrive_some", listOf(safe), "$safe 个订阅")
    } else {
        uiText("settings_menu_clouddrive_off", "未启用")
    }

fun settingsProxyMenuSummary(
    enabled: Boolean,
    host: String = "",
    port: Int = DEFAULT_RSS_PROXY_PORT,
): UiText =
    when {
        !enabled -> uiText("settings_menu_proxy_off", "未启用")
        host.isBlank() -> uiText("settings_menu_proxy_unset", "待填写")
        else -> {
            val address = "${host.trim()}:${port.coerceIn(MIN_RSS_PROXY_PORT, MAX_RSS_PROXY_PORT)}"
            UiText("settings_menu_proxy_address", listOf(address), address)
        }
    }

fun settingsScanMenuSummary(
    autoScanEnabled: Boolean,
    mergeSameAnimeEnabled: Boolean,
    posterWallArrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
    appMode: String = "anime",
): UiText =
    joinUiText(
        buildList {
            add(settingsAppModeLabel(appMode))
            if (autoScanEnabled) add(uiText("settings_menu_scan_scheduled", "定时"))
            if (mergeSameAnimeEnabled) add(uiText("settings_menu_scan_merged", "合并"))
            if (posterWallArrangement == PosterWallArrangement.RELEASE_SEASON) {
                add(uiText("settings_menu_scan_season", "新番季"))
            }
        },
    )

fun settingsLogUploadMenuSummary(): UiText =
    uiText("settings_menu_log_upload", "OpenObserve")

fun settingsAndroidTvLogUploadMenuSummary(
    enabled: Boolean = false,
    tokenConfigured: Boolean = false,
    isUploading: Boolean = false,
): UiText =
    when {
        enabled && isUploading -> uiText("settings_menu_log_upload_uploading", "上报中")
        enabled && tokenConfigured -> uiText("settings_menu_log_upload_auto", "自动上报")
        enabled -> uiText("settings_menu_log_upload_wait_token", "等待 Token")
        else -> uiText("settings_menu_log_upload_off", "未启用")
    }

fun settingsMetadataTokenMenuSummary(
    hasBangumiToken: Boolean = false,
    hasTmdbToken: Boolean = false,
): UiText {
    val configuredCount = listOf(hasBangumiToken, hasTmdbToken).count { it }
    return when (configuredCount) {
        0 -> uiText("settings_menu_metadata_tokens_none", "未设置")
        1 -> uiText("settings_menu_metadata_tokens_one", "已设置 1 项")
        else -> uiText("settings_menu_metadata_tokens_two", "已设置 2 项")
    }
}

fun settingsAboutMenuSummary(versionName: String): UiText {
    val name = settingsVersionNameValue(versionName)
    return UiText("settings_menu_about", listOf(name), "版本 ${name.fallback}")
}

data class SettingsSectionMenuSummaryInput(
    val webUiAddressCount: Int = 0,
    val sourceCount: Int = 0,
    val playbackSummary: UiText = rawUiText(""),
    val audioDspEnabled: Boolean = false,
    val cloudDriveEnabled: Boolean = false,
    val rssCount: Int = 0,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = DEFAULT_RSS_PROXY_PORT,
    val autoScanEnabled: Boolean = false,
    val mergeSameAnimeEnabled: Boolean = false,
    val posterWallArrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
    val appMode: String = "anime",
    val metadataSummary: UiText = rawUiText(""),
    val logUploadSummary: UiText = settingsLogUploadMenuSummary(),
    val appUpdateSummary: UiText = settingsAppUpdateMenuSummary(),
    val appVersionName: String = "",
)

fun MiruPlaySettingsSection.settingsMenuSummary(
    input: SettingsSectionMenuSummaryInput,
): UiText =
    when (this) {
        MiruPlaySettingsSection.WEB_UI -> settingsWebUiMenuSummary(input.webUiAddressCount)
        MiruPlaySettingsSection.SOURCES -> settingsSourcesMenuSummary(input.sourceCount)
        MiruPlaySettingsSection.PLAYBACK -> input.playbackSummary
        MiruPlaySettingsSection.AUDIO_DSP ->
            if (input.audioDspEnabled) {
                uiText("settings_menu_audio_dsp_on", "已启用 · 扫频校准")
            } else {
                uiText("settings_menu_audio_dsp_off", "未启用")
            }
        MiruPlaySettingsSection.CLOUD_DRIVE -> settingsCloudDriveMenuSummary(input.cloudDriveEnabled, input.rssCount)
        MiruPlaySettingsSection.PROXY -> settingsProxyMenuSummary(input.proxyEnabled, input.proxyHost, input.proxyPort)
        MiruPlaySettingsSection.SCAN -> settingsScanMenuSummary(
            input.autoScanEnabled,
            input.mergeSameAnimeEnabled,
            input.posterWallArrangement,
            input.appMode,
        )
        MiruPlaySettingsSection.LOG_UPLOAD -> input.logUploadSummary
        MiruPlaySettingsSection.APP_UPDATE -> input.appUpdateSummary
        MiruPlaySettingsSection.METADATA -> input.metadataSummary
        MiruPlaySettingsSection.ABOUT -> settingsAboutMenuSummary(input.appVersionName)
    }

fun settingsMenuPanelTitle(): UiText =
    uiText("settings_menu_panel_title", "设置菜单")

fun settingsScreenTitleLabel(): UiText =
    uiText("settings_screen_title", "设置")

fun settingsScreenSubtitleLabel(): UiText =
    uiText("settings_screen_subtitle", "管理媒体源、WebUI 和元数据服务")

fun settingsMenuPanelDescriptionAndroidTv(): UiText =
    uiText("settings_menu_panel_desc_tv", "按上下切换分类，向右进入当前设置。")

fun settingsOpenLibraryActionLabel(): UiText =
    uiText("settings_open_library", "打开海报墙")

fun settingsScanActiveSourceActionLabel(): UiText =
    uiText("settings_scan_active_source", "扫描当前源")

fun settingsOpenPlayerActionLabel(): UiText =
    uiText("settings_open_player", "打开播放器")

fun settingsOpenDetailsActionLabel(): UiText =
    uiText("settings_open_details", "打开详情")

fun settingsBackActionLabel(): UiText =
    uiText("settings_back", "返回")

fun settingsSaveTokenActionLabel(): UiText =
    uiText("settings_save_token", "保存 Token")

fun settingsClearTokenActionLabel(): UiText =
    uiText("settings_clear_token", "清除 Token")

fun settingsAndroidTvLogUploadStatusMessage(): UiText =
    uiText(
        "settings_log_upload_entrypoint",
        "可在当前页面配置 OpenObserve curl 命令或 API 地址；本地日志会按同一配置写入上报队列。",
    )

fun settingsAndroidTvLogUploadHintMessage(): UiText =
    uiText(
        "settings_log_upload_hint",
        "粘贴 curl 命令会自动保存地址和鉴权；开启自动上报后会定时上传待处理日志。",
    )

fun settingsLogUploadAutoToggleLabel(): UiText =
    uiText("settings_log_upload_auto_toggle", "自动上报")

fun settingsLogUploadEndpointFieldLabel(): UiText =
    uiText("settings_log_upload_endpoint_field", "OpenObserve curl 命令")

fun settingsLogUploadStreamFieldLabel(): UiText =
    rawUiText("Stream")

fun settingsLogUploadTokenFieldLabel(): UiText =
    uiText("settings_log_upload_token_field", "OpenObserve Token")

fun settingsLogUploadSaveSettingsActionLabel(): UiText =
    uiText("settings_log_upload_save", "保存设置")

fun settingsLogUploadRunNowActionLabel(): UiText =
    uiText("settings_log_upload_run_now", "立即上报")

fun settingsProxyPanelTitleLabel(): UiText =
    uiText("settings_proxy_panel_title", "出站代理")

fun settingsProxyPanelDescription(): UiText =
    uiText(
        "settings_proxy_panel_desc",
        "Bangumi API、Bangumi Archive 下载和 RSS 请求会共用这个 HTTP 代理。",
    )

fun settingsProxyToggleLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("settings_proxy_toggle_on", "代理已启用")
    } else {
        uiText("settings_proxy_toggle_off", "代理关闭")
    }

fun settingsProxyHostFieldLabel(): UiText =
    uiText("settings_proxy_host_field", "代理地址")

fun settingsProxyPortFieldLabel(): UiText =
    uiText("settings_proxy_port_field", "代理端口")

fun settingsProxySaveActionLabel(): UiText =
    uiText("settings_proxy_save", "保存代理")

fun settingsProxySavedStatus(): UiText =
    uiText("settings_proxy_saved", "代理配置已保存。")

fun settingsProxyCurrentStatus(
    enabled: Boolean,
    host: String,
    port: Int,
): UiText =
    if (enabled) {
        if (host.isBlank()) {
            uiText("settings_proxy_current_unset", "代理已启用，请填写代理地址。")
        } else {
            val summary = settingsProxyMenuSummary(enabled = true, host = host, port = port)
            UiText("settings_proxy_current", listOf(summary), "当前代理：${summary.fallback}")
        }
    } else {
        uiText("settings_proxy_current_off", "当前未启用代理。")
    }

fun settingsAppUpdateMenuSummary(): UiText =
    uiText("settings_app_update_menu_summary", "GitHub")

fun settingsAppUpdatePanelTitleLabel(): UiText =
    uiText("settings_app_update_panel_title", "应用更新")

fun settingsAppUpdatePanelDescription(): UiText =
    uiText("settings_app_update_panel_desc", "从 GitHub Release 获取最新 APK。")

fun settingsAppUpdateCheckActionLabel(): UiText =
    uiText("settings_app_update_check", "检查更新")

fun settingsAppUpdateInstallActionLabel(): UiText =
    uiText("settings_app_update_install", "下载安装")

fun settingsAppUpdatePermissionActionLabel(): UiText =
    uiText("settings_app_update_permission", "安装授权")

fun settingsAppUpdateChannelLabel(): UiText =
    uiText("settings_app_update_channel", "更新渠道")

fun settingsAppUpdateChannelSwitchedStatus(channel: String): UiText =
    UiText("settings_app_update_switched", listOf(channel), "已切换到 $channel 渠道。")

fun settingsAppUpdateIdleStatus(): UiText =
    uiText("settings_app_update_idle", "尚未检查更新。")

fun settingsAppUpdateCheckingStatus(): UiText =
    uiText("settings_app_update_checking", "正在读取更新清单。")

fun settingsAppUpdateReadyStatus(versionName: String): UiText {
    val name = settingsVersionNameValue(versionName)
    return UiText("settings_app_update_ready", listOf(name), "发现新版本 ${name.fallback}。")
}

fun settingsAppUpdateLatestStatus(versionName: String): UiText {
    val name = settingsVersionNameValue(versionName)
    return UiText("settings_app_update_latest", listOf(name), "当前已是最新版本 ${name.fallback}。")
}

fun settingsAppUpdateDownloadProgressStatus(percent: Int?): UiText =
    if (percent != null) {
        UiText("settings_app_update_download_progress", listOf(percent), "正在下载 APK $percent%")
    } else {
        uiText("settings_app_update_downloading", "正在下载 APK。")
    }

fun settingsAppUpdateInstallPermissionStatus(): UiText =
    uiText("settings_app_update_permission_opened", "已打开安装授权页，授权后请返回重试。")

fun settingsAppUpdateInstallPermissionGrantedStatus(): UiText =
    uiText("settings_app_update_permission_granted", "已具备安装授权。")

fun settingsAppUpdateInstallerOpenedStatus(): UiText =
    uiText("settings_app_update_installer_opened", "已打开系统安装器。")

fun settingsAppUpdateBusyStatus(): UiText =
    uiText("settings_app_update_busy", "处理中")

fun settingsAppUpdateAvailableStatus(): UiText =
    uiText("settings_app_update_available", "可更新")

fun settingsAboutPanelTitleLabel(): UiText =
    uiText("settings_about_panel_title", "关于 MiruPlay")

fun settingsAboutPanelDescription(): UiText =
    uiText("settings_about_panel_desc", "当前安装包与版本信息。")

fun settingsAboutAppNameLabel(): UiText =
    uiText("settings_about_app_name", "应用")

fun settingsAboutVersionNameLabel(): UiText =
    uiText("settings_about_version_name", "版本")

fun settingsAboutVersionCodeLabel(): UiText =
    uiText("settings_about_version_code", "版本号")

fun settingsAboutPackageNameLabel(): UiText =
    uiText("settings_about_package_name", "包名")

fun settingsAboutUnknownValue(): UiText =
    uiText("settings_about_unknown", "未知")

fun settingsVersionNameValue(versionName: String): UiText =
    if (versionName.isBlank()) {
        settingsAboutUnknownValue()
    } else {
        UiText("settings_version_name_value", listOf(versionName), versionName)
    }

fun settingsVersionCodeValue(versionCode: Long): UiText =
    if (versionCode > 0L) {
        UiText("settings_version_code_value", listOf(versionCode), versionCode.toString())
    } else {
        settingsAboutUnknownValue()
    }

fun settingsAboutStatusMessage(versionName: String, versionCode: Long): UiText {
    val name = settingsVersionNameValue(versionName)
    val code = settingsVersionCodeValue(versionCode)
    return UiText(
        "settings_about_status",
        listOf(name, code),
        "当前版本 ${name.fallback} · 版本号 ${code.fallback}",
    )
}

fun settingsLogUploadPendingStatus(pendingCount: Int): UiText {
    val safe = pendingCount.coerceAtLeast(0)
    return UiText("settings_log_upload_pending", listOf(safe), "待上报 $safe 条")
}

fun settingsLogUploadUploadStateStatus(isUploading: Boolean): UiText =
    if (isUploading) {
        uiText("settings_log_upload_uploading", "上报中")
    } else {
        uiText("settings_log_upload_idle", "待命")
    }

fun settingsLogUploadTokenConfiguredStatus(configured: Boolean): UiText =
    if (configured) {
        uiText("settings_log_upload_token_saved", "Token 已保存")
    } else {
        uiText("settings_log_upload_token_missing", "未保存 Token")
    }

fun settingsLogUploadLastUploadStatus(lastUploadAt: Long): UiText {
    val timestamp = formatShortLocalTimestamp(lastUploadAt) ?: return uiText(
        "settings_log_upload_never",
        "尚未上报",
    )
    return UiText("settings_log_upload_last", listOf(timestamp), "上次上报 $timestamp")
}

fun settingsLogUploadResultStatus(lastUploadStatus: String?): UiText {
    val status = lastUploadStatus?.trim()?.takeIf { it.isNotEmpty() }
        ?: return uiText("settings_log_upload_result_empty", "暂无上报结果")
    return UiText("settings_log_upload_result", listOf(status), status)
}

fun settingsLogUploadStatusMessage(
    pendingCount: Int,
    isUploading: Boolean,
    tokenConfigured: Boolean,
    lastUploadAt: Long,
    lastUploadStatus: String?,
    entryPointMessage: UiText = settingsAndroidTvLogUploadStatusMessage(),
): UiText =
    UiText(
        "settings_log_upload_status",
        listOf(
            entryPointMessage,
            settingsLogUploadPendingStatus(pendingCount),
            settingsLogUploadUploadStateStatus(isUploading),
            settingsLogUploadTokenConfiguredStatus(tokenConfigured),
            settingsLogUploadLastUploadStatus(lastUploadAt),
            settingsLogUploadResultStatus(lastUploadStatus),
        ),
        listOf(
            entryPointMessage.fallback,
            settingsLogUploadPendingStatus(pendingCount).fallback,
            settingsLogUploadUploadStateStatus(isUploading).fallback,
            settingsLogUploadTokenConfiguredStatus(tokenConfigured).fallback,
            settingsLogUploadLastUploadStatus(lastUploadAt).fallback,
            settingsLogUploadResultStatus(lastUploadStatus).fallback,
        ).joinToString(" · "),
    )

fun settingsAndroidTvLogUploadStatusMessage(
    pendingCount: Int,
    isUploading: Boolean,
    tokenConfigured: Boolean,
    lastUploadAt: Long,
    lastUploadStatus: String?,
): UiText =
    settingsLogUploadStatusMessage(
        pendingCount = pendingCount,
        isUploading = isUploading,
        tokenConfigured = tokenConfigured,
        lastUploadAt = lastUploadAt,
        lastUploadStatus = lastUploadStatus,
    )

fun settingsWebUiPanelTitleLabel(): UiText =
    uiText("settings_webui_panel_title", "WebUI 访问")

fun settingsWebUiPanelDescription(): UiText =
    uiText(
        "settings_webui_panel_desc",
        "默认关闭。开启后，同一局域网设备需要携带访问令牌才能管理媒体源和遥控播放。",
    )

fun settingsWebUiToggleActionLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("settings_webui_toggle_off", "关闭 WebUI")
    } else {
        uiText("settings_webui_toggle_on", "开启 WebUI")
    }

fun settingsWebUiRotateTokenActionLabel(): UiText =
    uiText("settings_webui_rotate_token", "更换令牌")

fun settingsWebUiRefreshAddressActionLabel(): UiText =
    uiText("settings_webui_refresh_address", "刷新地址")

fun settingsWebUiAccessTokenMissingValue(): UiText =
    uiText("settings_webui_token_missing", "未生成")

fun settingsWebUiAccessTokenLabel(accessToken: String): UiText {
    val token = accessToken.ifBlank { settingsWebUiAccessTokenMissingValue().fallback }
    return UiText("settings_webui_token_label", listOf(token), "访问令牌：$token")
}

fun settingsWebUiDisabledStatus(): UiText =
    uiText("settings_webui_disabled", "WebUI 当前未启用，不会监听局域网端口。")

fun settingsWebUiNoLanAddressStatus(): UiText =
    uiText("settings_webui_no_lan", "暂未检测到局域网地址，请确认电视已连接网络后刷新。")

fun settingsWebUiAvailableAddressesLabel(): UiText =
    uiText("settings_webui_addresses", "可用地址")

fun settingsWebUiAddressLabel(index: Int): UiText =
    if (index <= 0) {
        uiText("settings_webui_address_main", "主地址")
    } else {
        uiText("settings_webui_address_secondary", "备用地址")
    }

fun settingsWebUiQrOpenLabel(): UiText =
    uiText("settings_webui_qr_open", "扫码打开")

fun settingsWebUiTileLabel(): UiText =
    uiText("settings_webui_tile", "WebUI")

fun settingsWebUiAndroidTvValue(): UiText =
    rawUiText("Android TV")

fun settingsActiveSourceSharedDetail(): UiText =
    uiText(
        "settings_active_source_shared_detail",
        "媒体库、远程浏览器和 Cloud/RSS 共用这个活动源。",
    )

fun settingsPosterWallIndexDetail(): UiText =
    uiText("settings_poster_wall_index_detail", "扫描后优先回到媒体库海报墙。")

fun settingsPlaybackPageDetail(): UiText =
    uiText("settings_playback_page_detail", "mpv、RIFE、字幕和起播时间在播放页调整。")

fun settingsRecentPlaybackDetail(): UiText =
    uiText("settings_recent_playback_detail", "mpv 进度同步后会刷新这里。")

fun settingsSelectedMediaDetail(): UiText =
    uiText("settings_selected_media_detail", "从海报墙或详情页选择后可直接播放。")

fun settingsPlaybackStatusMessage(): UiText =
    uiText(
        "settings_playback_status",
        "mpv 播放设置保留在播放页，RIFE/字幕/起播秒数仍可直接调整。",
    )

fun settingsMetadataStatusMessage(): UiText =
    uiText(
        "settings_metadata_status",
        "Bangumi 搜索、批量预览、应用和撤销保留在详情页。",
    )

fun metadataBangumiTokenFieldLabel(): UiText =
    rawUiText("Bangumi Access Token")

fun metadataTmdbTokenFieldLabel(): UiText =
    rawUiText("TMDB Read Access Token")

fun metadataBangumiTokenOptionalHint(): UiText =
    uiText(
        "settings_metadata_bangumi_hint",
        "Bangumi Token 是可选项，用于 Bangumi 收藏与观看进度同步；元数据搜索不需要 Token。",
    )

fun metadataTmdbTokenOptionalHint(): UiText =
    uiText(
        "settings_metadata_tmdb_hint",
        "TMDB Token 是可选项，用于电视剧详情补充海报、简介和首播日期；不填也能正常本地浏览和播放。",
    )

fun metadataBangumiTokenSavedStatus(): UiText =
    uiText("settings_metadata_bangumi_saved", "Token 已保存在加密存储中。")

fun metadataTmdbTokenSavedStatus(): UiText =
    uiText("settings_metadata_tmdb_saved", "TMDB Token 已保存在加密存储中。")

fun metadataBangumiTokenMissingStatus(): UiText =
    uiText("settings_metadata_bangumi_missing", "当前未设置 Token。")

fun metadataTmdbTokenMissingStatus(): UiText =
    uiText("settings_metadata_tmdb_missing", "当前未设置 TMDB Token。")

fun metadataBangumiTokenTileLabel(): UiText =
    uiText("settings_metadata_bangumi_tile", "Bangumi Token")

fun metadataBangumiTokenTileDetail(): UiText =
    uiText("settings_metadata_bangumi_tile_detail", "仅用于 Bangumi 收藏与观看进度同步。")

fun metadataBangumiTokenSavedMessage(): UiText =
    uiText("settings_metadata_bangumi_saved_msg", "Bangumi Token 已保存。")

fun metadataBangumiTokenEmptyMessage(): UiText =
    uiText("settings_metadata_bangumi_empty_msg", "Bangumi Access Token 为空，未保存。")

fun metadataBangumiTokenClearedMessage(): UiText =
    uiText("settings_metadata_bangumi_cleared_msg", "Bangumi Access Token 已清除。")

fun metadataTmdbTokenSavedMessage(): UiText =
    uiText("settings_metadata_tmdb_saved_msg", "TMDB Token 已保存。")

fun metadataTmdbTokenEmptyMessage(): UiText =
    uiText("settings_metadata_tmdb_empty_msg", "TMDB Read Access Token 为空，未保存。")

fun metadataTmdbTokenClearedMessage(): UiText =
    uiText("settings_metadata_tmdb_cleared_msg", "TMDB Read Access Token 已清除。")

data class TmdbTokenSaveResult(
    val token: String?,
    val configured: Boolean,
    val status: UiText,
    val shouldPersistTokenInput: Boolean,
)

fun saveTmdbTokenFormResult(
    input: String,
    existingToken: String?,
): TmdbTokenSaveResult {
    val normalized = input.trim()
    if (normalized.isBlank()) {
        return TmdbTokenSaveResult(
            token = existingToken,
            configured = !existingToken.isNullOrBlank(),
            status = metadataTmdbTokenEmptyMessage(),
            shouldPersistTokenInput = false,
        )
    }
    return TmdbTokenSaveResult(
        token = normalized,
        configured = true,
        status = metadataTmdbTokenSavedMessage(),
        shouldPersistTokenInput = true,
    )
}

data class BangumiTokenSaveResult(
    val token: String?,
    val configured: Boolean,
    val status: UiText,
    val shouldPersistTokenInput: Boolean,
)

fun saveBangumiTokenFormResult(
    input: String,
    existingToken: String?,
): BangumiTokenSaveResult {
    val normalized = input.trim()
    if (normalized.isBlank()) {
        return BangumiTokenSaveResult(
            token = existingToken,
            configured = !existingToken.isNullOrBlank(),
            status = metadataBangumiTokenEmptyMessage(),
            shouldPersistTokenInput = false,
        )
    }
    return BangumiTokenSaveResult(
        token = normalized,
        configured = true,
        status = metadataBangumiTokenSavedMessage(),
        shouldPersistTokenInput = true,
    )
}

fun metadataBangumiTokenSettingsStatus(configured: Boolean): UiText {
    val status = settingsMetadataStatusMessage()
    return if (configured) {
        val saved = metadataBangumiTokenSavedMessage()
        UiText(
            "settings_metadata_bangumi_status_on",
            listOf(status, saved),
            "${status.fallback} ${saved.fallback}",
        )
    } else {
        UiText(
            "settings_metadata_bangumi_status_hint",
            listOf(status),
            "${status.fallback} 保存 Token 后可同步观看进度。",
        )
    }
}

fun settingsIndexSharedDetail(): UiText =
    uiText("settings_index_shared_detail", "本地、WebDAV、SMB 都写入同一媒体库索引。")

fun settingsCloudDriveRescanSourceDetail(): UiText =
    uiText("settings_clouddrive_rescan_detail", "CloudDrive 完成后可触发这个源的重扫。")

fun settingsRecentScanStatusDetail(): UiText =
    uiText("settings_recent_scan_detail", "扫描入口也保留在媒体库顶部。")

fun settingsScanPanelTitleLabel(): UiText =
    uiText("settings_scan_panel_title", "媒体库扫描")

fun settingsScanPanelDescription(): UiText =
    uiText(
        "settings_scan_panel_desc",
        "首页的扫描按钮会立即执行；定时扫描只会在到达间隔后回到首页时触发。",
    )

fun settingsAppModeTitleLabel(): UiText =
    uiText("settings_app_mode_title", "首页模式")

fun settingsAppModeOptionLabel(mode: String): UiText =
    when {
        mode.equals("drama", ignoreCase = true) -> uiText("settings_app_mode_drama", "电视剧")
        mode.equals("music", ignoreCase = true) -> uiText("settings_app_mode_music", "音乐")
        else -> uiText("settings_app_mode_anime", "动漫")
    }

fun settingsAppModeLabel(mode: String): UiText =
    settingsAppModeOptionLabel(mode)

fun settingsAppModeStatus(mode: String): UiText {
    val option = settingsAppModeOptionLabel(mode)
    return UiText("settings_app_mode_status", listOf(option), "下次启动会进入${option.fallback}首页。")
}

fun settingsAppModeHint(): UiText =
    uiText(
        "settings_app_mode_hint",
        "这里只决定应用下次启动先进动漫首页还是电视剧首页，不会立刻切换当前界面。",
    )

fun mediaSourceContentModeTitleLabel(): UiText =
    uiText("settings_content_type_title", "内容类型")

fun mediaSourceContentModeLabel(mode: MediaContentMode): UiText =
    when (mode) {
        MediaContentMode.ANIME -> uiText("settings_content_type_anime", "动漫")
        MediaContentMode.DRAMA -> uiText("settings_content_type_drama", "电视剧")
        MediaContentMode.MUSIC -> uiText("settings_content_type_music", "音乐")
    }

fun mediaSourceContentModeHint(mode: MediaContentMode): UiText =
    when (mode) {
        MediaContentMode.ANIME -> uiText("settings_content_type_hint_anime", "这个源会出现在动漫首页。")
        MediaContentMode.DRAMA -> uiText("settings_content_type_hint_drama", "这个源会留给电视剧首页使用。")
        MediaContentMode.MUSIC -> uiText("settings_content_type_hint_music", "这个源会出现在音乐首页。")
    }

fun settingsAutoScanToggleLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("settings_autoscan_on", "定时已开")
    } else {
        uiText("settings_autoscan_off", "定时关闭")
    }

fun settingsScanIntervalOptionLabel(hours: Int): UiText {
    val safe = hours.coerceAtLeast(0)
    return UiText("settings_scan_interval_hours", listOf(safe), "${safe}小时")
}

fun settingsCurrentScanIntervalStatus(
    intervalHours: Int,
    lastScanText: UiText,
): UiText {
    val safe = intervalHours.coerceAtLeast(0)
    return UiText(
        "settings_current_interval",
        listOf(safe, lastScanText),
        "当前间隔 $safe 小时 · ${lastScanText.fallback}",
    )
}

fun settingsLastScanLabel(lastScanAt: Long): UiText {
    val timestamp = formatShortLocalTimestamp(lastScanAt)
        ?: return uiText("settings_last_scan_never", "还没有扫描记录")
    return UiText("settings_last_scan", listOf(timestamp), "上次扫描 $timestamp")
}

fun settingsCurrentScanIntervalStatus(
    intervalHours: Int,
    lastScanAt: Long,
): UiText =
    settingsCurrentScanIntervalStatus(intervalHours, settingsLastScanLabel(lastScanAt))

fun settingsLibraryDisplayTitleLabel(): UiText =
    uiText("settings_library_display_title", "媒体库显示")

fun settingsPosterWallArrangementTitleLabel(): UiText =
    uiText("settings_poster_wall_title", "海报墙排列")

fun settingsMergeSameAnimeToggleLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("settings_merge_on", "同番合并")
    } else {
        uiText("settings_merge_off", "目录分开")
    }

fun settingsMergeSameAnimeStatus(enabled: Boolean): UiText =
    if (enabled) {
        uiText("settings_merge_status_on", "首页和详情会按 Bangumi ID 或标题合并同一番。")
    } else {
        uiText("settings_merge_status_off", "首页按扫描出的目录条目分别显示。")
    }

fun settingsSelectedMetadataEntryDetail(): UiText =
    uiText("settings_selected_entry_detail", "详情页会显示可应用的 Bangumi 匹配。")

fun settingsMetadataMatchStatusDetail(): UiText =
    uiText("settings_metadata_match_detail", "支持单条应用、批量预览、应用和撤销。")

fun settingsMetadataCandidateScopeDetail(): UiText =
    uiText("settings_metadata_candidate_detail", "批量匹配会跳过已有冲突元数据。")

fun settingsSourceTileLabel(): UiText =
    uiText("settings_tile_source", "媒体源")

fun settingsActiveSourceTileLabel(): UiText =
    uiText("settings_tile_active_source", "当前源")

fun settingsPosterWallIndexTileLabel(): UiText =
    uiText("settings_tile_poster_index", "海报墙索引")

fun settingsPlaybackModeTileLabel(): UiText =
    uiText("settings_tile_playback_mode", "播放模式")

fun settingsRecentPlaybackTileLabel(): UiText =
    uiText("settings_tile_recent_playback", "继续观看")

fun settingsSelectedMediaTileLabel(): UiText =
    uiText("settings_tile_selected_media", "当前媒体")

fun settingsIndexTileLabel(): UiText =
    uiText("settings_tile_index", "索引")

fun settingsPostSyncSourceTileLabel(): UiText =
    uiText("settings_tile_post_sync_source", "同步后扫描源")

fun settingsRecentScanStatusTileLabel(): UiText =
    uiText("settings_tile_recent_scan", "最近扫描状态")

fun settingsSelectedMetadataEntryTileLabel(): UiText =
    uiText("settings_tile_selected_entry", "选中条目")

fun settingsMetadataMatchStatusTileLabel(): UiText =
    uiText("settings_tile_match_status", "匹配状态")

fun settingsMetadataCandidateScopeTileLabel(): UiText =
    uiText("settings_tile_candidate_scope", "候选范围")

fun settingsCountValue(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("settings_count_value", listOf(safe), "$safe 个")
}

fun settingsRecordCountValue(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("settings_record_count_value", listOf(safe), "$safe 条")
}

fun settingsIndexedCountValue(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("settings_indexed_count_value", listOf(safe), "$safe 条索引")
}

fun settingsSavedStateValue(saved: Boolean): UiText =
    if (saved) {
        uiText("settings_saved_state_saved", "已保存")
    } else {
        uiText("settings_saved_state_unsaved", "未保存")
    }

fun settingsNoSourceSelectedValue(): UiText =
    uiText("settings_no_source_selected", "未选择")

fun settingsMissingSourceValue(sourceId: Long): UiText =
    UiText("settings_missing_source", listOf(sourceId), "缺失媒体源 #$sourceId")

fun settingsSourceTypeBreakdown(sources: List<MediaSourceInfo>): UiText {
    if (sources.isEmpty()) {
        return uiText("settings_source_empty", "尚未添加本地、WebDAV、SMB 或 RSS 源。")
    }
    return joinUiText(
        MediaSourceType.entries.mapNotNull { type ->
            val count = sources.count { it.type == type }
            if (count == 0) {
                null
            } else {
                val label = type.tvLabel()
                UiText(
                    "settings_source_breakdown_item",
                    listOf(label, count),
                    "${label.fallback} $count",
                )
            }
        },
    )
}

fun settingsActiveSourceLabel(source: MediaSourceInfo?): UiText =
    source?.sourcePickerTitle() ?: settingsNoSourceSelectedValue()

fun settingsLinkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): UiText {
    if (sourceId == null) return settingsNoSourceSelectedValue()
    return sources.firstOrNull { it.id == sourceId }?.sourcePickerTitle()
        ?: settingsMissingSourceValue(sourceId)
}

data class SettingsSummaryTile(
    val label: UiText,
    val value: UiText,
    val detail: UiText,
)

fun sourceSettingsTiles(
    sources: List<MediaSourceInfo>,
    activeSourceLabel: String,
    indexedItemCount: Int,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsSourceTileLabel(),
            value = settingsCountValue(sources.size),
            detail = settingsSourceTypeBreakdown(sources),
        ),
        SettingsSummaryTile(
            label = settingsActiveSourceTileLabel(),
            value = rawUiText(activeSourceLabel),
            detail = settingsActiveSourceSharedDetail(),
        ),
        SettingsSummaryTile(
            label = settingsPosterWallIndexTileLabel(),
            value = settingsRecordCountValue(indexedItemCount),
            detail = settingsPosterWallIndexDetail(),
        ),
    )

fun playbackSettingsTiles(
    playbackSummary: UiText,
    recentCount: Int,
    selectedMediaTitle: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsPlaybackModeTileLabel(),
            value = playbackSummary,
            detail = settingsPlaybackPageDetail(),
        ),
        SettingsSummaryTile(
            label = settingsRecentPlaybackTileLabel(),
            value = settingsRecordCountValue(recentCount),
            detail = settingsRecentPlaybackDetail(),
        ),
        SettingsSummaryTile(
            label = settingsSelectedMediaTileLabel(),
            value = rawUiText(selectedMediaTitle),
            detail = settingsSelectedMediaDetail(),
        ),
    )

fun scanSettingsTiles(
    indexedItemCount: Int,
    linkedSourceLabel: String,
    libraryStatus: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsIndexTileLabel(),
            value = settingsRecordCountValue(indexedItemCount),
            detail = settingsIndexSharedDetail(),
        ),
        SettingsSummaryTile(
            label = settingsPostSyncSourceTileLabel(),
            value = rawUiText(linkedSourceLabel),
            detail = settingsCloudDriveRescanSourceDetail(),
        ),
        SettingsSummaryTile(
            label = settingsRecentScanStatusTileLabel(),
            value = rawUiText(mediaSourceStatusText(libraryStatus)),
            detail = settingsRecentScanStatusDetail(),
        ),
    )

fun metadataSettingsTiles(
    selectedMediaTitle: String,
    metadataSummary: UiText,
    indexedItemCount: Int,
    bangumiTokenConfigured: Boolean = false,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsSelectedMetadataEntryTileLabel(),
            value = rawUiText(selectedMediaTitle),
            detail = settingsSelectedMetadataEntryDetail(),
        ),
        SettingsSummaryTile(
            label = settingsMetadataMatchStatusTileLabel(),
            value = metadataSummary,
            detail = settingsMetadataMatchStatusDetail(),
        ),
        SettingsSummaryTile(
            label = settingsMetadataCandidateScopeTileLabel(),
            value = settingsIndexedCountValue(indexedItemCount),
            detail = settingsMetadataCandidateScopeDetail(),
        ),
        SettingsSummaryTile(
            label = metadataBangumiTokenTileLabel(),
            value = settingsSavedStateValue(bangumiTokenConfigured),
            detail = metadataBangumiTokenTileDetail(),
        ),
    )

fun logUploadSettingsTiles(): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = rawUiText("OpenObserve"),
            value = rawUiText("JSON"),
            detail = uiText(
                "settings_tile_openobserve_detail",
                "API 地址、Stream 和访问令牌可在设置页或 Web 控制端保存。",
            ),
        ),
        SettingsSummaryTile(
            label = uiText("settings_tile_local_log_label", "本地日志"),
            value = uiText("settings_tile_local_log_value", "自动上报"),
            detail = uiText(
                "settings_tile_local_log_detail",
                "应用日志进入本地队列后按配置批量发送。",
            ),
        ),
        SettingsSummaryTile(
            label = uiText("settings_tile_credential_label", "凭据"),
            value = uiText("settings_tile_credential_value", "安全存储"),
            detail = uiText(
                "settings_tile_credential_detail",
                "令牌只保存配置状态，清理后会停止上报。",
            ),
        ),
    )

fun aboutSettingsTiles(
    appName: String,
    versionName: String,
    versionCode: Long,
    packageName: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsAboutAppNameLabel(),
            value = rawUiText(appName.ifBlank { "MiruPlay" }),
            detail = settingsAboutPanelDescription(),
        ),
        SettingsSummaryTile(
            label = settingsAboutVersionNameLabel(),
            value = settingsVersionNameValue(versionName),
            detail = uiText(
                "settings_tile_release_note",
                "Release 与 nightly 构建都会显示这里的安装版本。",
            ),
        ),
        SettingsSummaryTile(
            label = settingsAboutVersionCodeLabel(),
            value = settingsVersionCodeValue(versionCode),
            detail = run {
                val pkg = settingsAboutPackageNameLabel()
                val unknown = settingsAboutUnknownValue()
                val name = packageName.ifBlank { unknown.fallback }
                UiText(
                    "settings_tile_package_detail",
                    listOf(pkg, name),
                    "${pkg.fallback}：$name",
                )
            },
        ),
    )

fun settingsWebUiTileDetail(): UiText =
    uiText("settings_webui_tile_detail", "二维码和局域网令牌入口由各平台设置页提供。")

fun settingsCloudRssOverviewValue(enabled: Boolean): UiText =
    if (enabled) {
        uiText("settings_clouddrive_rss_on", "已启用")
    } else {
        uiText("settings_clouddrive_rss_off", "未启用")
    }

fun settingsCloudRssSubscriptionsValue(subscriptionCount: Int): UiText =
    settingsCountValue(subscriptionCount)

fun settingsCloudRssLinkedSourceValue(linkedSourceLabel: String): UiText =
    rawUiText(linkedSourceLabel)

fun settingsCloudRssSchedulerIdleStatus(): UiText =
    uiText("settings_clouddrive_rss_idle", "调度器待命，尚未检查。")

fun settingsCloudRssStatusFallback(): UiText =
    uiText("settings_clouddrive_rss_fallback", "调度器待命，尚未检查。")

fun settingsCloudRssOverviewDetail(value: String): UiText =
    rawUiText(value)

fun notifArchiveDownloadTitle(): UiText =
    uiText("notif_archive_download_title", "Bangumi Archive 下载")

fun notifArchiveDownloadPreparing(): UiText =
    uiText("notif_archive_download_preparing", "正在准备下载 Archive")

fun notifArchiveDownloading(): UiText =
    uiText("notif_archive_downloading", "Bangumi Archive 正在下载。")

fun notifArchiveUpdated(): UiText =
    uiText("notif_archive_updated", "Bangumi Archive 已更新。")

fun notifArchiveFailed(): UiText =
    uiText("notif_archive_failed", "Bangumi Archive 下载失败。")

fun archiveHintStatus(): UiText =
    uiText("notif_archive_hint", "Bangumi Archive 每周自动更新；也可以手动下载或更新。")

fun archiveDownloadProgressKnown(downloaded: String, total: String, percent: Int): UiText =
    UiText(
        "notif_archive_progress_known",
        listOf(downloaded, total, percent),
        "已下载 $downloaded / $total（$percent%）",
    )

fun archiveDownloadProgressUnknown(downloaded: String): UiText =
    UiText("notif_archive_progress_unknown", listOf(downloaded), "已下载 $downloaded")

fun notifCloudDriveSyncTitle(): UiText =
    uiText("notif_clouddrive_title", "CloudDrive/RSS 同步")

fun notifCloudDriveSyncText(): UiText =
    uiText("notif_clouddrive_text", "正在下载、整理并扫描订阅内容")

fun notifAppUpdateDownloadTitle(): UiText =
    uiText("notif_app_update_download_title", "应用更新下载")

fun cloudDriveRootDisplayName(): UiText =
    uiText("dir_browser_root_clouddrive", "CloudDrive 根目录")

fun localStorageRootDisplayName(): UiText =
    uiText("dir_browser_root_local", "设备存储")
