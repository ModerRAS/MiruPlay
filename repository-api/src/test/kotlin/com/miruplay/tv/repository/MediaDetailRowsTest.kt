package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.mediaDetailBrowserItemLabel
import com.miruplay.tv.model.mediaDetailBrowserKindLabel
import com.miruplay.tv.model.mediaDetailFileValue
import com.miruplay.tv.model.mediaDetailIndexedSizeLabel
import com.miruplay.tv.model.mediaDetailIndexedTitleLabel
import com.miruplay.tv.model.mediaDetailIndexedTypeLabel
import com.miruplay.tv.model.mediaDetailMetadataIdLabel
import com.miruplay.tv.model.mediaDetailMetadataSourceLabel
import com.miruplay.tv.model.mediaDetailMetadataTitleLabel
import com.miruplay.tv.model.mediaDetailMimeLabel
import com.miruplay.tv.model.mediaDetailPathLabel
import com.miruplay.tv.model.mediaDetailPlayCountLabel
import com.miruplay.tv.model.mediaDetailPlotLabel
import com.miruplay.tv.model.mediaDetailResumeLabel
import com.miruplay.tv.model.mediaDetailSourceEmptyValue
import com.miruplay.tv.model.mediaDetailSourceLabel
import com.miruplay.tv.model.mediaDetailVideoValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDetailRowsTest {
    @Test
    fun `build includes indexed browser and recent metadata`() {
        val rows = MediaDetailRows.build(
            source = MediaSourceInfo(
                name = "Library",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to "D:/Anime"),
                isConnected = true,
            ),
            indexEntry = MediaIndexEntry(
                sourceId = 7L,
                path = "D:/Anime/Show/01.mkv",
                animeName = "Show",
                episodeTitle = "First Light",
                plot = "The story starts here.",
                seasonNumber = 2,
                episodeNumber = 1,
                metadataSource = "BANGUMI",
                metadataId = "431767",
                metadataTitle = "Show Metadata Title",
                isDirectory = false,
                fileSize = 2_097_152L,
                lastModified = 1_700_000_000_000L,
            ),
            remoteEntry = FileEntry(
                name = "01.mkv",
                path = "D:/Anime/Show/01.mkv",
                isDirectory = false,
                size = 2_097_152L,
                lastModified = 1_700_000_100_000L,
                mimeType = "video/x-matroska",
            ),
            recentRecord = ProgressRecord(
                episodeId = "D:/Anime/Show/01.mkv",
                positionMs = 95_000L,
                lastWatched = 1_700_000_200_000L,
                playCount = 3,
            ),
        )

        assertTrue(rows.any { it.label == mediaDetailSourceLabel() && it.value == "Library · 本地" })
        assertTrue(rows.any { it.label == mediaDetailIndexedTitleLabel() && it.value == "Show EP1 - First Light" })
        assertTrue(rows.any { it.label == mediaDetailIndexedTypeLabel() && it.value == mediaDetailVideoValue() })
        assertTrue(rows.any { it.label == mediaDetailMetadataSourceLabel() && it.value == "BANGUMI" })
        assertTrue(rows.any { it.label == mediaDetailMetadataIdLabel() && it.value == "431767" })
        assertTrue(rows.any { it.label == mediaDetailMetadataTitleLabel() && it.value == "Show Metadata Title" })
        assertTrue(rows.any { it.label == mediaDetailIndexedSizeLabel() && it.value == "2.0 MB" })
        assertTrue(rows.any { it.label == mediaDetailBrowserItemLabel() && it.value == "01.mkv" })
        assertTrue(rows.any { it.label == mediaDetailBrowserKindLabel() && it.value == mediaDetailFileValue() })
        assertTrue(rows.any { it.label == mediaDetailMimeLabel() && it.value == "video/x-matroska" })
        assertTrue(rows.any { it.label == mediaDetailResumeLabel() && it.value == "01:35" })
        assertTrue(rows.any { it.label == mediaDetailPlayCountLabel() && it.value == "3" })
        assertTrue(rows.any { it.label == mediaDetailPlotLabel() && it.value == "The story starts here." })
        assertTrue(rows.any { it.label == mediaDetailPathLabel() && it.value == "D:/Anime/Show/01.mkv" })
    }

    @Test
    fun `build falls back to recent record path`() {
        val rows = MediaDetailRows.build(
            source = null,
            indexEntry = null,
            remoteEntry = null,
            recentRecord = ProgressRecord(
                episodeId = "smb://nas/anime/Show/02.mkv",
                positionMs = 95_000L,
                lastWatched = 0L,
                playCount = 3,
            ),
        )

        assertEquals(mediaDetailSourceEmptyValue(), rows.first().value)
        assertTrue(rows.any { it.label == mediaDetailResumeLabel() && it.value == "01:35" })
        assertTrue(rows.any { it.label == mediaDetailPathLabel() && it.value == "smb://nas/anime/Show/02.mkv" })
    }

    @Test
    fun `build uses shared media source fallback label`() {
        val rows = MediaDetailRows.build(
            source = MediaSourceInfo(
                name = "",
                type = MediaSourceType.SMB,
                connectionInfo = mapOf("url" to "smb://nas/anime"),
            ),
            indexEntry = null,
            remoteEntry = null,
            recentRecord = null,
        )

        assertEquals("SMB 共享 · SMB", rows.first { it.label == mediaDetailSourceLabel() }.value)
    }
}
