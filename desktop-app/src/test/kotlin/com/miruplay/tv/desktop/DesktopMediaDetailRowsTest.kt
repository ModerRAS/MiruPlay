package com.miruplay.tv.desktop

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMediaDetailRowsTest {
    @Test
    fun `build includes legacy presenter fields plus browser and recent metadata`() {
        val rows = DesktopMediaDetailRows.build(
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

        assertTrue(rows.any { it.label == "Source" && it.value == "Library · LOCAL" })
        assertTrue(rows.any { it.label == "Indexed title" && it.value == "Show EP1 - First Light" })
        assertTrue(rows.any { it.label == "Indexed type" && it.value == "Video" })
        assertTrue(rows.any { it.label == "Metadata source" && it.value == "BANGUMI" })
        assertTrue(rows.any { it.label == "Metadata ID" && it.value == "431767" })
        assertTrue(rows.any { it.label == "Metadata title" && it.value == "Show Metadata Title" })
        assertTrue(rows.any { it.label == "Indexed size" && it.value == "2.0 MB" })
        assertTrue(rows.any { it.label == "Browser item" && it.value == "01.mkv" })
        assertTrue(rows.any { it.label == "Browser kind" && it.value == "File" })
        assertTrue(rows.any { it.label == "MIME" && it.value == "video/x-matroska" })
        assertTrue(rows.any { it.label == "Resume" && it.value == "01:35" })
        assertTrue(rows.any { it.label == "Play count" && it.value == "3" })
        assertTrue(rows.any { it.label == "Plot" && it.value == "The story starts here." })
        assertTrue(rows.any { it.label == "Path" && it.value == "D:/Anime/Show/01.mkv" })
    }

    @Test
    fun `build still produces a minimal path row when only a record exists`() {
        val rows = DesktopMediaDetailRows.build(
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

        assertEquals("None", rows.first().value)
        assertTrue(rows.any { it.label == "Resume" && it.value == "01:35" })
        assertTrue(rows.any { it.label == "Path" && it.value == "smb://nas/anime/Show/02.mkv" })
    }
}
