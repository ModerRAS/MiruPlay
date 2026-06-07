package com.miruplay.tv.repository

import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MatchRecommendation
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchProviderCandidate
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.toDramaSearchResult
import com.miruplay.tv.model.toPreferredDramaSearchResult
import com.miruplay.tv.model.toPreferredScraperResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataSearchAggregationTest {
    @Test
    fun `query planner extracts season aliases path titles and provider hints`() {
        val plan = MetadataQueryPlanner.plan(
            MetadataSearchContext(
                contentMode = MediaContentMode.ANIME,
                title = "Dr STONE 新石纪 第四季",
                originalTitle = "Dr.STONE SCIENCE FUTURE",
                aliases = listOf("Dr STONE 新石纪", "石纪元 科学与未来", "Bangumi:431767"),
                filePathSamples = listOf("/anime/医馆笑传/医馆笑传.S01/医馆笑传.S01E01.mkv"),
                boundProviderRef = MetadataProviderRef(source = "AniList", id = "154587"),
            ),
        )

        assertEquals(4, plan.seasonHint)
        assertTrue(plan.queryTexts.contains("Dr STONE 新石纪 第四季"))
        assertTrue(plan.queryTexts.contains("Dr STONE 新石纪"))
        assertTrue(plan.queryTexts.contains("Dr.STONE SCIENCE FUTURE"))
        assertTrue(plan.queryTexts.contains("医馆笑传"))
        assertEquals(
            listOf(
                MetadataProviderRef(source = "AniList", id = "154587"),
                MetadataProviderRef(source = "Bangumi", id = "431767"),
            ),
            plan.providerRefHints,
        )
        assertTrue(plan.queryTexts.none { it.contains(":") })
    }

    @Test
    fun `aggregate dedupes same provider hits and clusters cross provider equivalents`() {
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.ANIME,
            title = "Frieren",
            localizedTitle = "葬送的芙莉莲",
            aliases = listOf("Sousou no Frieren"),
        )
        val plan = MetadataQueryPlanner.plan(context)

        val result = MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = listOf(
                candidate(
                    source = "Bangumi",
                    id = "431767",
                    title = "Sousou no Frieren",
                    localizedTitle = "葬送的芙莉莲",
                    providerScore = 0.95f,
                    providerRank = 0,
                ),
                candidate(
                    source = "Bangumi",
                    id = "431767",
                    title = "Sousou no Frieren",
                    localizedTitle = "葬送的芙莉莲",
                    providerScore = 0.7f,
                    providerRank = 4,
                ),
                candidate(
                    source = "AniList",
                    id = "154587",
                    title = "Frieren: Beyond Journey's End",
                    originalTitle = "Sousou no Frieren",
                    aliases = listOf("葬送的芙莉莲"),
                    providerScore = 0.82f,
                    providerRank = 1,
                ),
            ),
            providerPriorities = mapOf("bangumi" to 1.06f, "anilist" to 1.0f),
        )

        assertEquals(1, result.candidates.size)
        assertEquals(2, result.candidates.single().providerCandidates.size)
        assertEquals("葬送的芙莉莲", result.candidates.single().displayTitle())
        assertEquals(ScraperSource.BANGUMI, result.candidates.single().toPreferredScraperResult()?.source)
        assertTrue(result.candidates.single().evidence.any { it.reason.contains("多源") })
    }

    @Test
    fun `aggregate keeps same title different years in separate clusters`() {
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.DRAMA,
            title = "The Legend of Heroes",
            yearHint = 2024,
        )
        val plan = MetadataQueryPlanner.plan(context)

        val result = MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = listOf(
                candidate(
                    source = "TMDB",
                    id = "100",
                    title = "The Legend of Heroes",
                    localizedTitle = "金庸武侠世界",
                    firstAirDate = "2024-06-17",
                    providerRank = 0,
                ),
                candidate(
                    source = "TVMaze",
                    id = "200",
                    title = "The Legend of Heroes",
                    localizedTitle = "金庸武侠世界",
                    firstAirDate = "2018-01-01",
                    providerRank = 0,
                ),
            ),
            providerPriorities = mapOf("tmdb" to 1.02f, "tvmaze" to 1.0f),
        )

        assertEquals(2, result.candidates.size)
    }

    @Test
    fun `rerank prefers exact title and season match over higher provider score`() {
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.ANIME,
            title = "Dr STONE 新石纪 第四季",
            aliases = listOf("Dr STONE 新石纪"),
            seasonHint = 4,
        )
        val plan = MetadataQueryPlanner.plan(context)

        val result = MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = listOf(
                candidate(
                    source = "AniList",
                    id = "1",
                    title = "Dr.STONE",
                    localizedTitle = "石纪元",
                    providerScore = 0.99f,
                    providerRank = 0,
                ),
                candidate(
                    source = "Bangumi",
                    id = "2",
                    title = "Dr.STONE SCIENCE FUTURE",
                    localizedTitle = "石纪元 科学与未来",
                    aliases = listOf("Dr STONE 新石纪 第四季"),
                    providerScore = 0.62f,
                    providerRank = 2,
                ),
            ),
            providerPriorities = mapOf("bangumi" to 1.06f, "anilist" to 1.0f),
        )

        val top = result.candidates.first()
        assertEquals("2", top.toPreferredScraperResult(preferredSources = listOf("Bangumi", "AniList"))?.animeId)
        assertTrue(top.rerankScore >= 0.64f)
        assertTrue(top.evidence.any { it.reason.contains("季号一致") })
        assertTrue(top.recommendation == MatchRecommendation.AUTO_ACCEPT || top.recommendation == MatchRecommendation.REVIEW)
    }

    @Test
    fun `year proximity beats weak provider score advantage`() {
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.DRAMA,
            title = "微暗之火",
            yearHint = 2024,
        )
        val plan = MetadataQueryPlanner.plan(context)

        val result = MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = listOf(
                candidate(
                    source = "TMDB",
                    id = "252374",
                    title = "微暗之火",
                    firstAirDate = "2024-04-27",
                    providerScore = 0.55f,
                    providerRank = 3,
                ),
                candidate(
                    source = "TVMaze",
                    id = "99999",
                    title = "微暗之火",
                    firstAirDate = "2018-01-01",
                    providerScore = 0.95f,
                    providerRank = 0,
                ),
            ),
            providerPriorities = mapOf("tmdb" to 1.02f, "tvmaze" to 1.0f),
        )

        assertEquals(252374, result.candidates.first().toPreferredDramaSearchResult()?.tmdbId)
        assertTrue(result.candidates.first().evidence.any { it.reason.contains("年份接近") })
    }

    @Test
    fun `drama representative result can stay non tmdb after aggregation`() {
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.DRAMA,
            title = "金庸武侠世界",
            localizedTitle = "金庸武侠世界",
        )
        val plan = MetadataQueryPlanner.plan(context)

        val result = MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = listOf(
                candidate(
                    source = "TMDB",
                    id = "321",
                    title = "The Legend of Heroes",
                    originalTitle = "金庸武侠世界",
                    providerRank = 0,
                ),
                candidate(
                    source = "TVMaze",
                    id = "maze-321",
                    title = "金庸武侠世界",
                    originalTitle = "The Legend of Heroes",
                    providerRank = 1,
                ),
            ),
            providerPriorities = mapOf("tmdb" to 1.02f, "tvmaze" to 1.0f),
        )

        val projected = result.candidates.first().toDramaSearchResult()
        assertEquals("TVMaze", projected?.providerRef?.source)
        assertEquals(listOf("TMDB", "TVMaze"), projected?.sourceLabels)
        assertEquals("金庸武侠世界", projected?.title)
    }

    @Test
    fun `season mismatch is downgraded even when provider score is higher`() {
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.DRAMA,
            title = "庆余年 第二季",
            seasonHint = 2,
        )
        val plan = MetadataQueryPlanner.plan(context)

        val reranked = MetadataCandidateReranker.rerank(
            context = context,
            plan = plan,
            clusters = listOf(
                listOf(
                    candidate(
                        source = "TMDB",
                        id = "301",
                        title = "庆余年 第二季",
                        providerScore = 0.58f,
                        providerRank = 2,
                    ),
                ),
                listOf(
                    candidate(
                        source = "TVMaze",
                        id = "302",
                        title = "庆余年 第一季",
                        providerScore = 0.99f,
                        providerRank = 0,
                    ),
                ),
            ),
            providerPriorities = mapOf("tmdb" to 1.02f, "tvmaze" to 1.0f),
        )

        assertEquals("301", reranked.first().providerCandidates.single().providerRef.id)
        assertTrue(reranked.first().evidence.any { it.reason.contains("季号一致") })
        assertTrue(reranked.last().evidence.any { it.reason.contains("季号") })
    }

    @Test
    fun `local structure signals outrank higher provider score during rerank`() {
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.DRAMA,
            title = "Mystery Show",
            episodeCountHint = 12,
            seasonCountHint = 1,
        )
        val plan = MetadataQueryPlanner.plan(context)

        val reranked = MetadataCandidateReranker.rerank(
            context = context,
            plan = plan,
            clusters = listOf(
                listOf(
                    candidate(
                        source = "TMDB",
                        id = "101",
                        title = "Mystery Show",
                        providerScore = 0.55f,
                        providerRank = 3,
                        episodeCount = 12,
                        seasonCount = 1,
                    ),
                ),
                listOf(
                    candidate(
                        source = "TVMaze",
                        id = "202",
                        title = "Mystery Show",
                        providerScore = 0.98f,
                        providerRank = 0,
                        episodeCount = 36,
                        seasonCount = 3,
                    ),
                ),
            ),
            providerPriorities = mapOf("tmdb" to 1.02f, "tvmaze" to 1.0f),
        )

        assertEquals("101", reranked.first().providerCandidates.single().providerRef.id)
        assertTrue(reranked.first().evidence.any { it.reason.contains("本地季集结构一致") })
        assertTrue(reranked.first().rerankScore > reranked.last().rerankScore)
    }

    private fun candidate(
        source: String,
        id: String,
        title: String,
        localizedTitle: String? = null,
        originalTitle: String = "",
        aliases: List<String> = emptyList(),
        providerScore: Float? = null,
        providerRank: Int? = null,
        firstAirDate: String? = null,
        episodeCount: Int? = null,
        seasonCount: Int? = null,
    ): MetadataSearchProviderCandidate =
        MetadataSearchProviderCandidate(
            providerRef = MetadataProviderRef(source = source, id = id),
            title = title,
            localizedTitle = localizedTitle,
            originalTitle = originalTitle,
            aliases = aliases,
            matchedQuery = title,
            providerScore = providerScore,
            providerRank = providerRank,
            firstAirDate = firstAirDate,
            summary = "summary",
            posterUrl = "poster",
            episodeCount = episodeCount,
            seasonCount = seasonCount,
        )
}
