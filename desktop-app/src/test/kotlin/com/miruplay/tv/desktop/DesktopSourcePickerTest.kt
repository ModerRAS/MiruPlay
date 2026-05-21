package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSourcePickerTest {
    @Test
    fun `source picker title keeps source name and type visible`() {
        val source = MediaSourceInfoConventions.local(
            name = "Living Room Anime",
            rootPath = "D:/Anime",
        )

        assertEquals("Living Room Anime · 本地", source.sourcePickerTitle())
    }

    @Test
    fun `source picker title localizes missing source names`() {
        val source = MediaSourceInfo(
            id = 1L,
            name = "",
            type = MediaSourceType.LOCAL,
        )

        assertEquals("本地媒体源 · 本地", source.sourcePickerTitle())
    }

    @Test
    fun `source management controls use TV facing labels`() {
        val labels = desktopLibrarySourceLabels()

        assertEquals("本地媒体库路径", labels.localLibraryRoot)
        assertEquals("索引搜索", labels.indexQuery)
        assertEquals("打开本地", labels.openLocal)
        assertEquals("扫描", labels.scan)
        assertEquals("搜索", labels.search)
        assertEquals("清空索引", labels.clearIndex)
        assertEquals("移除媒体源", labels.removeSource)
        assertEquals("WebDAV 地址", labels.webDavUrl)
        assertEquals("WebDAV 用户名", labels.webDavUser)
        assertEquals("WebDAV 密码", labels.webDavPassword)
        assertEquals("打开 WebDAV", labels.openWebDav)
        assertEquals("SMB 地址", labels.smbUrl)
        assertEquals("SMB 域", labels.smbDomain)
        assertEquals("SMB 用户名", labels.smbUser)
        assertEquals("SMB 密码", labels.smbPassword)
        assertEquals("打开 SMB", labels.openSmb)
        assertEquals("扫描媒体源", labels.scanSource)
        assertEquals("远程浏览", labels.remoteBrowser)
        assertEquals("上级", labels.up)
        assertEquals("先打开一个远程媒体源以浏览文件。", labels.remoteEmpty)
    }

    @Test
    fun `source management statuses use TV facing text`() {
        assertEquals("添加本地媒体源，或载入已保存的媒体源。", desktopLibraryStatusText("Add a local library source or load an existing one."))
        assertEquals("打开 WebDAV 或 SMB 媒体源后即可浏览文件。", desktopLibraryStatusText("Open a WebDAV or SMB source to browse it."))
        assertEquals("请先填写本地媒体库路径。", desktopLibraryStatusText("Enter a local library root first."))
        assertEquals("请先填写 WebDAV 地址。", desktopLibraryStatusText("Enter a WebDAV URL first."))
        assertEquals("请先填写 SMB 地址。", desktopLibraryStatusText("Enter an SMB URL first."))
        assertEquals("请先打开媒体源，再开始扫描。", desktopLibraryStatusText("Open a source before scanning."))
        assertEquals("已载入媒体源：Library · 本地", desktopLibraryStatusText("Loaded local source: Library"))
        assertEquals("已载入已保存媒体源：Library · 本地", desktopLibraryStatusText("Loaded saved local source: Library"))
        assertEquals("WebDAV 媒体源已就绪：Cloud", desktopLibraryStatusText("WebDAV source ready: Cloud"))
        assertEquals("正在扫描：Library", desktopLibraryStatusText("Scanning Library..."))
        assertEquals("扫描完成：12 个视频，3 个目录。", desktopLibraryStatusText("Scan complete: 12 videos, 3 directories."))
        assertEquals("重扫完成：12 个视频，3 个目录。", desktopLibraryStatusText("Rescan complete: 12 videos, 3 directories."))
        assertEquals("请先打开或扫描媒体源，再搜索。", desktopLibraryStatusText("Open or scan a source before searching."))
        assertEquals("请先打开或扫描媒体源，再清空索引。", desktopLibraryStatusText("Open or scan a source before clearing its index."))
        assertEquals("已清空媒体源 #42 的索引。", desktopLibraryStatusText("Index cleared for source id: 42."))
        assertEquals("请先打开媒体源，再移除。", desktopLibraryStatusText("Open a source before removing it."))
        assertEquals("媒体源已移除，关联索引已清空。", desktopLibraryStatusText("Source removed. Associated index entries were cleared."))
        assertEquals("已经在媒体源根目录。", desktopLibraryStatusText("Already at the source root."))
        assertEquals("请先打开远程媒体源，再浏览。", desktopLibraryStatusText("Open a remote source before browsing."))
        assertEquals("正在载入 WebDAV：/Anime", desktopLibraryStatusText("Loading WEBDAV /Anime..."))
        assertEquals("Cloud 中显示 1 个条目。", desktopLibraryStatusText("Showing 1 item(s) from Cloud."))
        assertEquals("已选择播放：Frieren EP1", desktopLibraryStatusText("Selected Frieren EP1 for playback."))
        assertEquals(
            "已选择远程媒体：Episode.mkv。mpv 将通过本地桥接串流。",
            desktopLibraryStatusText("Selected remote media: Episode.mkv. mpv will stream through the local bridge."),
        )
        assertEquals("没有匹配 \"frieren\" 的索引媒体。", desktopLibraryStatusText("No indexed media matched \"frieren\"."))
        assertEquals("显示 24 条索引视频结果。", desktopLibraryStatusText("Showing 24 indexed video result(s)."))
        assertEquals("custom status", desktopLibraryStatusText("custom status"))
    }

    @Test
    fun `source picker subtitle compacts long paths from the middle`() {
        val source = MediaSourceInfoConventions.local(
            name = "Long Library",
            rootPath = "D:/Software/dufs/anime/very-long-library-name/season-one/subfolder/episodes",
        )

        val subtitle = source.sourcePickerSubtitle(maxLength = 38)

        assertTrue(subtitle.length <= 38)
        assertTrue(subtitle.startsWith("D:/Software"))
        assertTrue(subtitle.endsWith("/episodes"))
        assertTrue(subtitle.contains("..."))
    }

    @Test
    fun `source picker subtitle shows configured remote url`() {
        val source = MediaSourceInfoConventions.smb(
            url = "smb://smb.example.test/share/temporary/test",
            username = "test-user",
            password = "test-user",
        )

        assertEquals("smb://smb.example.test/share/temporary/test", source.sourcePickerSubtitle(maxLength = 80))
    }

    @Test
    fun `source picker subtitle handles missing location`() {
        val source = MediaSourceInfo(
            id = 1L,
            name = "Broken",
            type = MediaSourceType.LOCAL,
        )

        assertEquals("未配置路径", source.sourcePickerSubtitle())
    }

    @Test
    fun `source picker directional keys move between saved sources`() {
        val sources = listOf(
            MediaSourceInfoConventions.local(name = "A", rootPath = "D:/A").copy(id = 10L),
            MediaSourceInfoConventions.webDav(url = "https://dav.example.test/anime").copy(id = 11L),
            MediaSourceInfoConventions.smb(url = "smb://nas.local/anime").copy(id = 12L),
        )

        assertEquals(11L, sources.savedSourcePickerNavigationTarget(activeSourceId = 10L, key = Key.DirectionDown)?.id)
        assertEquals(11L, sources.savedSourcePickerNavigationTarget(activeSourceId = 10L, key = Key.DirectionRight)?.id)
        assertEquals(10L, sources.savedSourcePickerNavigationTarget(activeSourceId = 11L, key = Key.DirectionUp)?.id)
        assertEquals(10L, sources.savedSourcePickerNavigationTarget(activeSourceId = 11L, key = Key.DirectionLeft)?.id)
        assertEquals(10L, sources.savedSourcePickerNavigationTarget(activeSourceId = null, key = Key.DirectionDown)?.id)
        assertEquals(12L, sources.savedSourcePickerNavigationTarget(activeSourceId = null, key = Key.DirectionUp)?.id)
        assertNull(sources.savedSourcePickerNavigationTarget(activeSourceId = 12L, key = Key.DirectionDown))
        assertNull(sources.savedSourcePickerNavigationTarget(activeSourceId = 10L, key = Key.DirectionUp))
    }

    @Test
    fun `source management action focus stays within visible button groups`() {
        assertEquals(
            LibrarySourceAction.Scan,
            librarySourceActionNavigationTarget(LibrarySourceAction.OpenLocal, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceAction.Search,
            librarySourceActionNavigationTarget(LibrarySourceAction.Scan, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceAction.ClearIndex,
            librarySourceActionNavigationTarget(LibrarySourceAction.Search, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceAction.Search,
            librarySourceActionNavigationTarget(LibrarySourceAction.ClearIndex, Key.DirectionLeft),
        )
        assertEquals(
            LibrarySourceAction.RemoveSource,
            librarySourceActionNavigationTarget(LibrarySourceAction.ClearIndex, Key.DirectionDown),
        )
        assertEquals(
            LibrarySourceAction.ClearIndex,
            librarySourceActionNavigationTarget(LibrarySourceAction.RemoveSource, Key.DirectionUp),
        )
        assertNull(librarySourceActionNavigationTarget(LibrarySourceAction.OpenLocal, Key.DirectionLeft))
        assertNull(librarySourceActionNavigationTarget(LibrarySourceAction.RemoveSource, Key.DirectionRight))
        assertNull(librarySourceActionNavigationTarget(LibrarySourceAction.RemoveSource, Key.DirectionDown))
    }

    @Test
    fun `source management fields bridge into action rows`() {
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery),
            librarySourceFieldFocusTarget(LibrarySourceField.LocalRoot, Key.DirectionDown),
        )
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.LocalRoot),
            librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, Key.DirectionUp),
        )
        assertEquals(
            LibrarySourceFocusTarget.PreviousPanel,
            librarySourceFieldFocusTarget(LibrarySourceField.LocalRoot, Key.DirectionUp),
        )
        assertEquals(
            LibrarySourceFocusTarget.Action(LibrarySourceAction.OpenLocal),
            librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, Key.DirectionRight),
        )
        assertEquals(
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery),
            librarySourceActionFocusTarget(LibrarySourceAction.OpenLocal, Key.DirectionLeft),
        )
        assertEquals(
            LibrarySourceFocusTarget.Action(LibrarySourceAction.Scan),
            librarySourceActionFocusTarget(LibrarySourceAction.OpenLocal, Key.DirectionRight),
        )
        assertNull(librarySourceFieldFocusTarget(LibrarySourceField.IndexQuery, Key.DirectionLeft))
    }

    @Test
    fun `remote source action focus follows editor card layout`() {
        assertEquals(
            RemoteSourceAction.OpenSmb,
            remoteSourceActionNavigationTarget(RemoteSourceAction.OpenWebDav, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceAction.ScanSource,
            remoteSourceActionNavigationTarget(RemoteSourceAction.OpenSmb, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceAction.OpenSmb,
            remoteSourceActionNavigationTarget(RemoteSourceAction.ScanSource, Key.DirectionLeft),
        )
        assertEquals(
            RemoteSourceAction.OpenWebDav,
            remoteSourceActionNavigationTarget(RemoteSourceAction.ScanSource, Key.DirectionUp),
        )
        assertNull(remoteSourceActionNavigationTarget(RemoteSourceAction.OpenWebDav, Key.DirectionUp))
        assertNull(remoteSourceActionNavigationTarget(RemoteSourceAction.ScanSource, Key.DirectionDown))
        assertNull(remoteSourceActionNavigationTarget(RemoteSourceAction.OpenSmb, Key.DirectionLeft))
    }

    @Test
    fun `remote source fields bridge into editor actions`() {
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavUsername),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavPassword),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUsername, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.OpenWebDav),
            remoteSourceFieldFocusTarget(RemoteSourceField.WebDavPassword, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavPassword),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenWebDav, Key.DirectionUp),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain),
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbUrl, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.SmbPassword),
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbUsername, Key.DirectionRight),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.ScanSource),
            remoteSourceFieldFocusTarget(RemoteSourceField.SmbPassword, Key.DirectionDown),
        )
        assertEquals(
            RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, Key.DirectionUp),
        )
        assertEquals(
            RemoteSourceFocusTarget.Action(RemoteSourceAction.ScanSource),
            remoteSourceActionFocusTarget(RemoteSourceAction.OpenSmb, Key.DirectionRight),
        )
        assertNull(remoteSourceFieldFocusTarget(RemoteSourceField.WebDavUrl, Key.DirectionUp))
        assertNull(remoteSourceFieldFocusTarget(RemoteSourceField.SmbPassword, Key.DirectionRight))
    }

    @Test
    fun `remote source preview falls back and compacts endpoints`() {
        assertEquals("填写 SMB 共享地址", remoteSourcePreview("", fallback = "填写 SMB 共享地址", maxLength = 20))

        val preview = remoteSourcePreview(
            value = "smb://smb.example.test/share/temporary/test/very/deep/folder/with/long/name",
            fallback = "fallback",
            maxLength = 42,
        )

        assertTrue(preview.length <= 42)
        assertTrue("Preview should keep the endpoint host prefix: $preview", preview.startsWith("smb://smb.example."))
        assertTrue(preview.endsWith("/long/name"))
        assertTrue(preview.contains("..."))
    }

    @Test
    fun `remote browser path preview keeps root readable`() {
        assertEquals("/", remoteBrowserPathPreview("", maxLength = 20))

        val preview = remoteBrowserPathPreview(
            path = "/Fixture WebDAV/Season 01/Subfolder With A Very Long Name/Episode.mkv",
            maxLength = 36,
        )

        assertTrue(preview.length <= 36)
        assertTrue(preview.startsWith("/Fixture"))
        assertTrue(preview.endsWith("Episode.mkv"))
        assertTrue(preview.contains("..."))
    }

    @Test
    fun `remote browser first row up key maps to parent navigation`() {
        assertTrue(remoteBrowserShouldNavigateUp(currentIndex = 0, key = Key.DirectionUp))
        assertFalse(remoteBrowserShouldNavigateUp(currentIndex = 1, key = Key.DirectionUp))
        assertFalse(remoteBrowserShouldNavigateUp(currentIndex = 0, key = Key.DirectionDown))
    }
}
