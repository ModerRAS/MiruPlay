package com.miruplay.tv.desktop

import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPosterGroupingTest {
    @Test
    fun `poster wall groups indexed episodes by anime title`() {
        val entries = listOf(
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - 02.mkv", animeName = "Frieren", episodeNumber = 2),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren - 01.mkv", animeName = "Frieren", episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Bocchi - 01.mkv", animeName = "Bocchi", episodeNumber = 1),
            MediaIndexEntry(sourceId = 1, path = "show/Frieren", animeName = "Frieren", isDirectory = true),
        )

        val groups = entries.toDesktopPosterGroups()

        assertEquals(listOf("Bocchi", "Frieren"), groups.map { it.title })
        val frieren = groups.single { it.title == "Frieren" }
        assertEquals(2, frieren.entries.size)
        assertTrue(frieren.primaryEntry.path.endsWith("Frieren - 01.mkv"))
        assertEquals("2 episodes", frieren.subtitle)
    }

    @Test
    fun `poster wall uses six poster columns like Android TV library`() {
        val groups = (1..13).map { index ->
            DesktopPosterGroup(
                title = "Show $index",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "show-$index.mkv")),
            )
        }

        val rows = groups.toPosterWallRows()

        assertEquals(listOf(6, 6, 1), rows.map { it.size })
    }

    @Test
    fun `featured row orders highest heat before poster wall`() {
        val groups = listOf(
            DesktopPosterGroup(
                title = "Small",
                entries = listOf(MediaIndexEntry(sourceId = 1, path = "Small - S01E01.mkv", episodeNumber = 1)),
            ),
            DesktopPosterGroup(
                title = "Long Running",
                entries = listOf(
                    MediaIndexEntry(sourceId = 1, path = "Long Running - S01E01.mkv", episodeNumber = 1),
                    MediaIndexEntry(sourceId = 1, path = "Long Running - S01E02.mkv", episodeNumber = 2),
                    MediaIndexEntry(sourceId = 1, path = "Long Running - S01E03.mkv", episodeNumber = 3),
                ),
            ),
            DesktopPosterGroup(
                title = "Medium",
                entries = listOf(
                    MediaIndexEntry(sourceId = 1, path = "Medium - S01E01.mkv", episodeNumber = 1),
                    MediaIndexEntry(sourceId = 1, path = "Medium - S01E02.mkv", episodeNumber = 2),
                ),
            ),
        )

        val featured = groups.toFeaturedPosterGroups()

        assertEquals(listOf("Long Running", "Medium"), featured.map { it.title })
    }
}
