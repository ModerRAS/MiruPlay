package com.miruplay.tv.model

import com.miruplay.tv.core.common.rawUiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionDisplayConventionsTest {
    @Test
    fun `settings section copy is shared`() {
        assertEquals(
            listOf("WebUI", "媒体源", "播放", "音频 DSP", "CloudDrive", "代理配置", "扫描", "日志上报", "更新", "元数据", "关于"),
            androidTvSettingsSectionOrder.map { it.androidTvTitle.fallback },
        )
        assertEquals(
            listOf(
                "访问地址与二维码",
                "本地、WebDAV、SMB",
                "播完动作",
                "PEQ、扫频测量与校准",
                "RSS 离线下载与入库",
                "Bangumi、Archive 与 RSS 出站代理",
                "媒体库更新策略",
                "OpenObserve JSON",
                "GitHub APK",
                "Bangumi / TMDB Token",
                "版本与应用信息",
            ),
            androidTvSettingsSectionOrder.map { it.androidTvDescription.fallback },
        )
    }

    @Test
    fun `settings section orders keep platform entry points explicit`() {
        assertEquals(MiruPlaySettingsSection.WEB_UI, androidTvSettingsSectionOrder.first())
        assertEquals(MiruPlaySettingsSection.ABOUT, androidTvSettingsSectionOrder.last())
    }

    @Test
    fun `settings menu summaries are shared`() {
        assertEquals("等待网络", settingsWebUiMenuSummary(addressCount = 0).fallback)
        assertEquals("2 个地址", settingsWebUiMenuSummary(addressCount = 2).fallback)
        assertEquals("3 个源", settingsSourcesMenuSummary(sourceCount = 3).fallback)
        assertEquals("未启用", settingsCloudDriveMenuSummary(enabled = false, rssCount = 4).fallback)
        assertEquals("4 个订阅", settingsCloudDriveMenuSummary(enabled = true, rssCount = 4).fallback)
        assertEquals("未启用", settingsProxyMenuSummary(enabled = false).fallback)
        assertEquals("待填写", settingsProxyMenuSummary(enabled = true, host = "").fallback)
        assertEquals("203.0.113.20:7890", settingsProxyMenuSummary(enabled = true, host = " 203.0.113.20 ", port = 7890).fallback)
        assertEquals("动漫 · 定时 · 合并", settingsScanMenuSummary(autoScanEnabled = true, mergeSameAnimeEnabled = true).fallback)
        assertEquals("动漫 · 定时", settingsScanMenuSummary(autoScanEnabled = true, mergeSameAnimeEnabled = false).fallback)
        assertEquals("动漫 · 合并", settingsScanMenuSummary(autoScanEnabled = false, mergeSameAnimeEnabled = true).fallback)
        assertEquals("动漫", settingsScanMenuSummary(autoScanEnabled = false, mergeSameAnimeEnabled = false).fallback)
        assertEquals(
            "动漫 · 新番季",
            settingsScanMenuSummary(
                autoScanEnabled = false,
                mergeSameAnimeEnabled = false,
                posterWallArrangement = PosterWallArrangement.RELEASE_SEASON,
            ).fallback,
        )
        assertEquals("OpenObserve", settingsLogUploadMenuSummary().fallback)
        assertEquals("GitHub", settingsAppUpdateMenuSummary().fallback)
        assertEquals("未启用", settingsAndroidTvLogUploadMenuSummary().fallback)
        assertEquals(
            "自动上报",
            settingsAndroidTvLogUploadMenuSummary(enabled = true, tokenConfigured = true, isUploading = false).fallback,
        )
        assertEquals(
            "上报中",
            settingsAndroidTvLogUploadMenuSummary(enabled = true, tokenConfigured = true, isUploading = true).fallback,
        )
        assertEquals(
            "等待 Token",
            settingsAndroidTvLogUploadMenuSummary(enabled = true, tokenConfigured = false, isUploading = false).fallback,
        )
        assertEquals("未设置", settingsMetadataTokenMenuSummary().fallback)
        assertEquals("已设置 1 项", settingsMetadataTokenMenuSummary(hasBangumiToken = true).fallback)
        assertEquals("已设置 1 项", settingsMetadataTokenMenuSummary(hasTmdbToken = true).fallback)
        assertEquals(
            "已设置 2 项",
            settingsMetadataTokenMenuSummary(hasBangumiToken = true, hasTmdbToken = true).fallback,
        )
        assertEquals("版本 0.1.0", settingsAboutMenuSummary("0.1.0").fallback)
        assertEquals("版本 未知", settingsAboutMenuSummary("").fallback)

        val androidInput = SettingsSectionMenuSummaryInput(
            webUiAddressCount = 2,
            sourceCount = 3,
            playbackSummary = rawUiText("播完动作"),
            cloudDriveEnabled = true,
            rssCount = 4,
            proxyEnabled = true,
            proxyHost = "203.0.113.20",
            proxyPort = 7890,
            autoScanEnabled = true,
            mergeSameAnimeEnabled = true,
            posterWallArrangement = PosterWallArrangement.RELEASE_SEASON,
            appMode = "drama",
            metadataSummary = rawUiText("Token 已设置"),
            appVersionName = "0.1.0",
        )
        assertEquals("2 个地址", MiruPlaySettingsSection.WEB_UI.settingsMenuSummary(androidInput).fallback)
        assertEquals("3 个源", MiruPlaySettingsSection.SOURCES.settingsMenuSummary(androidInput).fallback)
        assertEquals("播完动作", MiruPlaySettingsSection.PLAYBACK.settingsMenuSummary(androidInput).fallback)
        assertEquals("4 个订阅", MiruPlaySettingsSection.CLOUD_DRIVE.settingsMenuSummary(androidInput).fallback)
        assertEquals("203.0.113.20:7890", MiruPlaySettingsSection.PROXY.settingsMenuSummary(androidInput).fallback)
        assertEquals("电视剧 · 定时 · 合并 · 新番季", MiruPlaySettingsSection.SCAN.settingsMenuSummary(androidInput).fallback)
        assertEquals("OpenObserve", MiruPlaySettingsSection.LOG_UPLOAD.settingsMenuSummary(androidInput).fallback)
        assertEquals("GitHub", MiruPlaySettingsSection.APP_UPDATE.settingsMenuSummary(androidInput).fallback)
        assertEquals("Token 已设置", MiruPlaySettingsSection.METADATA.settingsMenuSummary(androidInput).fallback)
        assertEquals("版本 0.1.0", MiruPlaySettingsSection.ABOUT.settingsMenuSummary(androidInput).fallback)

        val androidLogUploadInput = androidInput.copy(logUploadSummary = settingsAndroidTvLogUploadMenuSummary())
        assertEquals(
            "未启用",
            MiruPlaySettingsSection.LOG_UPLOAD.settingsMenuSummary(androidLogUploadInput).fallback,
        )
    }

    @Test
    fun `settings summary detail copy is shared`() {
        assertEquals("设置", settingsScreenTitleLabel().fallback)
        assertEquals("管理媒体源、WebUI 和元数据服务", settingsScreenSubtitleLabel().fallback)
        assertEquals("设置菜单", settingsMenuPanelTitle().fallback)
        assertEquals("按上下切换分类，向右进入当前设置。", settingsMenuPanelDescriptionAndroidTv().fallback)
        assertEquals("打开海报墙", settingsOpenLibraryActionLabel().fallback)
        assertEquals("扫描当前源", settingsScanActiveSourceActionLabel().fallback)
        assertEquals("打开播放器", settingsOpenPlayerActionLabel().fallback)
        assertEquals("打开详情", settingsOpenDetailsActionLabel().fallback)
        assertEquals("返回", settingsBackActionLabel().fallback)
        assertEquals("保存 Token", settingsSaveTokenActionLabel().fallback)
        assertEquals("清除 Token", settingsClearTokenActionLabel().fallback)
        assertEquals(
            "可在当前页面配置 OpenObserve curl 命令或 API 地址；本地日志会按同一配置写入上报队列。",
            settingsAndroidTvLogUploadStatusMessage().fallback,
        )
        assertEquals(
            "粘贴 curl 命令会自动保存地址和鉴权；开启自动上报后会定时上传待处理日志。",
            settingsAndroidTvLogUploadHintMessage().fallback,
        )
        assertEquals("自动上报", settingsLogUploadAutoToggleLabel().fallback)
        assertEquals("OpenObserve curl 命令", settingsLogUploadEndpointFieldLabel().fallback)
        assertEquals("Stream", settingsLogUploadStreamFieldLabel().fallback)
        assertEquals("OpenObserve Token", settingsLogUploadTokenFieldLabel().fallback)
        assertEquals("保存设置", settingsLogUploadSaveSettingsActionLabel().fallback)
        assertEquals("立即上报", settingsLogUploadRunNowActionLabel().fallback)
        assertEquals("应用更新", settingsAppUpdatePanelTitleLabel().fallback)
        assertEquals("从 GitHub Release 获取最新 APK。", settingsAppUpdatePanelDescription().fallback)
        assertEquals("检查更新", settingsAppUpdateCheckActionLabel().fallback)
        assertEquals("下载安装", settingsAppUpdateInstallActionLabel().fallback)
        assertEquals("安装授权", settingsAppUpdatePermissionActionLabel().fallback)
        assertEquals("尚未检查更新。", settingsAppUpdateIdleStatus().fallback)
        assertEquals("正在读取更新清单。", settingsAppUpdateCheckingStatus().fallback)
        assertEquals("发现新版本 2026.05.26。", settingsAppUpdateReadyStatus("2026.05.26").fallback)
        assertEquals("当前已是最新版本 2026.05.26。", settingsAppUpdateLatestStatus("2026.05.26").fallback)
        assertEquals("正在下载 APK 42%", settingsAppUpdateDownloadProgressStatus(42).fallback)
        assertEquals("正在下载 APK。", settingsAppUpdateDownloadProgressStatus(null).fallback)
        assertEquals("已打开安装授权页，授权后请返回重试。", settingsAppUpdateInstallPermissionStatus().fallback)
        assertEquals("已具备安装授权。", settingsAppUpdateInstallPermissionGrantedStatus().fallback)
        assertEquals("已打开系统安装器。", settingsAppUpdateInstallerOpenedStatus().fallback)
        assertEquals("关于 MiruPlay", settingsAboutPanelTitleLabel().fallback)
        assertEquals("当前安装包与版本信息。", settingsAboutPanelDescription().fallback)
        assertEquals("应用", settingsAboutAppNameLabel().fallback)
        assertEquals("版本", settingsAboutVersionNameLabel().fallback)
        assertEquals("版本号", settingsAboutVersionCodeLabel().fallback)
        assertEquals("包名", settingsAboutPackageNameLabel().fallback)
        assertEquals("未知", settingsAboutUnknownValue().fallback)
        assertEquals("0.1.0", settingsVersionNameValue("0.1.0").fallback)
        assertEquals("未知", settingsVersionNameValue("").fallback)
        assertEquals("42", settingsVersionCodeValue(42L).fallback)
        assertEquals("未知", settingsVersionCodeValue(0L).fallback)
        assertEquals("当前版本 0.1.0 · 版本号 42", settingsAboutStatusMessage("0.1.0", 42L).fallback)
        assertEquals("待上报 0 条", settingsLogUploadPendingStatus(-8).fallback)
        assertEquals("待命", settingsLogUploadUploadStateStatus(false).fallback)
        assertEquals("上报中", settingsLogUploadUploadStateStatus(true).fallback)
        assertEquals("Token 已保存", settingsLogUploadTokenConfiguredStatus(true).fallback)
        assertEquals("未保存 Token", settingsLogUploadTokenConfiguredStatus(false).fallback)
        assertEquals("尚未上报", settingsLogUploadLastUploadStatus(0L).fallback)
        assertEquals("暂无上报结果", settingsLogUploadResultStatus("  ").fallback)
        assertEquals("HTTP 200", settingsLogUploadResultStatus("  HTTP 200  ").fallback)
        val androidTvLogUploadStatus = settingsLogUploadStatusMessage(
            pendingCount = 12,
            isUploading = false,
            tokenConfigured = true,
            lastUploadAt = 0L,
            lastUploadStatus = "已上报 12 条日志",
        ).fallback
        assertEquals(
            "可在当前页面配置 OpenObserve curl 命令或 API 地址；本地日志会按同一配置写入上报队列。 · 待上报 12 条 · 待命 · Token 已保存 · 尚未上报 · 已上报 12 条日志",
            androidTvLogUploadStatus,
        )
        assertEquals("WebUI 访问", settingsWebUiPanelTitleLabel().fallback)
        assertEquals(
            "默认关闭。开启后，同一局域网设备需要携带访问令牌才能管理媒体源和遥控播放。",
            settingsWebUiPanelDescription().fallback,
        )
        assertEquals("开启 WebUI", settingsWebUiToggleActionLabel(enabled = false).fallback)
        assertEquals("关闭 WebUI", settingsWebUiToggleActionLabel(enabled = true).fallback)
        assertEquals("更换令牌", settingsWebUiRotateTokenActionLabel().fallback)
        assertEquals("刷新地址", settingsWebUiRefreshAddressActionLabel().fallback)
        assertEquals("未生成", settingsWebUiAccessTokenMissingValue().fallback)
        assertEquals("访问令牌：未生成", settingsWebUiAccessTokenLabel("").fallback)
        assertEquals("访问令牌：secret", settingsWebUiAccessTokenLabel("secret").fallback)
        assertEquals("WebUI 当前未启用，不会监听局域网端口。", settingsWebUiDisabledStatus().fallback)
        assertEquals("暂未检测到局域网地址，请确认电视已连接网络后刷新。", settingsWebUiNoLanAddressStatus().fallback)
        assertEquals("可用地址", settingsWebUiAvailableAddressesLabel().fallback)
        assertEquals("主地址", settingsWebUiAddressLabel(0).fallback)
        assertEquals("备用地址", settingsWebUiAddressLabel(1).fallback)
        assertEquals("扫码打开", settingsWebUiQrOpenLabel().fallback)
        assertEquals("WebUI", settingsWebUiTileLabel().fallback)
        assertEquals("Android TV", settingsWebUiAndroidTvValue().fallback)
        assertEquals("媒体库、远程浏览器和 Cloud/RSS 共用这个活动源。", settingsActiveSourceSharedDetail().fallback)
        assertEquals("扫描后优先回到媒体库海报墙。", settingsPosterWallIndexDetail().fallback)
        assertEquals("mpv、RIFE、字幕和起播时间在播放页调整。", settingsPlaybackPageDetail().fallback)
        assertEquals("mpv 进度同步后会刷新这里。", settingsRecentPlaybackDetail().fallback)
        assertEquals("从海报墙或详情页选择后可直接播放。", settingsSelectedMediaDetail().fallback)
        assertEquals("mpv 播放设置保留在播放页，RIFE/字幕/起播秒数仍可直接调整。", settingsPlaybackStatusMessage().fallback)
        assertEquals("Bangumi 搜索、批量预览、应用和撤销保留在详情页。", settingsMetadataStatusMessage().fallback)
        assertEquals("Bangumi Access Token", metadataBangumiTokenFieldLabel().fallback)
        assertEquals(
            "Bangumi Token 是可选项，用于 Bangumi 收藏与观看进度同步；元数据搜索不需要 Token。",
            metadataBangumiTokenOptionalHint().fallback,
        )
        assertEquals("Token 已保存在加密存储中。", metadataBangumiTokenSavedStatus().fallback)
        assertEquals("当前未设置 Token。", metadataBangumiTokenMissingStatus().fallback)
        assertEquals("Bangumi Token", metadataBangumiTokenTileLabel().fallback)
        assertEquals("仅用于 Bangumi 收藏与观看进度同步。", metadataBangumiTokenTileDetail().fallback)
        assertEquals("Bangumi Token 已保存。", metadataBangumiTokenSavedMessage().fallback)
        assertEquals("Bangumi Access Token 为空，未保存。", metadataBangumiTokenEmptyMessage().fallback)
        assertEquals("Bangumi Access Token 已清除。", metadataBangumiTokenClearedMessage().fallback)
        assertEquals("TMDB Read Access Token", metadataTmdbTokenFieldLabel().fallback)
        assertEquals(
            "TMDB Token 是可选项，用于电视剧详情补充海报、简介和首播日期；不填也能正常本地浏览和播放。",
            metadataTmdbTokenOptionalHint().fallback,
        )
        assertEquals("TMDB Token 已保存在加密存储中。", metadataTmdbTokenSavedStatus().fallback)
        assertEquals("当前未设置 TMDB Token。", metadataTmdbTokenMissingStatus().fallback)
        assertEquals("TMDB Token 已保存。", metadataTmdbTokenSavedMessage().fallback)
        assertEquals("TMDB Read Access Token 为空，未保存。", metadataTmdbTokenEmptyMessage().fallback)
        assertEquals("TMDB Read Access Token 已清除。", metadataTmdbTokenClearedMessage().fallback)
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
        val emptyTmdbTokenResult = saveTmdbTokenFormResult(
            input = "   ",
            existingToken = "existing-token",
        )
        assertEquals("existing-token", emptyTmdbTokenResult.token)
        assertEquals(true, emptyTmdbTokenResult.configured)
        assertEquals(metadataTmdbTokenEmptyMessage(), emptyTmdbTokenResult.status)
        assertEquals(false, emptyTmdbTokenResult.shouldPersistTokenInput)
        val savedTmdbTokenResult = saveTmdbTokenFormResult(
            input = "  tmdb-token  ",
            existingToken = "existing-token",
        )
        assertEquals("tmdb-token", savedTmdbTokenResult.token)
        assertEquals(true, savedTmdbTokenResult.configured)
        assertEquals(metadataTmdbTokenSavedMessage(), savedTmdbTokenResult.status)
        assertEquals(true, savedTmdbTokenResult.shouldPersistTokenInput)
        assertEquals(
            "Bangumi 搜索、批量预览、应用和撤销保留在详情页。 保存 Token 后可同步观看进度。",
            metadataBangumiTokenSettingsStatus(configured = false).fallback,
        )
        assertEquals(
            "Bangumi 搜索、批量预览、应用和撤销保留在详情页。 Bangumi Token 已保存。",
            metadataBangumiTokenSettingsStatus(configured = true).fallback,
        )
        assertEquals("本地、WebDAV、SMB 都写入同一媒体库索引。", settingsIndexSharedDetail().fallback)
        assertEquals("CloudDrive 完成后可触发这个源的重扫。", settingsCloudDriveRescanSourceDetail().fallback)
        assertEquals("扫描入口也保留在媒体库顶部。", settingsRecentScanStatusDetail().fallback)
        assertEquals("媒体库扫描", settingsScanPanelTitleLabel().fallback)
        assertEquals("首页的扫描按钮会立即执行；定时扫描只会在到达间隔后回到首页时触发。", settingsScanPanelDescription().fallback)
        assertEquals("首页模式", settingsAppModeTitleLabel().fallback)
        assertEquals("动漫", settingsAppModeOptionLabel("anime").fallback)
        assertEquals("电视剧", settingsAppModeOptionLabel("drama").fallback)
        assertEquals("音乐", settingsAppModeOptionLabel("music").fallback)
        assertEquals("下次启动会进入动漫首页。", settingsAppModeStatus("anime").fallback)
        assertEquals("下次启动会进入电视剧首页。", settingsAppModeStatus("drama").fallback)
        assertEquals("这里只决定应用下次启动先进动漫首页还是电视剧首页，不会立刻切换当前界面。", settingsAppModeHint().fallback)
        assertEquals("定时已开", settingsAutoScanToggleLabel(enabled = true).fallback)
        assertEquals("定时关闭", settingsAutoScanToggleLabel(enabled = false).fallback)
        assertEquals("2小时", settingsScanIntervalOptionLabel(2).fallback)
        assertEquals("0小时", settingsScanIntervalOptionLabel(-1).fallback)
        assertEquals("当前间隔 6 小时 · 今天 12:00", settingsCurrentScanIntervalStatus(6, rawUiText("今天 12:00")).fallback)
        assertEquals("还没有扫描记录", settingsLastScanLabel(0L).fallback)
        assertTrue(settingsCurrentScanIntervalStatus(6, 1_700_000_000_000L).fallback.startsWith("当前间隔 6 小时 · 上次扫描 "))
        assertEquals("媒体库显示", settingsLibraryDisplayTitleLabel().fallback)
        assertEquals("海报墙排列", settingsPosterWallArrangementTitleLabel().fallback)
        assertEquals("同番合并", settingsMergeSameAnimeToggleLabel(enabled = true).fallback)
        assertEquals("目录分开", settingsMergeSameAnimeToggleLabel(enabled = false).fallback)
        assertEquals("首页和详情会按 Bangumi ID 或标题合并同一番。", settingsMergeSameAnimeStatus(enabled = true).fallback)
        assertEquals("首页按扫描出的目录条目分别显示。", settingsMergeSameAnimeStatus(enabled = false).fallback)
        assertEquals("内容类型", mediaSourceContentModeTitleLabel().fallback)
        assertEquals("动漫", mediaSourceContentModeLabel(MediaContentMode.ANIME).fallback)
        assertEquals("电视剧", mediaSourceContentModeLabel(MediaContentMode.DRAMA).fallback)
        assertEquals("这个源会出现在动漫首页。", mediaSourceContentModeHint(MediaContentMode.ANIME).fallback)
        assertEquals("这个源会留给电视剧首页使用。", mediaSourceContentModeHint(MediaContentMode.DRAMA).fallback)
        assertEquals("详情页会显示可应用的 Bangumi 匹配。", settingsSelectedMetadataEntryDetail().fallback)
        assertEquals("支持单条应用、批量预览、应用和撤销。", settingsMetadataMatchStatusDetail().fallback)
        assertEquals("批量匹配会跳过已有冲突元数据。", settingsMetadataCandidateScopeDetail().fallback)
        assertEquals("媒体源", settingsSourceTileLabel().fallback)
        assertEquals("当前源", settingsActiveSourceTileLabel().fallback)
        assertEquals("海报墙索引", settingsPosterWallIndexTileLabel().fallback)
        assertEquals("播放模式", settingsPlaybackModeTileLabel().fallback)
        assertEquals("继续观看", settingsRecentPlaybackTileLabel().fallback)
        assertEquals("当前媒体", settingsSelectedMediaTileLabel().fallback)
        assertEquals("索引", settingsIndexTileLabel().fallback)
        assertEquals("同步后扫描源", settingsPostSyncSourceTileLabel().fallback)
        assertEquals("最近扫描状态", settingsRecentScanStatusTileLabel().fallback)
        assertEquals("选中条目", settingsSelectedMetadataEntryTileLabel().fallback)
        assertEquals("匹配状态", settingsMetadataMatchStatusTileLabel().fallback)
        assertEquals("候选范围", settingsMetadataCandidateScopeTileLabel().fallback)
        assertEquals("0 个", settingsCountValue(-1).fallback)
        assertEquals("3 条", settingsRecordCountValue(3).fallback)
        assertEquals("7 条索引", settingsIndexedCountValue(7).fallback)
        assertEquals("已保存", settingsSavedStateValue(true).fallback)
        assertEquals("未保存", settingsSavedStateValue(false).fallback)
        assertEquals("未选择", settingsNoSourceSelectedValue().fallback)
        assertEquals("缺失媒体源 #99", settingsMissingSourceValue(99L).fallback)
        assertEquals("二维码和局域网令牌入口由各平台设置页提供。", settingsWebUiTileDetail().fallback)
        assertEquals("已启用", settingsCloudRssOverviewValue(true).fallback)
        assertEquals("未启用", settingsCloudRssOverviewValue(false).fallback)
        assertEquals("2 个", settingsCloudRssSubscriptionsValue(2).fallback)
        assertEquals("Cloud WebDAV · WebDAV", settingsCloudRssLinkedSourceValue("Cloud WebDAV · WebDAV").fallback)
        val logUploadTiles = logUploadSettingsTiles()
        assertEquals(3, logUploadTiles.size)
        assertEquals("OpenObserve", logUploadTiles[0].label.fallback)
        assertEquals("JSON", logUploadTiles[0].value.fallback)
        assertEquals(
            "API 地址、Stream 和访问令牌可在设置页或 Web 控制端保存。",
            logUploadTiles[0].detail.fallback,
        )
        val aboutTiles = aboutSettingsTiles(
            appName = "MiruPlay",
            versionName = "0.1.0",
            versionCode = 42L,
            packageName = "com.miruplay.tv",
        )
        assertEquals(listOf("应用", "版本", "版本号"), aboutTiles.map { it.label.fallback })
        assertEquals(listOf("MiruPlay", "0.1.0", "42"), aboutTiles.map { it.value.fallback })
        assertEquals("包名：com.miruplay.tv", aboutTiles[2].detail.fallback)
    }

    @Test
    fun `settings source labels summarize active and linked sources`() {
        val local = MediaSourceInfoConventions.local(name = "Local Anime", rootPath = "D:/Anime").copy(id = 1L)
        val webDav = MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime")
            .copy(id = 2L, name = "Cloud WebDAV")
        val smb = MediaSourceInfoConventions.smb(url = "smb://nas.local/anime").copy(id = 3L)
        val sources = listOf(local, webDav, smb)

        assertEquals("Local Anime · 本地", settingsActiveSourceLabel(local).fallback)
        assertEquals(settingsNoSourceSelectedValue().fallback, settingsActiveSourceLabel(null).fallback)
        assertEquals("Cloud WebDAV · WebDAV", settingsLinkedSourceLabel(sources, 2L).fallback)
        assertEquals("SMB 媒体源 · SMB", settingsLinkedSourceLabel(listOf(smb.copy(name = "")), 3L).fallback)
        assertEquals(settingsNoSourceSelectedValue().fallback, settingsLinkedSourceLabel(sources, null).fallback)
        assertEquals(settingsMissingSourceValue(99L).fallback, settingsLinkedSourceLabel(sources, 99L).fallback)
        assertEquals("尚未添加本地、WebDAV 或 SMB 源。", settingsSourceTypeBreakdown(emptyList()).fallback)
        assertEquals("本地 1 · WebDAV 1 · SMB 1", settingsSourceTypeBreakdown(sources).fallback)
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
            activeSourceLabel = settingsActiveSourceLabel(sources.first()).fallback,
            indexedItemCount = 42,
        )
        val playbackTiles = playbackSettingsTiles(
            playbackSummary = rawUiText("RIFE DIRECTML"),
            recentCount = 5,
            selectedMediaTitle = "Fixture Alpha",
        )
        val scanTiles = scanSettingsTiles(
            indexedItemCount = 11,
            linkedSourceLabel = "SMB Share · SMB",
            libraryStatus = libraryScanCompleteStatus(11, 4).fallback,
        )
        val metadataTiles = metadataSettingsTiles(
            selectedMediaTitle = "Fixture Beta",
            metadataSummary = metadataMatchedSummaryLabel("Fixture Beta"),
            indexedItemCount = 11,
            bangumiTokenConfigured = true,
        )
        assertEquals(listOf(settingsSourceTileLabel().fallback, settingsActiveSourceTileLabel().fallback, settingsPosterWallIndexTileLabel().fallback), sourceTiles.map { it.label.fallback })
        assertEquals(listOf(settingsPlaybackModeTileLabel().fallback, settingsRecentPlaybackTileLabel().fallback, settingsSelectedMediaTileLabel().fallback), playbackTiles.map { it.label.fallback })
        assertEquals(listOf(settingsIndexTileLabel().fallback, settingsPostSyncSourceTileLabel().fallback, settingsRecentScanStatusTileLabel().fallback), scanTiles.map { it.label.fallback })
        assertEquals(listOf(settingsSelectedMetadataEntryTileLabel().fallback, settingsMetadataMatchStatusTileLabel().fallback, settingsMetadataCandidateScopeTileLabel().fallback, metadataBangumiTokenTileLabel().fallback), metadataTiles.map { it.label.fallback })
        assertEquals(settingsCountValue(3), sourceTiles[0].value)
        assertEquals("Local Anime · 本地", sourceTiles[1].value.fallback)
        assertEquals(settingsRecordCountValue(42), sourceTiles[2].value)
        assertEquals(settingsPlaybackPageDetail().fallback, playbackTiles[0].detail.fallback)
        assertEquals(settingsRecordCountValue(5), playbackTiles[1].value)
        assertEquals("Fixture Alpha", playbackTiles[2].value.fallback)
        assertEquals(settingsIndexSharedDetail().fallback, scanTiles[0].detail.fallback)
        assertEquals("SMB Share · SMB", scanTiles[1].value.fallback)
        assertEquals(localizedLibraryScanCompleteStatus(11, 4).fallback, scanTiles[2].value.fallback)
        assertEquals(metadataMatchedSummaryLabel("Fixture Beta").fallback, metadataTiles[1].value.fallback)
        assertEquals(settingsIndexedCountValue(11), metadataTiles[2].value)
        assertEquals(settingsSavedStateValue(true).fallback, metadataTiles[3].value.fallback)
    }

    @Test
    fun `settings section navigation stops at platform list edges`() {
        assertNull(MiruPlaySettingsSection.WEB_UI.stepAndroidTvSettingsSection(-1))
        assertEquals(
            MiruPlaySettingsSection.SOURCES,
            MiruPlaySettingsSection.WEB_UI.stepAndroidTvSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.AUDIO_DSP,
            MiruPlaySettingsSection.PLAYBACK.stepAndroidTvSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.CLOUD_DRIVE,
            MiruPlaySettingsSection.AUDIO_DSP.stepAndroidTvSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.APP_UPDATE,
            MiruPlaySettingsSection.LOG_UPLOAD.stepAndroidTvSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.METADATA,
            MiruPlaySettingsSection.APP_UPDATE.stepAndroidTvSettingsSection(1),
        )
        assertEquals(
            MiruPlaySettingsSection.ABOUT,
            MiruPlaySettingsSection.METADATA.stepAndroidTvSettingsSection(1),
        )
        assertNull(MiruPlaySettingsSection.ABOUT.stepAndroidTvSettingsSection(1))
    }
}
