package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSourcePickerTest {
    @Test
    fun `source picker title keeps source name and type visible`() {
        val source = MediaSourceInfoConventions.local(
            name = "Living Room Anime",
            rootPath = "D:/Anime",
        )

        assertEquals("Living Room Anime · LOCAL", source.sourcePickerTitle())
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
