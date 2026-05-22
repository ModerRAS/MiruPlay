package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDriveRssStatusConventionsTest {
    @Test
    fun `scheduler status uses shared TV facing Chinese copy`() {
        assertEquals(
            "调度器待命，尚未检查。",
            CloudDriveRssSchedulerUiState().tvStatus(),
        )
        assertEquals(
            "调度器运行中，上次检查失败：network down",
            CloudDriveRssSchedulerUiState(running = true, lastError = "network down").tvStatus(),
        )
        assertEquals(
            "调度器待命，上次运行：提交 2 个，跳过 1 个，失败 1 个，整理 4 个。",
            CloudDriveRssSchedulerUiState(
                lastSummary = CloudDriveRssRunSummary(submitted = 2, skipped = 1, failed = 1, organized = 4),
            ).tvStatus(),
        )
    }

    @Test
    fun `credential and token statuses are shared`() {
        assertEquals("Cloud/RSS 自动化设置已保存。", cloudRssConfigSavedStatus())
        assertEquals("加载或保存 Cloud/RSS 自动化设置。", cloudRssInitialStatus())
        assertEquals("CloudDrive 凭据已保存。", cloudDriveCredentialsSavedStatus())
        assertEquals("CloudDrive 凭据已清空。", cloudDriveCredentialsClearedStatus())
        assertEquals("请先填写 CloudDrive2 地址、用户名和密码。", cloudDriveLoginRequiredStatus())
        assertEquals("正在登录 CloudDrive2...", cloudDriveLoginStartedStatus())
        assertEquals("CloudDrive2 登录成功，令牌已保存。", cloudDriveLoginSucceededStatus())
        assertEquals("请先填写 CloudDrive2 地址。", cloudDriveEndpointRequiredStatus())
        assertEquals("请填写 CloudDrive2 API Token 或 Key。", cloudDriveApiTokenRequiredStatus())
        assertEquals("请先填写 CloudDrive2 地址和 API 令牌。", cloudDriveTokenRequiredStatus())
        assertEquals("请先登录 CloudDrive2 或保存 API Token。", cloudDriveTokenLoginRequiredStatus())
        assertEquals("正在验证 CloudDrive2 API 令牌...", cloudDriveTokenValidationStartedStatus())
        assertEquals(
            "CloudDrive2 API 令牌已验证并保存：MiruPlay。",
            cloudDriveTokenVerifiedStatus(friendlyName = "MiruPlay", rootDir = "/Anime"),
        )
        assertEquals(
            "CloudDrive2 API 令牌已验证并保存：/Anime。",
            cloudDriveTokenVerifiedStatus(friendlyName = "", rootDir = "/Anime"),
        )
    }

    @Test
    fun `run scheduler source and RSS statuses are shared`() {
        val summary = CloudDriveRssRunSummary(submitted = 3, skipped = 2, failed = 1, organized = 4)

        assertEquals("正在执行 Cloud/RSS 同步...", cloudRssRunStartedStatus())
        assertEquals("同步完成：提交 3 个，跳过 2 个，失败 1 个，整理 4 个。", summary.completeStatus())
        assertEquals("启动调度前请先启用并保存 Cloud/RSS 同步。", cloudRssSchedulerDisabledStatus())
        assertEquals("Cloud/RSS 调度器已启动。", cloudRssSchedulerStartStatus(started = true))
        assertEquals("Cloud/RSS 调度器已经在运行。", cloudRssSchedulerStartStatus(started = false))
        assertEquals("Cloud/RSS 调度器已停止。", cloudRssSchedulerStoppedStatus())
        assertEquals("定时同步完成。", cloudRssScheduledSyncCompleteStatus())
        assertEquals("请先打开已保存的媒体源，再绑定 Cloud/RSS 扫描。", cloudRssScanSourceRequiredStatus())
        assertEquals("未找到已绑定的扫描源，请清除或重新绑定 Cloud/RSS 扫描源。", cloudRssScanSourceMissingStatus())
        assertEquals("已绑定同步后扫描源：Cloud WebDAV。请保存同步配置。", cloudRssLinkedScanSourceStatus("Cloud WebDAV"))
        assertEquals("定时同步完成，正在重扫 Cloud WebDAV...", cloudRssRescanStartedStatus("定时同步完成。", "Cloud WebDAV"))
        assertEquals("同步后扫描源已清除，请保存同步配置。", cloudRssScanSourceClearedStatus())
        assertEquals("请先填写 RSS 地址。", rssUrlRequiredStatus())
        assertEquals("RSS 订阅已保存：Anime", rssSubscriptionSavedStatus("Anime"))
        assertEquals("已选择 RSS 订阅：Anime", rssSubscriptionSelectedStatus("Anime"))
        assertEquals("请先选择一个 RSS 订阅。", rssSubscriptionRequiredStatus())
        assertEquals("RSS 订阅已删除。", rssSubscriptionDeletedStatus())
    }

    @Test
    fun `subscription list statuses are shared`() {
        assertEquals("尚未配置 RSS 订阅。", rssSubscriptionsLoadedStatus(0))
        assertEquals("已加载 2 个 RSS 订阅。", rssSubscriptionsLoadedStatus(2))
        assertEquals("尚未配置 RSS 订阅。", rssSubscriptionsShowingStatus(0))
        assertEquals("正在显示 2 个 RSS 订阅。", rssSubscriptionsShowingStatus(2))
        assertEquals("RSS 订阅加载失败。", rssSubscriptionsLoadFailedStatus(null))
        assertEquals("load failed", rssSubscriptionsLoadFailedStatus("load failed"))
        assertEquals("RSS 订阅刷新失败。", rssSubscriptionsRefreshFailedStatus(null))
        assertEquals("refresh failed", rssSubscriptionsRefreshFailedStatus("refresh failed"))
    }
}
