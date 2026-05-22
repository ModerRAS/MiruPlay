package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopSmbMediaSourceTest {
    @Test
    fun `normalizeRoot accepts UNC and SMB URLs`() {
        assertEquals(
            "smb://nas.local/anime",
            DesktopSmbMediaSource.normalizeRoot("\\\\nas.local\\anime\\")
        )
        assertEquals(
            "smb://nas.local/anime",
            DesktopSmbMediaSource.normalizeRoot("smb://nas.local/anime/")
        )
    }

    @Test
    fun `resolveUrl joins and encodes each remote path segment`() {
        val source = DesktopSmbMediaSource.create("NAS", "smb://nas.local/anime")

        val url = source.resolveUrl("/孤独摇滚/Season 01/Episode 01.mkv")

        assertEquals(
            "smb://nas.local/anime/%E5%AD%A4%E7%8B%AC%E6%91%87%E6%BB%9A/Season%2001/Episode%2001.mkv",
            url,
        )
    }

    @Test
    fun `create stores SMB credentials and domain`() {
        val source = DesktopSmbMediaSource.create(
            name = "NAS",
            url = "\\\\nas.local\\anime",
            username = "user",
            password = "pass",
            domain = "WORKGROUP",
        )

        assertEquals(MediaSourceType.SMB, source.info.type)
        assertEquals("smb://nas.local/anime", source.info.connectionInfo["url"])
        assertEquals("user", source.info.connectionInfo["username"])
        assertEquals("pass", source.info.connectionInfo["password"])
        assertEquals("WORKGROUP", source.info.connectionInfo["domain"])
    }

}
