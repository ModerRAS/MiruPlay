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
            url = "smb://smb.ynz.local/share/temporary/test",
            username = "ynsz",
            password = "ynsz",
        )

        assertEquals("smb://smb.ynz.local/share/temporary/test", source.sourcePickerSubtitle(maxLength = 80))
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
    fun `remote source preview falls back and compacts endpoints`() {
        assertEquals("填写 SMB 共享地址", remoteSourcePreview("", fallback = "填写 SMB 共享地址", maxLength = 20))

        val preview = remoteSourcePreview(
            value = "smb://smb.ynz.local/share/temporary/test/very/deep/folder/with/long/name",
            fallback = "fallback",
            maxLength = 42,
        )

        assertTrue(preview.length <= 42)
        assertTrue(preview.startsWith("smb://smb.ynz"))
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
