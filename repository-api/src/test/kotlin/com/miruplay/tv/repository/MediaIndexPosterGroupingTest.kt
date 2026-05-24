package com.miruplay.tv.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexPosterGroupingTest {
    @Test
    fun `poster groups indexed episodes by anime title`() {
        val entries = listOf(
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - 02.mkv", animeName = "Frieren", episodeNumber = 2),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - 01.mkv", animeName = "Frieren", episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Bocchi - 01.mkv", animeName = "Bocchi", episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren", animeName = "Frieren", isDirectory = true),
        )

        val groups = entries.toMediaIndexPosterGroups()

        assertEquals(listOf("Bocchi", "Frieren"), groups.map { it.title })
        val frieren = groups.single { it.title == "Frieren" }
        assertEquals(2, frieren.entries.size)
        assertTrue(frieren.primaryEntry.path.endsWith("Frieren - 01.mkv"))
        assertEquals("2 episodes", frieren.subtitle)
    }

    @Test
    fun `poster groups can merge entries that share external metadata`() {
        val entries = listOf(
            MediaIndexEntry(
                sourceId = 1,
                path = "Frieren Season 1/01.mkv",
                animeName = "Frieren Season 1",
                metadataId = "431767",
                metadataTitle = "葬送的芙莉莲",
            ),
            MediaIndexEntry(
                sourceId = 1,
                path = "Frieren Season 2/01.mkv",
                animeName = "Frieren Season 2",
                metadataId = "431767",
                metadataTitle = "葬送的芙莉莲",
            ),
        )

        assertEquals(2, entries.toMediaIndexPosterGroups(mergeSameAnimeEnabled = false).size)

        val merged = entries.toMediaIndexPosterGroups(mergeSameAnimeEnabled = true)

        assertEquals(listOf("葬送的芙莉莲"), merged.map { it.title })
        assertEquals(
            listOf("Frieren Season 1/01.mkv", "Frieren Season 2/01.mkv"),
            merged.single().entries.map { it.path },
        )
        assertEquals("431767", merged.single().animeId)
    }

    @Test
    fun `poster group ids follow merge preference`() {
        val entries = listOf(
            MediaIndexEntry(
                sourceId = 1,
                path = "Frieren Season 1/01.mkv",
                animeName = "Frieren Season 1",
                metadataId = "431767",
                metadataTitle = "葬送的芙莉莲",
            ),
            MediaIndexEntry(
                sourceId = 1,
                path = "Frieren Season 2/01.mkv",
                animeName = "Frieren Season 2",
                metadataId = "431767",
                metadataTitle = "葬送的芙莉莲",
            ),
        )

        assertEquals(
            listOf("Frieren Season 1", "Frieren Season 2"),
            entries.toMediaIndexPosterGroups(mergeSameAnimeEnabled = false).map { it.animeId },
        )
        assertEquals(
            listOf("431767"),
            entries.toMediaIndexPosterGroups(mergeSameAnimeEnabled = true).map { it.animeId },
        )
    }

    @Test
    fun `detail episodes group selected anime and sort by season episode`() {
        val selected = MediaIndexEntry(
            sourceId = 1,
            path = "show/Frieren - S01E02.mkv",
            animeName = "Frieren",
            seasonNumber = 1,
            episodeNumber = 2,
        )
        val entries = listOf(
            selected,
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - S01E01.mkv", animeName = "Frieren", seasonNumber = 1, episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - S02E01.mkv", animeName = "Frieren", seasonNumber = 2, episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Bocchi - S01E01.mkv", animeName = "Bocchi", seasonNumber = 1, episodeNumber = 1),
            MediaIndexEntry(sourceId = 2, path = "other/Frieren - S01E03.mkv", animeName = "Frieren", seasonNumber = 1, episodeNumber = 3),
        )

        val episodes = entries.mediaIndexEpisodesForPosterSelection(selected)

        assertEquals(
            listOf("show/Frieren - S01E01.mkv", "show/Frieren - S01E02.mkv", "show/Frieren - S02E01.mkv"),
            episodes.map { it.path },
        )
    }

    @Test
    fun `detail episodes can merge entries that share external metadata`() {
        val selected = MediaIndexEntry(
            sourceId = 1,
            path = "Frieren Season 1/01.mkv",
            animeName = "Frieren Season 1",
            metadataId = "431767",
            metadataTitle = "葬送的芙莉莲",
            seasonNumber = 1,
            episodeNumber = 1,
        )
        val entries = listOf(
            selected,
            MediaIndexEntry(
                sourceId = 1,
                path = "Frieren Season 2/01.mkv",
                animeName = "Frieren Season 2",
                metadataId = "431767",
                metadataTitle = "葬送的芙莉莲",
                seasonNumber = 2,
                episodeNumber = 1,
            ),
            MediaIndexEntry(
                sourceId = 1,
                path = "Bocchi/01.mkv",
                animeName = "Bocchi",
                metadataId = "999",
                metadataTitle = "Bocchi",
                seasonNumber = 1,
                episodeNumber = 1,
            ),
        )

        assertEquals(
            listOf("Frieren Season 1/01.mkv"),
            entries.mediaIndexEpisodesForPosterSelection(selected, mergeSameAnimeEnabled = false).map { it.path },
        )
        assertEquals(
            listOf("Frieren Season 1/01.mkv", "Frieren Season 2/01.mkv"),
            entries.mediaIndexEpisodesForPosterSelection(selected, mergeSameAnimeEnabled = true).map { it.path },
        )
    }
}
