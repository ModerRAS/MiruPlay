package com.miruplay.tv.repository

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.model.confidencePercentLabel
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `previewFor searches limited media queries and selects the first reviewable match`() = runBlocking {
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/02.mkv", animeName = "Frieren"),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Other/01.mkv", animeName = "Other"),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Directory", animeName = "Ignored", isDirectory = true),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Skipped/01.mkv", animeName = "Skipped"),
        )
        val searchedQueries = mutableListOf<String>()

        val preview = MetadataBatchPlanner.previewFor(
            entries = entries,
            queryLimit = 2,
            searchCandidates = { query ->
                searchedQueries += query
                when (query) {
                    "Frieren" -> listOf(result(confidence = 0.95f))
                    "Other" -> listOf(result(animeId = "2", title = "Other", titleCn = null, confidence = 0.70f))
                    else -> emptyList()
                }
            },
        )

        assertEquals(2, MetadataBatchPlanner.previewQueryCount(entries, queryLimit = 2))
        assertEquals(listOf("Frieren", "Other"), searchedQueries)
        assertEquals(2, preview.matches.size)
        assertEquals("Other", preview.selectedMatch?.query)
        assertEquals(2, preview.plan?.readyUpdates?.size)
        assertEquals(1, preview.plan?.reviewMatches?.size)
        assertEquals("2 ready, 1 review, 0 conflicts", preview.summaryStatus())
        assertEquals("Searching Bangumi for 2 indexed title(s)...", metadataBatchSearchingStatus(2, "Bangumi"))
    }

    @Test
    fun `previewFor returns empty preview when no metadata queries are available`() = runBlocking {
        val preview = MetadataBatchPlanner.previewFor(
            entries = listOf(MediaIndexEntry(sourceId = 1L, path = "", isDirectory = true)),
            queryLimit = 20,
            searchCandidates = { error("No queries should be searched") },
        )

        assertEquals(emptyList<MetadataBatchMatch>(), preview.matches)
        assertEquals(null, preview.plan)
        assertEquals(null, preview.selectedMatch)
        assertEquals("No indexed entries are available for Bangumi batch matching.", noMetadataBatchEntriesStatus("Bangumi"))
        assertEquals(noMetadataBatchEntriesStatus(), preview.summaryStatus())
    }

    @Test
    fun `metadata status helpers share desktop wording`() {
        val entry = MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv")
        val match = MetadataBatchMatch(query = "Frieren", result = result())
        val conflictPlan = MetadataBatchPlan(
            readyUpdates = emptyList(),
            reviewMatches = emptyList(),
            conflicts = listOf(
                MetadataBatchConflict("Frieren", entry),
                MetadataBatchConflict("Frieren", entry.copy(path = "D:/Anime/Frieren/02.mkv")),
            ),
        )

        assertEquals("Select an indexed video first.", metadataIndexedVideoRequiredStatus())
        assertEquals("Select an indexed video, then search Bangumi.", metadataInitialStatus("Bangumi"))
        assertEquals("Query set from selected index entry.", metadataQuerySetFromIndexStatus())
        assertEquals("Enter a Bangumi query or select an indexed video.", metadataQueryRequiredStatus("Bangumi"))
        assertEquals("Searching Bangumi for \"Frieren\"...", metadataSearchStartedStatus("Frieren", "Bangumi"))
        assertEquals("No Bangumi metadata matched \"Frieren\".", metadataSearchResultStatus("Frieren", 0, "Bangumi"))
        assertEquals("Found 2 Bangumi match(es).", metadataSearchResultStatus("Frieren", 2, "Bangumi"))
        assertEquals("Open or scan a source first.", metadataSourceRequiredStatus())
        assertEquals("Selected batch review: Frieren.", match.selectedReviewStatus())
        assertEquals("Select a batch match with a Bangumi result first.", metadataBatchResultRequiredStatus("Bangumi"))
        assertEquals(
            "Selected review has 2 metadata conflicts; nothing was overwritten.",
            conflictPlan.reviewConflictStatus(),
        )
        assertEquals("Selected review has no matching indexed entries.", metadataReviewNoMatchStatus())
        assertEquals("Selected 葬送的芙莉莲.", result().selectedMetadataStatus())
        assertEquals(
            "Select an indexed video before applying Bangumi metadata.",
            metadataApplyEntryRequiredStatus("Bangumi"),
        )
        assertEquals("Search Bangumi and select a match first.", metadataSearchSelectionRequiredStatus("Bangumi"))
        assertEquals("Applied Bangumi metadata to D:/Anime/Frieren/01.mkv.", entry.metadataAppliedStatus("Bangumi"))
        assertEquals("Select an indexed video before clearing metadata.", metadataClearEntryRequiredStatus())
        assertEquals("Cleared external metadata for D:/Anime/Frieren/01.mkv.", entry.metadataClearedStatus())
    }

    @Test
    fun `selectCandidate replaces match and replans against media entries`() {
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren", animeName = "Frieren", isDirectory = true),
        )
        val lowConfidence = result(confidence = 0.70f)
        val highConfidence = result(confidence = 1f)
        val match = MetadataBatchMatch(
            query = "Frieren",
            result = lowConfidence,
            candidates = listOf(lowConfidence, highConfidence),
        )

        val selection = MetadataBatchPlanner.selectCandidate(
            entries = entries,
            matches = listOf(match),
            match = match,
            candidate = highConfidence,
        )

        assertEquals(highConfidence, selection.updatedMatch.result)
        assertEquals(listOf(selection.updatedMatch), selection.updatedMatches)
        assertEquals(1, selection.plan.readyUpdates.size)
        assertEquals("Selected batch candidate for Frieren: 葬送的芙莉莲.", selection.selectedStatus())
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
