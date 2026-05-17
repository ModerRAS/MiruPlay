package com.miruplay.tv.desktop

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMediaDetailsPresenterTest {
    @Test
    fun `details include indexed episode metadata and plot`() {
        val file = FileEntry(
            name = "01.mkv",
            path = "D:/Anime/Show/01.mkv",
            isDirectory = false,
            size = 2_097_152L,
        )
        val indexEntry = MediaIndexEntry(
            sourceId = 7L,
            path = file.path,
            animeName = "Show",
            seasonNumber = 2,
            episodeNumber = 1,
            episodeTitle = "First Light",
            plot = "The story starts here.",
            metadataSource = "BANGUMI",
            metadataId = "431767",
            metadataTitle = "Show Metadata Title",
            fileSize = file.size,
        )

        val details = DesktopMediaDetailsPresenter.details(file, indexEntry)

        assertTrue(details.contains("Name: Show EP1 - First Light"))
        assertTrue(details.contains("Anime: Show"))
        assertTrue(details.contains("Season: 2"))
        assertTrue(details.contains("Episode: 1"))
        assertTrue(details.contains("Metadata source: BANGUMI"))
        assertTrue(details.contains("Metadata ID: 431767"))
        assertTrue(details.contains("Metadata title: Show Metadata Title"))
        assertTrue(details.contains("Size: 2.0 MB"))
        assertTrue(details.contains("Path: D:/Anime/Show/01.mkv"))
        assertTrue(details.contains("The story starts here."))
    }

    @Test
    fun `recent details include resume position and original path`() {
        val details = DesktopMediaDetailsPresenter.recentDetails(
            ProgressRecord(
                episodeId = "smb://nas/anime/Show/02.mkv",
                positionMs = 95_000L,
                lastWatched = 0L,
                playCount = 3,
            )
        )

        assertTrue(details.contains("Name: 02.mkv"))
        assertTrue(details.contains("Resume: 01:35"))
        assertTrue(details.contains("Path: smb://nas/anime/Show/02.mkv"))
    }
}
