package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.model.cloudDriveDirectoryDisplayPath
import com.miruplay.tv.model.cloudDriveDirectoryParentPath
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MiruPlaySettingsSection
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.desktopSettingsSectionOrder
import com.miruplay.tv.model.libraryScanCompleteStatus
import com.miruplay.tv.model.cloudRssOverviewTiles
import com.miruplay.tv.model.cloudRssPathPairPreview
import com.miruplay.tv.model.cloudRssSubscriptionCoercedPageStart
import com.miruplay.tv.model.cloudRssSubscriptionPageStartForIndex
import com.miruplay.tv.model.cloudRssSubscriptionPageSummary
import com.miruplay.tv.model.cloudDriveDirectoryCoercedPageStart
import com.miruplay.tv.model.localizedCloudRssStatusText
import com.miruplay.tv.model.localizedLibraryScanCompleteStatus
import com.miruplay.tv.model.cloudDriveRssApiTokenFieldLabel
import com.miruplay.tv.model.cloudDriveDirectoryPageStartForIndex
import com.miruplay.tv.model.cloudDriveDirectoryPageSummary
import com.miruplay.tv.model.cloudDriveRssDirectoryPageUnitLabel
import com.miruplay.tv.model.cloudDriveRssEndpointFieldLabel
import com.miruplay.tv.model.cloudDriveRssEnabledToggleLabel
import com.miruplay.tv.model.cloudDriveRssInboxPathFieldLabel
import com.miruplay.tv.model.cloudDriveRssLibraryPathFieldLabel
import com.miruplay.tv.model.cloudDriveRssPasswordFieldLabel
import com.miruplay.tv.model.cloudDriveRssPathPairSeparator
import com.miruplay.tv.model.cloudDriveRssPostSyncScanSummaryLabel
import com.miruplay.tv.model.cloudDriveRssSaveAndRunNowActionLabel
import com.miruplay.tv.model.cloudDriveRssSaveConfigActionLabel
import com.miruplay.tv.model.cloudDriveRssTitleLabel
import com.miruplay.tv.model.cloudRssStatusText
import com.miruplay.tv.model.cloudDriveRssUsernameFieldLabel
import com.miruplay.tv.model.cloudDriveRssUiLabels
import com.miruplay.tv.model.metadataBangumiTokenSettingsStatus
import com.miruplay.tv.model.metadataBangumiTokenTileDetail
import com.miruplay.tv.model.metadataBangumiTokenTileLabel
import com.miruplay.tv.model.metadataMatchedSummaryLabel
import com.miruplay.tv.model.normalizeCloudDriveDirectoryPath
import com.miruplay.tv.model.rssSubscriptionFilterRegexFieldLabel
import com.miruplay.tv.model.rssSubscriptionNameFieldLabel
import com.miruplay.tv.model.rssSubscriptionPageUnitLabel
import com.miruplay.tv.model.rssSubscriptionStateLabel
import com.miruplay.tv.model.rssSubscriptionUrlFieldLabel
import com.miruplay.tv.model.rssSubscriptionsTitleLabel
import com.miruplay.tv.model.settingsActiveSourceLabel
import com.miruplay.tv.model.settingsActiveSourceTileLabel
import com.miruplay.tv.model.settingsCloudRssLinkedSourceValue
import com.miruplay.tv.model.settingsCloudRssOverviewValue
import com.miruplay.tv.model.settingsCloudRssSubscriptionsValue
import com.miruplay.tv.model.settingsCountValue
import com.miruplay.tv.model.settingsDesktopLogUploadStatusMessage
import com.miruplay.tv.model.settingsIndexedCountValue
import com.miruplay.tv.model.settingsLinkedSourceLabel
import com.miruplay.tv.model.settingsLogUploadStatusMessage
import com.miruplay.tv.model.settingsLogUploadPendingStatus
import com.miruplay.tv.model.settingsMissingSourceValue
import com.miruplay.tv.model.settingsNoSourceSelectedValue
import com.miruplay.tv.model.settingsPlaybackPageDetail
import com.miruplay.tv.model.settingsPlaybackStatusMessage
import com.miruplay.tv.model.settingsPosterWallIndexTileLabel
import com.miruplay.tv.model.settingsRecordCountValue
import com.miruplay.tv.model.settingsLogUploadResultStatus
import com.miruplay.tv.model.settingsLogUploadTokenConfiguredStatus
import com.miruplay.tv.model.settingsLogUploadUploadStateStatus
import com.miruplay.tv.model.settingsSavedStateValue
import com.miruplay.tv.model.settingsWebUiDesktopValue
import com.miruplay.tv.model.settingsWebUiNativeControlTileLabel
import com.miruplay.tv.model.settingsWebUiTileLabel
import com.miruplay.tv.model.webUiSettingsTiles
import com.miruplay.tv.model.metadataSettingsTiles
import com.miruplay.tv.model.playbackSettingsTiles
import com.miruplay.tv.model.scopedCloudDriveDirectoryPath
import com.miruplay.tv.model.scanSettingsTiles
import com.miruplay.tv.model.settingsSourceTileLabel
import com.miruplay.tv.model.sourceSettingsTiles
import com.miruplay.tv.model.stepDesktopSettingsSection
import com.miruplay.tv.sync.rss.cloudDriveDirectoryEntries
import com.miruplay.tv.model.rssSubscriptionPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSettingsPanelTest {
    @Test
    fun `source settings tiles summarize source types active source and index`() {
        val activeSource = MediaSourceInfoConventions.local(name = "Local Anime", rootPath = "D:/Anime")
        val tiles = sourceSettingsTiles(
            sources = listOf(
            activeSource,
            MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime"),
            MediaSourceInfoConventions.smb(url = "smb://nas.local/anime"),
        ),
            activeSourceLabel = settingsActiveSourceLabel(activeSource),
            indexedItemCount = 42,
        )

        assertEquals(listOf(settingsSourceTileLabel(), settingsActiveSourceTileLabel(), settingsPosterWallIndexTileLabel()), tiles.map { it.label })
        assertEquals(settingsCountValue(3), tiles[0].value)
        assertTrue(tiles[0].detail.contains("本地 1"))
        assertTrue(tiles[0].detail.contains("WebDAV 1"))
        assertTrue(tiles[0].detail.contains("SMB 1"))
        assertEquals("Local Anime · 本地", tiles[1].value)
        assertEquals(settingsRecordCountValue(42), tiles[2].value)
    }

    @Test
    fun `desktop source status labels use TV facing type labels`() {
        val linkedSource = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/anime",
        ).copy(id = 42L, name = "Cloud WebDAV")

        assertEquals(settingsNoSourceSelectedValue(), settingsActiveSourceLabel(null))
        assertEquals("Cloud WebDAV · WebDAV", settingsLinkedSourceLabel(listOf(linkedSource), 42L))
        assertEquals(settingsMissingSourceValue(99L), settingsLinkedSourceLabel(listOf(linkedSource), 99L))
    }

    @Test
    fun `playback settings tiles expose RIFE recents and selected media`() {
        val tiles = playbackSettingsTiles(
            playbackSummary = "RIFE DIRECTML",
            recentCount = 5,
            selectedMediaTitle = "Fixture Alpha",
        )

        assertEquals("RIFE DIRECTML", tiles[0].value)
        assertEquals(settingsPlaybackPageDetail(), tiles[0].detail)
        assertEquals(settingsRecordCountValue(5), tiles[1].value)
        assertEquals("Fixture Alpha", tiles[2].value)
    }

    @Test
    fun `shared settings summary statuses use TV facing page names`() {
        assertEquals(
            "mpv 播放设置保留在播放页，RIFE/字幕/起播秒数仍可直接调整。",
            settingsPlaybackStatusMessage(),
        )
        assertEquals(
            "Bangumi 搜索、批量预览、应用和撤销保留在详情页。 保存 Token 后可同步观看进度。",
            metadataBangumiTokenSettingsStatus(configured = false),
        )
        assertEquals(
            "Bangumi 搜索、批量预览、应用和撤销保留在详情页。 Bangumi Token 已保存。",
            metadataBangumiTokenSettingsStatus(configured = true),
        )
    }

    @Test
    fun `scan and metadata settings tiles keep TV settings content concrete`() {
        val metadataSummary = metadataMatchedSummaryLabel("Fixture Beta")
        val scanTiles = scanSettingsTiles(
            indexedItemCount = 11,
            linkedSourceLabel = "SMB Share · SMB",
            libraryStatus = libraryScanCompleteStatus(11, 4),
        )
        val metadataTiles = metadataSettingsTiles(
            selectedMediaTitle = "Fixture Beta",
            metadataSummary = metadataSummary,
            indexedItemCount = 11,
            bangumiTokenConfigured = true,
        )

        assertEquals(settingsRecordCountValue(11), scanTiles[0].value)
        assertEquals("SMB Share · SMB", scanTiles[1].value)
        assertEquals(localizedLibraryScanCompleteStatus(11, 4), scanTiles[2].value)
        assertEquals("Fixture Beta", metadataTiles[0].value)
        assertEquals(metadataSummary, metadataTiles[1].value)
        assertEquals(settingsIndexedCountValue(11), metadataTiles[2].value)
        assertEquals(metadataBangumiTokenTileLabel(), metadataTiles[3].label)
        assertEquals(settingsSavedStateValue(true), metadataTiles[3].value)
        assertEquals(metadataBangumiTokenTileDetail(), metadataTiles[3].detail)
    }

    @Test
    fun `cloud rss overview tiles summarize endpoint subscriptions and scheduler`() {
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
    }

    @Test
    fun `desktop web ui settings tiles expose Windows access summary`() {
        val tiles = webUiSettingsTiles(platformValue = settingsWebUiDesktopValue())

        assertEquals(
            listOf(settingsWebUiTileLabel(), settingsWebUiNativeControlTileLabel(), "远程自动化"),
            tiles.map { it.label },
        )
        assertEquals(settingsWebUiDesktopValue(), tiles[0].value)
        assertEquals("原生窗口", tiles[1].value)
        assertEquals("Cloud/RSS", tiles[2].value)
    }

    @Test
    fun `desktop log upload status message keeps shared tv copy and runtime summary`() {
        assertEquals("待上报 6 条", settingsLogUploadPendingStatus(6))
        assertEquals("上报中", settingsLogUploadUploadStateStatus(true))
        assertEquals("待命", settingsLogUploadUploadStateStatus(false))
        assertEquals("Token 已保存", settingsLogUploadTokenConfiguredStatus(true))
        assertEquals("未保存 Token", settingsLogUploadTokenConfiguredStatus(false))
        assertEquals("暂无上报结果", settingsLogUploadResultStatus(null))
        val androidTvStatus = settingsLogUploadStatusMessage(
            pendingCount = 6,
            isUploading = true,
            tokenConfigured = true,
            lastUploadAt = 0L,
            lastUploadStatus = "HTTP 200",
        )
        val desktopStatus = settingsDesktopLogUploadStatusMessage(
            pendingCount = 6,
            isUploading = true,
            tokenConfigured = true,
            lastUploadAt = 0L,
            lastUploadStatus = "HTTP 200",
        )
        assertEquals(
            "可在当前页面或 Web 控制端配置 OpenObserve JSON；本地日志会按同一配置写入上报队列。 · 待上报 6 条 · 上报中 · Token 已保存 · 尚未上报 · HTTP 200",
            desktopStatus,
        )
        assertEquals(
            androidTvStatus.substringAfter(" · "),
            desktopStatus.substringAfter(" · "),
        )
    }

    @Test
    fun `desktop cloud rss labels use shared TV copy`() {
        val labels = cloudDriveRssUiLabels()

        assertEquals(cloudDriveRssEndpointFieldLabel(), labels.endpoint)
        assertEquals(cloudDriveRssUsernameFieldLabel(), labels.username)
        assertEquals(cloudDriveRssApiTokenFieldLabel(), labels.apiToken)
        assertEquals(cloudDriveRssPasswordFieldLabel(), labels.password)
        assertEquals(cloudDriveRssInboxPathFieldLabel(), labels.inboxPath)
        assertEquals(cloudDriveRssLibraryPathFieldLabel(), labels.libraryPath)
        assertEquals(cloudDriveRssEnabledToggleLabel(), labels.enabledToggle)
        assertEquals(cloudDriveRssSaveConfigActionLabel(), labels.saveSyncConfig)
        assertEquals(cloudDriveRssSaveAndRunNowActionLabel(), labels.runSyncNow)
        assertEquals(rssSubscriptionsTitleLabel(), labels.rssSubscriptions)
        assertEquals(rssSubscriptionNameFieldLabel(), labels.subscriptionName)
        assertEquals(rssSubscriptionUrlFieldLabel(), labels.subscriptionUrl)
        assertEquals(rssSubscriptionFilterRegexFieldLabel(), labels.filterRegex)
    }

    @Test
    fun `cloud rss card previews compact long paths and subscriptions`() {
        val pathPreview = cloudRssPathPairPreview(
            inboxPath = "/Downloads/CloudDrive2/rss/inbox/very/deep/path",
            libraryPath = "/Library/Anime/Season One/Very Long Destination",
            maxLength = 46,
        )
        val subscriptionPreview = rssSubscriptionPreview(
            RssSubscriptionInfo(
                name = "Disabled Feed",
                url = "https://rss.example.test/feeds/disabled.xml",
                enabled = false,
            ),
            maxLength = 42,
        )

        assertTrue(pathPreview.length <= 46)
        assertTrue(pathPreview.contains("..."))
        assertTrue(pathPreview.contains(cloudDriveRssPathPairSeparator()))
        assertTrue(subscriptionPreview.length <= 42)
        assertTrue(subscriptionPreview.startsWith(rssSubscriptionStateLabel(false)))
        assertTrue(subscriptionPreview.contains("..."))
    }

    @Test
    fun `cloud rss status text localizes scheduler credentials and subscriptions`() {
        assertEquals(
            localizedCloudRssStatusText("Scheduler idle. No checks yet."),
            cloudRssStatusText("Scheduler idle. No checks yet."),
        )
        assertEquals(
            localizedCloudRssStatusText("调度器待命，尚未检查。"),
            cloudRssStatusText("调度器待命，尚未检查。"),
        )
        assertEquals(
            localizedCloudRssStatusText("Sync complete: 3 submitted, 2 skipped, 1 failed, 4 organized."),
            cloudRssStatusText("Sync complete: 3 submitted, 2 skipped, 1 failed, 4 organized."),
        )
        assertEquals(
            localizedCloudRssStatusText("CloudDrive credentials saved."),
            cloudRssStatusText("CloudDrive credentials saved."),
        )
        assertEquals(
            localizedCloudRssStatusText("RSS subscription saved: Anime"),
            cloudRssStatusText("RSS subscription saved: Anime"),
        )
        assertEquals("custom status", cloudRssStatusText("custom status"))
    }

    @Test
    fun `rss subscription directional keys move between saved subscriptions`() {
        val subscriptions = listOf(
            RssSubscriptionInfo(id = 10L, name = "Season A", url = "https://rss.example.test/a.xml"),
            RssSubscriptionInfo(id = 11L, name = "Season B", url = "https://rss.example.test/b.xml"),
            RssSubscriptionInfo(id = 12L, name = "Season C", url = "https://rss.example.test/c.xml"),
        )

        assertEquals(11L, subscriptions.rssSubscriptionNavigationTarget(10L, Key.DirectionDown)?.id)
        assertEquals(10L, subscriptions.rssSubscriptionNavigationTarget(11L, Key.DirectionUp)?.id)
        assertEquals(10L, subscriptions.rssSubscriptionNavigationTarget(null, Key.DirectionDown)?.id)
        assertEquals(12L, subscriptions.rssSubscriptionNavigationTarget(null, Key.DirectionUp)?.id)
        assertNull(subscriptions.rssSubscriptionNavigationTarget(12L, Key.DirectionDown))
        assertNull(subscriptions.rssSubscriptionNavigationTarget(10L, Key.DirectionUp))
        assertNull(subscriptions.rssSubscriptionNavigationTarget(10L, Key.DirectionRight))
    }

    @Test
    fun `rss subscription navigation also accepts shared direction intents`() {
        val subscriptions = listOf(
            RssSubscriptionInfo(id = 10L, name = "Season A", url = "https://rss.example.test/a.xml"),
            RssSubscriptionInfo(id = 11L, name = "Season B", url = "https://rss.example.test/b.xml"),
            RssSubscriptionInfo(id = 12L, name = "Season C", url = "https://rss.example.test/c.xml"),
        )

        assertEquals(11L, subscriptions.rssSubscriptionNavigationTarget(10L, MiruPlayInputIntent.DirectionDown)?.id)
        assertEquals(10L, subscriptions.rssSubscriptionNavigationTarget(11L, MiruPlayInputIntent.DirectionUp)?.id)
        assertEquals(10L, subscriptions.rssSubscriptionNavigationTarget(null, MiruPlayInputIntent.DirectionDown)?.id)
        assertEquals(12L, subscriptions.rssSubscriptionNavigationTarget(null, MiruPlayInputIntent.DirectionUp)?.id)
        assertNull(subscriptions.rssSubscriptionNavigationTarget(12L, MiruPlayInputIntent.DirectionDown))
        assertNull(subscriptions.rssSubscriptionNavigationTarget(10L, MiruPlayInputIntent.DirectionRight))
    }

    @Test
    fun `cloud rss action grid moves like TV remote button rows`() {
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.ClearCredentials),
            cloudRssActionFocusTarget(CloudRssAction.SaveCredentials, Key.DirectionRight, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.LoginCloudDrive),
            cloudRssActionFocusTarget(CloudRssAction.SaveCredentials, Key.DirectionDown, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveCredentials),
            cloudRssActionFocusTarget(CloudRssAction.LoginCloudDrive, Key.DirectionUp, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.RunSyncNow),
            cloudRssActionFocusTarget(CloudRssAction.ClearScanSource, Key.DirectionDown, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.VerifyApiToken),
            cloudRssActionFocusTarget(CloudRssAction.LoginCloudDrive, Key.DirectionDown, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SetOrganizedMode),
            cloudRssActionFocusTarget(CloudRssAction.VerifyApiToken, Key.DirectionDown, subscriptionCount = 2),
        )
        assertNull(cloudRssActionFocusTarget(CloudRssAction.SaveCredentials, Key.DirectionLeft, subscriptionCount = 2))
    }

    @Test
    fun `cloud rss action grid also accepts shared direction intents`() {
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.ClearCredentials),
            cloudRssActionFocusTarget(
                CloudRssAction.SaveCredentials,
                MiruPlayInputIntent.DirectionRight,
                subscriptionCount = 2,
            ),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.LoginCloudDrive),
            cloudRssActionFocusTarget(
                CloudRssAction.SaveCredentials,
                MiruPlayInputIntent.DirectionDown,
                subscriptionCount = 2,
            ),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveCredentials),
            cloudRssActionFocusTarget(
                CloudRssAction.LoginCloudDrive,
                MiruPlayInputIntent.DirectionUp,
                subscriptionCount = 2,
            ),
        )
        assertNull(
            cloudRssActionFocusTarget(
                CloudRssAction.SaveCredentials,
                MiruPlayInputIntent.Activate,
                subscriptionCount = 2,
            ),
        )
    }

    @Test
    fun `cloud rss toggles bridge into action rows`() {
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.ProxyEnabled),
            cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, Key.DirectionRight),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.SyncEnabled),
            cloudRssToggleFocusTarget(CloudRssToggle.ProxyEnabled, Key.DirectionLeft),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.UseActiveSource),
            cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.ClearScanSource),
            cloudRssToggleFocusTarget(CloudRssToggle.ProxyEnabled, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveRss),
            cloudRssToggleFocusTarget(CloudRssToggle.RssEnabled, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.SyncEnabled),
            cloudRssActionFocusTarget(CloudRssAction.UseActiveSource, Key.DirectionUp, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.RssEnabled),
            cloudRssActionFocusTarget(CloudRssAction.DeleteRss, Key.DirectionUp, subscriptionCount = 2),
        )
        assertNull(cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, Key.DirectionLeft))
        assertNull(cloudRssToggleFocusTarget(CloudRssToggle.RssEnabled, Key.DirectionRight))
    }

    @Test
    fun `cloud rss toggles also accept shared direction intents`() {
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.ProxyEnabled),
            cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, MiruPlayInputIntent.DirectionRight),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.IntervalMinutes),
            cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.UseActiveSource),
            cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, MiruPlayInputIntent.DirectionDown),
        )
        assertNull(cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, MiruPlayInputIntent.DirectionLeft))
        assertNull(cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, MiruPlayInputIntent.Activate))
    }

    @Test
    fun `cloud rss sync path fields bridge into toggles`() {
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.LibraryPath),
            cloudRssFieldFocusTarget(CloudRssField.InboxPath, Key.DirectionRight),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.PickInboxPath),
            cloudRssFieldFocusTarget(CloudRssField.InboxPath, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.PickLibraryPath),
            cloudRssFieldFocusTarget(CloudRssField.LibraryPath, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.InboxPath),
            cloudRssActionFocusTarget(CloudRssAction.PickInboxPath, Key.DirectionUp, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.LibraryPath),
            cloudRssActionFocusTarget(CloudRssAction.PickLibraryPath, Key.DirectionUp, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.IntervalMinutes),
            cloudRssActionFocusTarget(CloudRssAction.PickInboxPath, Key.DirectionDown, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.ProxyHost),
            cloudRssActionFocusTarget(CloudRssAction.PickLibraryPath, Key.DirectionDown, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.PickLibraryPath),
            cloudRssActionFocusTarget(CloudRssAction.PickInboxPath, Key.DirectionRight, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.ProxyPort),
            cloudRssFieldFocusTarget(CloudRssField.ProxyHost, Key.DirectionRight),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.SyncEnabled),
            cloudRssFieldFocusTarget(CloudRssField.IntervalMinutes, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.ProxyEnabled),
            cloudRssFieldFocusTarget(CloudRssField.ProxyPort, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.IntervalMinutes),
            cloudRssToggleFocusTarget(CloudRssToggle.SyncEnabled, Key.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.ProxyHost),
            cloudRssToggleFocusTarget(CloudRssToggle.ProxyEnabled, Key.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SetSingleDirectoryMode),
            cloudRssFieldFocusTarget(CloudRssField.InboxPath, Key.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SetSingleDirectoryMode),
            cloudRssFieldFocusTarget(CloudRssField.LibraryPath, Key.DirectionUp),
        )
        assertNull(cloudRssFieldFocusTarget(CloudRssField.ProxyPort, Key.DirectionRight))
    }

    @Test
    fun `cloud rss fields also accept shared direction intents`() {
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.LibraryPath),
            cloudRssFieldFocusTarget(CloudRssField.InboxPath, MiruPlayInputIntent.DirectionRight),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.PickInboxPath),
            cloudRssFieldFocusTarget(CloudRssField.InboxPath, MiruPlayInputIntent.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SetSingleDirectoryMode),
            cloudRssFieldFocusTarget(CloudRssField.InboxPath, MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.SyncEnabled),
            cloudRssFieldFocusTarget(CloudRssField.IntervalMinutes, MiruPlayInputIntent.DirectionDown),
        )
        assertNull(cloudRssFieldFocusTarget(CloudRssField.Endpoint, MiruPlayInputIntent.DirectionRight))
        assertNull(cloudRssFieldFocusTarget(CloudRssField.Endpoint, MiruPlayInputIntent.Activate))
    }

    @Test
    fun `desktop CloudDrive directory browser scopes paths to token root`() {
        assertEquals("/", normalizeCloudDriveDirectoryPath(""))
        assertEquals("/Anime/Season 1", normalizeCloudDriveDirectoryPath("Anime\\Season 1\\"))
        assertEquals("/CloudRoot", scopedCloudDriveDirectoryPath("/", "/CloudRoot"))
        assertEquals("/CloudRoot", scopedCloudDriveDirectoryPath("/Outside/Inbox", "/CloudRoot"))
        assertEquals("/CloudRoot/Inbox", scopedCloudDriveDirectoryPath("/CloudRoot/Inbox", "/CloudRoot"))
        assertEquals("CloudDrive 根目录", cloudDriveDirectoryDisplayPath("/"))
        assertEquals("/CloudRoot", cloudDriveDirectoryParentPath("/CloudRoot/Inbox", "/CloudRoot"))
        assertNull(cloudDriveDirectoryParentPath("/CloudRoot", "/CloudRoot"))
    }

    @Test
    fun `desktop CloudDrive directory entries keep visible folders only`() {
        val entries = cloudDriveDirectoryEntries(
            listOf(
                CloudDriveFileInfo("Episode 01.mkv", "/CloudRoot/Episode 01.mkv", isDirectory = false),
                CloudDriveFileInfo(".hidden", "/CloudRoot/.hidden", isDirectory = true),
                CloudDriveFileInfo("Season B", "/CloudRoot/Season B", isDirectory = true),
                CloudDriveFileInfo("season a", "/CloudRoot/season a", isDirectory = true),
                CloudDriveFileInfo("", "/CloudRoot/Extras", isDirectory = true),
            ),
        )

        assertEquals(listOf("Extras", "season a", "Season B"), entries.map { it.name })
        assertEquals(listOf("/CloudRoot/Extras", "/CloudRoot/season a", "/CloudRoot/Season B"), entries.map { it.path })
    }

    @Test
    fun `desktop CloudDrive directory rows move vertically without wrapping`() {
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.Parent),
            cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.UseCurrent, itemCount = 3, Key.DirectionRight),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.UseCurrent),
            cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.Parent, itemCount = 3, Key.DirectionLeft),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.Close),
            cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.Parent, itemCount = 3, Key.DirectionRight),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Row(0),
            cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.Close, itemCount = 3, Key.DirectionDown),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.EmptyState,
            cloudDriveDirectoryActionFocusTarget(
                current = CloudDriveDirectoryAction.Close,
                itemCount = 0,
                key = Key.DirectionDown,
                hasEmptyState = true,
            ),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.UseCurrent),
            cloudDriveDirectoryEmptyFocusTarget(Key.DirectionUp),
        )
        assertNull(cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.UseCurrent, itemCount = 3, Key.DirectionLeft))
        assertNull(cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.Close, itemCount = 3, Key.DirectionRight))
        assertNull(cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.Close, itemCount = 0, Key.DirectionDown))
        assertNull(cloudDriveDirectoryActionFocusTarget(CloudDriveDirectoryAction.UseCurrent, itemCount = 3, Key.DirectionUp))
        assertNull(cloudDriveDirectoryEmptyFocusTarget(Key.DirectionDown))

        assertEquals(CloudDriveDirectoryFocusTarget.Row(1), cloudDriveDirectoryRowFocusTarget(currentIndex = 0, itemCount = 3, Key.DirectionDown))
        assertEquals(CloudDriveDirectoryFocusTarget.Row(1), cloudDriveDirectoryRowFocusTarget(currentIndex = 2, itemCount = 3, Key.DirectionUp))
        assertEquals(CloudDriveDirectoryFocusTarget.Row(6), cloudDriveDirectoryRowFocusTarget(currentIndex = 5, itemCount = 8, Key.DirectionDown))
        assertEquals(CloudDriveDirectoryFocusTarget.Row(5), cloudDriveDirectoryRowFocusTarget(currentIndex = 6, itemCount = 8, Key.DirectionUp))
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.UseCurrent),
            cloudDriveDirectoryRowFocusTarget(currentIndex = 0, itemCount = 3, Key.DirectionUp),
        )
        assertNull(cloudDriveDirectoryRowFocusTarget(currentIndex = 2, itemCount = 3, Key.DirectionDown))
        assertNull(cloudDriveDirectoryRowFocusTarget(currentIndex = 7, itemCount = 8, Key.DirectionDown))
        assertNull(cloudDriveDirectoryRowFocusTarget(currentIndex = 0, itemCount = 0, Key.DirectionDown))
        assertNull(cloudDriveDirectoryRowFocusTarget(currentIndex = 0, itemCount = 3, Key.DirectionRight))
    }

    @Test
    fun `desktop CloudDrive directory rows also accept shared direction intents`() {
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.Parent),
            cloudDriveDirectoryActionFocusTarget(
                CloudDriveDirectoryAction.UseCurrent,
                itemCount = 3,
                intent = MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Row(0),
            cloudDriveDirectoryActionFocusTarget(
                CloudDriveDirectoryAction.Close,
                itemCount = 3,
                intent = MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.EmptyState,
            cloudDriveDirectoryActionFocusTarget(
                current = CloudDriveDirectoryAction.Close,
                itemCount = 0,
                intent = MiruPlayInputIntent.DirectionDown,
                hasEmptyState = true,
            ),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.UseCurrent),
            cloudDriveDirectoryEmptyFocusTarget(MiruPlayInputIntent.DirectionUp),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Row(1),
            cloudDriveDirectoryRowFocusTarget(
                currentIndex = 0,
                itemCount = 3,
                intent = MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.UseCurrent),
            cloudDriveDirectoryRowFocusTarget(
                currentIndex = 0,
                itemCount = 3,
                intent = MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            cloudDriveDirectoryActionFocusTarget(
                CloudDriveDirectoryAction.UseCurrent,
                itemCount = 3,
                intent = MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertNull(cloudDriveDirectoryEmptyFocusTarget(MiruPlayInputIntent.DirectionDown))
        assertNull(
            cloudDriveDirectoryRowFocusTarget(
                currentIndex = 0,
                itemCount = 3,
                intent = MiruPlayInputIntent.DirectionRight,
            ),
        )
    }

    @Test
    fun `desktop CloudDrive directory page helpers keep every folder reachable`() {
        assertEquals(0, cloudDriveDirectoryPageStartForIndex(index = 0, itemCount = 13))
        assertEquals(0, cloudDriveDirectoryPageStartForIndex(index = 5, itemCount = 13))
        assertEquals(6, cloudDriveDirectoryPageStartForIndex(index = 6, itemCount = 13))
        assertEquals(12, cloudDriveDirectoryPageStartForIndex(index = 12, itemCount = 13))
        assertEquals(12, cloudDriveDirectoryPageStartForIndex(index = 20, itemCount = 13))
        assertEquals(6, cloudDriveDirectoryCoercedPageStart(pageStart = 9, itemCount = 13))
        assertEquals(12, cloudDriveDirectoryCoercedPageStart(pageStart = 24, itemCount = 13))
        assertEquals(0, cloudDriveDirectoryCoercedPageStart(pageStart = -6, itemCount = 13))

        assertEquals(
            "显示 7-12 / 13 ${cloudDriveRssDirectoryPageUnitLabel()}，按上/下继续翻页。",
            cloudDriveDirectoryPageSummary(pageStart = 6, visibleCount = 6, itemCount = 13),
        )
        assertEquals(
            "显示 13-13 / 13 ${cloudDriveRssDirectoryPageUnitLabel()}，按上/下继续翻页。",
            cloudDriveDirectoryPageSummary(pageStart = 12, visibleCount = 1, itemCount = 13),
        )
        assertNull(cloudDriveDirectoryPageSummary(pageStart = 0, visibleCount = 5, itemCount = 5))
    }

    @Test
    fun `cloud rss credential fields bridge into credential actions`() {
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.Username),
            cloudRssFieldFocusTarget(CloudRssField.Endpoint, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.ApiToken),
            cloudRssFieldFocusTarget(CloudRssField.Username, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.Password),
            cloudRssFieldFocusTarget(CloudRssField.ApiToken, Key.DirectionRight),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.Username),
            cloudRssFieldFocusTarget(CloudRssField.Password, Key.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveCredentials),
            cloudRssFieldFocusTarget(CloudRssField.ApiToken, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.ClearCredentials),
            cloudRssFieldFocusTarget(CloudRssField.Password, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.ApiToken),
            cloudRssActionFocusTarget(CloudRssAction.SaveCredentials, Key.DirectionUp, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.Password),
            cloudRssActionFocusTarget(CloudRssAction.ClearCredentials, Key.DirectionUp, subscriptionCount = 2),
        )
        assertNull(cloudRssFieldFocusTarget(CloudRssField.Endpoint, Key.DirectionUp))
        assertNull(cloudRssFieldFocusTarget(CloudRssField.Endpoint, Key.DirectionRight))
    }

    @Test
    fun `cloud rss subscription fields bridge into rss actions`() {
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.SubscriptionUrl),
            cloudRssFieldFocusTarget(CloudRssField.SubscriptionName, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.FilterRegex),
            cloudRssFieldFocusTarget(CloudRssField.SubscriptionUrl, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.RssEnabled),
            cloudRssFieldFocusTarget(CloudRssField.FilterRegex, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Field(CloudRssField.FilterRegex),
            cloudRssToggleFocusTarget(CloudRssToggle.RssEnabled, Key.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveRss),
            cloudRssToggleFocusTarget(CloudRssToggle.RssEnabled, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Toggle(CloudRssToggle.RssEnabled),
            cloudRssActionFocusTarget(CloudRssAction.SaveRss, Key.DirectionUp, subscriptionCount = 0),
        )
        assertNull(cloudRssFieldFocusTarget(CloudRssField.SubscriptionName, Key.DirectionUp))
        assertNull(cloudRssFieldFocusTarget(CloudRssField.SubscriptionUrl, Key.DirectionRight))
    }

    @Test
    fun `cloud rss rss and scheduler focus bridge through saved subscription rows`() {
        assertEquals(
            CloudRssFocusTarget.Subscription(0),
            cloudRssActionFocusTarget(CloudRssAction.SaveRss, Key.DirectionDown, subscriptionCount = 3),
        )
        assertEquals(
            CloudRssFocusTarget.EmptySubscriptions,
            cloudRssActionFocusTarget(CloudRssAction.SaveRss, Key.DirectionDown, subscriptionCount = 0),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveRss),
            cloudRssSubscriptionFocusTarget(currentIndex = 0, itemCount = 3, Key.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Subscription(1),
            cloudRssSubscriptionFocusTarget(currentIndex = 0, itemCount = 3, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.StartScheduler),
            cloudRssSubscriptionFocusTarget(currentIndex = 2, itemCount = 3, Key.DirectionDown),
        )
        assertEquals(
            CloudRssFocusTarget.Subscription(2),
            cloudRssActionFocusTarget(CloudRssAction.StopScheduler, Key.DirectionUp, subscriptionCount = 3),
        )
        assertEquals(
            CloudRssFocusTarget.EmptySubscriptions,
            cloudRssActionFocusTarget(CloudRssAction.DeleteRss, Key.DirectionDown, subscriptionCount = 0),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveRss),
            cloudRssSubscriptionEmptyFocusTarget(Key.DirectionUp),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.StartScheduler),
            cloudRssSubscriptionEmptyFocusTarget(Key.DirectionDown),
        )
        assertNull(cloudRssSubscriptionEmptyFocusTarget(Key.DirectionLeft))
        assertNull(cloudRssSubscriptionFocusTarget(currentIndex = 0, itemCount = 0, Key.DirectionDown))
    }

    @Test
    fun `cloud rss subscription page helpers keep every subscription reachable`() {
        assertEquals(0, cloudRssSubscriptionPageStartForIndex(index = 0, itemCount = 14))
        assertEquals(0, cloudRssSubscriptionPageStartForIndex(index = 5, itemCount = 14))
        assertEquals(6, cloudRssSubscriptionPageStartForIndex(index = 6, itemCount = 14))
        assertEquals(12, cloudRssSubscriptionPageStartForIndex(index = 13, itemCount = 14))
        assertEquals(12, cloudRssSubscriptionPageStartForIndex(index = 99, itemCount = 14))
        assertEquals(6, cloudRssSubscriptionCoercedPageStart(pageStart = 10, itemCount = 14))
        assertEquals(12, cloudRssSubscriptionCoercedPageStart(pageStart = 24, itemCount = 14))
        assertEquals(0, cloudRssSubscriptionCoercedPageStart(pageStart = -6, itemCount = 14))

        assertEquals(
            "显示 7-12 / 14 ${rssSubscriptionPageUnitLabel()}，按上/下继续翻页。",
            cloudRssSubscriptionPageSummary(pageStart = 6, visibleCount = 6, itemCount = 14),
        )
        assertEquals(
            "显示 13-14 / 14 ${rssSubscriptionPageUnitLabel()}，按上/下继续翻页。",
            cloudRssSubscriptionPageSummary(pageStart = 12, visibleCount = 2, itemCount = 14),
        )
        assertNull(cloudRssSubscriptionPageSummary(pageStart = 0, visibleCount = 4, itemCount = 4))
    }

    @Test
    fun `cloud rss scheduler actions move like a TV remote row`() {
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.StopScheduler),
            cloudRssActionFocusTarget(CloudRssAction.StartScheduler, Key.DirectionRight, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.StartScheduler),
            cloudRssActionFocusTarget(CloudRssAction.StopScheduler, Key.DirectionLeft, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Subscription(1),
            cloudRssActionFocusTarget(CloudRssAction.StartScheduler, Key.DirectionUp, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Subscription(1),
            cloudRssActionFocusTarget(CloudRssAction.StopScheduler, Key.DirectionUp, subscriptionCount = 2),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.SaveRss),
            cloudRssActionFocusTarget(CloudRssAction.StartScheduler, Key.DirectionUp, subscriptionCount = 0),
        )
        assertEquals(
            CloudRssFocusTarget.Action(CloudRssAction.DeleteRss),
            cloudRssActionFocusTarget(CloudRssAction.StopScheduler, Key.DirectionUp, subscriptionCount = 0),
        )
        assertNull(cloudRssActionFocusTarget(CloudRssAction.StartScheduler, Key.DirectionLeft, subscriptionCount = 2))
        assertNull(cloudRssActionFocusTarget(CloudRssAction.StopScheduler, Key.DirectionRight, subscriptionCount = 2))
        assertNull(cloudRssActionFocusTarget(CloudRssAction.StartScheduler, Key.DirectionDown, subscriptionCount = 2))
        assertNull(cloudRssActionFocusTarget(CloudRssAction.StopScheduler, Key.DirectionDown, subscriptionCount = 2))
    }

    @Test
    fun `desktop settings categories use shared TV section contract`() {
        assertEquals(
            listOf("WebUI", "媒体源", "播放", "云盘", "代理", "扫描", "日志", "更新", "元数据", "关于"),
            desktopSettingsSectionOrder.map { it.desktopTitle },
        )
        assertEquals(
            listOf(
                "访问地址与二维码",
                "本地、WebDAV、SMB",
                "mpv 与 RIFE",
                "RSS 离线下载与入库",
                "Bangumi、Archive 与 RSS 出站代理",
                "媒体库更新",
                "OpenObserve JSON",
                "GitHub Release",
                "Bangumi 匹配",
                "版本与应用信息",
            ),
            desktopSettingsSectionOrder.map { it.desktopDescription },
        )
    }

    @Test
    fun `settings category navigation stops at TV list edges`() {
        assertNull(MiruPlaySettingsSection.WEB_UI.stepDesktopSettingsSection(-1))
        assertEquals(MiruPlaySettingsSection.SOURCES, MiruPlaySettingsSection.WEB_UI.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.WEB_UI, MiruPlaySettingsSection.SOURCES.stepDesktopSettingsSection(-1))
        assertEquals(MiruPlaySettingsSection.PLAYBACK, MiruPlaySettingsSection.SOURCES.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.CLOUD_DRIVE, MiruPlaySettingsSection.PLAYBACK.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.PLAYBACK, MiruPlaySettingsSection.CLOUD_DRIVE.stepDesktopSettingsSection(-1))
        assertEquals(MiruPlaySettingsSection.PROXY, MiruPlaySettingsSection.CLOUD_DRIVE.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.CLOUD_DRIVE, MiruPlaySettingsSection.PROXY.stepDesktopSettingsSection(-1))
        assertEquals(MiruPlaySettingsSection.SCAN, MiruPlaySettingsSection.PROXY.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.LOG_UPLOAD, MiruPlaySettingsSection.SCAN.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.APP_UPDATE, MiruPlaySettingsSection.LOG_UPLOAD.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.METADATA, MiruPlaySettingsSection.APP_UPDATE.stepDesktopSettingsSection(1))
        assertEquals(MiruPlaySettingsSection.ABOUT, MiruPlaySettingsSection.METADATA.stepDesktopSettingsSection(1))
        assertNull(MiruPlaySettingsSection.ABOUT.stepDesktopSettingsSection(1))
    }

    @Test
    fun `settings category navigation accepts shared direction intents`() {
        assertEquals(
            MiruPlaySettingsSection.SOURCES,
            settingsSectionNavigationTarget(
                current = MiruPlaySettingsSection.WEB_UI,
                intent = MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertEquals(
            MiruPlaySettingsSection.PLAYBACK,
            settingsSectionNavigationTarget(
                current = MiruPlaySettingsSection.CLOUD_DRIVE,
                intent = MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            settingsSectionNavigationTarget(
                current = MiruPlaySettingsSection.WEB_UI,
                intent = MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            settingsSectionNavigationTarget(
                current = MiruPlaySettingsSection.SOURCES,
                intent = MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertNull(
            settingsSectionNavigationTarget(
                current = MiruPlaySettingsSection.SOURCES,
                intent = MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `settings category rows accept TV confirm keys`() {
        var selected = 0

        assertTrue(
            settingsSectionMenuRowKeyEvent(
                key = Key.DirectionCenter,
                type = KeyEventType.KeyDown,
                onSelected = { selected += 1 },
            ),
        )
        assertEquals(1, selected)

        assertTrue(
            settingsSectionMenuRowKeyEvent(
                key = Key.NumPadEnter,
                type = KeyEventType.KeyDown,
                onSelected = { selected += 1 },
            ),
        )
        assertEquals(2, selected)

        assertFalse(
            settingsSectionMenuRowKeyEvent(
                key = Key.Tab,
                type = KeyEventType.KeyDown,
                onSelected = { selected += 1 },
            ),
        )
        assertEquals(2, selected)

        assertFalse(
            settingsSectionMenuRowKeyEvent(
                key = Key.DirectionUp,
                type = KeyEventType.KeyDown,
                onSelected = { selected += 1 },
            ),
        )
        assertEquals(2, selected)
    }

    @Test
    fun `settings summary quick actions move across TV button rows`() {
        assertEquals(1, settingsQuickActionNavigationTarget(currentIndex = 0, actionCount = 2, Key.DirectionRight))
        assertEquals(0, settingsQuickActionNavigationTarget(currentIndex = 1, actionCount = 2, Key.DirectionLeft))
        assertNull(settingsQuickActionNavigationTarget(currentIndex = 0, actionCount = 2, Key.DirectionLeft))
        assertNull(settingsQuickActionNavigationTarget(currentIndex = 1, actionCount = 2, Key.DirectionRight))
        assertNull(settingsQuickActionNavigationTarget(currentIndex = 0, actionCount = 2, Key.DirectionDown))
        assertNull(settingsQuickActionNavigationTarget(currentIndex = 0, actionCount = 0, Key.DirectionRight))
        assertEquals(
            SettingsQuickActionFocusTarget.Action(1),
            settingsQuickActionFocusTarget(currentIndex = 0, actionCount = 2, Key.DirectionRight),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.Action(0),
            settingsQuickActionFocusTarget(currentIndex = 1, actionCount = 2, Key.DirectionLeft),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.SectionMenu,
            settingsQuickActionFocusTarget(currentIndex = 0, actionCount = 2, Key.DirectionUp),
        )
        assertNull(settingsQuickActionFocusTarget(currentIndex = 0, actionCount = 2, Key.DirectionDown))
        assertNull(settingsQuickActionFocusTarget(currentIndex = 0, actionCount = 0, Key.DirectionUp))
    }

    @Test
    fun `settings summary quick actions also accept shared direction intents`() {
        assertEquals(
            1,
            settingsQuickActionNavigationTarget(
                currentIndex = 0,
                actionCount = 2,
                intent = MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            0,
            settingsQuickActionNavigationTarget(
                currentIndex = 1,
                actionCount = 2,
                intent = MiruPlayInputIntent.DirectionLeft,
            ),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.Action(1),
            settingsQuickActionFocusTarget(
                currentIndex = 0,
                actionCount = 2,
                intent = MiruPlayInputIntent.DirectionRight,
            ),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.SectionMenu,
            settingsQuickActionFocusTarget(
                currentIndex = 0,
                actionCount = 2,
                intent = MiruPlayInputIntent.DirectionUp,
            ),
        )
        assertNull(
            settingsQuickActionFocusTarget(
                currentIndex = 0,
                actionCount = 2,
                intent = MiruPlayInputIntent.DirectionDown,
            ),
        )
        assertNull(
            settingsQuickActionNavigationTarget(
                currentIndex = 0,
                actionCount = 2,
                intent = MiruPlayInputIntent.Activate,
            ),
        )
    }

    @Test
    fun `settings summary extra field bridges between menu and quick actions`() {
        assertEquals(
            SettingsQuickActionFocusTarget.ExtraContent,
            settingsQuickActionFocusTarget(
                currentIndex = 0,
                actionCount = 3,
                key = Key.DirectionUp,
                hasExtraFocus = true,
            ),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.SectionMenu,
            settingsSummaryExtraFocusTarget(
                actionCount = 3,
                key = Key.DirectionUp,
            ),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.Action(0),
            settingsSummaryExtraFocusTarget(
                actionCount = 3,
                key = Key.DirectionDown,
            ),
        )
        assertNull(settingsSummaryExtraFocusTarget(actionCount = 3, key = Key.DirectionRight))
    }

    @Test
    fun `settings summary extra field uses shared intents and skips disabled quick actions`() {
        val enabledActions = listOf(false, true, true)

        assertEquals(
            SettingsQuickActionFocusTarget.ExtraContent,
            settingsQuickActionFocusTarget(
                currentIndex = 1,
                intent = MiruPlayInputIntent.DirectionUp,
                enabledActions = enabledActions,
                hasExtraFocus = true,
            ),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.Action(1),
            settingsSummaryExtraFocusTarget(
                intent = MiruPlayInputIntent.DirectionDown,
                enabledActions = enabledActions,
            ),
        )
        assertNull(
            settingsSummaryExtraFocusTarget(
                intent = MiruPlayInputIntent.DirectionDown,
                enabledActions = listOf(false, false),
            ),
        )
        assertNull(
            settingsSummaryExtraFocusTarget(
                intent = MiruPlayInputIntent.Activate,
                enabledActions = enabledActions,
            ),
        )
    }

    @Test
    fun `settings summary quick actions skip disabled buttons`() {
        val enabledActions = listOf(true, false, true)

        assertEquals(
            2,
            settingsQuickActionNavigationTarget(
                currentIndex = 0,
                key = Key.DirectionRight,
                enabledActions = enabledActions,
            ),
        )
        assertEquals(
            0,
            settingsQuickActionNavigationTarget(
                currentIndex = 2,
                key = Key.DirectionLeft,
                enabledActions = enabledActions,
            ),
        )
        assertEquals(
            SettingsQuickActionFocusTarget.Action(2),
            settingsQuickActionFocusTarget(
                currentIndex = 0,
                key = Key.DirectionRight,
                enabledActions = enabledActions,
            ),
        )
        assertNull(
            settingsQuickActionFocusTarget(
                currentIndex = 1,
                key = Key.DirectionUp,
                enabledActions = enabledActions,
            ),
        )
    }
}
