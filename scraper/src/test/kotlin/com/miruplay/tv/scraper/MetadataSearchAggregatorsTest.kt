package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchIntent
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.BangumiEpisodeMetadata
import com.miruplay.tv.repository.MetadataQueryPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataSearchAggregatorsTest {
    @Test
    fun `manual Bangumi provider searches explicit queries without alias fallback`() = runBlocking {
        val scraper = RecordingManualScraper()
        val provider = BangumiAnimeMetadataSearchProvider(scraper, scraper)
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.ANIME,
            intent = MetadataSearchIntent.MANUAL_MATCH,
            aliases = listOf("Dr STONE 新石纪 第四季", "Dr STONE 新石纪"),
        )

        provider.search(context, MetadataQueryPlanner.plan(context))

        assertEquals(listOf("Dr STONE 新石纪 第四季", "Dr STONE 新石纪"), scraper.manualQueries)
        assertEquals(emptyList<List<String>>(), scraper.aliasSearches)
    }
}

private class RecordingManualScraper : MetadataScraper, ManualMetadataSearchScraper {
    val manualQueries = mutableListOf<String>()
    val aliasSearches = mutableListOf<List<String>>()

    override val sourceName: String = "Bangumi"

    override suspend fun searchManualAnime(query: String): Result<List<ScraperResult>> {
        manualQueries += query
        return Result.success(listOf(result(query)))
    }

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
        Result.success(listOf(result(query)))

    override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
        Result.success(Anime(id = animeId, title = animeId))

    override suspend fun getEpisodes(animeId: String): Result<List<BangumiEpisodeMetadata>> =
        Result.success(emptyList())

    override suspend fun searchByAlias(
        normalizedName: String,
        candidates: List<String>,
    ): Result<ScraperResult?> {
        aliasSearches += candidates
        return Result.success(null)
    }

    private fun result(query: String): ScraperResult =
        ScraperResult(
            animeId = query,
            title = query,
            matchedTitle = query,
            confidence = 0.1f,
            source = ScraperSource.BANGUMI,
        )
}
