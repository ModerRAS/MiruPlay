package com.miruplay.tv.repository

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIndexDisplayTest {
    @Test
    fun `display name uses anime episode and episode title`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Show/01.mkv",
            animeName = "Show",
            episodeNumber = 1,
            episodeTitle = "First Light",
        )

        assertEquals("Show EP1 - First Light", entry.displayName())
    }

    @Test
    fun `display name falls back to metadata title then file name`() {
        assertEquals(
            "Metadata Title",
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Unknown/01.mkv",
                metadataTitle = "Metadata Title",
            ).displayName(),
        )
        assertEquals(
            "01",
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Unknown/01.mkv").displayName(),
        )
    }

    @Test
    fun `display line and browser entry preserve index metadata`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "smb://nas/anime/Show/02.mkv",
            animeName = "Show",
            episodeNumber = 2,
            isDirectory = false,
            fileSize = 2048L,
            lastModified = 1_700_000_000_000L,
        )
        val browserEntry = entry.toBrowserEntry()

        assertEquals("[视频] Show EP2  smb://nas/anime/Show/02.mkv", entry.displayLine())
        assertEquals("Show EP2", browserEntry.name)
        assertEquals(entry.path, browserEntry.path)
        assertEquals(2048L, browserEntry.size)
        assertEquals(1_700_000_000_000L, browserEntry.lastModified)
    }

    @Test
    fun `display line uses directory label for folders`() {
        val entry = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Show",
            animeName = "Show",
            isDirectory = true,
        )

        assertEquals("[目录] Show  D:/Anime/Show", entry.displayLine())
    }

    @Test
    fun `metadata query uses anime metadata then path stem`() {
        assertEquals(
            "Show",
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/01.mkv", animeName = "Show").metadataQuery(),
        )
        assertEquals(
            "Metadata Title",
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/01.mkv", metadataTitle = "Metadata Title").metadataQuery(),
        )
        assertEquals(
            "Episode 01",
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Episode 01.mkv").metadataQuery(),
        )
    }

    @Test
    fun `replace by media key matches source and path only`() {
        val original = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/01.mkv")
        val samePathDifferentSource = MediaIndexEntry(sourceId = 2L, path = original.path)
        val other = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/02.mkv")
        val updated = original.copy(metadataId = "431767")
        val directory = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show", isDirectory = true)

        assertEquals(listOf(updated, samePathDifferentSource, other), listOf(original, samePathDifferentSource, other).replaceByMediaKey(updated))
        assertEquals(listOf(updated, samePathDifferentSource, other), listOf(original, samePathDifferentSource, other).replaceByMediaKeys(listOf(updated)))
        assertEquals(listOf(original, samePathDifferentSource, other), listOf(original, samePathDifferentSource, other).replaceByMediaKeys(emptyList()))
        assertEquals(listOf(original, samePathDifferentSource, other), listOf(original, directory, samePathDifferentSource, other).mediaFilesOnly())
        assertEquals(updated, original.updatedSelectionAfterReplacingByMediaKeys(listOf(updated)))
        assertEquals(original, original.updatedSelectionAfterReplacingByMediaKeys(emptyList()))
        assertEquals(null, null.updatedSelectionAfterReplacingByMediaKeys(listOf(updated)))
        assertEquals(updated, original.retainedSelectionInMediaIndex(listOf(updated, samePathDifferentSource, other)))
        assertEquals(null, original.retainedSelectionInMediaIndex(listOf(samePathDifferentSource, other)))
        assertEquals(null, null.retainedSelectionInMediaIndex(listOf(updated)))
    }

    @Test
    fun `external metadata helpers apply and clear scraper metadata`() {
        val original = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Show/01.mkv",
            animeName = "Old Name",
            metadataSource = "BANGUMI",
            metadataId = "old",
            metadataTitle = "Old Title",
        )
        val result = ScraperResult(
            animeId = "431767",
            title = "Frieren",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "Frieren",
            confidence = 0.95f,
            source = ScraperSource.BANGUMI,
        )

        val updated = original.withExternalMetadata(result, sourceId = 7L)
        val cleared = updated.clearExternalMetadata(sourceId = 7L)

        assertEquals(7L, updated.sourceId)
        assertEquals("葬送的芙莉莲", updated.animeName)
        assertEquals("BANGUMI", updated.metadataSource)
        assertEquals("431767", updated.metadataId)
        assertEquals("葬送的芙莉莲", updated.metadataTitle)
        assertEquals(7L, cleared.sourceId)
        assertEquals("葬送的芙莉莲", cleared.animeName)
        assertEquals(null, cleared.metadataSource)
        assertEquals(null, cleared.metadataId)
        assertEquals(null, cleared.metadataTitle)
    }
}
