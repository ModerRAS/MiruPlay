package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataScraperSearchTest {
    @Test
    fun `preferred search keeps confident direct results without alias lookup`() = runBlocking {
        val direct = result(animeId = "1", confidence = 0.95f)
        val scraper = FakeScraper(
            searchResults = mapOf("Frieren" to listOf(direct)),
            aliasMatch = result(animeId = "2", confidence = 1f),
        )

        val results = scraper.searchPreferredResults("Frieren", listOf("葬送的芙莉莲")).getOrNull().orEmpty()

        assertEquals(listOf(direct), results)
        assertEquals(listOf("Frieren"), scraper.searchQueries)
        assertEquals(emptyList<List<String>>(), scraper.aliasCandidates)
    }

    @Test
    fun `preferred search promotes a stronger alias match after weak direct result`() = runBlocking {
        val weak = result(animeId = "1", title = "Different", confidence = 0.2f)
        val alias = result(animeId = "2", title = "Frieren", confidence = 0.9f)
        val scraper = FakeScraper(
            searchResults = mapOf("候选甲" to listOf(weak)),
            aliasMatch = alias,
        )

        val results = scraper.searchPreferredResults(
            query = "候选甲",
            candidates = listOf("候选甲", "候选二", "候选二", " "),
        ).getOrNull().orEmpty()

        assertEquals(listOf(alias, weak), results)
        assertEquals(listOf(listOf("候选二")), scraper.aliasCandidates)
    }

    @Test
    fun `preferred search ignores weak alias matches`() = runBlocking {
        val weak = result(animeId = "1", confidence = 0.2f)
        val alias = result(animeId = "2", confidence = 0.4f)
        val scraper = FakeScraper(
            searchResults = mapOf("Frieren" to listOf(weak)),
            aliasMatch = alias,
        )

        val results = scraper.searchPreferredResults("Frieren", listOf("葬送的芙莉莲")).getOrNull().orEmpty()

        assertEquals(listOf(weak), results)
    }

    private class FakeScraper(
        private val searchResults: Map<String, List<ScraperResult>>,
        private val aliasMatch: ScraperResult?,
    ) : MetadataScraper {
        val searchQueries = mutableListOf<String>()
        val aliasCandidates = mutableListOf<List<String>>()

        override val sourceName: String = "Fake"

        override suspend fun searchAnime(query: String): Result<List<ScraperResult>> {
            searchQueries += query
            return Result.success(searchResults[query].orEmpty())
        }

        override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
            error("Unused")

        override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
            Result.success(emptyList())

        override suspend fun searchByAlias(
            normalizedName: String,
            candidates: List<String>,
        ): Result<ScraperResult?> {
            aliasCandidates += candidates
            return Result.success(aliasMatch)
        }
    }

    private fun result(
        animeId: String,
        title: String = "葬送のフリーレン",
        confidence: Float,
    ): ScraperResult =
        ScraperResult(
            animeId = animeId,
            title = title,
            titleCn = "葬送的芙莉莲",
            matchedTitle = title,
            confidence = confidence,
            source = ScraperSource.BANGUMI,
        )
}
