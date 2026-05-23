package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.libraryRescanCompleteStatus
import com.miruplay.tv.model.libraryScanCompleteStatus
import com.miruplay.tv.model.libraryScanningStatus
import com.miruplay.tv.model.mediaSourceAlreadyAtRootStatus
import com.miruplay.tv.model.mediaSourceIndexClearedStatus
import com.miruplay.tv.model.mediaSourceIndexedSearchStatus
import com.miruplay.tv.model.mediaSourceLocalLibraryInitialStatus
import com.miruplay.tv.model.mediaSourceLocalRootRequiredStatus
import com.miruplay.tv.model.mediaSourceOpenBeforeClearingIndexStatus
import com.miruplay.tv.model.mediaSourceOpenBeforeScanningStatus
import com.miruplay.tv.model.mediaSourceOpenBeforeSearchingStatus
import com.miruplay.tv.model.mediaSourceOpenRemoteBeforeBrowsingStatus
import com.miruplay.tv.model.mediaSourceRemoteBrowserInitialStatus
import com.miruplay.tv.model.mediaSourceRemoveRequiredStatus
import com.miruplay.tv.model.mediaSourceRemovedStatus
import com.miruplay.tv.model.mediaSourceSmbUrlRequiredStatus
import com.miruplay.tv.model.mediaSourceWebDavUrlRequiredStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceDisplayTest {
    @Test
    fun `displayLabel joins source name and type`() {
        assertEquals(
            "Library · 本地",
            MediaSourceInfo(
                name = "Library",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to "D:/Anime"),
            ).displayLabel(),
        )
        assertEquals(
            "SMB 共享 · SMB",
            MediaSourceInfo(
                name = "",
                type = MediaSourceType.SMB,
                connectionInfo = mapOf("url" to "smb://nas/anime"),
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
    fun `source status helpers share TV facing wording`() {
        val local = MediaSourceInfo(id = 1L, name = "Library", type = MediaSourceType.LOCAL)
        val webDav = MediaSourceInfo(id = 2L, name = "Cloud", type = MediaSourceType.WEBDAV)
        val smb = MediaSourceInfo(id = 3L, name = "NAS", type = MediaSourceType.SMB)

        assertEquals(mediaSourceLocalLibraryInitialStatus(), localLibraryInitialStatus())
        assertEquals(mediaSourceRemoteBrowserInitialStatus(), remoteBrowserInitialStatus())
        assertEquals("已载入媒体源：Library · 本地", local.loadedStatus())
        assertEquals("已载入已保存媒体源：Library · 本地", local.loadedStatus(saved = true))
        assertEquals("已载入媒体源：Cloud · WebDAV", webDav.loadedStatus())
        assertEquals("已载入已保存媒体源：Cloud · WebDAV", webDav.loadedStatus(saved = true))
        assertEquals("已载入媒体源：NAS · SMB", smb.loadedStatus())
        assertEquals("已载入已保存媒体源：NAS · SMB", smb.loadedStatus(saved = true))
        assertEquals("本地媒体源已就绪：Library", local.readyStatus())
        assertEquals("WebDAV 媒体源已就绪：Cloud", webDav.readyStatus())
        assertEquals("SMB 媒体源已就绪：NAS", smb.readyStatus())
    }

    @Test
    fun `source action statuses share TV facing wording`() {
        assertEquals(mediaSourceLocalRootRequiredStatus(), localRootRequiredStatus())
        assertEquals(mediaSourceWebDavUrlRequiredStatus(), webDavUrlRequiredStatus())
        assertEquals(mediaSourceSmbUrlRequiredStatus(), smbUrlRequiredStatus())
        assertEquals(mediaSourceOpenBeforeScanningStatus(), openSourceBeforeScanningStatus())
        assertEquals(libraryScanningStatus("Library"), MediaSourceInfo(id = 1L, name = "Library", type = MediaSourceType.LOCAL).scanningStatus())
        assertEquals(libraryScanCompleteStatus(12, 3), scanCompleteStatus(filesIndexed = 12, directoriesVisited = 3))
        assertEquals(libraryRescanCompleteStatus(12, 3), rescanCompleteStatus(filesIndexed = 12, directoriesVisited = 3))
        assertEquals(mediaSourceOpenBeforeSearchingStatus(), openSourceBeforeSearchingStatus())
        assertEquals(mediaSourceOpenBeforeClearingIndexStatus(), openSourceBeforeClearingIndexStatus())
        assertEquals(mediaSourceIndexClearedStatus(42L), indexClearedStatus(42L))
        assertEquals(mediaSourceRemoveRequiredStatus(), sourceRemoveRequiredStatus())
        assertEquals(mediaSourceRemovedStatus(), sourceRemovedStatus())
        assertEquals(mediaSourceAlreadyAtRootStatus(), remoteRootStatus())
        assertEquals(mediaSourceOpenRemoteBeforeBrowsingStatus(), openRemoteSourceBeforeBrowsingStatus())
    }

    @Test
    fun `remote directory and playback statuses share TV facing wording`() {
        val source = MediaSourceInfo(id = 7L, name = "Cloud", type = MediaSourceType.WEBDAV)
        val entry = FileEntry(name = "Episode 01.mkv", path = "/Anime/Episode 01.mkv", isDirectory = false)
        val indexEntry = MediaIndexEntry(sourceId = 7L, path = "/Anime/Episode 01.mkv", animeName = "Frieren", episodeNumber = 1)

        assertEquals("正在载入 WebDAV：/", source.loadingRemoteDirectoryStatus(""))
        assertEquals("正在载入 WebDAV：/Anime", source.loadingRemoteDirectoryStatus("/Anime"))
        assertEquals("Cloud 中显示 1 个条目。", source.showingRemoteDirectoryStatus(listOf(entry)))
        assertEquals("已选择播放：Frieren EP1", indexEntry.selectedForPlaybackStatus())
        assertEquals(
            "已选择远程媒体：Episode 01.mkv。mpv 将通过本地桥接串流。",
            entry.selectedRemoteForPlaybackStatus(),
        )
    }

    @Test
    fun `indexed search status uses trimmed query and displayed result count`() {
        assertEquals(
            mediaSourceIndexedSearchStatus(query = "frieren", hasResults = false, displayedResultCount = 0),
            indexedSearchStatus(query = " frieren ", hasResults = false, displayedResultCount = 0),
        )
        assertEquals(
            mediaSourceIndexedSearchStatus(query = "frieren", hasResults = true, displayedResultCount = 24),
            indexedSearchStatus(query = "frieren", hasResults = true, displayedResultCount = 24),
        )
        assertEquals(
            mediaSourceIndexedSearchStatus(query = "frieren", hasResults = true, displayedResultCount = 0),
            indexedSearchStatus(query = "frieren", hasResults = true, displayedResultCount = 0),
        )
    }
}
