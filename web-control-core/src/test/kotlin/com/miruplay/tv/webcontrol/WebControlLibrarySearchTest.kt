package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WebControlLibrarySearchTest {
    @Test
    fun `anime list builds stable library with sorted unique items and recently added window`() {
        val continueWatching = listOf(
            ContinueWatchingDto(
                progressEpisodeId = "episode-1",
                positionMs = 12_000L,
                lastWatched = 34L,
                playCount = 1,
                episode = null,
                anime = null,
            )
        )
        val anime = listOf(
            anime("zeta", "Zeta"),
            anime("alpha", "Alpha Duplicate"),
            anime("alpha", "Alpha"),
        ) + (1..25).map { index -> anime("show-$index", "Show $index") }

        val library = anime.toWebControlLibrary(continueWatching)

        assertEquals(27, library.allAnime.size)
        assertEquals(continueWatching, library.continueWatching)
        assertEquals("alpha", library.allAnime.first().id)
        assertEquals("zeta", library.allAnime.last().id)
        assertEquals(24, library.recentlyAdded.size)
        assertEquals(library.allAnime.takeLast(24).map { it.id }, library.recentlyAdded.map { it.id })
    }

    @Test
    fun `blank query preserves the original library`() {
        val library = LibraryDto(
            continueWatching = emptyList(),
            recentlyAdded = listOf(anime("frieren", "Sousou no Frieren")),
            allAnime = listOf(anime("frieren", "Sousou no Frieren")),
        )

        assertSame(library, library.filteredByQuery("  "))
    }

    @Test
    fun `query matches anime id title and Chinese title case-insensitively`() {
        val frieren = anime("frieren", "Sousou no Frieren", "葬送的芙莉莲")
        val bocchi = anime("bocchi", "BOCCHI THE ROCK!", "孤独摇滚")
        val kOn = anime("k-on", "K-On!", "轻音少女")
        val library = LibraryDto(
            continueWatching = emptyList(),
            recentlyAdded = listOf(frieren, bocchi, kOn),
            allAnime = listOf(frieren, bocchi, kOn),
        )

        assertEquals(listOf("frieren"), library.filteredByQuery("FRIEREN").allAnime.map { it.id })
        assertEquals(listOf("bocchi"), library.filteredByQuery("rock").allAnime.map { it.id })
        assertEquals(listOf("k-on"), library.filteredByQuery("轻音").allAnime.map { it.id })
    }

    @Test
    fun `recently added mirrors the filtered result window`() {
        val matches = (1..30).map { index ->
            anime(id = "show-$index", title = "Fixture $index")
        }
        val library = LibraryDto(
            continueWatching = emptyList(),
            recentlyAdded = emptyList(),
            allAnime = matches + anime("other", "Other"),
        )

        val filtered = library.filteredByQuery("fixture")

        assertEquals(30, filtered.allAnime.size)
        assertEquals((1..24).map { "show-$it" }, filtered.recentlyAdded.map { it.id })
    }

    private fun anime(
        id: String,
        title: String,
        titleCn: String? = null,
    ): Anime =
        Anime(id = id, title = title, titleCn = titleCn)
}
