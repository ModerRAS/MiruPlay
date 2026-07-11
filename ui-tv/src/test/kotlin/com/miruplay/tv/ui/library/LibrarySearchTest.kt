package com.miruplay.tv.ui.library

import com.miruplay.tv.model.Anime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    private val anime = Anime(id = "frieren", title = "Sousou no Frieren", titleCn = "葬送的芙莉莲")

    @Test
    fun `query matches localized original and stable titles`() {
        assertTrue(anime.matchesLibraryQuery("芙莉莲"))
        assertTrue(anime.matchesLibraryQuery("SOUSOU"))
        assertTrue(anime.matchesLibraryQuery("frieren"))
        assertFalse(anime.matchesLibraryQuery("不存在"))
    }
}
