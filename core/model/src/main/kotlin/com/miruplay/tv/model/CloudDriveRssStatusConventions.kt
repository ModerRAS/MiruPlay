package com.miruplay.tv.model

data class CloudDriveRssSchedulerUiState(
    val running: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastSummary: CloudDriveRssRunSummary? = null,
    val lastError: String? = null,
)

fun cloudDriveRssTitleLabel(): String =
    "CloudDrive2"

fun cloudDriveRssDescriptionLabel(): String =
    "RSS 会提交到 CloudDrive2 离线下载目录，整理后触发所选 WebDAV 媒体源扫描。"

fun cloudDriveRssScheduledChipLabel(enabled: Boolean): String =
    if (enabled) "定时已开" else "定时关闭"

fun cloudDriveRssCredentialsBadgeLabel(configured: Boolean): String =
    if (configured) "已登录" else "未登录"

fun cloudDriveRssEndpointFieldLabel(): String =
    "CloudDrive2 地址"

fun cloudDriveRssUsernameFieldLabel(): String =
    "CloudDrive2 用户名"

fun cloudDriveRssPasswordFieldLabel(): String =
    "密码"

fun cloudDriveRssApiTokenFieldLabel(): String =
    "API Token / Key"

fun cloudDriveRssInboxPathFieldLabel(): String =
    "下载目录 A"

fun cloudDriveRssLibraryPathFieldLabel(): String =
    "整理目录 B"

fun cloudDriveRssIntervalMinutesFieldLabel(): String =
    "定时间隔（分钟）"

fun cloudDriveRssProxyToggleLabel(enabled: Boolean): String =
    if (enabled) "RSS 代理已开" else "RSS 代理关闭"

fun cloudDriveRssProxySettingLabel(): String =
    "RSS 代理"

fun cloudDriveRssEnabledToggleLabel(): String =
    "启用"

fun cloudDriveRssProxyHostFieldLabel(): String =
    "代理地址"

fun cloudDriveRssProxyPortFieldLabel(): String =
    "代理端口"

fun cloudDriveRssSaveConfigActionLabel(): String =
    "保存"

fun cloudDriveRssSaveCredentialsActionLabel(): String =
    "保存凭据"

fun cloudDriveRssClearCredentialsActionLabel(): String =
    "清空凭据"

fun cloudDriveRssLoginActionLabel(busy: Boolean = false): String =
    if (busy) "处理中" else "登录"

fun cloudDriveRssSaveApiTokenActionLabel(): String =
    "保存 Key"

fun cloudDriveRssVerifyApiTokenActionLabel(): String =
    "验证令牌"

fun cloudDriveRssRunNowActionLabel(busy: Boolean = false): String =
    if (busy) "执行中" else "立即执行"

fun cloudDriveRssTokenStatusMessage(configured: Boolean): String =
    if (configured) "CloudDrive2 令牌已保存在加密存储中。" else "登录后才能提交离线下载任务。"

fun cloudDriveRssSyncPathTitleLabel(): String =
    "同步路径"

fun cloudDriveRssRuntimeTitleLabel(): String =
    "运行状态"

fun cloudDriveRssDirectoryBadgeLabel(): String =
    "目录"

fun cloudDriveRssPathBadgeLabel(): String =
    "路径"

fun cloudDriveRssRunBadgeLabel(): String =
    "运行"

fun cloudDriveRssEnabledBadgeLabel(enabled: Boolean): String =
    if (enabled) "启用" else "停用"

fun cloudDriveRssEndpointFallbackLabel(): String =
    "填写 CloudDrive2 地址"

fun cloudDriveRssUnconfiguredEndpointLabel(): String =
    "未配置端点"

fun cloudDriveRssSchedulerIdleLabel(): String =
    "调度器待命"

data class CloudDriveRssUiLabels(
    val endpoint: String,
    val username: String,
    val apiToken: String,
    val password: String,
    val saveCredentials: String,
    val clearCredentials: String,
    val login: String,
    val verify: String,
    val inboxPath: String,
    val libraryPath: String,
    val intervalMinutes: String,
    val proxyHost: String,
    val proxyPort: String,
    val enabledToggle: String,
    val rssProxy: String,
    val useActiveSource: String,
    val clearSource: String,
    val postSyncSource: String,
    val saveSyncConfig: String,
    val runSyncNow: String,
    val rssSubscriptions: String,
    val subscriptionName: String,
    val subscriptionUrl: String,
    val filterRegex: String,
    val saveRss: String,
    val deleteRss: String,
    val rssEmpty: String,
    val rssPreviewFallback: String,
    val startScheduler: String,
    val stopScheduler: String,
    val endpointFallback: String,
    val schedulerIdle: String,
    val enabledBadge: String,
    val disabledBadge: String,
    val pathBadge: String,
    val runBadge: String,
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
        runSyncNow = cloudDriveRssRunNowActionLabel(),
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

fun cloudDriveRssCloudDriveEnabledValue(enabled: Boolean): String =
    if (enabled) "已启用" else "未启用"

fun cloudDriveRssPostSyncScanSummaryLabel(): String =
    "同步后扫描"

fun cloudDriveRssChooseDirectoryActionLabel(): String =
    "选择目录"

fun cloudDriveRssUseCurrentDirectoryActionLabel(): String =
    directoryBrowserUseCurrentActionLabel(isLocal = false)

fun cloudDriveRssParentDirectoryActionLabel(): String =
    directoryBrowserParentActionLabel(isLocal = false)

fun cloudDriveRssCloseActionLabel(): String =
    directoryBrowserCloseActionLabel()

fun cloudDriveRssDirectoryPageUnitLabel(): String =
    "个目录"

fun cloudDriveRssLoadingDirectoriesMessage(): String =
    directoryBrowserLoadingMessage(isLocal = false)

fun cloudDriveRssEmptyDirectoryMessage(): String =
    directoryBrowserEmptyMessage(isLocal = false)

fun cloudDriveRssInboxDirectoryPickerTitle(): String =
    "选择下载目录 A"

fun cloudDriveRssLibraryDirectoryPickerTitle(): String =
    "选择整理目录 B"

fun cloudDriveRssInboxDirectorySelectedLabel(): String =
    "下载目录 A"

fun cloudDriveRssLibraryDirectorySelectedLabel(): String =
    "整理目录 B"

fun cloudDriveRssDirectorySelectedStatus(
    selectedLabel: String,
    path: String,
): String =
    "已选择$selectedLabel：$path"

fun cloudDriveRssDirectoryBrowsingStatus(path: String): String =
    "正在浏览 CloudDrive2 目录：$path"

fun cloudDriveRssPathPairSeparator(): String =
    " 到 "

fun cloudDriveRssScanSourceTitleLabel(): String =
    "入库后扫描的 WebDAV 媒体源"

fun cloudDriveRssNoWebDavSourceMessage(): String =
    "还没有 WebDAV 媒体源，请先在媒体源里添加 CloudDrive WebDAV 地址。"

fun cloudDriveRssNoScanSourceOptionLabel(): String =
    "暂不扫描"

fun cloudDriveRssUseActiveSourceActionLabel(): String =
    "使用当前源"

fun cloudDriveRssClearScanSourceActionLabel(): String =
    "清除扫描源"

fun cloudDriveRssPostSyncSourceLabel(): String =
    "同步后扫描源："

fun cloudDriveRssStartSchedulerActionLabel(): String =
    "启动调度"

fun cloudDriveRssStopSchedulerActionLabel(): String =
    "停止调度"

fun rssSubscriptionsTitleLabel(): String =
    "RSS 订阅"

fun rssSubscriptionPageUnitLabel(): String =
    "个订阅"

fun rssSubscriptionNameFieldLabel(): String =
    "订阅名称"

fun rssSubscriptionUrlFieldLabel(): String =
    "RSS 地址"

fun rssSubscriptionFilterRegexFieldLabel(): String =
    "标题过滤正则（可选）"

fun rssSubscriptionNewEnabledLabel(enabled: Boolean): String =
    if (enabled) "新增后启用" else "新增后停用"

fun rssSubscriptionAddActionLabel(): String =
    "添加订阅"

fun rssSubscriptionSaveActionLabel(): String =
    "保存 RSS"

fun rssSubscriptionDeleteActionLabel(): String =
    "删除订阅"

fun rssSubscriptionEmptyMessage(): String =
    "还没有 RSS 订阅。"

fun rssSubscriptionPreviewFallbackLabel(): String =
    "暂无订阅"

fun rssSubscriptionFormPreviewFallbackLabel(): String =
    "保存订阅后在这里显示"

fun rssSubscriptionStateLabel(enabled: Boolean): String =
    if (enabled) "启用" else "停用"

fun rssSubscriptionStateActionLabel(enabled: Boolean): String =
    if (enabled) "停用" else "启用"

fun rssSubscriptionFallbackTitleLabel(): String =
    "RSS"

fun rssSubscriptionLastCheckedLabel(timestampText: String?): String =
    timestampText?.takeIf { it.isNotBlank() }?.let { "上次检查 $it" } ?: "尚未检查"

fun CloudDriveRssSchedulerUiState.tvStatus(): String {
    val prefix = if (running) "调度器运行中" else "调度器待命"
    val error = lastError
    if (!error.isNullOrBlank()) return "$prefix，上次检查失败：$error"
    val summary = lastSummary
    if (summary != null) {
        return "$prefix，上次运行：提交 ${summary.submitted} 个，跳过 ${summary.skipped} 个，失败 ${summary.failed} 个，整理 ${summary.organized} 个。"
    }
    return if (lastCheckedAt > 0L) {
        "$prefix，上次检查没有待同步内容。"
    } else {
        "$prefix，尚未检查。"
    }
}

fun cloudRssSchedulerStatus(state: CloudDriveRssSchedulerUiState): String =
    state.tvStatus()

fun cloudRssConfigSavedStatus(): String =
    "Cloud/RSS 自动化设置已保存。"

fun cloudRssInitialStatus(): String =
    "加载或保存 Cloud/RSS 自动化设置。"

fun cloudDriveCredentialsSavedStatus(): String =
    "CloudDrive 凭据已保存。"

fun cloudDriveCredentialsClearedStatus(): String =
    "CloudDrive 凭据已清空。"

fun cloudDriveLoginRequiredStatus(): String =
    "请先填写 CloudDrive2 地址、用户名和密码。"

fun cloudDriveLoginStartedStatus(): String =
    "正在登录 CloudDrive2..."

fun cloudDriveLoginSucceededStatus(): String =
    "CloudDrive2 登录成功，令牌已保存。"

fun cloudDriveEndpointRequiredStatus(): String =
    "请先填写 CloudDrive2 地址。"

fun cloudDriveApiTokenRequiredStatus(): String =
    "请填写 CloudDrive2 API Token 或 Key。"

fun cloudDriveTokenRequiredStatus(): String =
    "请先填写 CloudDrive2 地址和 API 令牌。"

fun cloudDriveTokenLoginRequiredStatus(): String =
    "请先登录 CloudDrive2 或保存 API Token。"

fun cloudDriveTokenValidationStartedStatus(): String =
    "正在验证 CloudDrive2 API 令牌..."

fun cloudDriveTokenVerifiedStatus(
    friendlyName: String,
    rootDir: String,
): String {
    val label = friendlyName.takeIf { it.isNotBlank() }
        ?: rootDir.ifBlank { "CloudDrive2" }
    return "CloudDrive2 API 令牌已验证并保存：$label。"
}

fun cloudRssRunStartedStatus(): String =
    "正在执行 Cloud/RSS 同步..."

fun CloudDriveRssRunSummary.completeStatus(): String =
    "同步完成：提交 $submitted 个，跳过 $skipped 个，失败 $failed 个，整理 $organized 个。"

fun cloudDriveRssRunSummaryStatus(summary: CloudDriveRssRunSummary): String =
    summary.completeStatus()

fun cloudRssSchedulerDisabledStatus(): String =
    "启动调度前请先启用并保存 Cloud/RSS 同步。"

fun cloudRssSchedulerStartStatus(started: Boolean): String =
    if (started) {
        "Cloud/RSS 调度器已启动。"
    } else {
        "Cloud/RSS 调度器已经在运行。"
    }

fun cloudRssSchedulerStoppedStatus(): String =
    "Cloud/RSS 调度器已停止。"

fun cloudRssScheduledSyncCompleteStatus(): String =
    "定时同步完成。"

fun cloudRssScanSourceRequiredStatus(): String =
    "请先打开已保存的媒体源，再绑定 Cloud/RSS 扫描。"

fun cloudRssScanSourceMissingStatus(): String =
    "未找到已绑定的扫描源，请清除或重新绑定 Cloud/RSS 扫描源。"

fun cloudRssLinkedScanSourceStatus(sourceName: String): String =
    "已绑定同步后扫描源：$sourceName。请保存同步配置。"

fun cloudRssRescanStartedStatus(
    reason: String,
    sourceName: String,
): String =
    "${reason.removeSuffix("。")}，正在重扫 $sourceName..."

fun cloudRssScanSourceClearedStatus(): String =
    "同步后扫描源已清除，请保存同步配置。"

fun rssUrlRequiredStatus(): String =
    "请先填写 RSS 地址。"

fun rssSubscriptionsLoadedStatus(count: Int): String =
    if (count <= 0) {
        "尚未配置 RSS 订阅。"
    } else {
        "已加载 $count 个 RSS 订阅。"
    }

fun rssSubscriptionsShowingStatus(count: Int): String =
    if (count <= 0) {
        "尚未配置 RSS 订阅。"
    } else {
        "正在显示 $count 个 RSS 订阅。"
    }

fun rssSubscriptionsLoadFailedStatus(errorMessage: String?): String =
    errorMessage ?: "RSS 订阅加载失败。"

fun rssSubscriptionsRefreshFailedStatus(errorMessage: String?): String =
    errorMessage ?: "RSS 订阅刷新失败。"

fun RssSubscriptionInfo?.retainedSelectionInRssSubscriptions(
    subscriptions: List<RssSubscriptionInfo>,
): RssSubscriptionInfo? =
    this?.let { selected ->
        subscriptions.firstOrNull { it.id == selected.id }
    }

fun rssSubscriptionSavedStatus(name: String): String =
    "RSS 订阅已保存：$name"

fun rssSubscriptionSelectedStatus(name: String): String =
    "已选择 RSS 订阅：$name"

fun rssSubscriptionRequiredStatus(): String =
    "请先选择一个 RSS 订阅。"

fun rssSubscriptionDeletedStatus(): String =
    "RSS 订阅已删除。"

fun cloudRssIdleStatus(): String =
    "Cloud/RSS 待命。"

fun cloudRssStatusText(status: String): String =
    localizedCloudRssStatusText(status) ?: status.trim()

fun localizedCloudRssStatusText(status: String): String? {
    val trimmed = status.trim()
    return when {
        trimmed.isBlank() -> cloudRssIdleStatus()
        trimmed.isTvCloudRssStatus() -> trimmed
        trimmed == "Scheduler idle. No checks yet." ->
            CloudDriveRssSchedulerUiState(running = false, lastCheckedAt = 0L).tvStatus()
        trimmed == "Scheduler idle. Last check found no due sync." ->
            CloudDriveRssSchedulerUiState(running = false, lastCheckedAt = 1L).tvStatus()
        trimmed == "Scheduler running. No checks yet." ->
            CloudDriveRssSchedulerUiState(running = true, lastCheckedAt = 0L).tvStatus()
        trimmed == "Scheduler running. Last check found no due sync." ->
            CloudDriveRssSchedulerUiState(running = true, lastCheckedAt = 1L).tvStatus()
        trimmed == "Cloud/RSS automation settings saved." -> cloudRssConfigSavedStatus()
        trimmed == "Load or save Cloud/RSS automation settings." -> cloudRssInitialStatus()
        trimmed == "CloudDrive credentials saved." -> cloudDriveCredentialsSavedStatus()
        trimmed == "CloudDrive credentials cleared." -> cloudDriveCredentialsClearedStatus()
        trimmed == "Enter CloudDrive2 endpoint, username, and password first." -> cloudDriveLoginRequiredStatus()
        trimmed == "Logging into CloudDrive2..." -> cloudDriveLoginStartedStatus()
        trimmed == "CloudDrive2 login succeeded; token saved." -> cloudDriveLoginSucceededStatus()
        trimmed == "Enter CloudDrive2 endpoint and API token first." -> cloudDriveTokenRequiredStatus()
        trimmed == "Validating CloudDrive2 API token..." -> cloudDriveTokenValidationStartedStatus()
        trimmed == "Running Cloud/RSS sync..." -> cloudRssRunStartedStatus()
        trimmed == "Enable and save Cloud/RSS sync before starting the scheduler." -> cloudRssSchedulerDisabledStatus()
        trimmed == "Cloud/RSS scheduler started." -> cloudRssSchedulerStartStatus(started = true)
        trimmed == "Cloud/RSS scheduler is already running." -> cloudRssSchedulerStartStatus(started = false)
        trimmed == "Cloud/RSS scheduler stopped." -> cloudRssSchedulerStoppedStatus()
        trimmed == "Scheduled sync complete." -> cloudRssScheduledSyncCompleteStatus()
        trimmed == "Open a saved media source before linking Cloud/RSS scanning." -> cloudRssScanSourceRequiredStatus()
        trimmed == "Linked scan source was not found. Clear or relink the Cloud/RSS scan source." -> cloudRssScanSourceMissingStatus()
        trimmed == "Cloud/RSS post-sync scan source cleared. Save sync config to persist it." -> cloudRssScanSourceClearedStatus()
        trimmed == "Enter an RSS URL first." -> rssUrlRequiredStatus()
        trimmed == "No RSS subscriptions configured." -> rssSubscriptionsLoadedStatus(0)
        trimmed == "Failed to load RSS subscriptions." -> rssSubscriptionsLoadFailedStatus(null)
        trimmed == "Failed to refresh RSS subscriptions." -> rssSubscriptionsRefreshFailedStatus(null)
        trimmed == "Select an RSS subscription first." -> rssSubscriptionRequiredStatus()
        trimmed == "RSS subscription deleted." -> rssSubscriptionDeletedStatus()
        else -> localizedDynamicCloudRssStatusText(trimmed)
    }
}

private fun String.isTvCloudRssStatus(): Boolean =
    any { it in '\u4E00'..'\u9FFF' } && (endsWith("。") || endsWith("..."))

private fun localizedDynamicCloudRssStatusText(status: String): String? {
    schedulerErrorStatusRegex.matchEntire(status)?.let { match ->
        return "${schedulerStateLabel(match.groupValues[1])}，上次检查失败：${match.groupValues[2]}"
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
