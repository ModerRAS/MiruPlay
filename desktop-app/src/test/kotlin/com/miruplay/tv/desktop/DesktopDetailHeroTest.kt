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

    @Test
    fun `detail hero down key falls back to bangumi when recents are absent`() {
        assertEquals(
            DesktopDetailDownTarget.EpisodeList,
            detailHeroDownTarget(hasRelatedEpisodes = true, hasRecentPlayback = false),
        )
        assertEquals(
            DesktopDetailDownTarget.RecentPlayback,
            detailHeroDownTarget(hasRelatedEpisodes = false, hasRecentPlayback = true),
        )
        assertEquals(
            DesktopDetailDownTarget.BangumiMetadata,
            detailHeroDownTarget(hasRelatedEpisodes = false, hasRecentPlayback = false),
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

        val episodes = detailEpisodesForSelection(entries, selected)

        assertEquals(
            listOf("show/Frieren - S01E01.mkv", "show/Frieren - S01E02.mkv", "show/Frieren - S02E01.mkv"),
            episodes.map { it.path },
        )
        assertEquals(listOf(1, 2), detailEpisodeSeasons(episodes))
        assertEquals(1, detailActiveEpisodeSeason(episodes, selected, requestedSeason = null))
        assertEquals(listOf("show/Frieren - S02E01.mkv"), detailEpisodesForSeason(episodes, 2).map { it.path })
    }

    @Test
    fun `detail episode navigation moves within visible rows`() {
        assertEquals(1, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 3, delta = 1))
        assertEquals(1, moveDetailEpisodeSelection(currentIndex = 2, itemCount = 3, delta = -1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 3, delta = -1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 2, itemCount = 3, delta = 1))
        assertEquals(null, moveDetailEpisodeSelection(currentIndex = 0, itemCount = 0, delta = 1))
    }
}
