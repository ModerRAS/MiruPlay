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
        val source = MediaSourceInfoConventions.local(
            name = "Library",
            rootPath = "content://tree/primary%3ADownload",
            displayName = "Download",
        )

        assertEquals("Library", source.name)
        assertEquals(MediaSourceType.LOCAL, source.type)
        assertEquals("content://tree/primary%3ADownload", source.connectionInfo.getValue("path"))
        assertEquals("content://tree/primary%3ADownload", source.connectionInfo.getValue("url"))
        assertEquals("content://tree/primary%3ADownload", source.connectionInfo.getValue("uri"))
        assertEquals("Download", source.connectionDisplayName())
        assertTrue(source.isConnected)
    }

    @Test
    fun `shared connection info helper preserves Android TV form fields`() {
        val local = MediaSourceInfoConventions.sourceConnectionInfo(
            type = MediaSourceType.LOCAL,
            location = " content://tree/primary%3AAnime ",
            displayName = " Anime ",
            username = " ignored-user ",
            password = "secret",
        )
        val webDav = MediaSourceInfoConventions.sourceConnectionInfo(
            type = MediaSourceType.WEBDAV,
            location = " https://dav.example.test/anime ",
            username = " alice ",
            password = "secret",
        )

        assertEquals("content://tree/primary%3AAnime", local.getValue("url"))
        assertEquals("content://tree/primary%3AAnime", local.getValue("path"))
        assertEquals("content://tree/primary%3AAnime", local.getValue("uri"))
        assertEquals("Anime", local.getValue("displayName"))
        assertEquals("ignored-user", local.getValue("username"))
        assertEquals("secret", local.getValue("password"))

        assertEquals("https://dav.example.test/anime", webDav.getValue("url"))
        assertFalse("path" in webDav)
        assertFalse("displayName" in webDav)
        assertEquals("alice", webDav.getValue("username"))
    }

    @Test
    fun `localRootPath reads current and legacy connection keys`() {
        val current = source(connectionInfo = mapOf("path" to "D:/Anime"))
        val legacyUri = source(connectionInfo = mapOf("uri" to "E:/Anime"))
        val legacyUrl = source(connectionInfo = mapOf("url" to "F:/Anime"))

        assertEquals("D:/Anime", current.localRootPath())
        assertEquals("E:/Anime", legacyUri.localRootPath())
        assertEquals("F:/Anime", legacyUrl.localRootPath())
    }

    @Test
    fun `sourceLocation and credential helpers read shared connection contract`() {
        val source = source(
            type = MediaSourceType.SMB,
            connectionInfo = mapOf(
                "url" to "smb://nas/anime",
                "domain" to "WORKGROUP",
                "username" to "alice",
                "password" to "secret",
            ),
        )

        assertEquals("smb://nas/anime", source.sourceLocation())
        assertEquals("smb://nas/anime", source.remoteUrl())
        assertEquals("WORKGROUP", source.connectionDomain())
        assertEquals("alice", source.connectionUsername())
        assertEquals("secret", source.connectionPassword())
        assertEquals("", source.connectionDisplayName())
    }

    @Test
    fun `credential helpers return blank when absent`() {
        val source = source(connectionInfo = emptyMap())

        assertEquals("", source.connectionDomain())
        assertEquals("", source.connectionUsername())
        assertEquals("", source.connectionPassword())
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

    private fun source(
        type: MediaSourceType = MediaSourceType.LOCAL,
        connectionInfo: Map<String, String>,
    ): MediaSourceInfo =
        MediaSourceInfo(
            name = "Test Source",
            type = type,
            connectionInfo = connectionInfo,
        )
}
