package com.miruplay.tv.repository

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataBatchPlannerTest {
    @Test
    fun `queries use anime metadata then file name fallback`() {
        val queries = MetadataBatchPlanner.queriesFor(
            listOf(
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/02.mkv", animeName = "Frieren"),
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/NoName/01.mkv", metadataTitle = "No Name"),
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Other/03.mkv"),
            )
        )

        assertEquals(listOf("Frieren", "No Name", "03"), queries)
    }

    @Test
    fun `plan applies high confidence matches and isolates existing metadata conflicts`() {
        val result = ScraperResult(
            animeId = "431767",
            title = "葬送のフリーレン",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "葬送的芙莉莲",
            confidence = 0.94f,
            source = ScraperSource.BANGUMI,
        )
        val plan = MetadataBatchPlanner.planFor(
            entries = listOf(
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Other/01.mkv",
                    animeName = "Other",
                    metadataId = "old",
                ),
            ),
            matches = listOf(
                MetadataBatchMatch(query = "Frieren", result = result),
                MetadataBatchMatch(query = "Other", result = result),
                MetadataBatchMatch(query = "Review", result = result.copy(confidence = 0.70f)),
            ),
        )

        assertEquals("葬送的芙莉莲", plan.readyUpdates.single().updated.metadataTitle)
        assertEquals("BANGUMI", plan.readyUpdates.single().updated.metadataSource)
        assertEquals("Other", plan.conflicts.single().query)
        assertEquals("Review", plan.reviewMatches.single().query)
        assertTrue(MetadataBatchPlanner.displayPlanSummary(plan).contains("conflicts"))
    }

    @Test
    fun `metadata batch status reflects plan buckets`() {
        val entry = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv")
        val match = MetadataBatchMatch(query = "Frieren", result = result())
        val ready = MetadataBatchUpdate(
            query = "Frieren",
            original = entry,
            updated = entry.copy(metadataId = "431767"),
            result = result(),
        )
        val plan = MetadataBatchPlan(
            readyUpdates = listOf(ready),
            reviewMatches = listOf(MetadataBatchMatch(query = "Review", result = result(confidence = 0.7f))),
            conflicts = listOf(MetadataBatchConflict("Conflict", entry)),
        )

        assertEquals("preview", null.statusFor(match))
        assertEquals("ready", plan.statusFor(match))
        assertEquals("review", plan.statusFor(MetadataBatchMatch(query = "Review", result = result(confidence = 0.7f))))
        assertEquals("conflict", plan.statusFor(MetadataBatchMatch(query = "Conflict", result = result())))
    }

    @Test
    fun `metadata candidate helpers use stable scraper keys`() {
        val original = result(animeId = "1", source = ScraperSource.BANGUMI)
        val duplicate = result(animeId = "1", source = ScraperSource.BANGUMI, title = "Duplicate")
        val other = result(animeId = "2", source = ScraperSource.BANGUMI)
        val match = MetadataBatchMatch(query = "Frieren", result = original, candidates = listOf(original))

        val sameCandidate = match.withSelectedCandidate(duplicate)
        val newCandidate = match.withSelectedCandidate(other)

        assertEquals(1, sameCandidate.candidates.size)
        assertEquals(2, newCandidate.candidates.size)
        assertTrue(original.isSameCandidate(duplicate))
        assertFalse(original.isSameCandidate(other))
        assertEquals("95%", original.confidencePercentLabel())
        assertEquals("candidate 2/2", newCandidate.selectedCandidateLabel())
        assertEquals(
            "1 candidates",
            MetadataBatchMatch(query = "Frieren", result = other, candidates = listOf(original)).selectedCandidateLabel(),
        )
        assertEquals(listOf(newCandidate), listOf(match).replaceMatch(newCandidate))
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
