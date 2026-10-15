package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSourceDisplayConventionsTest {
    @Test
    fun `media source type display conventions are shared`() {
        assertEquals("本地", MediaSourceType.LOCAL.tvLabel().fallback)
        assertEquals("WebDAV", MediaSourceType.WEBDAV.tvLabel().fallback)
        assertEquals("SMB", MediaSourceType.SMB.tvLabel().fallback)

        assertEquals("本地", MediaSourceType.LOCAL.tvBadgeLabel().fallback)
        assertEquals("DAV", MediaSourceType.WEBDAV.tvBadgeLabel().fallback)
        assertEquals("SMB", MediaSourceType.SMB.tvBadgeLabel().fallback)

        assertEquals("本地下载", MediaSourceType.LOCAL.defaultSourceName().fallback)
        assertEquals("WebDAV 媒体库", MediaSourceType.WEBDAV.defaultSourceName().fallback)
        assertEquals("SMB 共享", MediaSourceType.SMB.defaultSourceName().fallback)

        assertEquals("本地媒体源", MediaSourceType.LOCAL.genericSourceName().fallback)
        assertEquals("WebDAV 媒体源", MediaSourceType.WEBDAV.genericSourceName().fallback)
        assertEquals("SMB 媒体源", MediaSourceType.SMB.genericSourceName().fallback)

        assertEquals("设备文件夹", MediaSourceType.LOCAL.tvSourceHint().fallback)
        assertEquals("HTTP 文件服务", MediaSourceType.WEBDAV.tvSourceHint().fallback)
        assertEquals("局域网共享", MediaSourceType.SMB.tvSourceHint().fallback)

        assertEquals("媒体文件夹", MediaSourceType.LOCAL.tvLocationLabel().fallback)
        assertEquals("WebDAV 地址", MediaSourceType.WEBDAV.tvLocationLabel().fallback)
        assertEquals("SMB 地址", MediaSourceType.SMB.tvLocationLabel().fallback)
    }

    @Test
    fun `media source display label falls back to shared default names`() {
        assertEquals(
            "Living Room · 本地",
            MediaSourceInfo(name = "Living Room", type = MediaSourceType.LOCAL).tvDisplayLabel().fallback,
        )
        assertEquals(
            "本地下载 · 本地",
            MediaSourceInfo(name = "", type = MediaSourceType.LOCAL).tvDisplayLabel().fallback,
        )
        assertEquals(
            "本地媒体源 · 本地",
            MediaSourceInfo(name = "", type = MediaSourceType.LOCAL).tvDisplayLabel(fallbackName = "本地媒体源").fallback,
        )
    }

    @Test
    fun `media source status label combines shared type and connection copy`() {
        assertEquals(
            "WebDAV · 可连接",
            MediaSourceInfo(name = "Remote", type = MediaSourceType.WEBDAV, isConnected = true)
                .tvDisplayStatusLabel().fallback,
        )
        assertEquals(
            "SMB · 待验证",
            MediaSourceInfo(name = "Share", type = MediaSourceType.SMB, isConnected = false)
                .tvDisplayStatusLabel().fallback,
        )
    }

    @Test
    fun `source picker display uses shared labels and compact location`() {
        val local = MediaSourceInfoConventions.local(
            name = "Living Room Anime",
            rootPath = "D:/Anime/Shows/VeryLongSeasonFolder/Episodes",
        )
        val missing = MediaSourceInfo(id = 1L, name = "", type = MediaSourceType.WEBDAV)

        assertEquals("Living Room Anime · 本地", local.sourcePickerTitle().fallback)
        assertEquals("WebDAV 媒体源 · WebDAV", missing.sourcePickerTitle().fallback)
        assertEquals(mediaSourceLocationMissingLabel().fallback, missing.sourcePickerSubtitle())

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
        assertEquals("显示 9-16 / 17 个条目，按上/下继续翻页。", remoteBrowserPageSummary(8, 8, 17)?.fallback)
        assertEquals("显示 17-17 / 17 个条目，按上/下继续翻页。", remoteBrowserPageSummary(16, 1, 17)?.fallback)
        assertNull(remoteBrowserPageSummary(0, 4, 4))
    }

    @Test
    fun `media source management labels are shared`() {
        assertEquals("媒体源", mediaSourceListTitleLabel().fallback)
        assertEquals("还没有配置媒体源", mediaSourceEmptyListMessage().fallback)
        assertEquals("已配置 3 个源", mediaSourceConfiguredCountLabel(3).fallback)
        assertEquals("添加媒体源", mediaSourceFormTitleLabel(isEditing = false).fallback)
        assertEquals("编辑媒体源", mediaSourceFormTitleLabel(isEditing = true).fallback)
        assertEquals("选择媒体库所在位置，保存后可在首页手动扫描。", mediaSourceFormDescriptionLabel(isEditing = false).fallback)
        assertEquals("修改媒体库位置或凭据，保存后会覆盖当前配置。", mediaSourceFormDescriptionLabel(isEditing = true).fallback)
        assertEquals("新建", mediaSourceNewActionLabel().fallback)
        assertEquals("显示名称", mediaSourceDisplayNameFieldLabel().fallback)
        assertEquals("本地媒体库路径", mediaSourceLocalLibraryRootFieldLabel().fallback)
        assertEquals("索引搜索", mediaSourceIndexQueryFieldLabel().fallback)
        assertEquals("打开本地", MediaSourceType.LOCAL.openSourceActionLabel().fallback)
        assertEquals("打开 WebDAV", MediaSourceType.WEBDAV.openSourceActionLabel().fallback)
        assertEquals("打开 SMB", MediaSourceType.SMB.openSourceActionLabel().fallback)
        assertEquals("扫描", mediaSourceScanActionLabel().fallback)
        assertEquals("搜索", mediaSourceSearchActionLabel().fallback)
        assertEquals("清空索引", mediaSourceClearIndexActionLabel().fallback)
        assertEquals("移除媒体源", mediaSourceRemoveActionLabel().fallback)
        assertEquals("WebDAV 用户名", MediaSourceType.WEBDAV.sourceUsernameFieldLabel().fallback)
        assertEquals("WebDAV 密码", MediaSourceType.WEBDAV.sourcePasswordFieldLabel().fallback)
        assertEquals("SMB 域", mediaSourceSmbDomainFieldLabel().fallback)
        assertEquals("扫描媒体源", mediaSourceScanSourceActionLabel().fallback)
        assertEquals("远程浏览", mediaSourceRemoteBrowserTitleLabel().fallback)
        assertEquals("上级", mediaSourceUpActionLabel().fallback)
        assertEquals("先打开一个远程媒体源以浏览文件。", mediaSourceRemoteBrowserEmptyMessage().fallback)
        assertEquals("目录", mediaSourceRemoteBrowserItemTypeLabel(isDirectory = true).fallback)
        assertEquals("视频", mediaSourceRemoteBrowserItemTypeLabel(isDirectory = false).fallback)
        assertEquals("个条目", mediaSourceRemoteBrowserPageUnitLabel().fallback)
        assertEquals("已保存媒体源", mediaSourceSavedPickerTitleLabel().fallback)
        assertEquals("选择已配置媒体源", mediaSourceSavedPickerSubtitleLabel().fallback)
        assertEquals("没有已保存媒体源", mediaSourceSavedPickerEmptyMessage().fallback)
        assertEquals("未配置路径", mediaSourceLocationMissingLabel().fallback)
        assertEquals("填写 WebDAV 地址", MediaSourceType.WEBDAV.sourceEndpointPlaceholderLabel().fallback)
        assertEquals("填写 SMB 共享地址", MediaSourceType.SMB.sourceEndpointPlaceholderLabel().fallback)
        assertEquals("测试中", mediaSourceTestConnectionActionLabel(isTesting = true).fallback)
        assertEquals("测试连接", mediaSourceTestConnectionActionLabel(isTesting = false).fallback)
        assertEquals("更新源", mediaSourceSaveActionLabel(isEditing = true).fallback)
        assertEquals("保存源", mediaSourceSaveActionLabel(isEditing = false).fallback)
        assertEquals("尚未选择文件夹", mediaSourceLocalFolderEmptyLabel().fallback)
        assertEquals("已授权访问", mediaSourceLocalFolderAuthorizedLabel().fallback)
        assertEquals("本地媒体库", mediaSourceLocalLibraryFallbackName().fallback)
        assertEquals("Download", mediaSourceLocalPathDisplayName("/storage/emulated/0/Download"))
        assertEquals("Anime", mediaSourceLocalPathDisplayName("D:\\Media\\Anime\\"))
        assertEquals("本地媒体库", mediaSourceLocalPathDisplayName(" / "))
        assertEquals("Download", mediaSourceLocalScanDisplayName("/storage/emulated/0/Download"))
        assertEquals("Anime Library", mediaSourceLocalScanDisplayName("content://tree/primary%3AAnime%20Library"))
        assertEquals("选择文件夹", mediaSourceChooseFolderActionLabel().fallback)
        assertEquals("连接正常，可以保存并返回首页扫描。", mediaSourceConnectionSuccessMessage().fallback)
        assertEquals("正在验证连接...", mediaSourceConnectionTestingMessage().fallback)
    }

    @Test
    fun `media source status helpers are shared`() {
        val local = MediaSourceInfo(id = 1L, name = "Library", type = MediaSourceType.LOCAL)
        val webDav = MediaSourceInfo(id = 2L, name = "Cloud", type = MediaSourceType.WEBDAV)
        val smb = MediaSourceInfo(id = 3L, name = "NAS", type = MediaSourceType.SMB)

        assertEquals("添加本地媒体源，或载入已保存的媒体源。", mediaSourceLocalLibraryInitialStatus().fallback)
        assertEquals("打开 WebDAV 或 SMB 媒体源后即可浏览文件。", mediaSourceRemoteBrowserInitialStatus().fallback)
        assertEquals("已载入媒体源：Library · 本地", local.mediaSourceLoadedStatus().fallback)
        assertEquals("已载入已保存媒体源：Library · 本地", local.mediaSourceLoadedStatus(saved = true).fallback)
        assertEquals("已载入媒体源：Cloud · WebDAV", webDav.mediaSourceLoadedStatus().fallback)
        assertEquals("已载入媒体源：NAS · SMB", smb.mediaSourceLoadedStatus().fallback)
        assertEquals("本地媒体源已就绪：Library", local.mediaSourceReadyStatus().fallback)
        assertEquals("WebDAV 媒体源已就绪：Cloud", webDav.mediaSourceReadyStatus().fallback)
        assertEquals("SMB 媒体源已就绪：NAS", smb.mediaSourceReadyStatus().fallback)
        assertEquals("Anime", local.scanResultDisplayName("D:/Media/Anime"))
        assertEquals(
            "Authorized Anime",
            local.copy(connectionInfo = mapOf(MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME to "Authorized Anime"))
                .scanResultDisplayName("D:/Media/Anime"),
        )
        assertEquals("Cloud", webDav.scanResultDisplayName("/remote/root"))
        assertEquals("NAS", smb.scanResultDisplayName("/remote/root"))
        assertEquals("请先填写本地媒体库路径。", mediaSourceLocalRootRequiredStatus().fallback)
        assertEquals("请先填写 WebDAV 地址。", mediaSourceWebDavUrlRequiredStatus().fallback)
        assertEquals("请先填写 SMB 地址。", mediaSourceSmbUrlRequiredStatus().fallback)
        assertEquals("请先打开媒体源，再开始扫描。", mediaSourceOpenBeforeScanningStatus().fallback)
        assertEquals("请先打开或扫描媒体源，再搜索。", mediaSourceOpenBeforeSearchingStatus().fallback)
        assertEquals("请先打开或扫描媒体源，再清空索引。", mediaSourceOpenBeforeClearingIndexStatus().fallback)
        assertEquals("已清空媒体源 #42 的索引。", mediaSourceIndexClearedStatus(42L).fallback)
        assertEquals("请先打开媒体源，再移除。", mediaSourceRemoveRequiredStatus().fallback)
        assertEquals("媒体源已移除，关联索引已清空。", mediaSourceRemovedStatus().fallback)
        assertEquals("已经在媒体源根目录。", mediaSourceAlreadyAtRootStatus().fallback)
        assertEquals("请先打开远程媒体源，再浏览。", mediaSourceOpenRemoteBeforeBrowsingStatus().fallback)
        assertEquals("正在载入 WebDAV：/Anime", webDav.mediaSourceLoadingRemoteDirectoryStatus("/Anime").fallback)
        assertEquals("Cloud 中显示 2 个条目。", webDav.mediaSourceShowingRemoteDirectoryStatus(2).fallback)
        assertEquals("已选择播放：Frieren EP1", mediaSourceSelectedForPlaybackStatus("Frieren EP1").fallback)
        assertEquals("已选择远程媒体：Episode.mkv。mpv 将通过本地桥接串流。", mediaSourceSelectedRemoteForPlaybackStatus("Episode.mkv").fallback)
        assertEquals("没有匹配 \"frieren\" 的索引媒体。", mediaSourceIndexedSearchStatus(" frieren ", false, 0).fallback)
        assertEquals("显示 24 条索引视频结果。", mediaSourceIndexedSearchStatus("frieren", true, 24).fallback)
    }

    @Test
    fun `media source status text accepts shared and legacy wire statuses`() {
        assertEquals(
            "添加本地媒体源，或载入已保存的媒体源。",
            localizedMediaSourceStatusText(mediaSourceLocalLibraryInitialStatus().fallback),
        )
        assertEquals(
            "已载入媒体源：Library · 本地",
            localizedMediaSourceStatusText(MediaSourceInfo(name = "Library", type = MediaSourceType.LOCAL).mediaSourceLoadedStatus().fallback),
        )
        assertEquals(
            "已选择播放：Frieren EP1",
            localizedMediaSourceStatusText(mediaSourceSelectedForPlaybackStatus("Frieren EP1").fallback),
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
