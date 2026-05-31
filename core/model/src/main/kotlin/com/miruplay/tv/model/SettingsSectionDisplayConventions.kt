package com.miruplay.tv.model

enum class MiruPlaySettingsSection(
    val androidTvTitle: String,
    val androidTvDescription: String,
    val desktopTitle: String,
    val desktopDescription: String,
) {
    WEB_UI(
        androidTvTitle = "WebUI",
        androidTvDescription = "访问地址与二维码",
        desktopTitle = "WebUI",
        desktopDescription = "访问地址与二维码",
    ),
    SOURCES(
        androidTvTitle = "媒体源",
        androidTvDescription = "本地、WebDAV、SMB",
        desktopTitle = "媒体源",
        desktopDescription = "本地、WebDAV、SMB",
    ),
    PLAYBACK(
        androidTvTitle = "播放",
        androidTvDescription = "播完动作",
        desktopTitle = "播放",
        desktopDescription = "mpv 与 RIFE",
    ),
    CLOUD_DRIVE(
        androidTvTitle = "CloudDrive",
        androidTvDescription = "RSS 离线下载与入库",
        desktopTitle = "云盘",
        desktopDescription = "RSS 离线下载与入库",
    ),
    PROXY(
        androidTvTitle = "代理配置",
        androidTvDescription = "Bangumi、Archive 与 RSS 出站代理",
        desktopTitle = "代理",
        desktopDescription = "Bangumi、Archive 与 RSS 出站代理",
    ),
    SCAN(
        androidTvTitle = "扫描",
        androidTvDescription = "媒体库更新策略",
        desktopTitle = "扫描",
        desktopDescription = "媒体库更新",
    ),
    LOG_UPLOAD(
        androidTvTitle = "日志上报",
        androidTvDescription = "OpenObserve JSON",
        desktopTitle = "日志",
        desktopDescription = "OpenObserve JSON",
    ),
    APP_UPDATE(
        androidTvTitle = "更新",
        androidTvDescription = "GitHub APK",
        desktopTitle = "更新",
        desktopDescription = "GitHub Release",
    ),
    METADATA(
        androidTvTitle = "元数据",
        androidTvDescription = "Bangumi Token",
        desktopTitle = "元数据",
        desktopDescription = "Bangumi 匹配",
    ),
    ABOUT(
        androidTvTitle = "关于",
        androidTvDescription = "版本与应用信息",
        desktopTitle = "关于",
        desktopDescription = "版本与应用信息",
    ),
}

val androidTvSettingsSectionOrder: List<MiruPlaySettingsSection> =
    listOf(
        MiruPlaySettingsSection.WEB_UI,
        MiruPlaySettingsSection.SOURCES,
        MiruPlaySettingsSection.PLAYBACK,
        MiruPlaySettingsSection.CLOUD_DRIVE,
        MiruPlaySettingsSection.PROXY,
        MiruPlaySettingsSection.SCAN,
        MiruPlaySettingsSection.LOG_UPLOAD,
        MiruPlaySettingsSection.APP_UPDATE,
        MiruPlaySettingsSection.METADATA,
        MiruPlaySettingsSection.ABOUT,
    )

val desktopSettingsSectionOrder: List<MiruPlaySettingsSection> =
    listOf(
        MiruPlaySettingsSection.WEB_UI,
        MiruPlaySettingsSection.SOURCES,
        MiruPlaySettingsSection.PLAYBACK,
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

fun MiruPlaySettingsSection.stepDesktopSettingsSection(delta: Int): MiruPlaySettingsSection? =
    stepInSettingsOrder(desktopSettingsSectionOrder, delta)

fun settingsWebUiMenuSummary(addressCount: Int): String =
    if (addressCount > 0) "$addressCount 个地址" else "等待网络"

fun settingsSourcesMenuSummary(sourceCount: Int): String =
    "$sourceCount 个源"

fun settingsCloudDriveMenuSummary(enabled: Boolean, rssCount: Int): String =
    if (enabled) "$rssCount 个订阅" else "未启用"

fun settingsProxyMenuSummary(
    enabled: Boolean,
    host: String = "",
    port: Int = DEFAULT_RSS_PROXY_PORT,
): String =
    when {
        !enabled -> "未启用"
        host.isBlank() -> "待填写"
        else -> "${host.trim()}:${port.coerceIn(MIN_RSS_PROXY_PORT, MAX_RSS_PROXY_PORT)}"
    }

fun settingsScanMenuSummary(
    autoScanEnabled: Boolean,
    mergeSameAnimeEnabled: Boolean,
    posterWallArrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
): String =
    buildList {
        if (autoScanEnabled) add("定时")
        if (mergeSameAnimeEnabled) add("合并")
        if (posterWallArrangement == PosterWallArrangement.RELEASE_SEASON) add("新番季")
    }.joinToString(" · ").ifBlank { "定时关闭" }

fun settingsLogUploadMenuSummary(): String =
    "OpenObserve"

fun settingsAndroidTvLogUploadMenuSummary(
    enabled: Boolean = false,
    tokenConfigured: Boolean = false,
    isUploading: Boolean = false,
): String =
    when {
        enabled && isUploading -> "上报中"
        enabled && tokenConfigured -> "自动上报"
        enabled -> "等待 Token"
        else -> "未启用"
    }

fun settingsMetadataTokenMenuSummary(hasToken: Boolean): String =
    if (hasToken) "Token 已设置" else "未设置"

fun settingsAboutMenuSummary(versionName: String): String =
    "版本 ${settingsVersionNameValue(versionName)}"

data class SettingsSectionMenuSummaryInput(
    val webUiAddressCount: Int = 0,
    val sourceCount: Int = 0,
    val playbackSummary: String = "",
    val cloudDriveEnabled: Boolean = false,
    val rssCount: Int = 0,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = DEFAULT_RSS_PROXY_PORT,
    val autoScanEnabled: Boolean = false,
    val mergeSameAnimeEnabled: Boolean = false,
    val posterWallArrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
    val metadataSummary: String = "",
    val logUploadSummary: String = settingsLogUploadMenuSummary(),
    val appUpdateSummary: String = settingsAppUpdateMenuSummary(),
    val appVersionName: String = "",
)

fun MiruPlaySettingsSection.settingsMenuSummary(
    input: SettingsSectionMenuSummaryInput,
): String =
    when (this) {
        MiruPlaySettingsSection.WEB_UI -> settingsWebUiMenuSummary(input.webUiAddressCount)
        MiruPlaySettingsSection.SOURCES -> settingsSourcesMenuSummary(input.sourceCount)
        MiruPlaySettingsSection.PLAYBACK -> input.playbackSummary
        MiruPlaySettingsSection.CLOUD_DRIVE -> settingsCloudDriveMenuSummary(input.cloudDriveEnabled, input.rssCount)
        MiruPlaySettingsSection.PROXY -> settingsProxyMenuSummary(input.proxyEnabled, input.proxyHost, input.proxyPort)
        MiruPlaySettingsSection.SCAN -> settingsScanMenuSummary(
            input.autoScanEnabled,
            input.mergeSameAnimeEnabled,
            input.posterWallArrangement,
        )
        MiruPlaySettingsSection.LOG_UPLOAD -> input.logUploadSummary
        MiruPlaySettingsSection.APP_UPDATE -> input.appUpdateSummary
        MiruPlaySettingsSection.METADATA -> input.metadataSummary
        MiruPlaySettingsSection.ABOUT -> settingsAboutMenuSummary(input.appVersionName)
    }

fun settingsDesktopScanMenuSummary(): String =
    "媒体库更新"

fun settingsDesktopWebUiMenuSummary(): String =
    "访问地址"

fun settingsDesktopWebUiMenuSummary(addressCount: Int): String =
    settingsWebUiMenuSummary(addressCount)

fun settingsMenuPanelTitle(): String =
    "设置菜单"

fun settingsScreenTitleLabel(): String =
    "设置"

fun settingsScreenSubtitleLabel(): String =
    "管理媒体源、WebUI 和元数据服务"

fun settingsMenuPanelDescriptionDesktop(): String =
    "像 TV 版一样按分类管理桌面能力。"

fun settingsMenuPanelDescriptionAndroidTv(): String =
    "按上下切换分类，向右进入当前设置。"

fun settingsMenuPanelDescription(): String =
    settingsMenuPanelDescriptionDesktop()

fun settingsOpenLibraryActionLabel(): String =
    "打开海报墙"

fun settingsScanActiveSourceActionLabel(): String =
    "扫描当前源"

fun settingsOpenPlayerActionLabel(): String =
    "打开播放器"

fun settingsOpenDetailsActionLabel(): String =
    "打开详情"

fun settingsBackActionLabel(): String =
    "返回"

fun settingsSaveTokenActionLabel(): String =
    "保存 Token"

fun settingsClearTokenActionLabel(): String =
    "清除 Token"

fun settingsDesktopScanStatusMessage(): String =
    "扫描入口保留在媒体库海报墙和 CloudDrive 同步流程中。"

fun settingsAndroidTvLogUploadStatusMessage(): String =
    "可在当前页面配置 OpenObserve JSON；本地日志会按同一配置写入上报队列。"

fun settingsAndroidTvLogUploadHintMessage(): String =
    "保存设置会同时保存配置和当前 Token；开启自动上报后会定时上传待处理日志。"

fun settingsDesktopLogUploadStatusMessage(): String =
    "可在当前页面或 Web 控制端配置 OpenObserve JSON；本地日志会按同一配置写入上报队列。"

fun settingsLogUploadAutoToggleLabel(): String =
    "自动上报"

fun settingsLogUploadEndpointFieldLabel(): String =
    "OpenObserve API 地址"

fun settingsLogUploadStreamFieldLabel(): String =
    "Stream"

fun settingsLogUploadTokenFieldLabel(): String =
    "OpenObserve Token"

fun settingsLogUploadSaveSettingsActionLabel(): String =
    "保存设置"

fun settingsLogUploadRunNowActionLabel(): String =
    "立即上报"

fun settingsProxyPanelTitleLabel(): String =
    "出站代理"

fun settingsProxyPanelDescription(): String =
    "Bangumi API、Bangumi Archive 下载和 RSS 请求会共用这个 HTTP 代理。"

fun settingsProxyToggleLabel(enabled: Boolean): String =
    if (enabled) "代理已启用" else "代理关闭"

fun settingsProxyHostFieldLabel(): String =
    "代理地址"

fun settingsProxyPortFieldLabel(): String =
    "代理端口"

fun settingsProxySaveActionLabel(): String =
    "保存代理"

fun settingsProxySavedStatus(): String =
    "代理配置已保存。"

fun settingsProxyCurrentStatus(
    enabled: Boolean,
    host: String,
    port: Int,
): String =
    if (enabled) {
        val summary = settingsProxyMenuSummary(enabled = true, host = host, port = port)
        if (summary == "待填写") "代理已启用，请填写代理地址。" else "当前代理：$summary"
    } else {
        "当前未启用代理。"
    }

fun settingsAppUpdateMenuSummary(): String =
    "GitHub"

fun settingsAppUpdatePanelTitleLabel(): String =
    "应用更新"

fun settingsAppUpdatePanelDescription(): String =
    "从 GitHub Release 获取最新 APK。"

fun settingsAppUpdateCheckActionLabel(): String =
    "检查更新"

fun settingsAppUpdateInstallActionLabel(): String =
    "下载安装"

fun settingsAppUpdatePermissionActionLabel(): String =
    "安装授权"

fun settingsAppUpdateIdleStatus(): String =
    "尚未检查更新。"

fun settingsAppUpdateCheckingStatus(): String =
    "正在检查 GitHub Release。"

fun settingsAppUpdateReadyStatus(versionName: String): String =
    "发现新版本 ${versionName.ifBlank { "未知版本" }}。"

fun settingsAppUpdateLatestStatus(versionName: String): String =
    "当前已是最新版本 ${versionName.ifBlank { "未知版本" }}。"

fun settingsAppUpdateDownloadProgressStatus(percent: Int?): String =
    percent?.let { "正在下载 APK $it%" } ?: "正在下载 APK。"

fun settingsAppUpdateInstallPermissionStatus(): String =
    "已打开安装授权页，授权后请返回重试。"

fun settingsAppUpdateInstallPermissionGrantedStatus(): String =
    "已具备安装授权。"

fun settingsAppUpdateInstallerOpenedStatus(): String =
    "已打开系统安装器。"

fun settingsDesktopAppUpdatePanelDescription(): String =
    "从 GitHub Release 获取最新 Windows 桌面包。"

fun settingsDesktopAppUpdateStatusMessage(): String =
    "Windows 版更新请从 GitHub Release 下载最新桌面 ZIP 或安装包。"

fun settingsDesktopAppUpdateOpenReleaseActionLabel(): String =
    "打开 Release"

fun settingsDesktopAppUpdateReleaseOpenedStatus(): String =
    "已打开 GitHub Release 页面。"

fun settingsDesktopAppUpdateReleaseOpenFailedStatus(reason: String?): String =
    "无法打开 GitHub Release 页面：${reason?.takeIf { it.isNotBlank() } ?: "当前系统不支持打开浏览器"}。"

fun settingsAboutPanelTitleLabel(): String =
    "关于 MiruPlay"

fun settingsAboutPanelDescription(): String =
    "当前安装包与版本信息。"

fun settingsAboutAppNameLabel(): String =
    "应用"

fun settingsAboutVersionNameLabel(): String =
    "版本"

fun settingsAboutVersionCodeLabel(): String =
    "版本号"

fun settingsAboutPackageNameLabel(): String =
    "包名"

fun settingsAboutUnknownValue(): String =
    "未知"

fun settingsVersionNameValue(versionName: String): String =
    versionName.ifBlank { settingsAboutUnknownValue() }

fun settingsVersionCodeValue(versionCode: Long): String =
    versionCode.takeIf { it > 0L }?.toString() ?: settingsAboutUnknownValue()

fun settingsAboutStatusMessage(versionName: String, versionCode: Long): String =
    "当前版本 ${settingsVersionNameValue(versionName)} · 版本号 ${settingsVersionCodeValue(versionCode)}"

fun settingsLogUploadPendingStatus(pendingCount: Int): String =
    "待上报 ${pendingCount.coerceAtLeast(0)} 条"

fun settingsLogUploadUploadStateStatus(isUploading: Boolean): String =
    if (isUploading) "上报中" else "待命"

fun settingsLogUploadTokenConfiguredStatus(configured: Boolean): String =
    if (configured) "Token 已保存" else "未保存 Token"

fun settingsLogUploadLastUploadStatus(lastUploadAt: Long): String =
    formatShortLocalTimestamp(lastUploadAt)?.let { "上次上报 $it" } ?: "尚未上报"

fun settingsLogUploadResultStatus(lastUploadStatus: String?): String =
    lastUploadStatus?.trim()?.takeIf { it.isNotEmpty() } ?: "暂无上报结果"

fun settingsLogUploadStatusMessage(
    pendingCount: Int,
    isUploading: Boolean,
    tokenConfigured: Boolean,
    lastUploadAt: Long,
    lastUploadStatus: String?,
    entryPointMessage: String = settingsAndroidTvLogUploadStatusMessage(),
): String =
    listOf(
        entryPointMessage,
        settingsLogUploadPendingStatus(pendingCount),
        settingsLogUploadUploadStateStatus(isUploading),
        settingsLogUploadTokenConfiguredStatus(tokenConfigured),
        settingsLogUploadLastUploadStatus(lastUploadAt),
        settingsLogUploadResultStatus(lastUploadStatus),
    ).joinToString(" · ")

fun settingsDesktopLogUploadStatusMessage(
    pendingCount: Int,
    isUploading: Boolean,
    tokenConfigured: Boolean,
    lastUploadAt: Long,
    lastUploadStatus: String?,
): String =
    settingsLogUploadStatusMessage(
        pendingCount = pendingCount,
        isUploading = isUploading,
        tokenConfigured = tokenConfigured,
        lastUploadAt = lastUploadAt,
        lastUploadStatus = lastUploadStatus,
        entryPointMessage = settingsDesktopLogUploadStatusMessage(),
    )

fun settingsAndroidTvLogUploadStatusMessage(
    pendingCount: Int,
    isUploading: Boolean,
    tokenConfigured: Boolean,
    lastUploadAt: Long,
    lastUploadStatus: String?,
): String =
    settingsLogUploadStatusMessage(
        pendingCount = pendingCount,
        isUploading = isUploading,
        tokenConfigured = tokenConfigured,
        lastUploadAt = lastUploadAt,
        lastUploadStatus = lastUploadStatus,
    )

fun settingsDesktopWebUiStatusMessage(
    enabled: Boolean = false,
    addressCount: Int = 0,
): String =
    when {
        !enabled -> "WebUI 当前未启用；Windows 已复用同一套访问令牌和地址生成规则。"
        addressCount > 0 -> "WebUI 已启用，Windows 正在监听局域网访问地址；可管理媒体源并遥控播放。"
        else -> "WebUI 已启用，Windows 正在监听；暂未检测到可展示的局域网地址。"
    }

fun settingsWebUiPanelTitleLabel(): String =
    "WebUI 访问"

fun settingsWebUiPanelDescription(): String =
    "默认关闭。开启后，同一局域网设备需要携带访问令牌才能管理媒体源和遥控播放。"

fun settingsWebUiToggleActionLabel(enabled: Boolean): String =
    if (enabled) "关闭 WebUI" else "开启 WebUI"

fun settingsWebUiRotateTokenActionLabel(): String =
    "更换令牌"

fun settingsWebUiRefreshAddressActionLabel(): String =
    "刷新地址"

fun settingsWebUiAccessTokenMissingValue(): String =
    "未生成"

fun settingsWebUiAccessTokenLabel(accessToken: String): String =
    "访问令牌：${accessToken.ifBlank { settingsWebUiAccessTokenMissingValue() }}"

fun settingsWebUiDisabledStatus(): String =
    "WebUI 当前未启用，不会监听局域网端口。"

fun settingsWebUiNoLanAddressStatus(): String =
    "暂未检测到局域网地址，请确认电视已连接网络后刷新。"

fun settingsWebUiAvailableAddressesLabel(): String =
    "可用地址"

fun settingsWebUiAddressLabel(index: Int): String =
    if (index <= 0) "主地址" else "备用地址"

fun settingsWebUiQrOpenLabel(): String =
    "扫码打开"

fun settingsWebUiTileLabel(): String =
    "WebUI"

fun settingsWebUiAndroidTvValue(): String =
    "Android TV"

fun settingsWebUiDesktopValue(): String =
    "Windows"

fun settingsActiveSourceSharedDetail(): String =
    "媒体库、远程浏览器和 Cloud/RSS 共用这个活动源。"

fun settingsPosterWallIndexDetail(): String =
    "扫描后优先回到媒体库海报墙。"

fun settingsPlaybackPageDetail(): String =
    "mpv、RIFE、字幕和起播时间在播放页调整。"

fun settingsRecentPlaybackDetail(): String =
    "mpv 进度同步后会刷新这里。"

fun settingsSelectedMediaDetail(): String =
    "从海报墙或详情页选择后可直接播放。"

fun settingsPlaybackStatusMessage(): String =
    "mpv 播放设置保留在播放页，RIFE/字幕/起播秒数仍可直接调整。"

fun settingsMetadataStatusMessage(): String =
    "Bangumi 搜索、批量预览、应用和撤销保留在详情页。"

fun metadataBangumiTokenFieldLabel(): String =
    "Bangumi Access Token"

fun metadataBangumiTokenOptionalHint(): String =
    "Bangumi Token 是可选项，用于 Bangumi 收藏与观看进度同步；元数据搜索不需要 Token。"

fun metadataBangumiTokenSavedStatus(): String =
    "Token 已保存在加密存储中。"

fun metadataBangumiTokenMissingStatus(): String =
    "当前未设置 Token。"

fun metadataBangumiTokenTileLabel(): String =
    "Bangumi Token"

fun metadataBangumiTokenTileDetail(): String =
    "仅用于 Bangumi 收藏与观看进度同步。"

fun metadataBangumiTokenSavedMessage(): String =
    "Bangumi Token 已保存。"

fun metadataBangumiTokenEmptyMessage(): String =
    "Bangumi Access Token 为空，未保存。"

fun metadataBangumiTokenClearedMessage(): String =
    "Bangumi Access Token 已清除。"

data class BangumiTokenSaveResult(
    val token: String?,
    val configured: Boolean,
    val status: String,
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

fun metadataBangumiTokenSettingsStatus(configured: Boolean): String =
    if (configured) {
        "${settingsMetadataStatusMessage()} ${metadataBangumiTokenSavedMessage()}"
    } else {
        "${settingsMetadataStatusMessage()} 保存 Token 后可同步观看进度。"
    }

fun settingsIndexSharedDetail(): String =
    "本地、WebDAV、SMB 都写入同一桌面索引。"

fun settingsCloudDriveRescanSourceDetail(): String =
    "CloudDrive 完成后可触发这个源的重扫。"

fun settingsRecentScanStatusDetail(): String =
    "扫描入口也保留在媒体库顶部。"

fun settingsScanPanelTitleLabel(): String =
    "媒体库扫描"

fun settingsScanPanelDescription(): String =
    "首页的扫描按钮会立即执行；定时扫描只会在到达间隔后回到首页时触发。"

fun settingsAutoScanToggleLabel(enabled: Boolean): String =
    if (enabled) "定时已开" else "定时关闭"

fun settingsScanIntervalOptionLabel(hours: Int): String =
    "${hours.coerceAtLeast(0)}小时"

fun settingsCurrentScanIntervalStatus(
    intervalHours: Int,
    lastScanText: String,
): String =
    "当前间隔 ${intervalHours.coerceAtLeast(0)} 小时 · $lastScanText"

fun settingsLastScanLabel(lastScanAt: Long): String =
    formatShortLocalTimestamp(lastScanAt)?.let { "上次扫描 $it" } ?: "还没有扫描记录"

fun settingsCurrentScanIntervalStatus(
    intervalHours: Int,
    lastScanAt: Long,
): String =
    settingsCurrentScanIntervalStatus(intervalHours, settingsLastScanLabel(lastScanAt))

fun settingsLibraryDisplayTitleLabel(): String =
    "媒体库显示"

fun settingsPosterWallArrangementTitleLabel(): String =
    "海报墙排列"

fun settingsMergeSameAnimeToggleLabel(enabled: Boolean): String =
    if (enabled) "同番合并" else "目录分开"

fun settingsMergeSameAnimeStatus(enabled: Boolean): String =
    if (enabled) {
        "首页和详情会按 Bangumi ID 或标题合并同一番。"
    } else {
        "首页按扫描出的目录条目分别显示。"
    }

fun settingsSelectedMetadataEntryDetail(): String =
    "详情页会显示可应用的 Bangumi 匹配。"

fun settingsMetadataMatchStatusDetail(): String =
    "支持单条应用、批量预览、应用和撤销。"

fun settingsMetadataCandidateScopeDetail(): String =
    "批量匹配会跳过已有冲突元数据。"

fun settingsSourceTileLabel(): String =
    "媒体源"

fun settingsActiveSourceTileLabel(): String =
    "当前源"

fun settingsPosterWallIndexTileLabel(): String =
    "海报墙索引"

fun settingsPlaybackModeTileLabel(): String =
    "播放模式"

fun settingsRecentPlaybackTileLabel(): String =
    "继续观看"

fun settingsSelectedMediaTileLabel(): String =
    "当前媒体"

fun settingsIndexTileLabel(): String =
    "索引"

fun settingsPostSyncSourceTileLabel(): String =
    "同步后扫描源"

fun settingsRecentScanStatusTileLabel(): String =
    "最近扫描状态"

fun settingsSelectedMetadataEntryTileLabel(): String =
    "选中条目"

fun settingsMetadataMatchStatusTileLabel(): String =
    "匹配状态"

fun settingsMetadataCandidateScopeTileLabel(): String =
    "候选范围"

fun settingsWebUiNativeControlTileLabel(): String =
    "桌面控制"

fun settingsRemoteAutomationTileLabel(): String =
    "远程自动化"

fun settingsCountValue(count: Int): String =
    "${count.coerceAtLeast(0)} 个"

fun settingsRecordCountValue(count: Int): String =
    "${count.coerceAtLeast(0)} 条"

fun settingsIndexedCountValue(count: Int): String =
    "${count.coerceAtLeast(0)} 条索引"

fun settingsSavedStateValue(saved: Boolean): String =
    if (saved) "已保存" else "未保存"

fun settingsNoSourceSelectedValue(): String =
    "未选择"

fun settingsMissingSourceValue(sourceId: Long): String =
    "缺失媒体源 #$sourceId"

fun settingsSourceTypeBreakdown(sources: List<MediaSourceInfo>): String {
    if (sources.isEmpty()) return "尚未添加本地、WebDAV 或 SMB 源。"
    return MediaSourceType.entries
        .mapNotNull { type ->
            val count = sources.count { it.type == type }
            if (count == 0) null else "${type.tvLabel()} $count"
        }
        .joinToString(" · ")
}

fun settingsActiveSourceLabel(source: MediaSourceInfo?): String =
    source?.sourcePickerTitle() ?: settingsNoSourceSelectedValue()

fun settingsLinkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String {
    if (sourceId == null) return settingsNoSourceSelectedValue()
    return sources.firstOrNull { it.id == sourceId }?.sourcePickerTitle()
        ?: settingsMissingSourceValue(sourceId)
}

data class SettingsSummaryTile(
    val label: String,
    val value: String,
    val detail: String,
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
            value = activeSourceLabel,
            detail = settingsActiveSourceSharedDetail(),
        ),
        SettingsSummaryTile(
            label = settingsPosterWallIndexTileLabel(),
            value = settingsRecordCountValue(indexedItemCount),
            detail = settingsPosterWallIndexDetail(),
        ),
    )

fun playbackSettingsTiles(
    playbackSummary: String,
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
            value = selectedMediaTitle,
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
            value = linkedSourceLabel,
            detail = settingsCloudDriveRescanSourceDetail(),
        ),
        SettingsSummaryTile(
            label = settingsRecentScanStatusTileLabel(),
            value = mediaSourceStatusText(libraryStatus),
            detail = settingsRecentScanStatusDetail(),
        ),
    )

fun metadataSettingsTiles(
    selectedMediaTitle: String,
    metadataSummary: String,
    indexedItemCount: Int,
    bangumiTokenConfigured: Boolean = false,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsSelectedMetadataEntryTileLabel(),
            value = selectedMediaTitle,
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

fun webUiSettingsTiles(
    platformValue: String,
    nativeControlValue: String = settingsDesktopControlTileValue(),
    nativeControlDetail: String = settingsDesktopControlTileDetail(),
    remoteAutomationValue: String = settingsRemoteAutomationTileValue(),
    remoteAutomationDetail: String = settingsRemoteAutomationTileDetail(),
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsWebUiTileLabel(),
            value = platformValue,
            detail = settingsWebUiTileDetail(),
        ),
        SettingsSummaryTile(
            label = settingsWebUiNativeControlTileLabel(),
            value = nativeControlValue,
            detail = nativeControlDetail,
        ),
        SettingsSummaryTile(
            label = settingsRemoteAutomationTileLabel(),
            value = remoteAutomationValue,
            detail = remoteAutomationDetail,
        ),
    )

fun logUploadSettingsTiles(): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = "OpenObserve",
            value = "JSON",
            detail = "API 地址、Stream 和访问令牌可在设置页或 Web 控制端保存。",
        ),
        SettingsSummaryTile(
            label = "本地日志",
            value = "自动上报",
            detail = "应用日志进入本地队列后按配置批量发送。",
        ),
        SettingsSummaryTile(
            label = "凭据",
            value = "安全存储",
            detail = "令牌只保存配置状态，清理后会停止上报。",
        ),
    )

fun desktopAppUpdateSettingsTiles(
    versionName: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsAboutVersionNameLabel(),
            value = settingsVersionNameValue(versionName),
            detail = settingsDesktopAppUpdatePanelDescription(),
        ),
        SettingsSummaryTile(
            label = "Release",
            value = settingsAppUpdateMenuSummary(),
            detail = "Windows 版发行包随 nightly 和正式 Release 一起发布。",
        ),
        SettingsSummaryTile(
            label = "安装包",
            value = "ZIP / EXE",
            detail = "桌面版使用 Windows 包，不走 Android APK 安装流程。",
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
            value = appName.ifBlank { "MiruPlay" },
            detail = settingsAboutPanelDescription(),
        ),
        SettingsSummaryTile(
            label = settingsAboutVersionNameLabel(),
            value = settingsVersionNameValue(versionName),
            detail = "Release 与 nightly 构建都会显示这里的安装版本。",
        ),
        SettingsSummaryTile(
            label = settingsAboutVersionCodeLabel(),
            value = settingsVersionCodeValue(versionCode),
            detail = settingsAboutPackageNameLabel() + "：" + packageName.ifBlank { settingsAboutUnknownValue() },
        ),
    )

fun settingsWebUiTileDetail(): String =
    "二维码和局域网令牌入口由各平台设置页提供。"

fun settingsDesktopControlTileValue(): String =
    "原生窗口"

fun settingsDesktopControlTileDetail(): String =
    "Windows 版保留键盘/遥控式导航和本机播放控制。"

fun settingsRemoteAutomationTileValue(): String =
    "Cloud/RSS"

fun settingsRemoteAutomationTileDetail(): String =
    "CloudDrive2 与 RSS 同步在云盘设置页管理。"

fun settingsCloudRssOverviewValue(enabled: Boolean): String =
    if (enabled) "已启用" else "未启用"

fun settingsCloudRssSubscriptionsValue(subscriptionCount: Int): String =
    settingsCountValue(subscriptionCount)

fun settingsCloudRssLinkedSourceValue(linkedSourceLabel: String): String =
    linkedSourceLabel

fun settingsCloudRssSchedulerIdleStatus(): String =
    "调度器待命，尚未检查。"

fun settingsCloudRssStatusFallback(): String =
    "调度器待命，尚未检查。"

fun settingsCloudRssOverviewDetail(value: String): String =
    value
