package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMediaSourcePresentersTest {
    @Test
    fun `source label is shared by picker and detail rows`() {
        assertEquals(
            "Library · LOCAL",
            sourceLabel(
                MediaSourceInfo(
                    name = "Library",
                    type = MediaSourceType.LOCAL,
                    connectionInfo = mapOf("path" to "D:/Anime"),
                )
            )
        )
    }

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
    fun `remote parent handles webdav roots and smb shares`() {
        assertNull(remoteParent(""))
        assertNull(remoteParent("/"))
        assertEquals("", remoteParent("/Anime"))
        assertEquals("/Anime", remoteParent("/Anime/Season 1"))
        assertNull(remoteParent("smb://nas/share"))
        assertEquals("smb://nas/share/Anime", remoteParent("smb://nas/share/Anime/Season 1"))
    }

    @Test
    fun `upsert source replaces matching id or appends missing id`() {
        val local = MediaSourceInfo(id = 1L, name = "Local", type = MediaSourceType.LOCAL)
        val updatedLocal = local.copy(name = "Updated")
        val smb = MediaSourceInfo(id = 2L, name = "SMB", type = MediaSourceType.SMB)

        assertEquals(listOf(updatedLocal), listOf(local).upsertSource(updatedLocal))
        assertEquals(listOf(local, smb), listOf(local).upsertSource(smb))
    }

    @Test
    fun `scan statuses are shared with media source display`() {
        val local = MediaSourceInfo(id = 1L, name = "Library", type = MediaSourceType.LOCAL)

        assertEquals("Scanning Library...", scanningSourceStatus(local))
        assertEquals("Scan complete: 12 videos, 3 directories.", scanCompleteStatus(filesIndexed = 12, directoriesVisited = 3))
        assertEquals("Rescan complete: 12 videos, 3 directories.", rescanCompleteStatus(filesIndexed = 12, directoriesVisited = 3))
    }

    @Test
    fun `recent display name uses the last path segment`() {
        assertEquals(
            "Episode 01.mkv",
            recentDisplayName(
                ProgressRecord(
                    episodeId = "smb://nas/anime/Show/Episode 01.mkv",
                    positionMs = 1_000L,
                    lastWatched = 0L,
                )
            )
        )
        assertEquals(
            "opaque-id",
            recentDisplayName(
                ProgressRecord(
                    episodeId = "opaque-id",
                    positionMs = 1_000L,
                    lastWatched = 0L,
                )
            )
        )
    }
}
