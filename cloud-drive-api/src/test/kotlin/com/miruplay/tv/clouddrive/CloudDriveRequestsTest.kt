package com.miruplay.tv.clouddrive

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDriveRequestsTest {
    @Test
    fun `offline files request joins urls and keeps target folder`() {
        val request = CloudDriveRequests.offlineFiles(
            urls = listOf("magnet:?xt=urn:btih:one", "https://example.test/file.torrent"),
            targetFolder = "/Downloads",
        )

        assertEquals("magnet:?xt=urn:btih:one\nhttps://example.test/file.torrent", request.urls)
        assertEquals("/Downloads", request.targetFolder)
        assertEquals(30L, request.checkFolderAfterSeconds)
    }

    @Test
    fun `upload target normalizes parent path and exposes resulting remote path`() {
        val target = CloudDriveRequests.uploadTarget("Downloads\\Anime\\", "Episode 01.torrent")

        assertEquals("/Downloads/Anime", target.parentPath)
        assertEquals("Episode 01.torrent", target.remoteFileName)
        assertEquals("/Downloads/Anime/Episode 01.torrent", target.remotePath)
    }

    @Test
    fun `file info falls back to final path segment when server name is blank`() {
        val info = CloudDriveRequests.fileInfo(
            name = "",
            fullPathName = "/Downloads/Anime/Episode 01.mkv",
            isDirectory = false,
            size = 2048L,
        )

        assertEquals("Episode 01.mkv", info.name)
        assertEquals("/Downloads/Anime/Episode 01.mkv", info.path)
        assertEquals(false, info.isDirectory)
        assertEquals(2048L, info.size)
    }
}
