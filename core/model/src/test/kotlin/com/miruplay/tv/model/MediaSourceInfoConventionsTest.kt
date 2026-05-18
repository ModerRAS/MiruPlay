package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceInfoConventionsTest {
    @Test
    fun `webDav stores optional credentials only when present`() {
        val source = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/anime/",
            username = "alice",
            password = "",
            isConnected = true,
        )

        assertEquals("dav.example.test/anime", source.name)
        assertEquals(MediaSourceType.WEBDAV, source.type)
        assertEquals("https://dav.example.test/anime/", source.connectionInfo.getValue("url"))
        assertEquals("alice", source.connectionInfo.getValue("username"))
        assertFalse("password" in source.connectionInfo)
        assertTrue(source.isConnected)
    }

    @Test
    fun `smb normalizes root and stores optional credentials`() {
        val source = MediaSourceInfoConventions.smb(
            url = "\\\\nas\\anime",
            domain = "WORKGROUP",
            username = "alice",
            password = "secret",
            isConnected = true,
        )

        assertEquals("nas/anime", source.name)
        assertEquals(MediaSourceType.SMB, source.type)
        assertEquals("smb://nas/anime", source.connectionInfo.getValue("url"))
        assertEquals("WORKGROUP", source.connectionInfo.getValue("domain"))
        assertEquals("alice", source.connectionInfo.getValue("username"))
        assertEquals("secret", source.connectionInfo.getValue("password"))
        assertTrue(source.isConnected)
    }

    @Test
    fun `normalizeSmbRoot accepts UNC and SMB urls`() {
        assertEquals(
            "smb://nas.local/anime",
            MediaSourceInfoConventions.normalizeSmbRoot("\\\\nas.local\\anime\\"),
        )
        assertEquals(
            "smb://nas.local/anime",
            MediaSourceInfoConventions.normalizeSmbRoot("smb://nas.local/anime/"),
        )
    }

    @Test
    fun `local stores root path`() {
        val source = MediaSourceInfoConventions.local("Library", "D:/Anime")

        assertEquals("Library", source.name)
        assertEquals(MediaSourceType.LOCAL, source.type)
        assertEquals("D:/Anime", source.connectionInfo.getValue("path"))
        assertTrue(source.isConnected)
    }

    @Test
    fun `shouldBridgeForPlayback identifies credentialed remote paths`() {
        assertFalse(
            MediaSourceInfoConventions.shouldBridgeForPlayback(
                sourceType = null,
                path = " https://example.test/video.mkv ",
            )
        )
        assertFalse(
            MediaSourceInfoConventions.shouldBridgeForPlayback(
                sourceType = MediaSourceType.LOCAL,
                path = "D:/Anime/video.mkv",
            )
        )
        assertTrue(
            MediaSourceInfoConventions.shouldBridgeForPlayback(
                sourceType = MediaSourceType.WEBDAV,
                path = "/Anime/Episode 01.mkv",
            )
        )
        assertTrue(
            MediaSourceInfoConventions.shouldBridgeForPlayback(
                sourceType = MediaSourceType.SMB,
                path = "smb://nas/anime/Episode 01.mkv",
            )
        )
    }

    @Test
    fun `shouldBridgeForPlayback leaves relative remote paths unchanged`() {
        assertFalse(
            MediaSourceInfoConventions.shouldBridgeForPlayback(
                sourceType = MediaSourceType.WEBDAV,
                path = "Anime/Episode 01.mkv",
            )
        )
        assertFalse(
            MediaSourceInfoConventions.shouldBridgeForPlayback(
                sourceType = MediaSourceType.SMB,
                path = "/nas/anime/Episode 01.mkv",
            )
        )
    }
}
