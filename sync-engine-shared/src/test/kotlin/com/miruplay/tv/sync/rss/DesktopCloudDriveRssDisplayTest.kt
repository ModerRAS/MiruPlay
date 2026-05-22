package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCloudDriveRssDisplayTest {
    @Test
    fun `scheduler status describes idle and first run states`() {
        assertEquals("调度器待命，尚未检查。", DesktopCloudDriveRssSchedulerState().schedulerStatus())
        assertEquals(
            "调度器待命，上次检查没有待同步内容。",
            DesktopCloudDriveRssSchedulerState(lastCheckedAt = 123L).schedulerStatus(),
        )
    }

    @Test
    fun `scheduler status includes last error before summary`() {
        val state = DesktopCloudDriveRssSchedulerState(
            running = true,
            lastError = "network down",
            lastSummary = CloudDriveRssRunSummary(submitted = 1, skipped = 2, failed = 0, organized = 3),
        )

        assertEquals("调度器运行中，上次检查失败：network down", state.schedulerStatus())
    }

    @Test
    fun `scheduler status summarizes last successful run`() {
        val state = DesktopCloudDriveRssSchedulerState(
            running = false,
            lastSummary = CloudDriveRssRunSummary(submitted = 2, skipped = 1, failed = 1, organized = 4),
        )

        assertEquals("调度器待命，上次运行：提交 2 个，跳过 1 个，失败 1 个，整理 4 个。", state.schedulerStatus())
    }

    @Test
    fun `linked source label handles none missing and existing source`() {
        val sources = listOf(
            MediaSourceInfo(id = 7L, name = "Cloud WebDAV", type = MediaSourceType.WEBDAV),
        )

        assertEquals("None", linkedCloudDriveSourceLabel(sources, null))
        assertEquals("Missing source #8", linkedCloudDriveSourceLabel(sources, 8L))
        assertEquals("Cloud WebDAV (WEBDAV)", linkedCloudDriveSourceLabel(sources, 7L))
    }

    @Test
    fun `cloud drive credential statuses share desktop wording`() {
        assertEquals("Cloud/RSS 自动化设置已保存。", cloudRssConfigSavedStatus())
        assertEquals("CloudDrive 凭据已保存。", cloudDriveCredentialsSavedStatus())
        assertEquals("CloudDrive 凭据已清空。", cloudDriveCredentialsClearedStatus())
        assertEquals(
            "请先填写 CloudDrive2 地址、用户名和密码。",
            cloudDriveLoginRequiredStatus(),
        )
        assertEquals("正在登录 CloudDrive2...", cloudDriveLoginStartedStatus())
        assertEquals("CloudDrive2 登录成功，令牌已保存。", cloudDriveLoginSucceededStatus())
        assertEquals(
            "请先填写 CloudDrive2 地址和 API 令牌。",
            cloudDriveTokenRequiredStatus(),
        )
        assertEquals("正在验证 CloudDrive2 API 令牌...", cloudDriveTokenValidationStartedStatus())
    }

    @Test
    fun `token verification status uses friendly name then root fallback`() {
        val named = tokenInfo(friendlyName = "MiruPlay")
        val rooted = tokenInfo(friendlyName = "", rootDir = "/Anime")
        val fallback = tokenInfo(friendlyName = "", rootDir = "")

        assertEquals("CloudDrive2 API 令牌已验证并保存：MiruPlay。", named.verifiedStatus())
        assertEquals("CloudDrive2 API 令牌已验证并保存：/Anime。", rooted.verifiedStatus())
        assertEquals("CloudDrive2 API 令牌已验证并保存：CloudDrive2。", fallback.verifiedStatus())
    }

    @Test
    fun `subscription list statuses share desktop wording`() {
        val subscription = RssSubscriptionInfo(name = "Anime", url = "https://example.test/rss.xml")

        assertEquals("加载或保存 Cloud/RSS 自动化设置。", cloudRssInitialStatus())
        assertEquals("尚未配置 RSS 订阅。", emptyList<RssSubscriptionInfo>().loadedStatus())
        assertEquals("已加载 1 个 RSS 订阅。", listOf(subscription).loadedStatus())
        assertEquals("尚未配置 RSS 订阅。", emptyList<RssSubscriptionInfo>().showingStatus())
        assertEquals("正在显示 1 个 RSS 订阅。", listOf(subscription).showingStatus())
        assertEquals("RSS 订阅加载失败。", rssSubscriptionsLoadFailedStatus(null))
        assertEquals("load failed", rssSubscriptionsLoadFailedStatus("load failed"))
        assertEquals("RSS 订阅刷新失败。", rssSubscriptionsRefreshFailedStatus(null))
        assertEquals("refresh failed", rssSubscriptionsRefreshFailedStatus("refresh failed"))
    }

    @Test
    fun `run scheduler scan source and rss statuses share desktop wording`() {
        val summary = CloudDriveRssRunSummary(submitted = 3, skipped = 2, failed = 1, organized = 4)
        val source = MediaSourceInfo(id = 7L, name = "Cloud WebDAV", type = MediaSourceType.WEBDAV)
        val subscription = RssSubscriptionInfo(name = "Anime", url = "https://example.test/rss.xml")

        assertEquals("正在执行 Cloud/RSS 同步...", cloudRssRunStartedStatus())
        assertEquals(
            "同步完成：提交 3 个，跳过 2 个，失败 1 个，整理 4 个。",
            summary.completeStatus(),
        )
        assertEquals(
            "启动调度前请先启用并保存 Cloud/RSS 同步。",
            cloudRssSchedulerDisabledStatus(),
        )
        assertEquals("Cloud/RSS 调度器已启动。", cloudRssSchedulerStartStatus(started = true))
        assertEquals(
            "Cloud/RSS 调度器已经在运行。",
            cloudRssSchedulerStartStatus(started = false),
        )
        assertEquals("Cloud/RSS 调度器已停止。", cloudRssSchedulerStoppedStatus())
        assertEquals(
            "请先打开已保存的媒体源，再绑定 Cloud/RSS 扫描。",
            cloudRssScanSourceRequiredStatus(),
        )
        assertEquals(
            "未找到已绑定的扫描源，请清除或重新绑定 Cloud/RSS 扫描源。",
            cloudRssScanSourceMissingStatus(),
        )
        assertEquals(
            "已绑定同步后扫描源：Cloud WebDAV。请保存同步配置。",
            source.linkedCloudRssScanSourceStatus(),
        )
        assertEquals(
            "定时同步完成，正在重扫 Cloud WebDAV...",
            source.cloudRssRescanStartedStatus("定时同步完成。"),
        )
        assertEquals(
            "同步后扫描源已清除，请保存同步配置。",
            cloudRssScanSourceClearedStatus(),
        )
        assertEquals("请先填写 RSS 地址。", rssUrlRequiredStatus())
        assertEquals("RSS 订阅已保存：Anime", subscription.savedStatus())
        assertEquals("已选择 RSS 订阅：Anime", subscription.selectedStatus())
        assertEquals("请先选择一个 RSS 订阅。", rssSubscriptionRequiredStatus())
        assertEquals("RSS 订阅已删除。", rssSubscriptionDeletedStatus())
    }

    private fun tokenInfo(
        friendlyName: String,
        rootDir: String = "/",
    ): CloudDriveTokenInfo =
        CloudDriveTokenInfo(
            rootDir = rootDir,
            friendlyName = friendlyName,
            allowList = true,
            allowCreateFolder = true,
            allowCreateFile = true,
            allowWrite = true,
            allowMove = true,
            allowAddOfflineDownload = true,
        )
}
