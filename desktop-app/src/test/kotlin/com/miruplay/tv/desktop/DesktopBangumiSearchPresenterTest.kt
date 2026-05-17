package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.MediaIndexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBangumiSearchPresenterTest {
    @Test
    fun `displayResults formats ranked Bangumi matches`() {
        val output = DesktopBangumiSearchPresenter.displayResults(
            query = "Frieren",
            results = listOf(
                ScraperResult(
                    animeId = "431767",
                    title = "葬送のフリーレン",
                    titleCn = "葬送的芙莉莲",
                    matchedTitle = "葬送的芙莉莲",
                    confidence = 0.96f,
                    source = ScraperSource.BANGUMI,
                )
            )
        )

        assertTrue(output.contains("Bangumi matches for \"Frieren\""))
        assertTrue(output.contains("1. 葬送のフリーレン / 葬送的芙莉莲"))
        assertTrue(output.contains("id=431767"))
        assertTrue(output.contains("confidence=96%"))
    }

    @Test
    fun `details formats selected Bangumi match`() {
        val details = DesktopBangumiSearchPresenter.details(
            ScraperResult(
                animeId = "431767",
                title = "葬送のフリーレン",
                titleCn = "葬送的芙莉莲",
                matchedTitle = "葬送的芙莉莲",
                confidence = 0.96f,
                source = ScraperSource.BANGUMI,
            )
        )

        assertTrue(details.contains("ID: 431767"))
        assertTrue(details.contains("Chinese title: 葬送的芙莉莲"))
        assertTrue(details.contains("Confidence: 96%"))
        assertTrue(details.contains("Source: BANGUMI"))
    }

    @Test
    fun `batch presenter derives distinct queries from index entries`() {
        val queries = DesktopBangumiBatchPresenter.queriesFor(
            listOf(
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/02.mkv", animeName = "Frieren"),
                MediaIndexEntry(sourceId = 1L, path = "D:/Anime/NoName/01.mkv", metadataTitle = "No Name"),
            )
        )

        assertEquals(listOf("Frieren", "No Name"), queries)
    }

    @Test
    fun `batch presenter separates ready matches by confidence`() {
        val ready = DesktopBangumiBatchMatch(
            query = "Frieren",
            result = ScraperResult(
                animeId = "431767",
                title = "葬送のフリーレン",
                titleCn = "葬送的芙莉莲",
                matchedTitle = "葬送的芙莉莲",
                confidence = 0.94f,
                source = ScraperSource.BANGUMI,
            )
        )
        val review = ready.copy(query = "Other", result = ready.result?.copy(confidence = 0.72f))

        val accepted = DesktopBangumiBatchPresenter.acceptedMatches(listOf(ready, review))
        val preview = DesktopBangumiBatchPresenter.displayPreview(listOf(ready, review))

        assertEquals(listOf(ready), accepted)
        assertTrue(preview.contains("Frieren: 葬送のフリーレン / 葬送的芙莉莲"))
        assertTrue(preview.contains("ready"))
        assertTrue(preview.contains("review"))
    }

    @Test
    fun `batch presenter preserves alternate candidates for review selection`() {
        val firstCandidate = ScraperResult(
            animeId = "100",
            title = "Wrong Frieren",
            titleCn = null,
            matchedTitle = "Frieren",
            confidence = 0.78f,
            source = ScraperSource.BANGUMI,
        )
        val selectedCandidate = ScraperResult(
            animeId = "431767",
            title = "葬送のフリーレン",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "Frieren",
            confidence = 0.95f,
            source = ScraperSource.BANGUMI,
        )
        val reviewed = DesktopBangumiBatchMatch(
            query = "Frieren",
            result = firstCandidate,
            candidates = listOf(firstCandidate, selectedCandidate),
        ).copy(result = selectedCandidate)

        val plan = DesktopBangumiBatchPresenter.planFor(
            entries = listOf(MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren")),
            matches = listOf(reviewed),
        )
        val preview = DesktopBangumiBatchPresenter.displayPreview(listOf(reviewed))

        assertEquals(2, reviewed.candidates.size)
        assertEquals("431767", plan.readyUpdates.single().updated.metadataId)
        assertTrue(preview.contains("candidates=2"))
    }

    @Test
    fun `batch presenter plans ready updates and isolates metadata conflicts`() {
        val result = ScraperResult(
            animeId = "431767",
            title = "葬送のフリーレン",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "葬送的芙莉莲",
            confidence = 0.94f,
            source = ScraperSource.BANGUMI,
        )
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Frieren/02.mkv",
                animeName = "Frieren",
                metadataSource = "BANGUMI",
                metadataId = "old-id",
                metadataTitle = "Old title",
            ),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Other/01.mkv", animeName = "Other"),
        )

        val cleanPlan = DesktopBangumiBatchPresenter.planFor(
            entries = listOf(entries.first()),
            matches = listOf(DesktopBangumiBatchMatch(query = "Frieren", result = result)),
        )
        val conflictPlan = DesktopBangumiBatchPresenter.planFor(
            entries = entries,
            matches = listOf(
                DesktopBangumiBatchMatch(query = "Frieren", result = result),
                DesktopBangumiBatchMatch(query = "Other", result = result.copy(confidence = 0.72f)),
            ),
        )

        assertEquals(1, cleanPlan.readyUpdates.size)
        assertEquals("葬送的芙莉莲", cleanPlan.readyUpdates.single().updated.metadataTitle)
        assertEquals(0, cleanPlan.conflicts.size)
        assertEquals(0, conflictPlan.readyUpdates.size)
        assertEquals(2, conflictPlan.conflicts.size)
        assertEquals(1, conflictPlan.reviewMatches.size)
        assertTrue(DesktopBangumiBatchPresenter.displayPlanSummary(conflictPlan).contains("conflicts"))
    }
}
