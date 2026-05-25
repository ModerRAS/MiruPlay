package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMediaSourceFactoryTest {
    @Test
    fun `desktop source from info keeps local and remote source adapters`() {
        val local = MediaSourceInfo(
            name = "Local",
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf("path" to "D:/Anime"),
        )
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

        assertTrue(desktopSourceFromInfo(local) is DesktopLocalMediaSource)
        assertTrue(desktopSourceFromInfo(webDav) is DesktopWebDavMediaSource)
        assertTrue(desktopSourceFromInfo(smb) is DesktopSmbMediaSource)
    }

    @Test
    fun `desktop media source factory implements shared contract`() {
        val factory = DesktopMediaSourceFactory()
        val local = MediaSourceInfo(
            name = "Local",
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf("path" to "D:/Anime"),
        )

        assertTrue(factory.supports(MediaSourceType.LOCAL))
        assertTrue(factory.supports(MediaSourceType.WEBDAV))
        assertTrue(factory.supports(MediaSourceType.SMB))
        assertTrue(factory.create(local).getOrNull() is DesktopLocalMediaSource)
    }

    @Test
    fun `typed factories preserve source info`() {
        val webDav = MediaSourceInfo(
            id = 42L,
            name = "Cloud",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "https://dav.example.test/anime"),
        )

        assertEquals(webDav, desktopWebDavSourceFromInfo(webDav).info)
    }
}
