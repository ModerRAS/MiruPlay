package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebControlSourceRequestsTest {
    @Test
    fun `source request builds local source with shared connection keys`() {
        val source = SourceRequest(
            id = 7L,
            name = "  ",
            type = " local ",
            location = " D:/Anime ",
            displayName = " Anime Drive ",
        ).toMediaSourceInfo(isConnected = true, lastScanned = 123L)

        assertEquals(7L, source.id)
        assertEquals("本地媒体库", source.name)
        assertEquals(MediaSourceType.LOCAL, source.type)
        assertEquals(true, source.isConnected)
        assertEquals(123L, source.lastScanned)
        assertEquals("D:/Anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
        assertEquals("D:/Anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_PATH])
        assertEquals("Anime Drive", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME])
    }

    @Test
    fun `source request preserves fallback password when password is blank`() {
        val source = SourceRequest(
            name = "Remote",
            type = "WEBDAV",
            location = " https://dav.example.test/root ",
            username = " miru ",
            password = " ",
        ).toMediaSourceInfo(fallbackPassword = "old-secret")

        assertEquals("https://dav.example.test/root", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
        assertEquals("miru", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_USERNAME])
        assertEquals("old-secret", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
    }

    @Test
    fun `source request uses new password and default remote names`() {
        val webDav = SourceRequest(
            name = "",
            type = "webdav",
            location = "https://dav.example.test",
            password = "new-secret",
        ).toMediaSourceInfo(fallbackPassword = "old-secret")
        val smb = SourceRequest(
            name = "",
            type = "smb",
            location = "\\\\NAS\\Anime",
        ).toMediaSourceInfo()

        assertEquals("WebDAV 媒体库", webDav.name)
        assertEquals("new-secret", webDav.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD])
        assertEquals("SMB 共享", smb.name)
        assertEquals("smb://NAS/Anime", smb.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
    }

    @Test
    fun `source test request builds sanitized connection info`() {
        val source = SourceTestRequest(
            type = "local",
            location = " content://tree/anime ",
            displayName = "Tree",
        ).toMediaSourceInfo()

        assertEquals("content://tree/anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URL])
        assertEquals("content://tree/anime", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_URI])
        assertEquals("Tree", source.connectionInfo[MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME])
    }

    @Test
    fun `safe api source removes connection password case insensitively`() {
        val source = SourceRequest(
            name = "Remote",
            type = "webdav",
            location = "https://dav.example.test",
            password = "secret",
        ).toMediaSourceInfo()

        val safe = source.copy(
            connectionInfo = source.connectionInfo + ("Password" to "other-secret"),
        ).safeForApi()

        assertFalse(MediaSourceInfoConventions.CONNECTION_PASSWORD in safe.connectionInfo)
        assertFalse("Password" in safe.connectionInfo)
    }

    @Test
    fun `source test result maps success and disconnected messages`() {
        val connected = Result.success(true).toWebControlSourceTestResponse()
        val disconnected = Result.success(false).toWebControlSourceTestResponse()

        assertEquals(true, connected.connected)
        assertEquals("连接正常", connected.message)
        assertEquals(false, disconnected.connected)
        assertEquals("无法连接", disconnected.message)
    }

    @Test
    fun `source test result uses user facing error messages`() {
        val response = Result.failure(
            AppError.MediaSourceError.AuthenticationFailed("WebDAV"),
        ).toWebControlSourceTestResponse()

        assertEquals(false, response.connected)
        assertEquals("WebDAV 认证失败，请检查用户名和密码", response.message)
    }
}
