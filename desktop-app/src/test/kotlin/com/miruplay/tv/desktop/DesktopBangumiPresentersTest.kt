package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataBatchConflict
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.MetadataBatchUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBangumiPresentersTest {
    @Test
    fun `bangumi query prefers anime name then file stem`() {
        assertEquals("Frieren", bangumiQueryFor(MediaIndexEntry(sourceId = 1L, path = "D:/Anime/01.mkv", animeName = "Frieren")))
        assertEquals("Episode 01", bangumiQueryFor(MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Episode 01.mkv")))
        assertNull(bangumiQueryFor(null))
    }

    @Test
    fun `display title uses scraper localized title`() {
        assertEquals("葬送的芙莉莲", bangumiDisplayTitle(result(titleCn = "葬送的芙莉莲")))
        assertEquals("Frieren", bangumiDisplayTitle(result(title = "Frieren", titleCn = null)))
    }

    @Test
    fun `batch status reflects plan buckets`() {
        val entry = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv")
        val match = DesktopBangumiBatchMatch(query = "Frieren", result = result())
        val ready = MetadataBatchUpdate(
            query = "Frieren",
            original = entry,
            updated = entry.copy(metadataId = "431767"),
            result = result(),
        )
        val plan = MetadataBatchPlan(
            readyUpdates = listOf(ready),
            reviewMatches = listOf(DesktopBangumiBatchMatch(query = "Review", result = result(confidence = 0.7f))),
            conflicts = listOf(MetadataBatchConflict("Conflict", entry)),
        )

        assertEquals("preview", null.batchStatusFor(match))
        assertEquals("ready", plan.batchStatusFor(match))
        assertEquals("review", plan.batchStatusFor(DesktopBangumiBatchMatch(query = "Review", result = result(confidence = 0.7f))))
        assertEquals("conflict", plan.batchStatusFor(DesktopBangumiBatchMatch(query = "Conflict", result = result())))
    }

    @Test
    fun `candidate selection appends only new bangumi candidates`() {
        val original = result(animeId = "1", source = ScraperSource.BANGUMI)
        val duplicate = result(animeId = "1", source = ScraperSource.BANGUMI, title = "Duplicate")
        val other = result(animeId = "2", source = ScraperSource.BANGUMI)
        val match = DesktopBangumiBatchMatch(query = "Frieren", result = original, candidates = listOf(original))

        val sameCandidate = match.withSelectedCandidate(duplicate)
        val newCandidate = match.withSelectedCandidate(other)

        assertEquals(1, sameCandidate.candidates.size)
        assertEquals(2, newCandidate.candidates.size)
        assertTrue(original.isSameBangumiCandidate(duplicate))
        assertFalse(original.isSameBangumiCandidate(other))
    }

    @Test
    fun `selected candidate label describes current candidate position`() {
        val first = result(animeId = "1")
        val second = result(animeId = "2")

        assertEquals(
            "candidate 2/2",
            DesktopBangumiBatchMatch(query = "Frieren", result = second, candidates = listOf(first, second)).selectedCandidateLabel()
        )
        assertEquals(
            "1 candidates",
            DesktopBangumiBatchMatch(query = "Frieren", result = second, candidates = listOf(first)).selectedCandidateLabel()
        )
    }

    @Test
    fun `replace batch match and index entries use stable keys`() {
        val oldMatch = DesktopBangumiBatchMatch(query = "Frieren", result = result(animeId = "1"))
        val updatedMatch = oldMatch.copy(result = result(animeId = "2"))
        assertEquals(listOf(updatedMatch), listOf(oldMatch).replaceBatchMatch(updatedMatch))

        val entry = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv")
        val other = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/02.mkv")
        val updated = entry.copy(metadataId = "431767")

        assertEquals(listOf(updated, other), listOf(entry, other).replaceEntry(updated))
        assertEquals(listOf(updated, other), listOf(entry, other).replaceEntries(listOf(updated)))
    }

    private fun result(
        animeId: String = "431767",
        title: String = "Frieren",
        titleCn: String? = "葬送的芙莉莲",
        confidence: Float = 0.95f,
        source: ScraperSource = ScraperSource.BANGUMI,
    ): ScraperResult =
        ScraperResult(
            animeId = animeId,
            title = title,
            titleCn = titleCn,
            matchedTitle = titleCn ?: title,
            confidence = confidence,
            source = source,
        )
}
