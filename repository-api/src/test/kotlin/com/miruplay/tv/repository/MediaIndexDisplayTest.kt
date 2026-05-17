package com.miruplay.tv.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIndexDisplayTest {
    @Test
    fun `display name uses anime episode and episode title`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Show/01.mkv",
            animeName = "Show",
            episodeNumber = 1,
            episodeTitle = "First Light",
        )

        assertEquals("Show EP1 - First Light", entry.displayName())
    }

    @Test
    fun `display name falls back to metadata title then file name`() {
        assertEquals(
            "Metadata Title",
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Unknown/01.mkv",
                metadataTitle = "Metadata Title",
            ).displayName(),
        )
        assertEquals(
            "01",
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Unknown/01.mkv").displayName(),
        )
    }

    @Test
    fun `display line and browser entry preserve index metadata`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "smb://nas/anime/Show/02.mkv",
            animeName = "Show",
            episodeNumber = 2,
            isDirectory = false,
            fileSize = 2048L,
            lastModified = 1_700_000_000_000L,
        )
        val browserEntry = entry.toBrowserEntry()

        assertEquals("[VID] Show EP2  smb://nas/anime/Show/02.mkv", entry.displayLine())
        assertEquals("Show EP2", browserEntry.name)
        assertEquals(entry.path, browserEntry.path)
        assertEquals(2048L, browserEntry.size)
        assertEquals(1_700_000_000_000L, browserEntry.lastModified)
    }
}
