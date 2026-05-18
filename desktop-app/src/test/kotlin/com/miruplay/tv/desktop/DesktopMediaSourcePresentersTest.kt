package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMediaSourcePresentersTest {
    @Test
    fun `webdav source info stores optional credentials only when present`() {
        val source = webDavSourceInfo(
            url = "https://dav.example.test/anime/",
            username = "alice",
            password = "",
        )

        assertEquals("dav.example.test/anime", source.name)
        assertEquals(MediaSourceType.WEBDAV, source.type)
        assertEquals("https://dav.example.test/anime/", source.connectionInfo.getValue("url"))
        assertEquals("alice", source.connectionInfo.getValue("username"))
        assertTrue("password" !in source.connectionInfo)
        assertTrue(source.isConnected)
    }

    @Test
    fun `smb source info normalizes root and stores optional credentials`() {
        val source = smbSourceInfo(
            url = "\\\\nas\\anime",
            domain = "WORKGROUP",
            username = "alice",
            password = "secret",
        )

        assertEquals("nas/anime", source.name)
        assertEquals(MediaSourceType.SMB, source.type)
        assertEquals("smb://nas/anime", source.connectionInfo.getValue("url"))
        assertEquals("WORKGROUP", source.connectionInfo.getValue("domain"))
        assertEquals("alice", source.connectionInfo.getValue("username"))
        assertEquals("secret", source.connectionInfo.getValue("password"))
    }

    @Test
    fun `desktop source from info keeps remote source adapters`() {
        val webDav = MediaSourceInfo(
            name = "Cloud",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "https://dav.example.test/anime"),
        )
        val smb = MediaSourceInfo(
            name = "NAS",
            type = MediaSourceType.SMB,
            connectionInfo = mapOf("url" to "smb://nas/anime"),
        )

        assertEquals(MediaSourceType.WEBDAV, desktopSourceFromInfo(webDav).info.type)
        assertEquals(MediaSourceType.SMB, desktopSourceFromInfo(smb).info.type)
    }
}
