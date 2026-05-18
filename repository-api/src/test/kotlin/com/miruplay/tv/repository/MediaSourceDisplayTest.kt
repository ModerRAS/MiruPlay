package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceDisplayTest {
    @Test
    fun `displayLabel joins source name and type`() {
        assertEquals(
            "Library · LOCAL",
            MediaSourceInfo(
                name = "Library",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to "D:/Anime"),
            ).displayLabel(),
        )
    }

    @Test
    fun `upsertById replaces matching id or appends missing id`() {
        val local = MediaSourceInfo(id = 1L, name = "Local", type = MediaSourceType.LOCAL)
        val updatedLocal = local.copy(name = "Updated")
        val smb = MediaSourceInfo(id = 2L, name = "SMB", type = MediaSourceType.SMB)

        assertEquals(listOf(updatedLocal), listOf(local).upsertById(updatedLocal))
        assertEquals(listOf(local, smb), listOf(local).upsertById(smb))
    }

    @Test
    fun `mediaDisplayName uses the last path segment`() {
        assertEquals(
            "Episode 01.mkv",
            ProgressRecord(
                episodeId = "smb://nas/anime/Show/Episode 01.mkv",
                positionMs = 1_000L,
                lastWatched = 0L,
            ).mediaDisplayName(),
        )
        assertEquals(
            "opaque-id",
            ProgressRecord(
                episodeId = "opaque-id",
                positionMs = 1_000L,
                lastWatched = 0L,
            ).mediaDisplayName(),
        )
    }

    @Test
    fun `source status helpers share desktop wording`() {
        val local = MediaSourceInfo(id = 1L, name = "Library", type = MediaSourceType.LOCAL)
        val webDav = MediaSourceInfo(id = 2L, name = "Cloud", type = MediaSourceType.WEBDAV)
        val smb = MediaSourceInfo(id = 3L, name = "NAS", type = MediaSourceType.SMB)

        assertEquals("Add a local library source or load an existing one.", localLibraryInitialStatus())
        assertEquals("Open a WebDAV or SMB source to browse it.", remoteBrowserInitialStatus())
        assertEquals("Loaded local source: Library", local.loadedStatus())
        assertEquals("Loaded saved local source: Library", local.loadedStatus(saved = true))
        assertEquals("Loaded WebDAV source: Cloud", webDav.loadedStatus())
        assertEquals("Loaded saved WebDAV source: Cloud", webDav.loadedStatus(saved = true))
        assertEquals("Loaded SMB source: NAS", smb.loadedStatus())
        assertEquals("Loaded saved SMB source: NAS", smb.loadedStatus(saved = true))
        assertEquals("Local source ready: Library", local.readyStatus())
        assertEquals("WebDAV source ready: Cloud", webDav.readyStatus())
        assertEquals("SMB source ready: NAS", smb.readyStatus())
    }

    @Test
    fun `source action statuses share desktop wording`() {
        assertEquals("Enter a local library root first.", localRootRequiredStatus())
        assertEquals("Enter a WebDAV URL first.", webDavUrlRequiredStatus())
        assertEquals("Enter an SMB URL first.", smbUrlRequiredStatus())
        assertEquals("Open a source before scanning.", openSourceBeforeScanningStatus())
        assertEquals("Open or scan a source before searching.", openSourceBeforeSearchingStatus())
        assertEquals("Open or scan a source before clearing its index.", openSourceBeforeClearingIndexStatus())
        assertEquals("Index cleared for source id: 42.", indexClearedStatus(42L))
        assertEquals("Open a source before removing it.", sourceRemoveRequiredStatus())
        assertEquals("Source removed. Associated index entries were cleared.", sourceRemovedStatus())
        assertEquals("Already at the source root.", remoteRootStatus())
        assertEquals("Open a remote source before browsing.", openRemoteSourceBeforeBrowsingStatus())
    }

    @Test
    fun `remote directory and playback statuses share desktop wording`() {
        val source = MediaSourceInfo(id = 7L, name = "Cloud", type = MediaSourceType.WEBDAV)
        val entry = FileEntry(name = "Episode 01.mkv", path = "/Anime/Episode 01.mkv", isDirectory = false)
        val indexEntry = MediaIndexEntry(sourceId = 7L, path = "/Anime/Episode 01.mkv", animeName = "Frieren", episodeNumber = 1)

        assertEquals("Loading WEBDAV /...", source.loadingRemoteDirectoryStatus(""))
        assertEquals("Loading WEBDAV /Anime...", source.loadingRemoteDirectoryStatus("/Anime"))
        assertEquals("Showing 1 item(s) from Cloud.", source.showingRemoteDirectoryStatus(listOf(entry)))
        assertEquals("Selected Frieren EP1 for playback.", indexEntry.selectedForPlaybackStatus())
        assertEquals(
            "Selected remote media: Episode 01.mkv. mpv will stream through the local bridge.",
            entry.selectedRemoteForPlaybackStatus(),
        )
    }

    @Test
    fun `indexed search status uses trimmed query and displayed result count`() {
        assertEquals(
            "No indexed media matched \"frieren\".",
            indexedSearchStatus(query = " frieren ", hasResults = false, displayedResultCount = 0),
        )
        assertEquals(
            "Showing 24 indexed video result(s).",
            indexedSearchStatus(query = "frieren", hasResults = true, displayedResultCount = 24),
        )
        assertEquals(
            "Showing 0 indexed video result(s).",
            indexedSearchStatus(query = "frieren", hasResults = true, displayedResultCount = 0),
        )
    }
}
