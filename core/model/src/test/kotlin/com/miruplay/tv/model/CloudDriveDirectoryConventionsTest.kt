package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudDriveDirectoryConventionsTest {
    @Test
    fun `normalizes CloudDrive directory paths across platforms`() {
        assertEquals("/", normalizeCloudDriveDirectoryPath(""))
        assertEquals("/", normalizeCloudDriveDirectoryPath(" / "))
        assertEquals("/Anime/Season 1", normalizeCloudDriveDirectoryPath("Anime\\Season 1\\"))
        assertEquals("/CloudRoot/Anime", normalizeCloudDriveDirectoryPath("/CloudRoot/Anime/"))
    }

    @Test
    fun `scopes navigation requests to token root`() {
        assertEquals("/CloudRoot", scopedCloudDriveDirectoryPath("/", "/CloudRoot"))
        assertEquals("/CloudRoot", scopedCloudDriveDirectoryPath("/Outside/Inbox", "/CloudRoot"))
        assertEquals("/CloudRoot/Inbox", scopedCloudDriveDirectoryPath("/CloudRoot/Inbox", "/CloudRoot"))
        assertEquals("/Outside", scopedCloudDriveDirectoryPath("/Outside", "/"))
    }

    @Test
    fun `calculates parent paths without escaping token root`() {
        assertEquals("/CloudRoot", cloudDriveDirectoryParentPath("/CloudRoot/Inbox", "/CloudRoot"))
        assertEquals("/CloudRoot/Inbox", cloudDriveDirectoryParentPath("/CloudRoot/Inbox/Season", "/CloudRoot"))
        assertNull(cloudDriveDirectoryParentPath("/CloudRoot", "/CloudRoot"))
        assertNull(cloudDriveDirectoryParentPath("/", "/"))
        assertEquals("/", cloudDriveDirectoryParentPath("/Anime", "/"))
    }

    @Test
    fun `formats root display path consistently`() {
        assertEquals(CLOUD_DRIVE_ROOT_DISPLAY_NAME, cloudDriveDirectoryDisplayPath("/"))
        assertEquals("/CloudRoot", cloudDriveDirectoryDisplayPath("/CloudRoot"))
    }

    @Test
    fun `directory browser labels cover local and CloudDrive variants`() {
        assertEquals("选择本地媒体文件夹", directoryBrowserTitleLabel(isLocal = true))
        assertEquals("选择目录", directoryBrowserTitleLabel(isLocal = false))
        assertEquals("上一级", directoryBrowserParentLabel())
        assertEquals("上一级", directoryBrowserParentActionLabel(isLocal = true))
        assertEquals("返回上级", directoryBrowserParentActionLabel(isLocal = false))
        assertEquals("正在读取目录...", directoryBrowserLoadingMessage(isLocal = true))
        assertEquals("正在读取 CloudDrive2 目录...", directoryBrowserLoadingMessage(isLocal = false))
        assertEquals("没有可进入的子文件夹。", directoryBrowserEmptyMessage(isLocal = true))
        assertEquals("当前目录没有可进入的子目录。", directoryBrowserEmptyMessage(isLocal = false))
        assertEquals("取消", directoryBrowserCancelActionLabel())
        assertEquals("关闭", directoryBrowserCloseActionLabel())
        assertEquals("选择当前目录", directoryBrowserSelectCurrentActionLabel())
        assertEquals("选择当前目录", directoryBrowserUseCurrentActionLabel(isLocal = true))
        assertEquals("使用当前目录", directoryBrowserUseCurrentActionLabel(isLocal = false))
        assertEquals(DIRECTORY_BROWSER_ROOT_DISPLAY_NAME, directoryBrowserRootDisplayName(isLocal = true))
        assertEquals(CLOUD_DRIVE_ROOT_DISPLAY_NAME, directoryBrowserRootDisplayName(isLocal = false))
    }

    @Test
    fun `directory items keep visible folders in case-insensitive order`() {
        val items = cloudDriveDirectoryItems(
            listOf(
                CloudDriveDirectoryItem("Season B", "/CloudRoot/Season B"),
                CloudDriveDirectoryItem(".hidden", "/CloudRoot/.hidden"),
                CloudDriveDirectoryItem("season a", "/CloudRoot/season a"),
                CloudDriveDirectoryItem("", "/CloudRoot/Extras"),
                CloudDriveDirectoryItem("", ""),
            ),
        )

        assertEquals(listOf("Extras", "season a", "Season B"), items.map { it.name })
        assertEquals(listOf("/CloudRoot/Extras", "/CloudRoot/season a", "/CloudRoot/Season B"), items.map { it.path })
    }
}
