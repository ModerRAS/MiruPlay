package com.miruplay.tv.repository

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import org.junit.Assert.assertEquals
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
}
