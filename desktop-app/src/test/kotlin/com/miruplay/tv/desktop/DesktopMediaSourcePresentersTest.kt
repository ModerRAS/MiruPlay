package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopMediaSourcePresentersTest {
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
