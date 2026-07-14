package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceDisplayConventionsTest {
    @Test
    fun `media source type display conventions are shared`() {
        assertEquals("本地", MediaSourceType.LOCAL.tvLabel())
        assertEquals("WebDAV", MediaSourceType.WEBDAV.tvLabel())
        assertEquals("SMB", MediaSourceType.SMB.tvLabel())

        assertEquals("本地", MediaSourceType.LOCAL.tvBadgeLabel())
        assertEquals("DAV", MediaSourceType.WEBDAV.tvBadgeLabel())
        assertEquals("SMB", MediaSourceType.SMB.tvBadgeLabel())

        assertEquals("本地下载", MediaSourceType.LOCAL.defaultSourceName())
        assertEquals("WebDAV 媒体库", MediaSourceType.WEBDAV.defaultSourceName())
        assertEquals("SMB 共享", MediaSourceType.SMB.defaultSourceName())

        assertEquals("本地媒体源", MediaSourceType.LOCAL.genericSourceName())
        assertEquals("WebDAV 媒体源", MediaSourceType.WEBDAV.genericSourceName())
        assertEquals("SMB 媒体源", MediaSourceType.SMB.genericSourceName())

        assertEquals("设备文件夹", MediaSourceType.LOCAL.tvSourceHint())
        assertEquals("HTTP 文件服务", MediaSourceType.WEBDAV.tvSourceHint())
        assertEquals("局域网共享", MediaSourceType.SMB.tvSourceHint())

        assertEquals("媒体文件夹", MediaSourceType.LOCAL.tvLocationLabel())
        assertEquals("WebDAV 地址", MediaSourceType.WEBDAV.tvLocationLabel())
        assertEquals("SMB 地址", MediaSourceType.SMB.tvLocationLabel())
    }

    @Test
    fun `media source display label falls back to shared default names`() {
        assertEquals(
            "Living Room · 本地",
            MediaSourceInfo(name = "Living Room", type = MediaSourceType.LOCAL).tvDisplayLabel(),
        )
        assertEquals(
            "本地下载 · 本地",
            MediaSourceInfo(name = "", type = MediaSourceType.LOCAL).tvDisplayLabel(),
        )
        assertEquals(
            "本地媒体源 · 本地",
            MediaSourceInfo(name = "", type = MediaSourceType.LOCAL).tvDisplayLabel(fallbackName = "本地媒体源"),
        )
    }

    @Test
    fun `media source status label combines shared type and connection copy`() {
        assertEquals(
            "WebDAV · 可连接",
            MediaSourceInfo(name = "Remote", type = MediaSourceType.WEBDAV, isConnected = true)
                .tvDisplayStatusLabel(),
        )
        assertEquals(
            "SMB · 待验证",
            MediaSourceInfo(name = "Share", type = MediaSourceType.SMB, isConnected = false)
                .tvDisplayStatusLabel(),
        )
    }

    @Test
    fun `source picker display uses shared labels and compact location`() {
        val local = MediaSourceInfoConventions.local(
            name = "Living Room Anime",
            rootPath = "D:/Anime/Shows/VeryLongSeasonFolder/Episodes",
        )
        val missing = MediaSourceInfo(id = 1L, name = "", type = MediaSourceType.WEBDAV)

        assertEquals("Living Room Anime · 本地", local.sourcePickerTitle())
        assertEquals("WebDAV 媒体源 · WebDAV", missing.sourcePickerTitle())
        assertEquals(mediaSourceLocationMissingLabel(), missing.sourcePickerSubtitle())

        val subtitle = local.sourcePickerSubtitle(maxLength = 24)
        assertEquals(24, subtitle.length)
        assertEquals(true, subtitle.startsWith("D:/Anime/"))
        assertEquals(true, subtitle.endsWith("/Episodes"))
        assertEquals(true, subtitle.contains("..."))
    }

    @Test
    fun `compact middle text preserves short text and clamps tiny limits`() {
        assertEquals("short", "short".compactMiddleText(maxLength = 12))
        assertEquals("a...z", "abcdefghijklmnopqrstuvwxyz".compactMiddleText(maxLength = 3))
    }

    @Test
    fun `remote source previews compact and fall back consistently`() {
        assertEquals("填写 SMB 共享地址", remoteSourcePreview("", fallback = "填写 SMB 共享地址", maxLength = 20))

        val preview = remoteSourcePreview(
            "https://smb.example.test/shares/very/long/path/with/subdirs",
            fallback = "填写 SMB 共享地址",
            maxLength = 24,
        )
        assertEquals(24, preview.length)
        assertEquals(true, preview.contains("..."))

        assertEquals("/", remoteBrowserPathPreview("", maxLength = 20))

        val browserPreview = remoteBrowserPathPreview(
            "/mnt/media/library/very/long/season/path",
            maxLength = 24,
        )
        assertEquals(24, browserPreview.length)
        assertEquals(true, browserPreview.contains("..."))
    }

    @Test
    fun `remote browser pagination helpers are shared`() {
        assertEquals(8, REMOTE_BROWSER_PAGE_SIZE)
        assertEquals(0, remoteBrowserPageStartForIndex(index = 0, itemCount = 17))
        assertEquals(0, remoteBrowserPageStartForIndex(index = 7, itemCount = 17))
        assertEquals(8, remoteBrowserPageStartForIndex(index = 8, itemCount = 17))
        assertEquals(16, remoteBrowserPageStartForIndex(index = 30, itemCount = 17))
        assertEquals(8, remoteBrowserCoercedPageStart(pageStart = 12, itemCount = 17))
        assertEquals(16, remoteBrowserCoercedPageStart(pageStart = 40, itemCount = 17))
        assertEquals(0, remoteBrowserCoercedPageStart(pageStart = -8, itemCount = 17))
        assertEquals("显示 9-16 / 17 个条目，按上/下继续翻页。", remoteBrowserPageSummary(8, 8, 17))
        assertEquals("显示 17-17 / 17 个条目，按上/下继续翻页。", remoteBrowserPageSummary(16, 1, 17))
        assertEquals(null, remoteBrowserPageSummary(0, 4, 4))
    }

    @Test
    fun `media source management labels are shared`() {
        assertEquals("媒体源", mediaSourceListTitleLabel())
        assertEquals("还没有配置媒体源", mediaSourceEmptyListMessage())
        assertEquals("已配置 3 个源", mediaSourceConfiguredCountLabel(3))
        assertEquals("添加媒体源", mediaSourceFormTitleLabel(isEditing = false))
        assertEquals("编辑媒体源", mediaSourceFormTitleLabel(isEditing = true))
        assertEquals("选择媒体库所在位置，保存后可在首页手动扫描。", mediaSourceFormDescriptionLabel(isEditing = false))
        assertEquals("修改媒体库位置或凭据，保存后会覆盖当前配置。", mediaSourceFormDescriptionLabel(isEditing = true))
        assertEquals("新建", mediaSourceNewActionLabel())
        assertEquals("显示名称", mediaSourceDisplayNameFieldLabel())
        assertEquals("本地媒体库路径", mediaSourceLocalLibraryRootFieldLabel())
        assertEquals("索引搜索", mediaSourceIndexQueryFieldLabel())
        assertEquals("打开本地", MediaSourceType.LOCAL.openSourceActionLabel())
        assertEquals("打开 WebDAV", MediaSourceType.WEBDAV.openSourceActionLabel())
        assertEquals("打开 SMB", MediaSourceType.SMB.openSourceActionLabel())
        assertEquals("扫描", mediaSourceScanActionLabel())
        assertEquals("搜索", mediaSourceSearchActionLabel())
        assertEquals("清空索引", mediaSourceClearIndexActionLabel())
        assertEquals("移除媒体源", mediaSourceRemoveActionLabel())
        assertEquals("WebDAV 用户名", MediaSourceType.WEBDAV.sourceUsernameFieldLabel())
        assertEquals("WebDAV 密码", MediaSourceType.WEBDAV.sourcePasswordFieldLabel())
        assertEquals("SMB 域", mediaSourceSmbDomainFieldLabel())
        assertEquals("扫描媒体源", mediaSourceScanSourceActionLabel())
        assertEquals("远程浏览", mediaSourceRemoteBrowserTitleLabel())
        assertEquals("上级", mediaSourceUpActionLabel())
        assertEquals("先打开一个远程媒体源以浏览文件。", mediaSourceRemoteBrowserEmptyMessage())
        assertEquals("目录", mediaSourceRemoteBrowserItemTypeLabel(isDirectory = true))
        assertEquals("视频", mediaSourceRemoteBrowserItemTypeLabel(isDirectory = false))
        assertEquals("个条目", mediaSourceRemoteBrowserPageUnitLabel())
        assertEquals("已保存媒体源", mediaSourceSavedPickerTitleLabel())
        assertEquals("选择已配置媒体源", mediaSourceSavedPickerSubtitleLabel())
        assertEquals("没有已保存媒体源", mediaSourceSavedPickerEmptyMessage())
        assertEquals("未配置路径", mediaSourceLocationMissingLabel())
        assertEquals("填写 WebDAV 地址", MediaSourceType.WEBDAV.sourceEndpointPlaceholderLabel())
        assertEquals("填写 SMB 共享地址", MediaSourceType.SMB.sourceEndpointPlaceholderLabel())
        assertEquals("测试中", mediaSourceTestConnectionActionLabel(isTesting = true))
        assertEquals("测试连接", mediaSourceTestConnectionActionLabel(isTesting = false))
        assertEquals("更新源", mediaSourceSaveActionLabel(isEditing = true))
        assertEquals("保存源", mediaSourceSaveActionLabel(isEditing = false))
        assertEquals("尚未选择文件夹", mediaSourceLocalFolderEmptyLabel())
        assertEquals("已授权访问", mediaSourceLocalFolderAuthorizedLabel())
        assertEquals("本地媒体库", mediaSourceLocalLibraryFallbackName())
        assertEquals("Download", mediaSourceLocalPathDisplayName("/storage/emulated/0/Download"))
        assertEquals("Anime", mediaSourceLocalPathDisplayName("D:\\Media\\Anime\\"))
        assertEquals("本地媒体库", mediaSourceLocalPathDisplayName(" / "))
        assertEquals("Download", mediaSourceLocalScanDisplayName("/storage/emulated/0/Download"))
        assertEquals("Anime Library", mediaSourceLocalScanDisplayName("content://tree/primary%3AAnime%20Library"))
        assertEquals("选择文件夹", mediaSourceChooseFolderActionLabel())
        assertEquals("连接正常，可以保存并返回首页扫描。", mediaSourceConnectionSuccessMessage())
        assertEquals("正在验证连接...", mediaSourceConnectionTestingMessage())
    }

    @Test
    fun `media source status helpers are shared`() {
        val local = MediaSourceInfo(id = 1L, name = "Library", type = MediaSourceType.LOCAL)
        val webDav = MediaSourceInfo(id = 2L, name = "Cloud", type = MediaSourceType.WEBDAV)
        val smb = MediaSourceInfo(id = 3L, name = "NAS", type = MediaSourceType.SMB)

        assertEquals("添加本地媒体源，或载入已保存的媒体源。", mediaSourceLocalLibraryInitialStatus())
        assertEquals("打开 WebDAV 或 SMB 媒体源后即可浏览文件。", mediaSourceRemoteBrowserInitialStatus())
        assertEquals("已载入媒体源：Library · 本地", local.mediaSourceLoadedStatus())
        assertEquals("已载入已保存媒体源：Library · 本地", local.mediaSourceLoadedStatus(saved = true))
        assertEquals("已载入媒体源：Cloud · WebDAV", webDav.mediaSourceLoadedStatus())
        assertEquals("已载入媒体源：NAS · SMB", smb.mediaSourceLoadedStatus())
        assertEquals("本地媒体源已就绪：Library", local.mediaSourceReadyStatus())
        assertEquals("WebDAV 媒体源已就绪：Cloud", webDav.mediaSourceReadyStatus())
        assertEquals("SMB 媒体源已就绪：NAS", smb.mediaSourceReadyStatus())
        assertEquals("Anime", local.scanResultDisplayName("D:/Media/Anime"))
        assertEquals(
            "Authorized Anime",
            local.copy(connectionInfo = mapOf(MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME to "Authorized Anime"))
                .scanResultDisplayName("D:/Media/Anime"),
        )
        assertEquals("Cloud", webDav.scanResultDisplayName("/remote/root"))
        assertEquals("NAS", smb.scanResultDisplayName("/remote/root"))
        assertEquals("请先填写本地媒体库路径。", mediaSourceLocalRootRequiredStatus())
        assertEquals("请先填写 WebDAV 地址。", mediaSourceWebDavUrlRequiredStatus())
        assertEquals("请先填写 SMB 地址。", mediaSourceSmbUrlRequiredStatus())
        assertEquals("请先打开媒体源，再开始扫描。", mediaSourceOpenBeforeScanningStatus())
        assertEquals("请先打开或扫描媒体源，再搜索。", mediaSourceOpenBeforeSearchingStatus())
        assertEquals("请先打开或扫描媒体源，再清空索引。", mediaSourceOpenBeforeClearingIndexStatus())
        assertEquals("已清空媒体源 #42 的索引。", mediaSourceIndexClearedStatus(42L))
        assertEquals("请先打开媒体源，再移除。", mediaSourceRemoveRequiredStatus())
        assertEquals("媒体源已移除，关联索引已清空。", mediaSourceRemovedStatus())
        assertEquals("已经在媒体源根目录。", mediaSourceAlreadyAtRootStatus())
        assertEquals("请先打开远程媒体源，再浏览。", mediaSourceOpenRemoteBeforeBrowsingStatus())
        assertEquals("正在载入 WebDAV：/Anime", webDav.mediaSourceLoadingRemoteDirectoryStatus("/Anime"))
        assertEquals("Cloud 中显示 2 个条目。", webDav.mediaSourceShowingRemoteDirectoryStatus(2))
        assertEquals("已选择播放：Frieren EP1", mediaSourceSelectedForPlaybackStatus("Frieren EP1"))
        assertEquals("已选择远程媒体：Episode.mkv。mpv 将通过本地桥接串流。", mediaSourceSelectedRemoteForPlaybackStatus("Episode.mkv"))
        assertEquals("没有匹配 \"frieren\" 的索引媒体。", mediaSourceIndexedSearchStatus(" frieren ", false, 0))
        assertEquals("显示 24 条索引视频结果。", mediaSourceIndexedSearchStatus("frieren", true, 24))
    }

    @Test
    fun `media source status text accepts shared and legacy wire statuses`() {
        assertEquals(
            "添加本地媒体源，或载入已保存的媒体源。",
            localizedMediaSourceStatusText(mediaSourceLocalLibraryInitialStatus()),
        )
        assertEquals(
            "已载入媒体源：Library · 本地",
            localizedMediaSourceStatusText(MediaSourceInfo(name = "Library", type = MediaSourceType.LOCAL).mediaSourceLoadedStatus()),
        )
        assertEquals(
            "已选择播放：Frieren EP1",
            localizedMediaSourceStatusText(mediaSourceSelectedForPlaybackStatus("Frieren EP1")),
        )
        assertEquals(
            "添加本地媒体源，或载入已保存的媒体源。",
            localizedMediaSourceStatusText("Add a local library source or load an existing one."),
        )
        assertEquals(
            "打开 WebDAV 或 SMB 媒体源后即可浏览文件。",
            localizedMediaSourceStatusText("Open a WebDAV or SMB source to browse it."),
        )
        assertEquals("请先填写本地媒体库路径。", localizedMediaSourceStatusText("Enter a local library root first."))
        assertEquals("请先填写 WebDAV 地址。", localizedMediaSourceStatusText("Enter a WebDAV URL first."))
        assertEquals("请先填写 SMB 地址。", localizedMediaSourceStatusText("Enter an SMB URL first."))
        assertEquals("请先打开媒体源，再开始扫描。", localizedMediaSourceStatusText("Open a source before scanning."))
        assertEquals(
            "请先打开或扫描媒体源，再搜索。",
            localizedMediaSourceStatusText("Open or scan a source before searching."),
        )
        assertEquals(
            "请先打开或扫描媒体源，再清空索引。",
            localizedMediaSourceStatusText("Open or scan a source before clearing its index."),
        )
        assertEquals("请先打开媒体源，再移除。", localizedMediaSourceStatusText("Open a source before removing it."))
        assertEquals(
            "媒体源已移除，关联索引已清空。",
            localizedMediaSourceStatusText("Source removed. Associated index entries were cleared."),
        )
        assertEquals("已经在媒体源根目录。", localizedMediaSourceStatusText("Already at the source root."))
        assertEquals("请先打开远程媒体源，再浏览。", localizedMediaSourceStatusText("Open a remote source before browsing."))
        assertEquals(null, localizedMediaSourceStatusText("custom status"))
        assertEquals("custom status", mediaSourceStatusText("custom status"))
    }

    @Test
    fun `media source status text localizes dynamic source index and playback statuses`() {
        assertEquals("已载入媒体源：Library · 本地", localizedMediaSourceStatusText("Loaded local source: Library"))
        assertEquals("已载入已保存媒体源：Library · 本地", localizedMediaSourceStatusText("Loaded saved local source: Library"))
        assertEquals("WebDAV 媒体源已就绪：Cloud", localizedMediaSourceStatusText("WebDAV source ready: Cloud"))
        assertEquals("扫描完成：12 个视频，3 个目录。", localizedMediaSourceStatusText("Scan complete: 12 videos, 3 directories."))
        assertEquals("已清空媒体源 #42 的索引。", localizedMediaSourceStatusText("Index cleared for source id: 42."))
        assertEquals("正在载入 WebDAV：/Anime", localizedMediaSourceStatusText("Loading WEBDAV /Anime..."))
        assertEquals("Cloud 中显示 1 个条目。", localizedMediaSourceStatusText("Showing 1 item(s) from Cloud."))
        assertEquals("已选择播放：Frieren EP1", localizedMediaSourceStatusText("Selected Frieren EP1 for playback."))
        assertEquals(
            "已选择远程媒体：Episode.mkv。mpv 将通过本地桥接串流。",
            localizedMediaSourceStatusText("Selected remote media: Episode.mkv. mpv will stream through the local bridge."),
        )
        assertEquals("没有匹配 \"frieren\" 的索引媒体。", localizedMediaSourceStatusText("No indexed media matched \"frieren\"."))
        assertEquals("显示 24 条索引视频结果。", localizedMediaSourceStatusText("Showing 24 indexed video result(s)."))
    }
}
