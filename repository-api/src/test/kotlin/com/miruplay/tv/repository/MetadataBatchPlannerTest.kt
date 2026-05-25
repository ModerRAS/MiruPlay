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
        assertTrue(MetadataBatchPlanner.displayPlanSummary(plan).contains("冲突"))
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
    fun `metadata batch preview display uses shared TV labels`() {
        val preview = MetadataBatchPlanner.displayPreview(
            listOf(
                MetadataBatchMatch(
                    query = "Frieren",
                    result = result(),
                    candidates = listOf(result(), result(animeId = "2", title = "Other", titleCn = null)),
                ),
                MetadataBatchMatch(query = "Missing", result = null, candidates = emptyList()),
            )
        )

        assertEquals(
            "Frieren: Frieren / 葬送的芙莉莲 [可用] 2 个候选\n" +
                "Missing: 无匹配 [复核]\n",
            preview,
        )
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
        assertEquals("候选 2/2", newCandidate.selectedCandidateLabel())
        assertEquals(
            "1 个候选",
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
            searchCandidates = { query, _ ->
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
        assertEquals("2 个可应用，1 个需复核，0 个冲突", preview.summaryStatus())
        assertEquals("正在用 Bangumi 搜索 2 个索引标题...", metadataBatchSearchingStatus(2, "Bangumi"))
    }

    @Test
    fun `previewFor returns empty preview when no metadata queries are available`() = runBlocking {
        val preview = MetadataBatchPlanner.previewFor(
            entries = listOf(MediaIndexEntry(sourceId = 1L, path = "", isDirectory = true)),
            queryLimit = 20,
            searchCandidates = { _, _ -> error("No queries should be searched") },
        )

        assertEquals(emptyList<MetadataBatchMatch>(), preview.matches)
        assertEquals(null, preview.plan)
        assertEquals(null, preview.selectedMatch)
        assertEquals("没有可用于 Bangumi 批量匹配的索引条目。", noMetadataBatchEntriesStatus("Bangumi"))
        assertEquals(noMetadataBatchEntriesStatus(), preview.summaryStatus())
    }

    @Test
    fun `previewFor supplies stable alias candidates for each query`() = runBlocking {
        val searchedCandidates = mutableListOf<List<String>>()

        MetadataBatchPlanner.previewFor(
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/01.mkv",
                    animeName = "Frieren",
                    metadataTitle = "葬送的芙莉莲",
                    metadataId = "431767",
                ),
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Anime/Frieren/02.mkv",
                    animeName = "Frieren",
                    metadataTitle = "葬送的芙莉莲",
                    metadataId = "431767",
                ),
            ),
            queryLimit = 1,
            searchCandidates = { _, candidates ->
                searchedCandidates += candidates
                emptyList()
            },
        )

        assertEquals(listOf(listOf("Frieren", "葬送的芙莉莲", "431767")), searchedCandidates)
    }

    @Test
    fun `metadata status helpers share TV wording`() {
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

        assertEquals("请先选择一个索引视频。", metadataIndexedVideoRequiredStatus())
        assertEquals("选择索引视频后可搜索 Bangumi。", metadataInitialStatus("Bangumi"))
        assertEquals("已从当前索引条目填入搜索词。", metadataQuerySetFromIndexStatus())
        assertEquals("请输入 Bangumi 搜索词，或先选择索引视频。", metadataQueryRequiredStatus("Bangumi"))
        assertEquals("正在搜索 Bangumi：\"Frieren\"...", metadataSearchStartedStatus("Frieren", "Bangumi"))
        assertEquals("没有匹配 \"Frieren\" 的 Bangumi 元数据。", metadataSearchResultStatus("Frieren", 0, "Bangumi"))
        assertEquals("找到 2 个 Bangumi 匹配。", metadataSearchResultStatus("Frieren", 2, "Bangumi"))
        assertEquals("请先打开或扫描媒体源。", metadataSourceRequiredStatus())
        assertEquals("已选择批量复核：Frieren", match.selectedReviewStatus())
        assertEquals("请先选择带 Bangumi 结果的批量匹配。", metadataBatchResultRequiredStatus("Bangumi"))
        assertEquals(
            "当前复核项有 2 个元数据冲突，未覆盖任何内容。",
            conflictPlan.reviewConflictStatus(),
        )
        assertEquals("当前复核项没有匹配的索引条目。", metadataReviewNoMatchStatus())
        assertEquals("已选择：葬送的芙莉莲", result().selectedMetadataStatus())
        assertEquals(
            "请先选择索引视频，再应用 Bangumi 元数据。",
            metadataApplyEntryRequiredStatus("Bangumi"),
        )
        assertEquals("请先搜索 Bangumi 并选择一个匹配。", metadataSearchSelectionRequiredStatus("Bangumi"))
        assertEquals("已将 Bangumi 元数据应用到 D:/Anime/Frieren/01.mkv。", entry.metadataAppliedStatus("Bangumi"))
        assertEquals("请先选择索引视频，再清除元数据。", metadataClearEntryRequiredStatus())
        assertEquals("已清除 D:/Anime/Frieren/01.mkv 的外部元数据。", entry.metadataClearedStatus())
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
        assertEquals("已选择批量候选：Frieren -> 葬送的芙莉莲", selection.selectedStatus())
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
