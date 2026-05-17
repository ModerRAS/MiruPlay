package com.miruplay.tv.desktop

import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DesktopIndexSearchPresenterTest {
    @Test
    fun `indexed video display name includes anime episode and title`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Show/01.mkv",
            animeName = "Show",
            episodeNumber = 1,
            episodeTitle = "First Light",
        )

        assertEquals("Show EP1 - First Light", DesktopIndexSearchPresenter.displayName(entry))
        assertEquals("[VID] Show EP1 - First Light  D:/Anime/Show/01.mkv", DesktopIndexSearchPresenter.displayLine(entry))
    }

    @Test
    fun `indexed entry becomes actionable browser item`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "smb://nas/anime/Show/02.mkv",
            animeName = "Show",
            episodeNumber = 2,
            fileSize = 1_024L,
            lastModified = 123L,
        )

        val browserEntry = DesktopIndexSearchPresenter.toBrowserEntry(entry)

        assertEquals("Show EP2", browserEntry.name)
        assertEquals("smb://nas/anime/Show/02.mkv", browserEntry.path)
        assertFalse(browserEntry.isDirectory)
        assertEquals(1_024L, browserEntry.size)
        assertEquals(123L, browserEntry.lastModified)
    }

    @Test
    fun `display name falls back to metadata title`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Frieren/01.mkv",
            episodeNumber = 1,
            metadataTitle = "葬送的芙莉莲",
        )

        assertEquals("葬送的芙莉莲 EP1", DesktopIndexSearchPresenter.displayName(entry))
    }
}
