package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDetailHeroTest {
    @Test
    fun `detail hero prefers metadata title and includes episode context`() {
        val source = MediaSourceInfoConventions.local(
            name = "Anime Library",
            rootPath = "D:/Anime",
        )
        val entry = MediaIndexEntry(
            sourceId = 7,
            path = "D:/Anime/Frieren/Frieren - S01E02.mkv",
            animeName = "Fixture Frieren",
            metadataTitle = "Frieren",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "The Journey",
        )

        assertEquals("Frieren", entry.detailTitle())
        val subtitle = entry.detailSubtitle(source)
        assertTrue(subtitle.contains("Anime Library"))
        assertTrue(subtitle.contains("S1"))
        assertTrue(subtitle.contains("EP2"))
        assertTrue(subtitle.contains("The Journey"))
    }

    @Test
    fun `detail hero action navigation moves within primary actions`() {
        assertEquals(
            DesktopDetailHeroAction.BackToLibrary,
            moveDesktopDetailHeroAction(DesktopDetailHeroAction.Play, 1),
        )
        assertEquals(
            DesktopDetailHeroAction.Play,
            moveDesktopDetailHeroAction(DesktopDetailHeroAction.BackToLibrary, -1),
        )
        assertEquals(null, moveDesktopDetailHeroAction(DesktopDetailHeroAction.Play, -1))
        assertEquals(null, moveDesktopDetailHeroAction(DesktopDetailHeroAction.BackToLibrary, 1))
    }

    @Test
    fun `recent playback navigation moves within visible records`() {
        assertEquals(1, moveRecentPlaybackSelection(currentIndex = 0, itemCount = 3, delta = 1))
        assertEquals(1, moveRecentPlaybackSelection(currentIndex = 2, itemCount = 3, delta = -1))
        assertEquals(null, moveRecentPlaybackSelection(currentIndex = 0, itemCount = 3, delta = -1))
        assertEquals(null, moveRecentPlaybackSelection(currentIndex = 2, itemCount = 3, delta = 1))
        assertEquals(null, moveRecentPlaybackSelection(currentIndex = 0, itemCount = 0, delta = 1))
    }
}
