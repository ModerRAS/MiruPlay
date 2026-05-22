package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceDisplayConventionsTest {
    @Test
    fun `media source type display conventions are shared by TV and desktop`() {
        assertEquals("本地", MediaSourceType.LOCAL.tvLabel())
        assertEquals("WebDAV", MediaSourceType.WEBDAV.tvLabel())
        assertEquals("SMB", MediaSourceType.SMB.tvLabel())

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
}
