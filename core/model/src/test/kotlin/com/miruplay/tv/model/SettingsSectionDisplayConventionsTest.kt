package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionDisplayConventionsTest {
    @Test
    fun `settings section copy is shared by Android TV and desktop`() {
        assertEquals(
            listOf("WebUI", "媒体源", "播放", "CloudDrive", "扫描", "元数据"),
            androidTvSettingsSectionOrder.map { it.androidTvTitle },
        )
        assertEquals(
            listOf("访问地址与二维码", "本地、WebDAV、SMB", "播完动作", "RSS 离线下载与入库", "媒体库更新策略", "Bangumi Token"),
            androidTvSettingsSectionOrder.map { it.androidTvDescription },
        )
        assertEquals(
            listOf("WebUI", "媒体源", "播放", "云盘", "扫描", "元数据"),
            desktopSettingsSectionOrder.map { it.desktopTitle },
        )
        assertEquals(
            listOf("访问地址与二维码", "本地、WebDAV、SMB", "mpv 与 RIFE", "RSS 离线下载与入库", "媒体库更新", "Bangumi 匹配"),
            desktopSettingsSectionOrder.map { it.desktopDescription },
        )
    }

    @Test
    fun `settings section orders keep platform entry points explicit`() {
        assertEquals(MiruPlaySettingsSection.WEB_UI, androidTvSettingsSectionOrder.first())
        assertEquals(MiruPlaySettingsSection.WEB_UI, desktopSettingsSectionOrder.first())
        assertEquals(MiruPlaySettingsSection.METADATA, androidTvSettingsSectionOrder.last())
        assertEquals(MiruPlaySettingsSection.METADATA, desktopSettingsSectionOrder.last())
    }

    @Test
    fun `settings menu summaries are shared`() {
        assertEquals("等待网络", settingsWebUiMenuSummary(addressCount = 0))
        assertEquals("2 个地址", settingsWebUiMenuSummary(addressCount = 2))
        assertEquals("3 个源", settingsSourcesMenuSummary(sourceCount = 3))
        assertEquals("未启用", settingsCloudDriveMenuSummary(enabled = false, rssCount = 4))
        assertEquals("4 个订阅", settingsCloudDriveMenuSummary(enabled = true, rssCount = 4))
        assertEquals("定时 · 合并", settingsScanMenuSummary(autoScanEnabled = true, mergeSameAnimeEnabled = true))
        assertEquals("定时已开", settingsScanMenuSummary(autoScanEnabled = true, mergeSameAnimeEnabled = false))
        assertEquals("同番合并", settingsScanMenuSummary(autoScanEnabled = false, mergeSameAnimeEnabled = true))
        assertEquals("定时关闭", settingsScanMenuSummary(autoScanEnabled = false, mergeSameAnimeEnabled = false))
        assertEquals("Token 已设置", settingsMetadataTokenMenuSummary(hasToken = true))
        assertEquals("未设置", settingsMetadataTokenMenuSummary(hasToken = false))
        assertEquals("媒体库更新", settingsDesktopScanMenuSummary())
        assertEquals("访问地址", settingsDesktopWebUiMenuSummary())
        assertEquals("等待网络", settingsDesktopWebUiMenuSummary(addressCount = 0))
        assertEquals("2 个地址", settingsDesktopWebUiMenuSummary(addressCount = 2))
    }

    @Test
    fun `desktop settings summary detail copy is shared`() {
        assertEquals("设置菜单", settingsMenuPanelTitle())
        assertEquals("像 TV 版一样按分类管理桌面能力。", settingsMenuPanelDescription())
        assertEquals("打开海报墙", settingsOpenLibraryActionLabel())
        assertEquals("扫描当前源", settingsScanActiveSourceActionLabel())
        assertEquals("打开播放器", settingsOpenPlayerActionLabel())
        assertEquals("打开详情", settingsOpenDetailsActionLabel())
        assertEquals("返回", settingsBackActionLabel())
        assertEquals("保存 Token", settingsSaveTokenActionLabel())
        assertEquals("清除 Token", settingsClearTokenActionLabel())
        assertEquals("扫描入口保留在媒体库海报墙和 CloudDrive 同步流程中。", settingsDesktopScanStatusMessage())
        assertEquals(
            "WebUI 当前未启用；Windows 已复用同一套访问令牌和地址生成规则。",
            settingsDesktopWebUiStatusMessage(enabled = false, addressCount = 2),
        )
        assertEquals(
            "WebUI 已启用，Windows 正在监听局域网访问地址；可管理媒体源并遥控播放。",
            settingsDesktopWebUiStatusMessage(enabled = true, addressCount = 2),
        )
        assertEquals(
            "WebUI 已启用，Windows 正在监听；暂未检测到可展示的局域网地址。",
            settingsDesktopWebUiStatusMessage(enabled = true, addressCount = 0),
        )
        assertEquals("WebUI 访问", settingsWebUiPanelTitleLabel())
        assertEquals(
            "默认关闭。开启后，同一局域网设备需要携带访问令牌才能管理媒体源和遥控播放。",
            settingsWebUiPanelDescription(),
        )
        assertEquals("开启 WebUI", settingsWebUiToggleActionLabel(enabled = false))
        assertEquals("关闭 WebUI", settingsWebUiToggleActionLabel(enabled = true))
        assertEquals("更换令牌", settingsWebUiRotateTokenActionLabel())
        assertEquals("刷新地址", settingsWebUiRefreshAddressActionLabel())
        assertEquals("未生成", settingsWebUiAccessTokenMissingValue())
        assertEquals("访问令牌：未生成", settingsWebUiAccessTokenLabel(""))
        assertEquals("访问令牌：secret", settingsWebUiAccessTokenLabel("secret"))
        assertEquals("WebUI 当前未启用，不会监听局域网端口。", settingsWebUiDisabledStatus())
        assertEquals("暂未检测到局域网地址，请确认电视已连接网络后刷新。", settingsWebUiNoLanAddressStatus())
        assertEquals("可用地址", settingsWebUiAvailableAddressesLabel())
        assertEquals("主地址", settingsWebUiAddressLabel(0))
        assertEquals("备用地址", settingsWebUiAddressLabel(1))
        assertEquals("扫码打开", settingsWebUiQrOpenLabel())
        assertEquals("WebUI", settingsWebUiTileLabel())
        assertEquals("Android TV", settingsWebUiAndroidTvValue())
        assertEquals("Windows", settingsWebUiDesktopValue())
        assertEquals("媒体库、远程浏览器和 Cloud/RSS 共用这个活动源。", settingsActiveSourceSharedDetail())
        assertEquals("扫描后优先回到媒体库海报墙。", settingsPosterWallIndexDetail())
        assertEquals("mpv、RIFE、字幕和起播时间在播放页调整。", settingsPlaybackPageDetail())
        assertEquals("mpv 进度同步后会刷新这里。", settingsRecentPlaybackDetail())
        assertEquals("从海报墙或详情页选择后可直接播放。", settingsSelectedMediaDetail())
        assertEquals("mpv 播放设置保留在播放页，RIFE/字幕/起播秒数仍可直接调整。", settingsPlaybackStatusMessage())
        assertEquals("Bangumi 搜索、批量预览、应用和撤销保留在详情页。", settingsMetadataStatusMessage())
        assertEquals("Bangumi Access Token", metadataBangumiTokenFieldLabel())
        assertEquals(
            "Bangumi Token 是可选项，用于 Bangumi 收藏与观看进度同步；元数据搜索不需要 Token。",
            metadataBangumiTokenOptionalHint(),
        )
        assertEquals("Token 已保存在加密存储中。", metadataBangumiTokenSavedStatus())
        assertEquals("当前未设置 Token。", metadataBangumiTokenMissingStatus())
        assertEquals("Bangumi Token", metadataBangumiTokenTileLabel())
        assertEquals("仅用于 Bangumi 收藏与观看进度同步。", metadataBangumiTokenTileDetail())
        assertEquals("Bangumi Token 已保存。", metadataBangumiTokenSavedMessage())
        assertEquals("Bangumi Access Token 为空，未保存。", metadataBangumiTokenEmptyMessage())
        assertEquals("Bangumi Access Token 已清除。", metadataBangumiTokenClearedMessage())
        val emptyTokenResult = saveBangumiTokenFormResult(
            input = "   ",
            existingToken = "existing-token",
        )
        assertEquals("existing-token", emptyTokenResult.token)
        assertEquals(true, emptyTokenResult.configured)
        assertEquals(metadataBangumiTokenEmptyMessage(), emptyTokenResult.status)
        assertEquals(false, emptyTokenResult.shouldPersistTokenInput)
        val savedTokenResult = saveBangumiTokenFormResult(
            input = "  new-token  ",
            existingToken = "existing-token",
        )
        assertEquals("new-token", savedTokenResult.token)
        assertEquals(true, savedTokenResult.configured)
        assertEquals(metadataBangumiTokenSavedMessage(), savedTokenResult.status)
        assertEquals(true, savedTokenResult.shouldPersistTokenInput)
        assertEquals(
            "Bangumi 搜索、批量预览、应用和撤销保留在详情页。 保存 Token 后可同步观看进度。",
            metadataBangumiTokenSettingsStatus(configured = false),
        )
        assertEquals(
            "Bangumi 搜索、批量预览、应用和撤销保留在详情页。 Bangumi Token 已保存。",
            metadataBangumiTokenSettingsStatus(configured = true),
        )
        assertEquals("本地、WebDAV、SMB 都写入同一桌面索引。", settingsIndexSharedDetail())
        assertEquals("CloudDrive 完成后可触发这个源的重扫。", settingsCloudDriveRescanSourceDetail())
        assertEquals("扫描入口也保留在媒体库顶部。", settingsRecentScanStatusDetail())
        assertEquals("媒体库扫描", settingsScanPanelTitleLabel())
        assertEquals("首页的扫描按钮会立即执行；定时扫描只会在到达间隔后回到首页时触发。", settingsScanPanelDescription())
        assertEquals("定时已开", settingsAutoScanToggleLabel(enabled = true))
        assertEquals("定时关闭", settingsAutoScanToggleLabel(enabled = false))
        assertEquals("2小时", settingsScanIntervalOptionLabel(2))
        assertEquals("0小时", settingsScanIntervalOptionLabel(-1))
        assertEquals("当前间隔 6 小时 · 今天 12:00", settingsCurrentScanIntervalStatus(6, "今天 12:00"))
        assertEquals("还没有扫描记录", settingsLastScanLabel(0L))
        assertTrue(settingsCurrentScanIntervalStatus(6, 1_700_000_000_000L).startsWith("当前间隔 6 小时 · 上次扫描 "))
        assertEquals("媒体库显示", settingsLibraryDisplayTitleLabel())
        assertEquals("同番合并", settingsMergeSameAnimeToggleLabel(enabled = true))
        assertEquals("目录分开", settingsMergeSameAnimeToggleLabel(enabled = false))
        assertEquals("首页和详情会按 Bangumi ID 或标题合并同一番。", settingsMergeSameAnimeStatus(enabled = true))
        assertEquals("首页按扫描出的目录条目分别显示。", settingsMergeSameAnimeStatus(enabled = false))
        assertEquals("详情页会显示可应用的 Bangumi 匹配。", settingsSelectedMetadataEntryDetail())
        assertEquals("支持单条应用、批量预览、应用和撤销。", settingsMetadataMatchStatusDetail())
        assertEquals("批量匹配会跳过已有冲突元数据。", settingsMetadataCandidateScopeDetail())
        assertEquals("媒体源", settingsSourceTileLabel())
        assertEquals("当前源", settingsActiveSourceTileLabel())
        assertEquals("海报墙索引", settingsPosterWallIndexTileLabel())
        assertEquals("播放模式", settingsPlaybackModeTileLabel())
        assertEquals("继续观看", settingsRecentPlaybackTileLabel())
        assertEquals("当前媒体", settingsSelectedMediaTileLabel())
        assertEquals("索引", settingsIndexTileLabel())
        assertEquals("同步后扫描源", settingsPostSyncSourceTileLabel())
        assertEquals("最近扫描状态", settingsRecentScanStatusTileLabel())
        assertEquals("选中条目", settingsSelectedMetadataEntryTileLabel())
        assertEquals("匹配状态", settingsMetadataMatchStatusTileLabel())
        assertEquals("候选范围", settingsMetadataCandidateScopeTileLabel())
        assertEquals("桌面控制", settingsWebUiNativeControlTileLabel())
        assertEquals("远程自动化", settingsRemoteAutomationTileLabel())
        assertEquals("0 个", settingsCountValue(-1))
        assertEquals("3 条", settingsRecordCountValue(3))
        assertEquals("7 条索引", settingsIndexedCountValue(7))
        assertEquals("已保存", settingsSavedStateValue(true))
        assertEquals("未保存", settingsSavedStateValue(false))
        assertEquals("未选择", settingsNoSourceSelectedValue())
        assertEquals("缺失媒体源 #99", settingsMissingSourceValue(99L))
        assertEquals("二维码和局域网令牌入口由各平台设置页提供。", settingsWebUiTileDetail())
        assertEquals("原生窗口", settingsDesktopControlTileValue())
        assertEquals("Windows 版保留键盘/遥控式导航和本机播放控制。", settingsDesktopControlTileDetail())
        assertEquals("Cloud/RSS", settingsRemoteAutomationTileValue())
        assertEquals("CloudDrive2 与 RSS 同步在云盘设置页管理。", settingsRemoteAutomationTileDetail())
        assertEquals("已启用", settingsCloudRssOverviewValue(true))
        assertEquals("未启用", settingsCloudRssOverviewValue(false))
        assertEquals("2 个", settingsCloudRssSubscriptionsValue(2))
        assertEquals("Cloud WebDAV · WebDAV", settingsCloudRssLinkedSourceValue("Cloud WebDAV · WebDAV"))
    }

    @Test
    fun `settings source labels summarize active and linked sources`() {
        val local = MediaSourceInfoConventions.local(name = "Local Anime", rootPath = "D:/Anime").copy(id = 1L)
        val webDav = MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime")
            .copy(id = 2L, name = "Cloud WebDAV")
        val smb = MediaSourceInfoConventions.smb(url = "smb://nas.local/anime").copy(id = 3L)
        val sources = listOf(local, webDav, smb)

        assertEquals("Local Anime · 本地", settingsActiveSourceLabel(local))
        assertEquals(settingsNoSourceSelectedValue(), settingsActiveSourceLabel(null))
        assertEquals("Cloud WebDAV · WebDAV", settingsLinkedSourceLabel(sources, 2L))
        assertEquals("SMB 媒体源 · SMB", settingsLinkedSourceLabel(listOf(smb.copy(name = "")), 3L))
        assertEquals(settingsNoSourceSelectedValue(), settingsLinkedSourceLabel(sources, null))
        assertEquals(settingsMissingSourceValue(99L), settingsLinkedSourceLabel(sources, 99L))
        assertEquals("尚未添加本地、WebDAV 或 SMB 源。", settingsSourceTypeBreakdown(emptyList()))
        assertEquals("本地 1 · WebDAV 1 · SMB 1", settingsSourceTypeBreakdown(sources))
    }

    @Test
    fun `settings summary tiles are shared`() {
        val sources = listOf(
            MediaSourceInfoConventions.local(name = "Local Anime", rootPath = "D:/Anime").copy(id = 1L),
            MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime")
                .copy(id = 2L, name = "Cloud WebDAV"),
            MediaSourceInfoConventions.smb(url = "smb://nas.local/anime").copy(id = 3L),
        )

        val sourceTiles = sourceSettingsTiles(
            sources = sources,
            activeSourceLabel = settingsActiveSourceLabel(sources.first()),
            indexedItemCount = 42,
        )
        val playbackTiles = playbackSettingsTiles(
            playbackSummary = "RIFE DIRECTML",
            recentCount = 5,
            selectedMediaTitle = "Fixture Alpha",
        )
        val scanTiles = scanSettingsTiles(
            indexedItemCount = 11,
            linkedSourceLabel = "SMB Share · SMB",
            libraryStatus = libraryScanCompleteStatus(11, 4),
        )
        val metadataTiles = metadataSettingsTiles(
            selectedMediaTitle = "Fixture Beta",
            metadataSummary = metadataMatchedSummaryLabel("Fixture Beta"),
            indexedItemCount = 11,
            bangumiTokenConfigured = true,
        )
        val webUiTiles = webUiSettingsTiles(platformValue = settingsWebUiDesktopValue())

        assertEquals(listOf(settingsSourceTileLabel(), settingsActiveSourceTileLabel(), settingsPosterWallIndexTileLabel()), sourceTiles.map { it.label })
        assertEquals(listOf(settingsPlaybackModeTileLabel(), settingsRecentPlaybackTileLabel(), settingsSelectedMediaTileLabel()), playbackTiles.map { it.label })
        assertEquals(listOf(settingsIndexTileLabel(), settingsPostSyncSourceTileLabel(), settingsRecentScanStatusTileLabel()), scanTiles.map { it.label })
        assertEquals(listOf(settingsSelectedMetadataEntryTileLabel(), settingsMetadataMatchStatusTileLabel(), settingsMetadataCandidateScopeTileLabel(), metadataBangumiTokenTileLabel()), metadataTiles.map { it.label })
        assertEquals(listOf(settingsWebUiTileLabel(), settingsWebUiNativeControlTileLabel(), settingsRemoteAutomationTileLabel()), webUiTiles.map { it.label })
        assertEquals(settingsCountValue(3), sourceTiles[0].value)
        assertEquals("Local Anime · 本地", sourceTiles[1].value)
        assertEquals(settingsRecordCountValue(42), sourceTiles[2].value)
        assertEquals(settingsPlaybackPageDetail(), playbackTiles[0].detail)
        assertEquals(settingsRecordCountValue(5), playbackTiles[1].value)
        assertEquals("Fixture Alpha", playbackTiles[2].value)
        assertEquals(settingsIndexSharedDetail(), scanTiles[0].detail)
        assertEquals("SMB Share · SMB", scanTiles[1].value)
        assertEquals(localizedLibraryScanCompleteStatus(11, 4), scanTiles[2].value)
        assertEquals(metadataMatchedSummaryLabel("Fixture Beta"), metadataTiles[1].value)
        assertEquals(settingsIndexedCountValue(11), metadataTiles[2].value)
        assertEquals(settingsSavedStateValue(true), metadataTiles[3].value)
        assertEquals(settingsWebUiDesktopValue(), webUiTiles[0].value)
        assertEquals(settingsWebUiTileDetail(), webUiTiles[0].detail)
        assertEquals(settingsDesktopControlTileValue(), webUiTiles[1].value)
    }

    @Test
    fun `settings section navigation stops at platform list edges`() {
        assertNull(MiruPlaySettingsSection.WEB_UI.stepAndroidTvSettingsSection(-1))
        assertEquals(
            MiruPlaySettingsSection.SOURCES,
            MiruPlaySettingsSection.WEB_UI.stepAndroidTvSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.CLOUD_DRIVE,
            MiruPlaySettingsSection.PLAYBACK.stepAndroidTvSettingsSection(1),
        )
        assertNull(MiruPlaySettingsSection.METADATA.stepAndroidTvSettingsSection(1))

        assertNull(MiruPlaySettingsSection.WEB_UI.stepDesktopSettingsSection(-1))
        assertEquals(
            MiruPlaySettingsSection.SOURCES,
            MiruPlaySettingsSection.WEB_UI.stepDesktopSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.WEB_UI,
            MiruPlaySettingsSection.SOURCES.stepDesktopSettingsSection(-1),
        )
        assertEquals(
            MiruPlaySettingsSection.PLAYBACK,
            MiruPlaySettingsSection.SOURCES.stepDesktopSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.CLOUD_DRIVE,
            MiruPlaySettingsSection.PLAYBACK.stepDesktopSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.SCAN,
            MiruPlaySettingsSection.CLOUD_DRIVE.stepDesktopSettingsSection(1),
        )
        assertNull(MiruPlaySettingsSection.METADATA.stepDesktopSettingsSection(1))
    }
}
