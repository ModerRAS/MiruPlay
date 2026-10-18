package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.joinCommaUiText
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.core.common.uiText

const val CLOUD_DRIVE_DIRECTORY_PAGE_SIZE = 6
const val CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE = 6

data class CloudDriveRssSchedulerUiState(
    val running: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastSummary: CloudDriveRssRunSummary? = null,
    val lastError: String? = null,
)

fun cloudDriveRssTitleLabel(): UiText =
    rawUiText("CloudDrive2")

fun cloudDriveRssDescriptionLabel(): UiText =
    uiText(
        "clouddrive_rss_desc",
        "RSS 会提交到 CloudDrive2 离线下载目录，整理后触发所选 WebDAV 媒体源扫描。",
    )

fun cloudDriveRssScheduledChipLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("clouddrive_rss_scheduled_on", "定时已开")
    } else {
        uiText("clouddrive_rss_scheduled_off", "定时关闭")
    }

fun cloudDriveRssCredentialsBadgeLabel(tokenConfigured: Boolean, passwordConfigured: Boolean = false): UiText =
    when {
        tokenConfigured && passwordConfigured -> uiText(
            "clouddrive_rss_cred_both",
            "Key 与密码已保存",
        )
        tokenConfigured -> uiText("clouddrive_rss_cred_key", "Key 已保存")
        passwordConfigured -> uiText("clouddrive_rss_cred_password", "密码已保存")
        else -> uiText("clouddrive_rss_cred_none", "未授权")
    }

fun cloudDriveRssPasswordCredentialLabel(configured: Boolean): UiText =
    if (configured) {
        uiText("clouddrive_rss_password_saved", "用户名密码已保存")
    } else {
        uiText("clouddrive_rss_password_missing", "用户名密码未保存")
    }

fun cloudDriveRssApiTokenCredentialLabel(configured: Boolean): UiText =
    if (configured) {
        uiText("clouddrive_rss_token_saved", "Key 已保存")
    } else {
        uiText("clouddrive_rss_token_missing", "Key 未保存")
    }

fun cloudDriveRssEndpointFieldLabel(): UiText =
    uiText("clouddrive_rss_endpoint_field", "CloudDrive2 地址")

fun cloudDriveRssUsernameFieldLabel(): UiText =
    uiText("clouddrive_rss_username_field", "CloudDrive2 用户名")

fun cloudDriveRssPasswordFieldLabel(): UiText =
    uiText("clouddrive_rss_password_field", "密码（登录后保存）")

fun cloudDriveRssApiTokenFieldLabel(): UiText =
    uiText("clouddrive_rss_token_field", "API Token / Key（可替代密码登录）")

fun cloudDriveRssInboxPathFieldLabel(): UiText =
    uiText("clouddrive_rss_inbox_field", "下载目录 A")

fun cloudDriveRssLibraryPathFieldLabel(): UiText =
    uiText("clouddrive_rss_library_field", "整理目录 B")

fun cloudDriveRssLibraryModeFieldLabel(): UiText =
    uiText("clouddrive_rss_mode_field", "入库模式")

fun cloudDriveRssLibraryModeOrganizedLabel(): UiText =
    uiText("clouddrive_rss_mode_organized", "整理入库")

fun cloudDriveRssLibraryModeSingleDirectoryLabel(): UiText =
    uiText("clouddrive_rss_mode_single_dir", "单目录入库")

fun cloudDriveRssIntervalMinutesFieldLabel(): UiText =
    uiText("clouddrive_rss_interval_field", "定时间隔（分钟）")

fun cloudDriveRssProxyToggleLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("clouddrive_rss_proxy_on", "RSS 代理已开")
    } else {
        uiText("clouddrive_rss_proxy_off", "RSS 代理关闭")
    }

fun cloudDriveRssProxySettingLabel(): UiText =
    uiText("clouddrive_rss_proxy_title", "RSS 代理")

fun cloudDriveRssEnabledToggleLabel(): UiText =
    uiText("clouddrive_rss_enabled_toggle", "启用")

fun cloudDriveRssProxyHostFieldLabel(): UiText =
    uiText("clouddrive_rss_proxy_host", "代理地址")

fun cloudDriveRssProxyPortFieldLabel(): UiText =
    uiText("clouddrive_rss_proxy_port", "代理端口")

fun cloudDriveRssSaveConfigActionLabel(): UiText =
    uiText("clouddrive_rss_save_config", "保存")

fun cloudDriveRssSaveCredentialsActionLabel(): UiText =
    uiText("clouddrive_rss_save_credentials", "保存凭据")

fun cloudDriveRssClearCredentialsActionLabel(): UiText =
    uiText("clouddrive_rss_clear_credentials", "清空凭据")

fun cloudDriveRssLoginActionLabel(busy: Boolean = false): UiText =
    if (busy) {
        uiText("clouddrive_rss_login_busy", "处理中")
    } else {
        uiText("clouddrive_rss_login", "登录并保存密码")
    }

fun cloudDriveRssSaveApiTokenActionLabel(): UiText =
    uiText("clouddrive_rss_save_token", "验证并保存 Key")

fun cloudDriveRssVerifyApiTokenActionLabel(): UiText =
    uiText("clouddrive_rss_verify_token", "验证令牌")

fun cloudDriveRssRunNowActionLabel(busy: Boolean = false): UiText =
    if (busy) {
        uiText("clouddrive_rss_run_busy", "执行中")
    } else {
        uiText("clouddrive_rss_run_now", "立即执行")
    }

fun cloudDriveRssSaveAndRunNowActionLabel(busy: Boolean = false): UiText =
    if (busy) {
        uiText("clouddrive_rss_save_run_busy", "执行中")
    } else {
        uiText("clouddrive_rss_save_and_run", "保存并立即执行")
    }

fun cloudDriveRssTokenStatusMessage(tokenConfigured: Boolean, passwordConfigured: Boolean = false): UiText =
    when {
        tokenConfigured && passwordConfigured -> uiText(
            "clouddrive_rss_token_status_both",
            "CloudDrive2 Key 和用户名密码已保存在加密存储中。",
        )
        tokenConfigured -> uiText(
            "clouddrive_rss_token_status_key",
            "CloudDrive2 Key 已保存在加密存储中。",
        )
        passwordConfigured -> uiText(
            "clouddrive_rss_token_status_password",
            "CloudDrive2 密码已保存，执行时会自动登录刷新 Key。",
        )
        else -> uiText(
            "clouddrive_rss_token_status_none",
            "请先登录并保存密码，或验证并保存 Key。",
        )
    }

fun cloudDriveRssSyncPathTitleLabel(): UiText =
    uiText("clouddrive_rss_sync_path_title", "同步路径")

fun cloudDriveRssRuntimeTitleLabel(): UiText =
    uiText("clouddrive_rss_runtime_title", "运行状态")

fun cloudDriveRssDirectoryBadgeLabel(): UiText =
    uiText("clouddrive_rss_directory_badge", "目录")

fun cloudDriveRssPathBadgeLabel(): UiText =
    uiText("clouddrive_rss_path_badge", "路径")

fun cloudDriveRssRunBadgeLabel(): UiText =
    uiText("clouddrive_rss_run_badge", "运行")

fun cloudDriveRssEnabledBadgeLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("clouddrive_rss_badge_enabled", "启用")
    } else {
        uiText("clouddrive_rss_badge_disabled", "停用")
    }

fun cloudDriveRssEndpointFallbackLabel(): UiText =
    uiText("clouddrive_rss_endpoint_fallback", "填写 CloudDrive2 地址")

fun cloudDriveRssUnconfiguredEndpointLabel(): UiText =
    uiText("clouddrive_rss_endpoint_unconfigured", "未配置端点")

fun cloudDriveRssSchedulerIdleLabel(): UiText =
    uiText("clouddrive_rss_scheduler_idle", "调度器待命")

private const val CLOUD_RSS_OVERVIEW_PREVIEW_LIMIT = 58
private const val CLOUD_RSS_WIDE_PREVIEW_LIMIT = 86

data class CloudDriveRssUiLabels(
    val endpoint: UiText,
    val username: UiText,
    val apiToken: UiText,
    val password: UiText,
    val saveCredentials: UiText,
    val clearCredentials: UiText,
    val login: UiText,
    val verify: UiText,
    val inboxPath: UiText,
    val libraryPath: UiText,
    val intervalMinutes: UiText,
    val proxyHost: UiText,
    val proxyPort: UiText,
    val enabledToggle: UiText,
    val rssProxy: UiText,
    val useActiveSource: UiText,
    val clearSource: UiText,
    val postSyncSource: UiText,
    val saveSyncConfig: UiText,
    val runSyncNow: UiText,
    val rssSubscriptions: UiText,
    val subscriptionName: UiText,
    val subscriptionUrl: UiText,
    val filterRegex: UiText,
    val saveRss: UiText,
    val deleteRss: UiText,
    val rssEmpty: UiText,
    val rssPreviewFallback: UiText,
    val startScheduler: UiText,
    val stopScheduler: UiText,
    val endpointFallback: UiText,
    val schedulerIdle: UiText,
    val enabledBadge: UiText,
    val disabledBadge: UiText,
    val pathBadge: UiText,
    val runBadge: UiText,
)

fun cloudDriveRssUiLabels(): CloudDriveRssUiLabels =
    CloudDriveRssUiLabels(
        endpoint = cloudDriveRssEndpointFieldLabel(),
        username = cloudDriveRssUsernameFieldLabel(),
        apiToken = cloudDriveRssApiTokenFieldLabel(),
        password = cloudDriveRssPasswordFieldLabel(),
        saveCredentials = cloudDriveRssSaveCredentialsActionLabel(),
        clearCredentials = cloudDriveRssClearCredentialsActionLabel(),
        login = cloudDriveRssLoginActionLabel(),
        verify = cloudDriveRssVerifyApiTokenActionLabel(),
        inboxPath = cloudDriveRssInboxPathFieldLabel(),
        libraryPath = cloudDriveRssLibraryPathFieldLabel(),
        intervalMinutes = cloudDriveRssIntervalMinutesFieldLabel(),
        proxyHost = cloudDriveRssProxyHostFieldLabel(),
        proxyPort = cloudDriveRssProxyPortFieldLabel(),
        enabledToggle = cloudDriveRssEnabledToggleLabel(),
        rssProxy = cloudDriveRssProxySettingLabel(),
        useActiveSource = cloudDriveRssUseActiveSourceActionLabel(),
        clearSource = cloudDriveRssClearScanSourceActionLabel(),
        postSyncSource = cloudDriveRssPostSyncSourceLabel(),
        saveSyncConfig = cloudDriveRssSaveConfigActionLabel(),
        runSyncNow = cloudDriveRssSaveAndRunNowActionLabel(),
        rssSubscriptions = rssSubscriptionsTitleLabel(),
        subscriptionName = rssSubscriptionNameFieldLabel(),
        subscriptionUrl = rssSubscriptionUrlFieldLabel(),
        filterRegex = rssSubscriptionFilterRegexFieldLabel(),
        saveRss = rssSubscriptionSaveActionLabel(),
        deleteRss = rssSubscriptionDeleteActionLabel(),
        rssEmpty = rssSubscriptionEmptyMessage(),
        rssPreviewFallback = rssSubscriptionFormPreviewFallbackLabel(),
        startScheduler = cloudDriveRssStartSchedulerActionLabel(),
        stopScheduler = cloudDriveRssStopSchedulerActionLabel(),
        endpointFallback = cloudDriveRssEndpointFallbackLabel(),
        schedulerIdle = cloudDriveRssSchedulerIdleLabel(),
        enabledBadge = cloudDriveRssEnabledBadgeLabel(true),
        disabledBadge = cloudDriveRssEnabledBadgeLabel(false),
        pathBadge = cloudDriveRssPathBadgeLabel(),
        runBadge = cloudDriveRssRunBadgeLabel(),
    )

fun cloudRssOverviewTiles(
    endpointUrl: String,
    subscriptions: List<RssSubscriptionInfo>,
    enabled: Boolean,
    linkedSourceLabel: String,
    schedulerStatus: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = cloudDriveRssTitleLabel(),
            value = settingsCloudRssOverviewValue(enabled),
            detail = rawUiText(
                cloudRssPreview(
                    endpointUrl,
                    fallback = cloudDriveRssUnconfiguredEndpointLabel().fallback,
                    maxLength = CLOUD_RSS_OVERVIEW_PREVIEW_LIMIT,
                ),
            ),
        ),
        SettingsSummaryTile(
            label = rssSubscriptionsTitleLabel(),
            value = settingsCloudRssSubscriptionsValue(subscriptions.size),
            detail = rawUiText(
                subscriptions.firstOrNull()?.let { rssSubscriptionPreview(it, CLOUD_RSS_OVERVIEW_PREVIEW_LIMIT) }
                    ?: rssSubscriptionPreviewFallbackLabel().fallback,
            ),
        ),
        SettingsSummaryTile(
            label = cloudDriveRssPostSyncScanSummaryLabel(),
            value = settingsCloudRssLinkedSourceValue(linkedSourceLabel),
            detail = rawUiText(
                cloudRssPreview(
                    cloudRssStatusText(schedulerStatus),
                    fallback = cloudDriveRssSchedulerIdleLabel().fallback,
                    maxLength = CLOUD_RSS_OVERVIEW_PREVIEW_LIMIT,
                ),
            ),
        ),
    )

fun cloudRssPreview(
    value: String,
    fallback: String,
    maxLength: Int = CLOUD_RSS_WIDE_PREVIEW_LIMIT,
): String =
    value.trim()
        .ifBlank { fallback }
        .compactMiddleText(maxLength)

fun cloudRssPathPairPreview(
    inboxPath: String,
    libraryPath: String,
    maxLength: Int = CLOUD_RSS_WIDE_PREVIEW_LIMIT,
): String {
    val separator = cloudDriveRssPathPairSeparator().fallback
    val safeMaxLength = maxLength.coerceAtLeast(separator.length + 8)
    val available = safeMaxLength - separator.length
    val inboxLength = available / 2
    val libraryLength = available - inboxLength
    return cloudRssPreview(
        inboxPath,
        fallback = cloudDriveRssInboxPathFieldLabel().fallback,
        maxLength = inboxLength,
    ) +
        separator +
        cloudRssPreview(
            libraryPath,
            fallback = cloudDriveRssLibraryPathFieldLabel().fallback,
            maxLength = libraryLength,
        )
}

fun rssSubscriptionPreview(
    subscription: RssSubscriptionInfo,
    maxLength: Int = CLOUD_RSS_WIDE_PREVIEW_LIMIT,
): String {
    val state = rssSubscriptionStateLabel(subscription.enabled).fallback
    val filter = subscription.filterRegex?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    val label = subscription.name.ifBlank { rssSubscriptionFallbackTitleLabel().fallback }
    return "$state · $label · ${subscription.url}$filter".compactMiddleText(maxLength)
}

fun cloudDriveRssCloudDriveEnabledValue(enabled: Boolean): UiText =
    if (enabled) {
        uiText("clouddrive_rss_cloud_enabled", "已启用")
    } else {
        uiText("clouddrive_rss_cloud_disabled", "未启用")
    }

fun cloudDriveRssPostSyncScanSummaryLabel(): UiText =
    uiText("clouddrive_rss_post_sync_scan", "同步后扫描")

fun cloudDriveRssChooseDirectoryActionLabel(): UiText =
    uiText("clouddrive_rss_choose_dir", "选择目录")

fun cloudDriveRssUseCurrentDirectoryActionLabel(): UiText =
    directoryBrowserUseCurrentActionLabel(isLocal = false)

fun cloudDriveRssParentDirectoryActionLabel(): UiText =
    directoryBrowserParentActionLabel(isLocal = false)

fun cloudDriveRssCloseActionLabel(): UiText =
    directoryBrowserCloseActionLabel()

fun cloudDriveRssDirectoryPageUnitLabel(): UiText =
    uiText("clouddrive_rss_dir_page_unit", "个目录")

fun cloudDriveDirectoryPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_DRIVE_DIRECTORY_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

fun cloudDriveDirectoryCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_DRIVE_DIRECTORY_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

fun cloudDriveDirectoryPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): UiText? =
    pagedListPageSummary(
        pageStart = cloudDriveDirectoryCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = CLOUD_DRIVE_DIRECTORY_PAGE_SIZE,
        unitLabel = cloudDriveRssDirectoryPageUnitLabel(),
    )

fun cloudDriveRssLoadingDirectoriesMessage(): UiText =
    directoryBrowserLoadingMessage(isLocal = false)

fun cloudDriveRssEmptyDirectoryMessage(): UiText =
    directoryBrowserEmptyMessage(isLocal = false)

fun cloudDriveRssInboxDirectoryPickerTitle(): UiText =
    uiText("clouddrive_rss_inbox_picker_title", "选择下载目录 A")

fun cloudDriveRssLibraryDirectoryPickerTitle(): UiText =
    uiText("clouddrive_rss_library_picker_title", "选择整理目录 B")

fun cloudDriveRssInboxDirectorySelectedLabel(): UiText =
    uiText("clouddrive_rss_inbox_selected", "下载目录 A")

fun cloudDriveRssLibraryDirectorySelectedLabel(): UiText =
    uiText("clouddrive_rss_library_selected", "整理目录 B")

fun cloudDriveRssDirectorySelectedStatus(
    selectedLabel: UiText,
    path: String,
): UiText =
    UiText(
        "clouddrive_rss_dir_selected_status",
        listOf(selectedLabel, path),
        "已选择${selectedLabel.fallback}：$path",
    )

fun cloudDriveRssDirectoryBrowsingStatus(path: String): UiText =
    UiText("clouddrive_rss_dir_browsing", listOf(path), "正在浏览 CloudDrive2 目录：$path")

fun cloudDriveRssPathPairSeparator(): UiText =
    uiText("clouddrive_rss_path_separator", " 到 ")

fun cloudDriveRssScanSourceTitleLabel(): UiText =
    uiText("clouddrive_rss_scan_source_title", "入库后扫描的 WebDAV 媒体源")

fun cloudDriveRssNoWebDavSourceMessage(): UiText =
    uiText(
        "clouddrive_rss_no_webdav",
        "还没有 WebDAV 媒体源，请先在媒体源里添加 CloudDrive WebDAV 地址。",
    )

fun cloudDriveRssNoScanSourceOptionLabel(): UiText =
    uiText("clouddrive_rss_no_scan_source", "暂不扫描")

fun cloudDriveRssUseActiveSourceActionLabel(): UiText =
    uiText("clouddrive_rss_use_active_source", "使用当前源")

fun cloudDriveRssClearScanSourceActionLabel(): UiText =
    uiText("clouddrive_rss_clear_scan_source", "清除扫描源")

fun cloudDriveRssPostSyncSourceLabel(): UiText =
    uiText("clouddrive_rss_post_sync_source", "同步后扫描源：")

fun cloudDriveRssStartSchedulerActionLabel(): UiText =
    uiText("clouddrive_rss_start_scheduler", "启动调度")

fun cloudDriveRssStopSchedulerActionLabel(): UiText =
    uiText("clouddrive_rss_stop_scheduler", "停止调度")

fun rssSubscriptionsTitleLabel(): UiText =
    uiText("clouddrive_rss_subscriptions_title", "RSS 订阅")

fun rssSubscriptionPageUnitLabel(): UiText =
    uiText("clouddrive_rss_subscription_page_unit", "个订阅")

fun cloudRssSubscriptionPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

fun cloudRssSubscriptionCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

fun cloudRssSubscriptionPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): UiText? =
    pagedListPageSummary(
        pageStart = cloudRssSubscriptionCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE,
        unitLabel = rssSubscriptionPageUnitLabel(),
    )

fun rssSubscriptionNameFieldLabel(): UiText =
    uiText("clouddrive_rss_subscription_name", "订阅名称")

fun rssSubscriptionUrlFieldLabel(): UiText =
    uiText("clouddrive_rss_subscription_url", "RSS 地址")

fun rssSubscriptionFilterRegexFieldLabel(): UiText =
    uiText("clouddrive_rss_filter_regex", "标题过滤正则（可选）")

fun rssSubscriptionNewEnabledLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("clouddrive_rss_new_enabled", "新增后启用")
    } else {
        uiText("clouddrive_rss_new_disabled", "新增后停用")
    }

fun rssSubscriptionAddActionLabel(): UiText =
    uiText("clouddrive_rss_add", "添加订阅")

fun rssSubscriptionSaveActionLabel(): UiText =
    uiText("clouddrive_rss_save", "保存 RSS")

fun rssSubscriptionDeleteActionLabel(): UiText =
    uiText("clouddrive_rss_delete", "删除订阅")

fun rssSubscriptionEmptyMessage(): UiText =
    uiText("clouddrive_rss_empty", "还没有 RSS 订阅。")

fun rssSubscriptionPreviewFallbackLabel(): UiText =
    uiText("clouddrive_rss_preview_fallback", "暂无订阅")

fun rssSubscriptionFormPreviewFallbackLabel(): UiText =
    uiText("clouddrive_rss_form_preview_fallback", "保存订阅后在这里显示")

fun rssSubscriptionStateLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("clouddrive_rss_state_enabled", "启用")
    } else {
        uiText("clouddrive_rss_state_disabled", "停用")
    }

fun rssSubscriptionStateActionLabel(enabled: Boolean): UiText =
    if (enabled) {
        uiText("clouddrive_rss_action_disable", "停用")
    } else {
        uiText("clouddrive_rss_action_enable", "启用")
    }

fun rssSubscriptionFallbackTitleLabel(): UiText =
    rawUiText("RSS")

fun rssSubscriptionLastCheckedLabel(timestampText: String?): UiText =
    timestampText?.takeIf { it.isNotBlank() }?.let {
        UiText("clouddrive_rss_last_checked", listOf(it), "上次检查 $it")
    } ?: uiText("clouddrive_rss_never_checked", "尚未检查")

fun rssSubscriptionLastCheckedLabel(lastCheckedAt: Long): UiText =
    rssSubscriptionLastCheckedLabel(formatShortLocalTimestamp(lastCheckedAt))

fun CloudDriveRssSchedulerUiState.tvStatus(): UiText {
    val prefix = if (running) {
        uiText("clouddrive_scheduler_running", "调度器运行中")
    } else {
        uiText("clouddrive_scheduler_idle_state", "调度器待命")
    }
    val error = lastError
    if (!error.isNullOrBlank()) {
        return UiText(
            "clouddrive_scheduler_error",
            listOf(prefix, error.trim()),
            "${prefix.fallback}，上次检查失败：${error.trim()}",
        )
    }
    val summary = lastSummary
    if (summary != null) {
        val submitted = summary.submitted
        val skipped = summary.skipped
        val failed = summary.failed
        val organized = summary.organized
        return UiText(
            "clouddrive_scheduler_summary",
            listOf(prefix, submitted, skipped, failed, organized),
            "${prefix.fallback}，上次运行：提交 $submitted 个，跳过 $skipped 个，失败 $failed 个，整理 $organized 个。",
        )
    }
    return if (lastCheckedAt > 0L) {
        UiText(
            "clouddrive_scheduler_idle_checked",
            listOf(prefix),
            "${prefix.fallback}，上次检查没有待同步内容。",
        )
    } else {
        UiText(
            "clouddrive_scheduler_idle_never",
            listOf(prefix),
            "${prefix.fallback}，尚未检查。",
        )
    }
}

fun cloudRssSchedulerStatus(state: CloudDriveRssSchedulerUiState): UiText =
    state.tvStatus()

fun cloudRssConfigSavedStatus(): UiText =
    uiText("clouddrive_rss_config_saved", "Cloud/RSS 自动化设置已保存。")

fun cloudRssInitialStatus(): UiText =
    uiText("clouddrive_rss_initial", "加载或保存 Cloud/RSS 自动化设置。")

fun cloudDriveCredentialsSavedStatus(): UiText =
    uiText("clouddrive_credentials_saved", "CloudDrive 凭据已保存。")

fun cloudDriveCredentialsClearedStatus(): UiText =
    uiText("clouddrive_credentials_cleared", "CloudDrive 凭据已清空。")

fun cloudDriveLoginRequiredStatus(): UiText =
    uiText("clouddrive_login_required", "请先填写 CloudDrive2 地址、用户名和密码。")

fun cloudDriveLoginStartedStatus(): UiText =
    uiText("clouddrive_login_started", "正在登录 CloudDrive2...")

fun cloudDriveLoginSucceededStatus(): UiText =
    uiText("clouddrive_login_succeeded", "CloudDrive2 登录成功，令牌已保存。")

fun cloudDriveEndpointRequiredStatus(): UiText =
    uiText("clouddrive_endpoint_required", "请先填写 CloudDrive2 地址。")

fun cloudDriveEndpointInvalidStatus(): UiText =
    uiText(
        "clouddrive_endpoint_invalid",
        "CloudDrive2 地址格式不正确，请填写服务端地址（例如 http://<host>:19798），不要粘贴带 token 或查询参数的页面链接。",
    )

fun cloudDriveApiTokenRequiredStatus(): UiText =
    uiText("clouddrive_token_required", "请填写 CloudDrive2 API Token 或 Key。")

fun cloudDriveTokenRequiredStatus(): UiText =
    uiText("clouddrive_token_endpoint_required", "请先填写 CloudDrive2 地址和 API 令牌。")

fun cloudDriveTokenLoginRequiredStatus(): UiText =
    uiText("clouddrive_token_login_required", "请先登录并保存密码，或验证并保存 Key。")

fun cloudDriveTokenValidationStartedStatus(): UiText =
    uiText("clouddrive_token_validating", "正在验证 CloudDrive2 API 令牌...")

fun cloudDriveTokenVerifiedStatus(
    friendlyName: String,
    rootDir: String,
): UiText {
    val label = friendlyName.takeIf { it.isNotBlank() }
        ?: rootDir.ifBlank { "CloudDrive2" }
    return UiText(
        "clouddrive_token_verified",
        listOf(label),
        "CloudDrive2 API 令牌已验证并保存：$label。",
    )
}

fun cloudRssRunStartedStatus(): UiText =
    uiText("clouddrive_rss_run_started", "正在执行 Cloud/RSS 同步...")

fun CloudDriveRssRunSummary.completeStatus(): UiText {
    val submitted = submitted.coerceAtLeast(0)
    val skipped = skipped.coerceAtLeast(0)
    val failed = failed.coerceAtLeast(0)
    val organized = organized.coerceAtLeast(0)
    val indexed = indexed.coerceAtLeast(0)
    val scraped = scraped.coerceAtLeast(0)
    val noMatch = noMatch.coerceAtLeast(0)
    val handled = if (organized > 0 || (indexed == 0 && scraped == 0 && noMatch == 0)) {
        UiText("clouddrive_handled_organized", listOf(organized), "整理 $organized 个")
    } else {
        UiText("clouddrive_handled_indexed", listOf(indexed), "入库 $indexed 个")
    }
    val extras = buildList {
        if (indexed > 0) add(UiText("clouddrive_extra_indexed", listOf(indexed), "索引 $indexed 个"))
        if (scraped > 0) add(UiText("clouddrive_extra_scraped", listOf(scraped), "刮削 $scraped 个"))
        if (noMatch > 0) add(UiText("clouddrive_extra_no_match", listOf(noMatch), "未匹配 $noMatch 个"))
    }
    val extrasText = if (extras.isEmpty()) {
        rawUiText("")
    } else {
        joinCommaUiText(extras)
    }
    return UiText(
        "clouddrive_sync_complete",
        listOf(submitted, skipped, failed, handled, extrasText),
        "同步完成：提交 $submitted 个，跳过 $skipped 个，失败 $failed 个，${handled.fallback}${extrasText.fallback}。",
    )
}

fun cloudDriveRssRunSummaryStatus(summary: CloudDriveRssRunSummary): UiText =
    summary.completeStatus()

fun cloudRssSchedulerDisabledStatus(): UiText =
    uiText("clouddrive_scheduler_disabled", "启动调度前请先启用并保存 Cloud/RSS 同步。")

fun cloudRssSchedulerStartStatus(started: Boolean): UiText =
    if (started) {
        uiText("clouddrive_scheduler_started", "Cloud/RSS 调度器已启动。")
    } else {
        uiText("clouddrive_scheduler_already_running", "Cloud/RSS 调度器已经在运行。")
    }

fun cloudRssSchedulerStoppedStatus(): UiText =
    uiText("clouddrive_scheduler_stopped", "Cloud/RSS 调度器已停止。")

fun cloudRssScheduledSyncCompleteStatus(): UiText =
    uiText("clouddrive_scheduled_sync_complete", "定时同步完成。")

fun cloudRssScanSourceRequiredStatus(): UiText =
    uiText("clouddrive_scan_source_required", "请先打开已保存的媒体源，再绑定 Cloud/RSS 扫描。")

fun cloudRssScanSourceMissingStatus(): UiText =
    uiText("clouddrive_scan_source_missing", "未找到已绑定的扫描源，请清除或重新绑定 Cloud/RSS 扫描源。")

fun cloudRssLinkedScanSourceStatus(sourceName: String): UiText =
    UiText(
        "clouddrive_scan_source_linked",
        listOf(sourceName),
        "已绑定同步后扫描源：$sourceName。请保存同步配置。",
    )

fun cloudRssRescanStartedStatus(
    reason: String,
    sourceName: String,
): UiText {
    val cleanReason = reason.removeSuffix("。")
    return UiText(
        "clouddrive_rescan_started",
        listOf(cleanReason, sourceName),
        "$cleanReason，正在重扫 $sourceName...",
    )
}

fun cloudRssScanSourceClearedStatus(): UiText =
    uiText("clouddrive_scan_source_cleared", "同步后扫描源已清除，请保存同步配置。")

fun rssUrlRequiredStatus(): UiText =
    uiText("clouddrive_rss_url_required", "请先填写 RSS 地址。")

fun rssSubscriptionsLoadedStatus(count: Int): UiText =
    if (count <= 0) {
        uiText("clouddrive_rss_loaded_empty", "尚未配置 RSS 订阅。")
    } else {
        val safe = count
        UiText("clouddrive_rss_loaded", listOf(safe), "已加载 $safe 个 RSS 订阅。")
    }

fun rssSubscriptionsShowingStatus(count: Int): UiText =
    if (count <= 0) {
        uiText("clouddrive_rss_showing_empty", "尚未配置 RSS 订阅。")
    } else {
        val safe = count
        UiText("clouddrive_rss_showing", listOf(safe), "正在显示 $safe 个 RSS 订阅。")
    }

fun rssSubscriptionsLoadFailedStatus(errorMessage: String?): UiText =
    errorMessage?.let { rawUiText(it) }
        ?: uiText("clouddrive_rss_load_failed", "RSS 订阅加载失败。")

fun rssSubscriptionsRefreshFailedStatus(errorMessage: String?): UiText =
    errorMessage?.let { rawUiText(it) }
        ?: uiText("clouddrive_rss_refresh_failed", "RSS 订阅刷新失败。")

fun RssSubscriptionInfo?.retainedSelectionInRssSubscriptions(
    subscriptions: List<RssSubscriptionInfo>,
): RssSubscriptionInfo? =
    this?.let { selected ->
        subscriptions.firstOrNull { it.id == selected.id }
    }

fun rssSubscriptionSavedStatus(name: String): UiText =
    UiText("clouddrive_rss_saved", listOf(name), "RSS 订阅已保存：$name")

fun rssSubscriptionSelectedStatus(name: String): UiText =
    UiText("clouddrive_rss_selected", listOf(name), "已选择 RSS 订阅：$name")

fun rssSubscriptionRequiredStatus(): UiText =
    uiText("clouddrive_rss_required", "请先选择一个 RSS 订阅。")

fun rssSubscriptionDeletedStatus(): UiText =
    uiText("clouddrive_rss_deleted", "RSS 订阅已删除。")

fun cloudRssIdleStatus(): UiText =
    uiText("clouddrive_rss_idle_status", "Cloud/RSS 待命。")

fun cloudRssStatusText(status: String): String =
    localizedCloudRssStatusText(status) ?: status.trim()

fun localizedCloudRssStatusText(status: String): String? {
    val trimmed = status.trim()
    return when {
        trimmed.isBlank() -> cloudRssIdleStatus().fallback
        trimmed.isTvCloudRssStatus() -> trimmed
        trimmed == "Scheduler idle. No checks yet." ->
            CloudDriveRssSchedulerUiState(running = false, lastCheckedAt = 0L).tvStatus().fallback
        trimmed == "Scheduler idle. Last check found no due sync." ->
            CloudDriveRssSchedulerUiState(running = false, lastCheckedAt = 1L).tvStatus().fallback
        trimmed == "Scheduler running. No checks yet." ->
            CloudDriveRssSchedulerUiState(running = true, lastCheckedAt = 0L).tvStatus().fallback
        trimmed == "Scheduler running. Last check found no due sync." ->
            CloudDriveRssSchedulerUiState(running = true, lastCheckedAt = 1L).tvStatus().fallback
        trimmed == "Cloud/RSS automation settings saved." -> cloudRssConfigSavedStatus().fallback
        trimmed == "Load or save Cloud/RSS automation settings." -> cloudRssInitialStatus().fallback
        trimmed == "CloudDrive credentials saved." -> cloudDriveCredentialsSavedStatus().fallback
        trimmed == "CloudDrive credentials cleared." -> cloudDriveCredentialsClearedStatus().fallback
        trimmed == "Enter CloudDrive2 endpoint, username, and password first." ->
            cloudDriveLoginRequiredStatus().fallback
        trimmed == "Logging into CloudDrive2..." -> cloudDriveLoginStartedStatus().fallback
        trimmed == "CloudDrive2 login succeeded; token saved." -> cloudDriveLoginSucceededStatus().fallback
        trimmed == "Enter CloudDrive2 endpoint and API token first." ->
            cloudDriveTokenRequiredStatus().fallback
        trimmed == "Validating CloudDrive2 API token..." -> cloudDriveTokenValidationStartedStatus().fallback
        trimmed == "Running Cloud/RSS sync..." -> cloudRssRunStartedStatus().fallback
        trimmed == "Enable and save Cloud/RSS sync before starting the scheduler." ->
            cloudRssSchedulerDisabledStatus().fallback
        trimmed == "Cloud/RSS scheduler started." -> cloudRssSchedulerStartStatus(started = true).fallback
        trimmed == "Cloud/RSS scheduler is already running." ->
            cloudRssSchedulerStartStatus(started = false).fallback
        trimmed == "Cloud/RSS scheduler stopped." -> cloudRssSchedulerStoppedStatus().fallback
        trimmed == "Scheduled sync complete." -> cloudRssScheduledSyncCompleteStatus().fallback
        trimmed == "Open a saved media source before linking Cloud/RSS scanning." ->
            cloudRssScanSourceRequiredStatus().fallback
        trimmed == "Linked scan source was not found. Clear or relink the Cloud/RSS scan source." ->
            cloudRssScanSourceMissingStatus().fallback
        trimmed == "Cloud/RSS post-sync scan source cleared. Save sync config to persist it." ->
            cloudRssScanSourceClearedStatus().fallback
        trimmed == "Enter an RSS URL first." -> rssUrlRequiredStatus().fallback
        trimmed == "No RSS subscriptions configured." -> rssSubscriptionsLoadedStatus(0).fallback
        trimmed == "Failed to load RSS subscriptions." -> rssSubscriptionsLoadFailedStatus(null).fallback
        trimmed == "Failed to refresh RSS subscriptions." -> rssSubscriptionsRefreshFailedStatus(null).fallback
        trimmed == "Select an RSS subscription first." -> rssSubscriptionRequiredStatus().fallback
        trimmed == "RSS subscription deleted." -> rssSubscriptionDeletedStatus().fallback
        else -> localizedDynamicCloudRssStatusText(trimmed)?.fallback
    }
}

private fun String.isTvCloudRssStatus(): Boolean =
    any { it in '\u4E00'..'\u9FFF' } && (endsWith("。") || endsWith("..."))

private fun localizedDynamicCloudRssStatusText(status: String): UiText? {
    schedulerErrorStatusRegex.matchEntire(status)?.let { match ->
        val state = schedulerStateLabel(match.groupValues[1])
        val error = match.groupValues[2]
        return UiText(
            "clouddrive_scheduler_error_legacy",
            listOf(state, error),
            "$state，上次检查失败：$error",
        )
    }
    schedulerSummaryStatusRegex.matchEntire(status)?.let { match ->
        return CloudDriveRssSchedulerUiState(
            running = match.groupValues[1] == "running",
            lastSummary = CloudDriveRssRunSummary(
                submitted = match.groupValues[2].toInt(),
                skipped = match.groupValues[3].toInt(),
                failed = match.groupValues[4].toInt(),
                organized = match.groupValues[5].toInt(),
            ),
        ).tvStatus()
    }
    syncCompleteStatusRegex.matchEntire(status)?.let { match ->
        return CloudDriveRssRunSummary(
            submitted = match.groupValues[1].toInt(),
            skipped = match.groupValues[2].toInt(),
            failed = match.groupValues[3].toInt(),
            organized = match.groupValues[4].toInt(),
        ).completeStatus()
    }
    loadedRssStatusRegex.matchEntire(status)?.let { match ->
        return rssSubscriptionsLoadedStatus(match.groupValues[1].toInt())
    }
    showingRssStatusRegex.matchEntire(status)?.let { match ->
        return rssSubscriptionsShowingStatus(match.groupValues[1].toInt())
    }
    verifiedTokenStatusRegex.matchEntire(status)?.let { match ->
        return cloudDriveTokenVerifiedStatus(
            friendlyName = match.groupValues[1],
            rootDir = "",
        )
    }
    linkedScanSourceStatusRegex.matchEntire(status)?.let { match ->
        return cloudRssLinkedScanSourceStatus(match.groupValues[1])
    }
    rescanStartedStatusRegex.matchEntire(status)?.let { match ->
        val reason = localizedCloudRssStatusText(match.groupValues[1]) ?: match.groupValues[1]
        return cloudRssRescanStartedStatus(reason, match.groupValues[2])
    }
    rssSubscriptionSavedRegex.matchEntire(status)?.let { match ->
        return rssSubscriptionSavedStatus(match.groupValues[1])
    }
    rssSubscriptionSelectedRegex.matchEntire(status)?.let { match ->
        return rssSubscriptionSelectedStatus(match.groupValues[1])
    }
    return null
}

private fun schedulerStateLabel(state: String): String =
    if (state == "running") "调度器运行中" else "调度器待命"

private val schedulerErrorStatusRegex = Regex("""^Scheduler (running|idle)\. Last check failed: (.+)$""")
private val schedulerSummaryStatusRegex =
    Regex("""^Scheduler (running|idle)\. Last run: (\d+) submitted, (\d+) skipped, (\d+) failed, (\d+) organized\.$""")
private val syncCompleteStatusRegex =
    Regex("""^Sync complete: (\d+) submitted, (\d+) skipped, (\d+) failed, (\d+) organized\.$""")
private val loadedRssStatusRegex = Regex("""^Loaded (\d+) RSS subscription\(s\)\.$""")
private val showingRssStatusRegex = Regex("""^Showing (\d+) RSS subscription\(s\)\.$""")
private val verifiedTokenStatusRegex = Regex("""^CloudDrive2 API token verified and saved: (.+)\.$""")
private val linkedScanSourceStatusRegex =
    Regex("""^Linked Cloud/RSS post-sync scan source: (.+)\. Save sync config to persist it\.$""")
private val rescanStartedStatusRegex = Regex("""^(.+) Rescanning (.+)\.\.\.$""")
private val rssSubscriptionSavedRegex = Regex("""^RSS subscription saved: (.+)$""")
private val rssSubscriptionSelectedRegex = Regex("""^Selected RSS subscription: (.+)$""")
