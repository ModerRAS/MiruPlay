package com.miruplay.tv.model

import com.miruplay.tv.core.common.rawUiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriveRssStatusConventionsTest {
    @Test
    fun `cloud rss form labels are shared`() {
        assertEquals("CloudDrive2", cloudDriveRssTitleLabel().fallback)
        assertEquals(
            "RSS 会提交到 CloudDrive2 离线下载目录，整理后触发所选 WebDAV 媒体源扫描。",
            cloudDriveRssDescriptionLabel().fallback,
        )
        assertEquals("定时已开", cloudDriveRssScheduledChipLabel(true).fallback)
        assertEquals("定时关闭", cloudDriveRssScheduledChipLabel(false).fallback)
        assertEquals("Key 已保存", cloudDriveRssCredentialsBadgeLabel(true).fallback)
        assertEquals("密码已保存", cloudDriveRssCredentialsBadgeLabel(tokenConfigured = false, passwordConfigured = true).fallback)
        assertEquals(
            "Key 与密码已保存",
            cloudDriveRssCredentialsBadgeLabel(tokenConfigured = true, passwordConfigured = true).fallback,
        )
        assertEquals("未授权", cloudDriveRssCredentialsBadgeLabel(false).fallback)
        assertEquals("用户名密码已保存", cloudDriveRssPasswordCredentialLabel(true).fallback)
        assertEquals("用户名密码未保存", cloudDriveRssPasswordCredentialLabel(false).fallback)
        assertEquals("Key 已保存", cloudDriveRssApiTokenCredentialLabel(true).fallback)
        assertEquals("Key 未保存", cloudDriveRssApiTokenCredentialLabel(false).fallback)
        assertEquals("CloudDrive2 地址", cloudDriveRssEndpointFieldLabel().fallback)
        assertEquals("CloudDrive2 用户名", cloudDriveRssUsernameFieldLabel().fallback)
        assertEquals("密码（登录后保存）", cloudDriveRssPasswordFieldLabel().fallback)
        assertEquals("API Token / Key（可替代密码登录）", cloudDriveRssApiTokenFieldLabel().fallback)
        assertEquals("下载目录 A", cloudDriveRssInboxPathFieldLabel().fallback)
        assertEquals("整理目录 B", cloudDriveRssLibraryPathFieldLabel().fallback)
        assertEquals("定时间隔（分钟）", cloudDriveRssIntervalMinutesFieldLabel().fallback)
        assertEquals("RSS 代理已开", cloudDriveRssProxyToggleLabel(true).fallback)
        assertEquals("RSS 代理关闭", cloudDriveRssProxyToggleLabel(false).fallback)
        assertEquals("启用", cloudDriveRssEnabledToggleLabel().fallback)
        assertEquals("代理地址", cloudDriveRssProxyHostFieldLabel().fallback)
        assertEquals("代理端口", cloudDriveRssProxyPortFieldLabel().fallback)
    }

    @Test
    fun `cloud rss actions scan source and directory labels are shared`() {
        assertEquals("保存", cloudDriveRssSaveConfigActionLabel().fallback)
        assertEquals("保存凭据", cloudDriveRssSaveCredentialsActionLabel().fallback)
        assertEquals("清空凭据", cloudDriveRssClearCredentialsActionLabel().fallback)
        assertEquals("登录并保存密码", cloudDriveRssLoginActionLabel().fallback)
        assertEquals("处理中", cloudDriveRssLoginActionLabel(busy = true).fallback)
        assertEquals("验证并保存 Key", cloudDriveRssSaveApiTokenActionLabel().fallback)
        assertEquals("验证令牌", cloudDriveRssVerifyApiTokenActionLabel().fallback)
        assertEquals("立即执行", cloudDriveRssRunNowActionLabel().fallback)
        assertEquals("执行中", cloudDriveRssRunNowActionLabel(busy = true).fallback)
        assertEquals("保存并立即执行", cloudDriveRssSaveAndRunNowActionLabel().fallback)
        assertEquals("执行中", cloudDriveRssSaveAndRunNowActionLabel(busy = true).fallback)
        assertEquals("CloudDrive2 Key 已保存在加密存储中。", cloudDriveRssTokenStatusMessage(true).fallback)
        assertEquals(
            "CloudDrive2 密码已保存，执行时会自动登录刷新 Key。",
            cloudDriveRssTokenStatusMessage(tokenConfigured = false, passwordConfigured = true).fallback,
        )
        assertEquals(
            "CloudDrive2 Key 和用户名密码已保存在加密存储中。",
            cloudDriveRssTokenStatusMessage(tokenConfigured = true, passwordConfigured = true).fallback,
        )
        assertEquals("请先登录并保存密码，或验证并保存 Key。", cloudDriveRssTokenStatusMessage(false).fallback)
        assertEquals("同步路径", cloudDriveRssSyncPathTitleLabel().fallback)
        assertEquals("运行状态", cloudDriveRssRuntimeTitleLabel().fallback)
        assertEquals("目录", cloudDriveRssDirectoryBadgeLabel().fallback)
        assertEquals("路径", cloudDriveRssPathBadgeLabel().fallback)
        assertEquals("运行", cloudDriveRssRunBadgeLabel().fallback)
        assertEquals("启用", cloudDriveRssEnabledBadgeLabel(true).fallback)
        assertEquals("停用", cloudDriveRssEnabledBadgeLabel(false).fallback)
        assertEquals("已启用", cloudDriveRssCloudDriveEnabledValue(true).fallback)
        assertEquals("未启用", cloudDriveRssCloudDriveEnabledValue(false).fallback)
        assertEquals("填写 CloudDrive2 地址", cloudDriveRssEndpointFallbackLabel().fallback)
        assertEquals("未配置端点", cloudDriveRssUnconfiguredEndpointLabel().fallback)
        assertEquals("调度器待命", cloudDriveRssSchedulerIdleLabel().fallback)
        assertEquals("同步后扫描", cloudDriveRssPostSyncScanSummaryLabel().fallback)
        assertEquals("选择目录", cloudDriveRssChooseDirectoryActionLabel().fallback)
        assertEquals("使用当前目录", cloudDriveRssUseCurrentDirectoryActionLabel().fallback)
        assertEquals("返回上级", cloudDriveRssParentDirectoryActionLabel().fallback)
        assertEquals("关闭", cloudDriveRssCloseActionLabel().fallback)
        assertEquals("个目录", cloudDriveRssDirectoryPageUnitLabel().fallback)
        assertEquals("正在读取 CloudDrive2 目录...", cloudDriveRssLoadingDirectoriesMessage().fallback)
        assertEquals("当前目录没有可进入的子目录。", cloudDriveRssEmptyDirectoryMessage().fallback)
        assertEquals("选择下载目录 A", cloudDriveRssInboxDirectoryPickerTitle().fallback)
        assertEquals("选择整理目录 B", cloudDriveRssLibraryDirectoryPickerTitle().fallback)
        assertEquals("下载目录 A", cloudDriveRssInboxDirectorySelectedLabel().fallback)
        assertEquals("整理目录 B", cloudDriveRssLibraryDirectorySelectedLabel().fallback)
        assertEquals("已选择下载目录 A：/Anime", cloudDriveRssDirectorySelectedStatus(rawUiText("下载目录 A"), "/Anime").fallback)
        assertEquals("正在浏览 CloudDrive2 目录：/Anime", cloudDriveRssDirectoryBrowsingStatus("/Anime").fallback)
        assertEquals(" 到 ", cloudDriveRssPathPairSeparator().fallback)
        assertEquals("入库后扫描的 WebDAV 媒体源", cloudDriveRssScanSourceTitleLabel().fallback)
        assertEquals("还没有 WebDAV 媒体源，请先在媒体源里添加 CloudDrive WebDAV 地址。", cloudDriveRssNoWebDavSourceMessage().fallback)
        assertEquals("暂不扫描", cloudDriveRssNoScanSourceOptionLabel().fallback)
        assertEquals("使用当前源", cloudDriveRssUseActiveSourceActionLabel().fallback)
        assertEquals("清除扫描源", cloudDriveRssClearScanSourceActionLabel().fallback)
        assertEquals("同步后扫描源：", cloudDriveRssPostSyncSourceLabel().fallback)
        assertEquals("启动调度", cloudDriveRssStartSchedulerActionLabel().fallback)
        assertEquals("停止调度", cloudDriveRssStopSchedulerActionLabel().fallback)
    }

    @Test
    fun `rss subscription labels are shared`() {
        assertEquals("RSS 订阅", rssSubscriptionsTitleLabel().fallback)
        assertEquals("个订阅", rssSubscriptionPageUnitLabel().fallback)
        assertEquals("订阅名称", rssSubscriptionNameFieldLabel().fallback)
        assertEquals("RSS 地址", rssSubscriptionUrlFieldLabel().fallback)
        assertEquals("标题过滤正则（可选）", rssSubscriptionFilterRegexFieldLabel().fallback)
        assertEquals("新增后启用", rssSubscriptionNewEnabledLabel(true).fallback)
        assertEquals("新增后停用", rssSubscriptionNewEnabledLabel(false).fallback)
        assertEquals("添加订阅", rssSubscriptionAddActionLabel().fallback)
        assertEquals("保存 RSS", rssSubscriptionSaveActionLabel().fallback)
        assertEquals("删除订阅", rssSubscriptionDeleteActionLabel().fallback)
        assertEquals("还没有 RSS 订阅。", rssSubscriptionEmptyMessage().fallback)
        assertEquals("暂无订阅", rssSubscriptionPreviewFallbackLabel().fallback)
        assertEquals("保存订阅后在这里显示", rssSubscriptionFormPreviewFallbackLabel().fallback)
        assertEquals("启用", rssSubscriptionStateLabel(true).fallback)
        assertEquals("停用", rssSubscriptionStateLabel(false).fallback)
        assertEquals("停用", rssSubscriptionStateActionLabel(true).fallback)
        assertEquals("启用", rssSubscriptionStateActionLabel(false).fallback)
        assertEquals("RSS", rssSubscriptionFallbackTitleLabel().fallback)
        assertEquals("上次检查 05-22 10:30", rssSubscriptionLastCheckedLabel("05-22 10:30").fallback)
        assertEquals("尚未检查", rssSubscriptionLastCheckedLabel(null).fallback)
        assertEquals("尚未检查", rssSubscriptionLastCheckedLabel("").fallback)
        assertEquals("尚未检查", rssSubscriptionLastCheckedLabel(0L).fallback)
        assertTrue(rssSubscriptionLastCheckedLabel(1_700_000_000_000L).fallback.startsWith("上次检查 "))
    }

    @Test
    fun `cloud rss pagination helpers are shared`() {
        assertEquals(6, CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE)
        assertEquals(6, CLOUD_DRIVE_DIRECTORY_PAGE_SIZE)

        assertEquals(12, cloudRssSubscriptionPageStartForIndex(index = 99, itemCount = 14))
        assertEquals(6, cloudRssSubscriptionCoercedPageStart(pageStart = 10, itemCount = 14))
        assertEquals("显示 13-14 / 14 个订阅，按上/下继续翻页。", cloudRssSubscriptionPageSummary(12, 2, 14)?.fallback)
        assertNull(cloudRssSubscriptionPageSummary(0, 4, 4))

        assertEquals(12, cloudDriveDirectoryPageStartForIndex(index = 20, itemCount = 13))
        assertEquals(6, cloudDriveDirectoryCoercedPageStart(pageStart = 9, itemCount = 13))
        assertEquals("显示 13-13 / 13 个目录，按上/下继续翻页。", cloudDriveDirectoryPageSummary(12, 1, 13)?.fallback)
        assertNull(cloudDriveDirectoryPageSummary(0, 5, 5))
    }

    @Test
    fun `cloud rss summary tiles and previews are shared`() {
        val subscription = RssSubscriptionInfo(
            id = 7L,
            name = "Bangumi Feed",
            url = "https://rss.example.test/feeds/very/long/path/season-one.xml",
            filterRegex = "S01",
            enabled = true,
        )

        val tiles = cloudRssOverviewTiles(
            endpointUrl = "http://127.0.0.1:19798/clouddrive/very/long/endpoint",
            subscriptions = listOf(subscription),
            enabled = true,
            linkedSourceLabel = "Cloud WebDAV · WebDAV",
            schedulerStatus = "调度器运行中，上次运行：提交 3 个，跳过 2 个，失败 1 个，整理 4 个。",
        )
        val pathPreview = cloudRssPathPairPreview(
            inboxPath = "/Downloads/CloudDrive2/rss/inbox/very/deep/path",
            libraryPath = "/Library/Anime/Season One/Very Long Destination",
            maxLength = 46,
        )
        val subscriptionPreview = rssSubscriptionPreview(subscription, maxLength = 42)

        assertEquals(
            listOf(cloudDriveRssTitleLabel().fallback, rssSubscriptionsTitleLabel().fallback, cloudDriveRssPostSyncScanSummaryLabel().fallback),
            tiles.map { it.label.fallback },
        )
        assertEquals(settingsCloudRssOverviewValue(true), tiles[0].value)
        assertEquals(settingsCloudRssSubscriptionsValue(1), tiles[1].value)
        assertEquals(settingsCloudRssLinkedSourceValue("Cloud WebDAV · WebDAV"), tiles[2].value)
        assertTrue(tiles[0].detail.fallback.length <= 58)
        assertTrue(tiles[1].detail.fallback.contains(rssSubscriptionStateLabel(true).fallback))
        assertTrue(tiles[1].detail.fallback.contains("Bangumi Feed"))
        assertTrue(tiles[2].detail.fallback.contains("调度器运行中"))
        assertTrue(tiles[2].detail.fallback.length <= 58)
        assertTrue(pathPreview.length <= 46)
        assertTrue(pathPreview.contains("..."))
        assertTrue(pathPreview.contains(cloudDriveRssPathPairSeparator().fallback))
        assertTrue(subscriptionPreview.length <= 42)
        assertTrue(subscriptionPreview.startsWith(rssSubscriptionStateLabel(true).fallback))
        assertTrue(subscriptionPreview.contains("..."))
    }

    @Test
    fun `scheduler status uses shared TV facing Chinese copy`() {
        assertEquals(
            "调度器待命，尚未检查。",
            CloudDriveRssSchedulerUiState().tvStatus().fallback,
        )
        assertEquals(
            "调度器运行中，上次检查失败：network down",
            CloudDriveRssSchedulerUiState(running = true, lastError = "network down").tvStatus().fallback,
        )
        assertEquals(
            "调度器待命，上次运行：提交 2 个，跳过 1 个，失败 1 个，整理 4 个。",
            CloudDriveRssSchedulerUiState(
                lastSummary = CloudDriveRssRunSummary(submitted = 2, skipped = 1, failed = 1, organized = 4),
            ).tvStatus().fallback,
        )
    }

    @Test
    fun `credential and token statuses are shared`() {
        assertEquals("Cloud/RSS 自动化设置已保存。", cloudRssConfigSavedStatus().fallback)
        assertEquals("加载或保存 Cloud/RSS 自动化设置。", cloudRssInitialStatus().fallback)
        assertEquals("CloudDrive 凭据已保存。", cloudDriveCredentialsSavedStatus().fallback)
        assertEquals("CloudDrive 凭据已清空。", cloudDriveCredentialsClearedStatus().fallback)
        assertEquals("请先填写 CloudDrive2 地址、用户名和密码。", cloudDriveLoginRequiredStatus().fallback)
        assertEquals("正在登录 CloudDrive2...", cloudDriveLoginStartedStatus().fallback)
        assertEquals("CloudDrive2 登录成功，令牌已保存。", cloudDriveLoginSucceededStatus().fallback)
        assertEquals("请先填写 CloudDrive2 地址。", cloudDriveEndpointRequiredStatus().fallback)
        assertEquals("请填写 CloudDrive2 API Token 或 Key。", cloudDriveApiTokenRequiredStatus().fallback)
        assertEquals("请先填写 CloudDrive2 地址和 API 令牌。", cloudDriveTokenRequiredStatus().fallback)
        assertEquals("请先登录并保存密码，或验证并保存 Key。", cloudDriveTokenLoginRequiredStatus().fallback)
        assertEquals("正在验证 CloudDrive2 API 令牌...", cloudDriveTokenValidationStartedStatus().fallback)
        assertEquals(
            "CloudDrive2 API 令牌已验证并保存：MiruPlay。",
            cloudDriveTokenVerifiedStatus(friendlyName = "MiruPlay", rootDir = "/Anime").fallback,
        )
        assertEquals(
            "CloudDrive2 API 令牌已验证并保存：/Anime。",
            cloudDriveTokenVerifiedStatus(friendlyName = "", rootDir = "/Anime").fallback,
        )
    }

    @Test
    fun `run scheduler source and RSS statuses are shared`() {
        val summary = CloudDriveRssRunSummary(submitted = 3, skipped = 2, failed = 1, organized = 4)

        assertEquals("正在执行 Cloud/RSS 同步...", cloudRssRunStartedStatus().fallback)
        assertEquals("同步完成：提交 3 个，跳过 2 个，失败 1 个，整理 4 个。", summary.completeStatus().fallback)
        assertEquals("启动调度前请先启用并保存 Cloud/RSS 同步。", cloudRssSchedulerDisabledStatus().fallback)
        assertEquals("Cloud/RSS 调度器已启动。", cloudRssSchedulerStartStatus(started = true).fallback)
        assertEquals("Cloud/RSS 调度器已经在运行。", cloudRssSchedulerStartStatus(started = false).fallback)
        assertEquals("Cloud/RSS 调度器已停止。", cloudRssSchedulerStoppedStatus().fallback)
        assertEquals("定时同步完成。", cloudRssScheduledSyncCompleteStatus().fallback)
        assertEquals("请先打开已保存的媒体源，再绑定 Cloud/RSS 扫描。", cloudRssScanSourceRequiredStatus().fallback)
        assertEquals("未找到已绑定的扫描源，请清除或重新绑定 Cloud/RSS 扫描源。", cloudRssScanSourceMissingStatus().fallback)
        assertEquals("已绑定同步后扫描源：Cloud WebDAV。请保存同步配置。", cloudRssLinkedScanSourceStatus("Cloud WebDAV").fallback)
        assertEquals("定时同步完成，正在重扫 Cloud WebDAV...", cloudRssRescanStartedStatus("定时同步完成。", "Cloud WebDAV").fallback)
        assertEquals("同步后扫描源已清除，请保存同步配置。", cloudRssScanSourceClearedStatus().fallback)
        assertEquals("请先填写 RSS 地址。", rssUrlRequiredStatus().fallback)
        assertEquals("RSS 订阅已保存：Anime", rssSubscriptionSavedStatus("Anime").fallback)
        assertEquals("已选择 RSS 订阅：Anime", rssSubscriptionSelectedStatus("Anime").fallback)
        assertEquals("请先选择一个 RSS 订阅。", rssSubscriptionRequiredStatus().fallback)
        assertEquals("RSS 订阅已删除。", rssSubscriptionDeletedStatus().fallback)
    }

    @Test
    fun `subscription list statuses are shared`() {
        val selected = RssSubscriptionInfo(id = 10L, name = "Season A", url = "https://rss.example.test/a.xml")
        val refreshed = selected.copy(enabled = false)

        assertEquals("尚未配置 RSS 订阅。", rssSubscriptionsLoadedStatus(0).fallback)
        assertEquals("已加载 2 个 RSS 订阅。", rssSubscriptionsLoadedStatus(2).fallback)
        assertEquals("尚未配置 RSS 订阅。", rssSubscriptionsShowingStatus(0).fallback)
        assertEquals("正在显示 2 个 RSS 订阅。", rssSubscriptionsShowingStatus(2).fallback)
        assertEquals("RSS 订阅加载失败。", rssSubscriptionsLoadFailedStatus(null).fallback)
        assertEquals("load failed", rssSubscriptionsLoadFailedStatus("load failed").fallback)
        assertEquals("RSS 订阅刷新失败。", rssSubscriptionsRefreshFailedStatus(null).fallback)
        assertEquals("refresh failed", rssSubscriptionsRefreshFailedStatus("refresh failed").fallback)
        assertEquals(refreshed, selected.retainedSelectionInRssSubscriptions(listOf(refreshed)))
        assertEquals(null, selected.retainedSelectionInRssSubscriptions(listOf(selected.copy(id = 11L))))
        assertEquals(null, null.retainedSelectionInRssSubscriptions(listOf(refreshed)))
    }

    @Test
    fun `localized Cloud RSS status text converts stable wire statuses`() {
        assertEquals("Cloud/RSS 待命。", localizedCloudRssStatusText(""))
        assertEquals("Cloud/RSS 待命。", cloudRssStatusText(""))
        assertEquals("调度器待命，尚未检查。", localizedCloudRssStatusText("Scheduler idle. No checks yet."))
        assertEquals(
            "调度器待命，上次检查没有待同步内容。",
            localizedCloudRssStatusText("Scheduler idle. Last check found no due sync."),
        )
        assertEquals("调度器运行中，尚未检查。", localizedCloudRssStatusText("Scheduler running. No checks yet."))
        assertEquals(
            "调度器运行中，上次检查没有待同步内容。",
            localizedCloudRssStatusText("Scheduler running. Last check found no due sync."),
        )
        assertEquals("Cloud/RSS 自动化设置已保存。", localizedCloudRssStatusText("Cloud/RSS automation settings saved."))
        assertEquals("加载或保存 Cloud/RSS 自动化设置。", localizedCloudRssStatusText("Load or save Cloud/RSS automation settings."))
        assertEquals("CloudDrive 凭据已保存。", localizedCloudRssStatusText("CloudDrive credentials saved."))
        assertEquals("CloudDrive 凭据已清空。", localizedCloudRssStatusText("CloudDrive credentials cleared."))
        assertEquals(
            "请先填写 CloudDrive2 地址、用户名和密码。",
            localizedCloudRssStatusText("Enter CloudDrive2 endpoint, username, and password first."),
        )
        assertEquals("正在登录 CloudDrive2...", localizedCloudRssStatusText("Logging into CloudDrive2..."))
        assertEquals("CloudDrive2 登录成功，令牌已保存。", localizedCloudRssStatusText("CloudDrive2 login succeeded; token saved."))
        assertEquals(
            "请先填写 CloudDrive2 地址和 API 令牌。",
            localizedCloudRssStatusText("Enter CloudDrive2 endpoint and API token first."),
        )
        assertEquals("正在验证 CloudDrive2 API 令牌...", localizedCloudRssStatusText("Validating CloudDrive2 API token..."))
        assertEquals("正在执行 Cloud/RSS 同步...", localizedCloudRssStatusText("Running Cloud/RSS sync..."))
        assertEquals(
            "启动调度前请先启用并保存 Cloud/RSS 同步。",
            localizedCloudRssStatusText("Enable and save Cloud/RSS sync before starting the scheduler."),
        )
        assertEquals("Cloud/RSS 调度器已启动。", localizedCloudRssStatusText("Cloud/RSS scheduler started."))
        assertEquals("Cloud/RSS 调度器已经在运行。", localizedCloudRssStatusText("Cloud/RSS scheduler is already running."))
        assertEquals("Cloud/RSS 调度器已停止。", localizedCloudRssStatusText("Cloud/RSS scheduler stopped."))
        assertEquals("定时同步完成。", localizedCloudRssStatusText("Scheduled sync complete."))
        assertEquals(
            "请先打开已保存的媒体源，再绑定 Cloud/RSS 扫描。",
            localizedCloudRssStatusText("Open a saved media source before linking Cloud/RSS scanning."),
        )
        assertEquals(
            "未找到已绑定的扫描源，请清除或重新绑定 Cloud/RSS 扫描源。",
            localizedCloudRssStatusText("Linked scan source was not found. Clear or relink the Cloud/RSS scan source."),
        )
        assertEquals(
            "同步后扫描源已清除，请保存同步配置。",
            localizedCloudRssStatusText("Cloud/RSS post-sync scan source cleared. Save sync config to persist it."),
        )
        assertEquals("请先填写 RSS 地址。", localizedCloudRssStatusText("Enter an RSS URL first."))
        assertEquals("尚未配置 RSS 订阅。", localizedCloudRssStatusText("No RSS subscriptions configured."))
        assertEquals("RSS 订阅加载失败。", localizedCloudRssStatusText("Failed to load RSS subscriptions."))
        assertEquals("RSS 订阅刷新失败。", localizedCloudRssStatusText("Failed to refresh RSS subscriptions."))
        assertEquals("请先选择一个 RSS 订阅。", localizedCloudRssStatusText("Select an RSS subscription first."))
        assertEquals("RSS 订阅已删除。", localizedCloudRssStatusText("RSS subscription deleted."))
        assertEquals("调度器待命，尚未检查。", localizedCloudRssStatusText("调度器待命，尚未检查。"))
        assertEquals(null, localizedCloudRssStatusText("custom status"))
        assertEquals("custom status", cloudRssStatusText("custom status"))
    }

    @Test
    fun `localized Cloud RSS status text converts dynamic wire statuses`() {
        assertEquals(
            "调度器运行中，上次检查失败：network down",
            localizedCloudRssStatusText("Scheduler running. Last check failed: network down"),
        )
        assertEquals(
            "调度器待命，上次运行：提交 2 个，跳过 1 个，失败 1 个，整理 4 个。",
            localizedCloudRssStatusText("Scheduler idle. Last run: 2 submitted, 1 skipped, 1 failed, 4 organized."),
        )
        assertEquals(
            "同步完成：提交 3 个，跳过 2 个，失败 1 个，整理 4 个。",
            localizedCloudRssStatusText("Sync complete: 3 submitted, 2 skipped, 1 failed, 4 organized."),
        )
        assertEquals("尚未配置 RSS 订阅。", localizedCloudRssStatusText("Loaded 0 RSS subscription(s)."))
        assertEquals("已加载 2 个 RSS 订阅。", localizedCloudRssStatusText("Loaded 2 RSS subscription(s)."))
        assertEquals("正在显示 2 个 RSS 订阅。", localizedCloudRssStatusText("Showing 2 RSS subscription(s)."))
        assertEquals(
            "CloudDrive2 API 令牌已验证并保存：MiruPlay。",
            localizedCloudRssStatusText("CloudDrive2 API token verified and saved: MiruPlay."),
        )
        assertEquals(
            "已绑定同步后扫描源：Cloud WebDAV。请保存同步配置。",
            localizedCloudRssStatusText("Linked Cloud/RSS post-sync scan source: Cloud WebDAV. Save sync config to persist it."),
        )
        assertEquals(
            "定时同步完成，正在重扫 Cloud WebDAV...",
            localizedCloudRssStatusText("Scheduled sync complete. Rescanning Cloud WebDAV..."),
        )
        assertEquals("RSS 订阅已保存：Anime", localizedCloudRssStatusText("RSS subscription saved: Anime"))
        assertEquals("已选择 RSS 订阅：Anime", localizedCloudRssStatusText("Selected RSS subscription: Anime"))
    }
}
