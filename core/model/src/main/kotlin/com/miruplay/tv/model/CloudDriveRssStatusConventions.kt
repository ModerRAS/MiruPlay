package com.miruplay.tv.model

data class CloudDriveRssSchedulerUiState(
    val running: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastSummary: CloudDriveRssRunSummary? = null,
    val lastError: String? = null,
)

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

fun rssSubscriptionSavedStatus(name: String): String =
    "RSS 订阅已保存：$name"

fun rssSubscriptionSelectedStatus(name: String): String =
    "已选择 RSS 订阅：$name"

fun rssSubscriptionRequiredStatus(): String =
    "请先选择一个 RSS 订阅。"

fun rssSubscriptionDeletedStatus(): String =
    "RSS 订阅已删除。"
