package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.LocalDirectoryBrowser
import org.junit.Assert.assertEquals
import org.junit.Test

class WebControlLocalDirectoriesTest {
    @Test
    fun `local directory listing maps to WebUI dto`() {
        val dto = LocalDirectoryBrowser.Listing(
            path = "D:/Anime",
            displayPath = "D:/Anime",
            parentPath = "D:/",
            entries = listOf(
                LocalDirectoryBrowser.Entry(
                    name = "Shows",
                    path = "D:/Anime/Shows",
                    canRead = true,
                ),
                LocalDirectoryBrowser.Entry(
                    name = "Unreadable",
                    path = "D:/Anime/Unreadable",
                    canRead = false,
                ),
            ),
        ).toWebControlDirectoryDto()

        assertEquals("D:/Anime", dto.path)
        assertEquals("D:/Anime", dto.displayPath)
        assertEquals("D:/", dto.parentPath)
        assertEquals(2, dto.entries.size)
        assertEquals("Shows", dto.entries[0].name)
        assertEquals("D:/Anime/Shows", dto.entries[0].path)
        assertEquals(true, dto.entries[0].canRead)
        assertEquals("Unreadable", dto.entries[1].name)
        assertEquals("D:/Anime/Unreadable", dto.entries[1].path)
        assertEquals(false, dto.entries[1].canRead)
    }
}
