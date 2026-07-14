package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriveRssStatusConventionsTest {
    @Test
    fun `cloud rss form labels are shared`() {
        assertEquals("CloudDrive2", cloudDriveRssTitleLabel())
        assertEquals(
            "RSS 会提交到 CloudDrive2 离线下载目录，整理后触发所选 WebDAV 媒体源扫描。",
            cloudDriveRssDescriptionLabel(),
        )
        assertEquals("定时已开", cloudDriveRssScheduledChipLabel(true))
        assertEquals("定时关闭", cloudDriveRssScheduledChipLabel(false))
        assertEquals("Key 已保存", cloudDriveRssCredentialsBadgeLabel(true))
        assertEquals("密码已保存", cloudDriveRssCredentialsBadgeLabel(tokenConfigured = false, passwordConfigured = true))
        assertEquals(
            "Key 与密码已保存",
            cloudDriveRssCredentialsBadgeLabel(tokenConfigured = true, passwordConfigured = true),
        )
        assertEquals("未授权", cloudDriveRssCredentialsBadgeLabel(false))
        assertEquals("用户名密码已保存", cloudDriveRssPasswordCredentialLabel(true))
        assertEquals("用户名密码未保存", cloudDriveRssPasswordCredentialLabel(false))
        assertEquals("Key 已保存", cloudDriveRssApiTokenCredentialLabel(true))
        assertEquals("Key 未保存", cloudDriveRssApiTokenCredentialLabel(false))
        assertEquals("CloudDrive2 地址", cloudDriveRssEndpointFieldLabel())
        assertEquals("CloudDrive2 用户名", cloudDriveRssUsernameFieldLabel())
        assertEquals("密码（登录后保存）", cloudDriveRssPasswordFieldLabel())
        assertEquals("API Token / Key（可替代密码登录）", cloudDriveRssApiTokenFieldLabel())
        assertEquals("下载目录 A", cloudDriveRssInboxPathFieldLabel())
        assertEquals("整理目录 B", cloudDriveRssLibraryPathFieldLabel())
        assertEquals("定时间隔（分钟）", cloudDriveRssIntervalMinutesFieldLabel())
        assertEquals("RSS 代理已开", cloudDriveRssProxyToggleLabel(true))
        assertEquals("RSS 代理关闭", cloudDriveRssProxyToggleLabel(false))
        assertEquals("启用", cloudDriveRssEnabledToggleLabel())
        assertEquals("代理地址", cloudDriveRssProxyHostFieldLabel())
        assertEquals("代理端口", cloudDriveRssProxyPortFieldLabel())
    }

    @Test
    fun `cloud rss actions scan source and directory labels are shared`() {
        assertEquals("保存", cloudDriveRssSaveConfigActionLabel())
        assertEquals("保存凭据", cloudDriveRssSaveCredentialsActionLabel())
        assertEquals("清空凭据", cloudDriveRssClearCredentialsActionLabel())
        assertEquals("登录并保存密码", cloudDriveRssLoginActionLabel())
        assertEquals("处理中", cloudDriveRssLoginActionLabel(busy = true))
        assertEquals("验证并保存 Key", cloudDriveRssSaveApiTokenActionLabel())
        assertEquals("验证令牌", cloudDriveRssVerifyApiTokenActionLabel())
        assertEquals("立即执行", cloudDriveRssRunNowActionLabel())
        assertEquals("执行中", cloudDriveRssRunNowActionLabel(busy = true))
        assertEquals("保存并立即执行", cloudDriveRssSaveAndRunNowActionLabel())
        assertEquals("执行中", cloudDriveRssSaveAndRunNowActionLabel(busy = true))
        assertEquals("CloudDrive2 Key 已保存在加密存储中。", cloudDriveRssTokenStatusMessage(true))
        assertEquals(
            "CloudDrive2 密码已保存，执行时会自动登录刷新 Key。",
            cloudDriveRssTokenStatusMessage(tokenConfigured = false, passwordConfigured = true),
        )
        assertEquals(
            "CloudDrive2 Key 和用户名密码已保存在加密存储中。",
            cloudDriveRssTokenStatusMessage(tokenConfigured = true, passwordConfigured = true),
        )
        assertEquals("请先登录并保存密码，或验证并保存 Key。", cloudDriveRssTokenStatusMessage(false))
        assertEquals("同步路径", cloudDriveRssSyncPathTitleLabel())
        assertEquals("运行状态", cloudDriveRssRuntimeTitleLabel())
        assertEquals("目录", cloudDriveRssDirectoryBadgeLabel())
        assertEquals("路径", cloudDriveRssPathBadgeLabel())
        assertEquals("运行", cloudDriveRssRunBadgeLabel())
        assertEquals("启用", cloudDriveRssEnabledBadgeLabel(true))
        assertEquals("停用", cloudDriveRssEnabledBadgeLabel(false))
        assertEquals("已启用", cloudDriveRssCloudDriveEnabledValue(true))
        assertEquals("未启用", cloudDriveRssCloudDriveEnabledValue(false))
        assertEquals("填写 CloudDrive2 地址", cloudDriveRssEndpointFallbackLabel())
        assertEquals("未配置端点", cloudDriveRssUnconfiguredEndpointLabel())
        assertEquals("调度器待命", cloudDriveRssSchedulerIdleLabel())
        assertEquals("同步后扫描", cloudDriveRssPostSyncScanSummaryLabel())
        assertEquals("选择目录", cloudDriveRssChooseDirectoryActionLabel())
        assertEquals("使用当前目录", cloudDriveRssUseCurrentDirectoryActionLabel())
        assertEquals("返回上级", cloudDriveRssParentDirectoryActionLabel())
        assertEquals("关闭", cloudDriveRssCloseActionLabel())
        assertEquals("个目录", cloudDriveRssDirectoryPageUnitLabel())
        assertEquals("正在读取 CloudDrive2 目录...", cloudDriveRssLoadingDirectoriesMessage())
        assertEquals("当前目录没有可进入的子目录。", cloudDriveRssEmptyDirectoryMessage())
        assertEquals("选择下载目录 A", cloudDriveRssInboxDirectoryPickerTitle())
        assertEquals("选择整理目录 B", cloudDriveRssLibraryDirectoryPickerTitle())
        assertEquals("下载目录 A", cloudDriveRssInboxDirectorySelectedLabel())
        assertEquals("整理目录 B", cloudDriveRssLibraryDirectorySelectedLabel())
        assertEquals("已选择下载目录 A：/Anime", cloudDriveRssDirectorySelectedStatus("下载目录 A", "/Anime"))
        assertEquals("正在浏览 CloudDrive2 目录：/Anime", cloudDriveRssDirectoryBrowsingStatus("/Anime"))
        assertEquals(" 到 ", cloudDriveRssPathPairSeparator())
        assertEquals("入库后扫描的 WebDAV 媒体源", cloudDriveRssScanSourceTitleLabel())
        assertEquals("还没有 WebDAV 媒体源，请先在媒体源里添加 CloudDrive WebDAV 地址。", cloudDriveRssNoWebDavSourceMessage())
        assertEquals("暂不扫描", cloudDriveRssNoScanSourceOptionLabel())
        assertEquals("使用当前源", cloudDriveRssUseActiveSourceActionLabel())
        assertEquals("清除扫描源", cloudDriveRssClearScanSourceActionLabel())
        assertEquals("同步后扫描源：", cloudDriveRssPostSyncSourceLabel())
        assertEquals("启动调度", cloudDriveRssStartSchedulerActionLabel())
        assertEquals("停止调度", cloudDriveRssStopSchedulerActionLabel())
    }

    @Test
    fun `rss subscription labels are shared`() {
        assertEquals("RSS 订阅", rssSubscriptionsTitleLabel())
        assertEquals("个订阅", rssSubscriptionPageUnitLabel())
        assertEquals("订阅名称", rssSubscriptionNameFieldLabel())
        assertEquals("RSS 地址", rssSubscriptionUrlFieldLabel())
        assertEquals("标题过滤正则（可选）", rssSubscriptionFilterRegexFieldLabel())
        assertEquals("新增后启用", rssSubscriptionNewEnabledLabel(true))
        assertEquals("新增后停用", rssSubscriptionNewEnabledLabel(false))
        assertEquals("添加订阅", rssSubscriptionAddActionLabel())
        assertEquals("保存 RSS", rssSubscriptionSaveActionLabel())
        assertEquals("删除订阅", rssSubscriptionDeleteActionLabel())
        assertEquals("还没有 RSS 订阅。", rssSubscriptionEmptyMessage())
        assertEquals("暂无订阅", rssSubscriptionPreviewFallbackLabel())
        assertEquals("保存订阅后在这里显示", rssSubscriptionFormPreviewFallbackLabel())
        assertEquals("启用", rssSubscriptionStateLabel(true))
        assertEquals("停用", rssSubscriptionStateLabel(false))
        assertEquals("停用", rssSubscriptionStateActionLabel(true))
        assertEquals("启用", rssSubscriptionStateActionLabel(false))
        assertEquals("RSS", rssSubscriptionFallbackTitleLabel())
        assertEquals("上次检查 05-22 10:30", rssSubscriptionLastCheckedLabel("05-22 10:30"))
        assertEquals("尚未检查", rssSubscriptionLastCheckedLabel(null))
        assertEquals("尚未检查", rssSubscriptionLastCheckedLabel(""))
        assertEquals("尚未检查", rssSubscriptionLastCheckedLabel(0L))
        assertTrue(rssSubscriptionLastCheckedLabel(1_700_000_000_000L).startsWith("上次检查 "))
    }

    @Test
    fun `cloud rss pagination helpers are shared`() {
        assertEquals(6, CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE)
        assertEquals(6, CLOUD_DRIVE_DIRECTORY_PAGE_SIZE)

        assertEquals(12, cloudRssSubscriptionPageStartForIndex(index = 99, itemCount = 14))
        assertEquals(6, cloudRssSubscriptionCoercedPageStart(pageStart = 10, itemCount = 14))
        assertEquals("显示 13-14 / 14 个订阅，按上/下继续翻页。", cloudRssSubscriptionPageSummary(12, 2, 14))
        assertEquals(null, cloudRssSubscriptionPageSummary(0, 4, 4))

        assertEquals(12, cloudDriveDirectoryPageStartForIndex(index = 20, itemCount = 13))
        assertEquals(6, cloudDriveDirectoryCoercedPageStart(pageStart = 9, itemCount = 13))
        assertEquals("显示 13-13 / 13 个目录，按上/下继续翻页。", cloudDriveDirectoryPageSummary(12, 1, 13))
        assertEquals(null, cloudDriveDirectoryPageSummary(0, 5, 5))
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
            listOf(cloudDriveRssTitleLabel(), rssSubscriptionsTitleLabel(), cloudDriveRssPostSyncScanSummaryLabel()),
            tiles.map { it.label },
        )
        assertEquals(settingsCloudRssOverviewValue(true), tiles[0].value)
        assertEquals(settingsCloudRssSubscriptionsValue(1), tiles[1].value)
        assertEquals(settingsCloudRssLinkedSourceValue("Cloud WebDAV · WebDAV"), tiles[2].value)
        assertTrue(tiles[0].detail.length <= 58)
        assertTrue(tiles[1].detail.contains(rssSubscriptionStateLabel(true)))
        assertTrue(tiles[1].detail.contains("Bangumi Feed"))
        assertTrue(tiles[2].detail.contains("调度器运行中"))
        assertTrue(tiles[2].detail.length <= 58)
        assertTrue(pathPreview.length <= 46)
        assertTrue(pathPreview.contains("..."))
        assertTrue(pathPreview.contains(cloudDriveRssPathPairSeparator()))
        assertTrue(subscriptionPreview.length <= 42)
        assertTrue(subscriptionPreview.startsWith(rssSubscriptionStateLabel(true)))
        assertTrue(subscriptionPreview.contains("..."))
    }

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
        assertEquals("请先登录并保存密码，或验证并保存 Key。", cloudDriveTokenLoginRequiredStatus())
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
        val selected = RssSubscriptionInfo(id = 10L, name = "Season A", url = "https://rss.example.test/a.xml")
        val refreshed = selected.copy(enabled = false)

        assertEquals("尚未配置 RSS 订阅。", rssSubscriptionsLoadedStatus(0))
        assertEquals("已加载 2 个 RSS 订阅。", rssSubscriptionsLoadedStatus(2))
        assertEquals("尚未配置 RSS 订阅。", rssSubscriptionsShowingStatus(0))
        assertEquals("正在显示 2 个 RSS 订阅。", rssSubscriptionsShowingStatus(2))
        assertEquals("RSS 订阅加载失败。", rssSubscriptionsLoadFailedStatus(null))
        assertEquals("load failed", rssSubscriptionsLoadFailedStatus("load failed"))
        assertEquals("RSS 订阅刷新失败。", rssSubscriptionsRefreshFailedStatus(null))
        assertEquals("refresh failed", rssSubscriptionsRefreshFailedStatus("refresh failed"))
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
