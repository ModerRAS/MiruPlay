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
}
