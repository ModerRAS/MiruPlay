package com.miruplay.tv.ui.detail

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.AggregatedMetadataSearchResult
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.boundMetadataProviderRef
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchProviderCandidate
import com.miruplay.tv.model.MetadataSearchIntent
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.DramaMetadataRepository
import com.miruplay.tv.repository.DramaMetadataSearchAggregator
import com.miruplay.tv.repository.MetadataQueryPlanner
import com.miruplay.tv.repository.MetadataSearchAggregationSupport
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.dramaSeriesCacheKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DramaDetailViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSeries groups episodes into seasons and loads progress`() = runTest {
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 2,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s02e01.mkv",
                        fileName = "s02e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = EmptyDramaMetadataRepository(),
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(
                progressByEpisodeId = mapOf(
                    "9:/drama/series-1/s01e01.mkv" to ProgressRecord(
                        episodeId = "9:/drama/series-1/s01e01.mkv",
                        positionMs = 15_000L,
                        lastWatched = 456L,
                    ),
                ),
            ),
            credentials = DetailFakeCredentialStore(),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertEquals("示例剧", viewModel.series.value?.title)
        assertEquals(listOf(1, 2), viewModel.seasons.value.map { it.seasonNumber })
        assertEquals(1, viewModel.selectedSeason.value)
        assertEquals(1, viewModel.episodesWithProgress.value.size)
        assertEquals(15_000L, viewModel.episodesWithProgress.value.single().second?.positionMs)
        assertEquals("继续观看 1", viewModel.primaryActionLabel.value)
        assertEquals("示例剧", viewModel.heroTitle.value)
        assertNull(viewModel.actionMessage.value)
        assertFalse(viewModel.isRefreshingMetadata.value)
        assertFalse(viewModel.hasTmdbTokenConfigured.value)
    }

    @Test
    fun `loadSeries keeps local detail visible while initial metadata enrichment is running`() = runTest {
        val enrichmentGate = CompletableDeferred<Unit>()
        val metadataRepository = DeferredInitialDramaMetadataRepository(
            gate = enrichmentGate,
            response = Result.success(
                DramaSeriesMetadata(
                    series = DramaSeries(
                        id = "tmdb:321",
                        title = "示例剧",
                        summary = "在线简介",
                        tmdbId = 321,
                    ),
                    seasons = listOf(
                        dramaSeasonMetadata(
                            seasonNumber = 1,
                            episodeTitles = listOf("在线标题"),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    MediaIndexEntry(
                        sourceId = 9L,
                        path = "/drama/series-1/s01e01.mkv",
                        animeName = "示例剧",
                        episodeTitle = "本地标题",
                        plot = "本地简介",
                        seasonNumber = 1,
                        episodeNumber = 1,
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertTrue(viewModel.isRefreshingMetadata.value)
        assertEquals("本地简介", viewModel.series.value?.summary)
        assertEquals("本地标题", viewModel.episodesWithProgress.value.single().first.title)

        enrichmentGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.isRefreshingMetadata.value)
        assertEquals("在线简介", viewModel.series.value?.summary)
        assertEquals("在线标题", viewModel.episodesWithProgress.value.single().first.title)
    }

    @Test
    fun `selectSeason switches visible episode list and publishes season status`() = runTest {
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 2,
                        episodeNumber = 3,
                        filePath = "/drama/series-1/s02e03.mkv",
                        fileName = "s02e03.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = EmptyDramaMetadataRepository(),
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()
        viewModel.selectSeason(2)

        assertEquals(2, viewModel.selectedSeason.value)
        assertEquals(1, viewModel.episodesWithProgress.value.size)
        assertEquals(2, viewModel.episodesWithProgress.value.single().first.seasonNumber)
        assertEquals(3, viewModel.episodesWithProgress.value.single().first.episodeNumber)
        assertEquals("已切换到第 2 季，共 1 集。", viewModel.actionMessage.value)
    }

    @Test
    fun `selectSeason reports empty state when selected season has no episodes`() = runTest {
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = EmptyDramaMetadataRepository(),
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()
        viewModel.selectSeason(2)

        assertEquals(2, viewModel.selectedSeason.value)
        assertTrue(viewModel.episodesWithProgress.value.isEmpty())
        assertEquals("第 2 季还没有可播放剧集。", viewModel.actionMessage.value)
    }

    @Test
    fun `loadSeries clears state when series is missing`() = runTest {
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(entries = emptyList()),
            dramaMetadataRepository = EmptyDramaMetadataRepository(),
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("missing")
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
        assertEquals(null, viewModel.series.value)
        assertTrue(viewModel.seasons.value.isEmpty())
        assertTrue(viewModel.episodesWithProgress.value.isEmpty())
        assertEquals("播放", viewModel.primaryActionLabel.value)
        assertEquals("", viewModel.heroTitle.value)
        assertNull(viewModel.actionMessage.value)
    }

    @Test
    fun `refreshSeries updates metadata and publishes success message`() = runTest {
        val metadataRepository = MutableDramaMetadataRepository(
            responses = mutableListOf(
                Result.success(
                    DramaSeriesMetadata(
                        series = DramaSeries(
                            id = "tmdb:1",
                            title = "示例剧",
                            summary = "初始简介",
                            tmdbId = 1,
                        ),
                        seasons = listOf(
                            dramaSeasonMetadata(
                                seasonNumber = 1,
                                episodeTitles = listOf("初始标题"),
                            ),
                        ),
                    ),
                ),
                Result.success(
                    DramaSeriesMetadata(
                        series = DramaSeries(
                            id = "tmdb:1",
                            title = "示例剧",
                            summary = "刷新后简介",
                            tmdbId = 1,
                        ),
                        seasons = listOf(
                            dramaSeasonMetadata(
                                seasonNumber = 1,
                                episodeTitles = listOf("刷新后标题"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val indexRepository = DetailFakeMediaIndexRepository(
            entries = listOf(
                detailEntry(
                    animeName = "示例剧",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    filePath = "/drama/series-1/s01e01.mkv",
                    fileName = "s01e01.mkv",
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = indexRepository,
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()
        assertEquals("初始简介", viewModel.series.value?.summary)
        assertEquals("初始标题", viewModel.episodesWithProgress.value.single().first.title)

        viewModel.refreshSeries()
        advanceUntilIdle()

        assertEquals("刷新后简介", viewModel.series.value?.summary)
        assertEquals("刷新后标题", viewModel.episodesWithProgress.value.single().first.title)
        assertEquals("电视剧信息已刷新。", viewModel.actionMessage.value)
        assertEquals(1, indexRepository.upsertedEntries.size)
        assertEquals("刷新后标题", indexRepository.upsertedEntries.single().episodeTitle)
        assertEquals("刷新后简介", indexRepository.upsertedEntries.single().plot)
        assertEquals("TMDB", indexRepository.upsertedEntries.single().metadataSource)
        assertEquals("1", indexRepository.upsertedEntries.single().metadataId)
        assertFalse(viewModel.isRefreshingMetadata.value)
        assertTrue(viewModel.hasTmdbTokenConfigured.value)
    }

    @Test
    fun `initial drama metadata enrichment stores reusable series cache`() = runTest {
        val metadataRepository = DeferredInitialDramaMetadataRepository(
            gate = CompletableDeferred<Unit>().also { it.complete(Unit) },
            response = Result.success(
                DramaSeriesMetadata(
                    series = DramaSeries(
                        id = "tmdb:9",
                        title = "庆余年 第一季",
                        originalTitle = "Joy of Life",
                        summary = "缓存简介",
                        posterUrl = "poster-url",
                        fanartUrl = "fanart-url",
                        firstAirDate = "2024-01-01",
                        tmdbId = 9,
                    ),
                    seasons = listOf(
                        dramaSeasonMetadata(
                            seasonNumber = 1,
                            episodeTitles = listOf("在线标题"),
                        ),
                    ),
                ),
            ),
        )
        val cacheRepository = DetailFakeMetadataRepository()
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "庆余年",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = cacheRepository,
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("庆余年")
        advanceUntilIdle()

        val cached = cacheRepository.storedMetadata[dramaSeriesCacheKey("庆余年")]
        assertEquals("庆余年 第一季", cached?.title)
        assertEquals("Joy of Life", cached?.titleCn)
        assertEquals("缓存简介", cached?.summary)
        assertEquals("poster-url", cached?.posterUrl)
        assertEquals("fanart-url", cached?.fanartUrl)
        assertEquals(9, cached?.tmdbId)
    }

    @Test
    fun `refreshSeries publishes failure message when metadata refresh fails`() = runTest {
        val metadataRepository = MutableDramaMetadataRepository(
            responses = mutableListOf(
                Result.success(null),
                Result.failure(AppError.NetworkError.HttpError(429, "Too Many Requests")),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()
        assertNull(viewModel.actionMessage.value)

        viewModel.refreshSeries()
        advanceUntilIdle()

        assertEquals("刷新电视剧信息失败：HTTP 错误 429：Too Many Requests", viewModel.actionMessage.value)
    }

    @Test
    fun `loadSeries exposes playable primary target when continue progress is missing`() = runTest {
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 2,
                        filePath = "/drama/series-1/s01e02.mkv",
                        fileName = "s01e02.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = EmptyDramaMetadataRepository(),
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()

        assertNull(viewModel.continueEpisode.value)
        assertEquals("播放", viewModel.primaryActionLabel.value)
        assertEquals("9:/drama/series-1/s01e01.mkv", viewModel.primaryActionEpisode.value?.id)
        assertTrue(viewModel.hasPlayableEpisodes.value)
    }

    @Test
    fun `refreshSeries shows provider neutral guidance when no direct refresh source is available`() = runTest {
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = EmptyDramaMetadataRepository(),
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = null),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()
        viewModel.refreshSeries()
        advanceUntilIdle()

        assertEquals(
            "当前没有可直接刷新的在线详情源；在线手动匹配仍可继续使用。如果还没绑定在线来源，也可以配置 TMDB Token 来启用按标题直接刷新。",
            viewModel.actionMessage.value,
        )
    }

    @Test
    fun `loadSeries auto refreshes bound tvmaze metadata without tmdb token`() = runTest {
        val providerRef = MetadataProviderRef(source = "TVMaze", id = "maze-321")
        val metadataRepository = SearchableDramaMetadataRepository(
            metadataByProviderRef = mapOf(
                providerRef to Result.success(
                    DramaSeriesMetadata(
                        series = DramaSeries(
                            id = "tvmaze:maze-321",
                            title = "示例剧",
                            summary = "TVMaze 在线简介",
                            metadataProviderRef = providerRef,
                        ),
                        seasons = listOf(
                            dramaSeasonMetadata(
                                seasonNumber = 1,
                                episodeTitles = listOf("TVMaze 在线标题"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    MediaIndexEntry(
                        sourceId = 9L,
                        path = "/drama/series-1/s01e01.mkv",
                        animeName = "示例剧",
                        episodeTitle = "本地标题",
                        plot = "本地简介",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        metadataSource = "TVMaze",
                        metadataId = "maze-321",
                        metadataTitle = "示例剧",
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = null),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()

        assertEquals(listOf(providerRef), metadataRepository.requestedProviderRefs)
        assertFalse(viewModel.hasTmdbTokenConfigured.value)
        assertTrue(viewModel.canRefreshBoundMetadata.value)
        assertEquals("TVMaze 在线简介", viewModel.series.value?.summary)
        assertEquals("TVMaze 在线标题", viewModel.episodesWithProgress.value.single().first.title)
        assertNull(viewModel.actionMessage.value)
    }

    @Test
    fun `manual match search uses local drama title and returns tmdb candidates`() = runTest {
        val metadataRepository = SearchableDramaMetadataRepository(
            searchResultsByQuery = mapOf(
                "金庸武侠世界" to Result.success(
                    listOf(
                        DramaMetadataSearchResult(
                            tmdbId = 321,
                            title = "金庸武侠世界",
                            originalTitle = "The Legend of Heroes",
                            summary = "在线简介",
                            firstAirDate = "2024-06-17",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "金庸武侠世界",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = RepositoryBackedDramaSearchAggregator(metadataRepository),
        )

        viewModel.loadSeries("金庸武侠世界")
        advanceUntilIdle()
        viewModel.openManualMatch()
        viewModel.searchManualMatches()
        advanceUntilIdle()

        assertTrue(metadataRepository.requestedQueries.contains("金庸武侠世界"))
        assertTrue(viewModel.manualMatch.value.isOpen)
        assertEquals(1, viewModel.manualMatch.value.results.size)
        assertEquals("金庸武侠世界", viewModel.manualMatch.value.selectedResult?.title)
        assertEquals("找到 1 个聚合候选。", viewModel.manualMatch.value.statusMessage)
    }

    @Test
    fun `manual match search still shows provider neutral results without tmdb token`() = runTest {
        val metadataRepository = SearchableDramaMetadataRepository(
            searchResultsByQuery = mapOf(
                "金庸武侠世界" to Result.success(
                    listOf(
                        providerNeutralDramaMetadataSearchResult(
                            source = "TVMaze",
                            id = "maze-321",
                            title = "金庸武侠世界",
                            originalTitle = "The Legend of Heroes",
                            summary = "在线简介",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "金庸武侠世界",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = null),
            dramaSearchAggregator = RepositoryBackedDramaSearchAggregator(metadataRepository),
        )

        viewModel.loadSeries("金庸武侠世界")
        advanceUntilIdle()
        viewModel.openManualMatch()
        viewModel.searchManualMatches()
        advanceUntilIdle()

        assertTrue(metadataRepository.requestedQueries.contains("金庸武侠世界"))
        assertEquals(1, viewModel.manualMatch.value.results.size)
        assertEquals("TVMaze", viewModel.manualMatch.value.selectedResult?.providerRef?.source)
        assertEquals(listOf("TVMaze"), viewModel.manualMatch.value.selectedResult?.sourceLabels)
        assertEquals("找到 1 个聚合候选。", viewModel.manualMatch.value.statusMessage)
    }

    @Test
    fun `manual match aggregated drama result does not hard prefer tmdb representative`() = runTest {
        val metadataRepository = SearchableDramaMetadataRepository(
            searchResultsByQuery = mapOf(
                "金庸武侠世界" to Result.success(
                    listOf(
                        providerNeutralDramaMetadataSearchResult(
                            source = "TMDB",
                            id = "321",
                            title = "The Legend of Heroes",
                            originalTitle = "金庸武侠世界",
                        ),
                        providerNeutralDramaMetadataSearchResult(
                            source = "TVMaze",
                            id = "maze-321",
                            title = "金庸武侠世界",
                            originalTitle = "The Legend of Heroes",
                            summary = "在线简介",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "金庸武侠世界",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = null),
            dramaSearchAggregator = RepositoryBackedDramaSearchAggregator(metadataRepository),
        )

        viewModel.loadSeries("金庸武侠世界")
        advanceUntilIdle()
        viewModel.openManualMatch()
        viewModel.searchManualMatches()
        advanceUntilIdle()

        val selectedResult = viewModel.manualMatch.value.selectedResult
        assertEquals(1, viewModel.manualMatch.value.results.size)
        assertEquals("TVMaze", selectedResult?.providerRef?.source)
        assertEquals(listOf("TMDB", "TVMaze"), selectedResult?.sourceLabels)
        assertEquals("金庸武侠世界", selectedResult?.title)
    }

    @Test
    fun `applyManualMatch persists selected tmdb metadata into cache and index`() = runTest {
        val metadataRepository = SearchableDramaMetadataRepository(
            metadataById = mapOf(
                321 to Result.success(
                    DramaSeriesMetadata(
                        series = DramaSeries(
                            id = "tmdb:321",
                            title = "金庸武侠世界",
                            originalTitle = "The Legend of Heroes",
                            summary = "在线简介",
                            posterUrl = "poster-url",
                            fanartUrl = "fanart-url",
                            firstAirDate = "2024-06-17",
                            tmdbId = 321,
                            metadataProviderRef = MetadataProviderRef(source = "TMDB", id = "321"),
                        ),
                        seasons = listOf(
                            dramaSeasonMetadata(
                                seasonNumber = 1,
                                episodeTitles = listOf("在线标题"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val indexRepository = DetailFakeMediaIndexRepository(
            entries = listOf(
                detailEntry(
                    animeName = "金庸武侠世界",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    filePath = "/drama/series-1/s01e01.mkv",
                    fileName = "s01e01.mkv",
                ),
            ),
        )
        val cacheRepository = DetailFakeMetadataRepository()
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = indexRepository,
            dramaMetadataRepository = metadataRepository,
            metadataRepository = cacheRepository,
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("金庸武侠世界")
        advanceUntilIdle()
        viewModel.openManualMatch()
        viewModel.selectManualMatchResult(
            DramaMetadataSearchResult(
                tmdbId = 321,
                title = "金庸武侠世界",
            ),
        )
        viewModel.applyManualMatch()
        advanceUntilIdle()

        val cached = cacheRepository.storedMetadata[dramaSeriesCacheKey("金庸武侠世界")]
        assertEquals("金庸武侠世界", cached?.title)
        assertEquals("The Legend of Heroes", cached?.titleCn)
        assertEquals("在线简介", cached?.summary)
        assertEquals("poster-url", cached?.posterUrl)
        assertEquals("fanart-url", cached?.fanartUrl)
        assertEquals(321, cached?.tmdbId)
        assertEquals(1, indexRepository.upsertedEntries.size)
        assertEquals("321", indexRepository.upsertedEntries.single().metadataId)
        assertEquals("金庸武侠世界", indexRepository.upsertedEntries.single().metadataTitle)
        assertEquals("在线标题", indexRepository.upsertedEntries.single().episodeTitle)
        assertEquals("已应用手动匹配，电视剧信息已更新。", viewModel.actionMessage.value)
        assertEquals(321, viewModel.series.value?.tmdbId)
        assertFalse(viewModel.manualMatch.value.isOpen)
    }

    @Test
    fun `applyManualMatch persists provider neutral metadata binding`() = runTest {
        val providerRef = MetadataProviderRef(source = "TVMaze", id = "maze-321")
        val metadataRepository = SearchableDramaMetadataRepository(
            metadataByProviderRef = mapOf(
                providerRef to Result.success(
                    DramaSeriesMetadata(
                        series = DramaSeries(
                            id = "tvmaze:maze-321",
                            title = "金庸武侠世界",
                            originalTitle = "The Legend of Heroes",
                            summary = "在线简介",
                            posterUrl = "poster-url",
                            fanartUrl = "fanart-url",
                            firstAirDate = "2024-06-17",
                            metadataProviderRef = providerRef,
                        ),
                        seasons = listOf(
                            dramaSeasonMetadata(
                                seasonNumber = 1,
                                episodeTitles = listOf("在线标题"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val indexRepository = DetailFakeMediaIndexRepository(
            entries = listOf(
                detailEntry(
                    animeName = "金庸武侠世界",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    filePath = "/drama/series-1/s01e01.mkv",
                    fileName = "s01e01.mkv",
                ),
            ),
        )
        val cacheRepository = DetailFakeMetadataRepository(
            initialMetadata = mapOf(
                dramaSeriesCacheKey("金庸武侠世界") to Anime(
                    id = dramaSeriesCacheKey("金庸武侠世界"),
                    title = "旧缓存标题",
                    titleCn = "Old Cached Title",
                    summary = "旧缓存简介",
                    tmdbId = 9,
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = indexRepository,
            dramaMetadataRepository = metadataRepository,
            metadataRepository = cacheRepository,
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("金庸武侠世界")
        advanceUntilIdle()
        viewModel.openManualMatch()
        viewModel.selectManualMatchResult(
            providerNeutralDramaMetadataSearchResult(
                source = "TVMaze",
                id = "maze-321",
                title = "金庸武侠世界",
                originalTitle = "The Legend of Heroes",
            ),
        )
        viewModel.applyManualMatch()
        advanceUntilIdle()

        val cached = cacheRepository.storedMetadata[dramaSeriesCacheKey("金庸武侠世界")]
        assertEquals(listOf(providerRef), metadataRepository.requestedProviderRefs)
        assertEquals(1, indexRepository.upsertedEntries.size)
        assertEquals("TVMaze", indexRepository.upsertedEntries.single().metadataSource)
        assertEquals("maze-321", indexRepository.upsertedEntries.single().metadataId)
        assertEquals("金庸武侠世界", indexRepository.upsertedEntries.single().metadataTitle)
        assertEquals("在线标题", indexRepository.upsertedEntries.single().episodeTitle)
        assertEquals("TVMaze", viewModel.series.value?.boundMetadataProviderRef()?.source)
        assertEquals("maze-321", viewModel.series.value?.boundMetadataProviderRef()?.id)
        assertNull(viewModel.series.value?.tmdbId)
        assertNull(cached?.tmdbId)
        assertEquals("已应用手动匹配，电视剧信息已更新。", viewModel.actionMessage.value)
    }

    @Test
    fun `refreshSeries keeps detail page visible while metadata refresh is running`() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        val metadataRepository = BlockingDramaMetadataRepository(
            initialResponse = Result.success(
                DramaSeriesMetadata(
                    series = DramaSeries(
                        id = "tmdb:7",
                        title = "示例剧",
                        summary = "初始简介",
                        tmdbId = 7,
                    ),
                    seasons = listOf(
                        dramaSeasonMetadata(
                            seasonNumber = 1,
                            episodeTitles = listOf("初始标题"),
                        ),
                    ),
                ),
            ),
            refreshGate = refreshGate,
            refreshResponse = Result.success(
                DramaSeriesMetadata(
                    series = DramaSeries(
                        id = "tmdb:7",
                        title = "示例剧",
                        summary = "刷新后简介",
                        tmdbId = 7,
                    ),
                    seasons = listOf(
                        dramaSeasonMetadata(
                            seasonNumber = 1,
                            episodeTitles = listOf("刷新后标题"),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = DramaDetailViewModel(
            mediaSources = DetailFakeMediaSourceRepository(),
            mediaIndexRepository = DetailFakeMediaIndexRepository(
                entries = listOf(
                    detailEntry(
                        animeName = "示例剧",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        filePath = "/drama/series-1/s01e01.mkv",
                        fileName = "s01e01.mkv",
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = DetailFakeMetadataRepository(),
            progressRepository = DetailFakePlaybackProgressRepository(),
            credentials = DetailFakeCredentialStore(tmdbAccessToken = "token-123"),
            dramaSearchAggregator = noOpDramaSearchAggregator,
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()

        viewModel.refreshSeries()

        assertFalse(viewModel.isLoading.value)
        assertTrue(viewModel.isRefreshingMetadata.value)
        assertEquals("初始简介", viewModel.series.value?.summary)

        refreshGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.isRefreshingMetadata.value)
        assertEquals("刷新后简介", viewModel.series.value?.summary)
        assertEquals("电视剧信息已刷新。", viewModel.actionMessage.value)
    }
}

private class DetailFakeMediaSourceRepository : MediaSourceRepository {
    private val sources = listOf(
        MediaSourceInfo(
            id = 9L,
            name = "Drama Source",
            type = MediaSourceType.LOCAL,
            contentMode = MediaContentMode.DRAMA,
            connectionInfo = mapOf("path" to "/drama"),
        ),
    )

    override suspend fun addSource(source: MediaSourceInfo): Result<Long> = Result.success(0L)
    override suspend fun getSources(): Result<List<MediaSourceInfo>> = Result.success(sources)
    override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
        Result.success(sources.first { it.id == sourceId })
    override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> = Result.success(Unit)
    override suspend fun removeSource(sourceId: Long): Result<Unit> = Result.success(Unit)
}

private class DetailFakeMediaIndexRepository(
    private val entries: List<MediaIndexEntry>,
) : MediaIndexRepository {
    val upsertedEntries = mutableListOf<MediaIndexEntry>()

    override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
        Result.success(Unit)

    override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> =
        Result.success(Unit).also {
            upsertedEntries += entry
        }

    override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> =
        Result.success(entries)

    override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
        Result.success(emptyList())

    override suspend fun clearIndex(sourceId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
        Result.success(Unit)

    override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> =
        Result.success(emptyList())

    override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = Result.success(Unit)
}

private data class DetailFakeCredentialStore(
    override var tmdbAccessToken: String? = null,
    override var tmdbApiBaseUrlOverride: String? = null,
    override var bangumiAccessToken: String? = null,
    override var otlpAccessToken: String? = null,
    override var cloudDriveToken: String? = null,
    override var cloudDrivePassword: String? = null,
) : AppCredentialStore {
    override fun clearCloudDriveCredentials() {
        cloudDriveToken = null
        cloudDrivePassword = null
    }

    override fun clearBangumiToken() {
        bangumiAccessToken = null
    }

    override fun clearTmdbToken() {
        tmdbAccessToken = null
        tmdbApiBaseUrlOverride = null
    }

    override fun clearOtlpAccessToken() {
        otlpAccessToken = null
    }
}

private class DetailFakeMetadataRepository(
    initialMetadata: Map<String, Anime> = emptyMap(),
) : MetadataRepository {
    val storedMetadata = initialMetadata.toMutableMap()

    override suspend fun cacheMetadata(anime: Anime): Result<Unit> =
        Result.success(Unit).also {
            storedMetadata[anime.id] = anime
        }

    override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
        Result.success(storedMetadata[animeId])

    override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
        Result.success(animeIds.mapNotNull(storedMetadata::get))

    override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
        Result.success(null)

    override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
        Result.success(emptyList())

    override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> =
        Result.success(Unit)

    override suspend fun invalidateCache(animeId: String): Result<Unit> =
        Result.success(Unit).also {
            storedMetadata.remove(animeId)
        }
}

private class DetailFakePlaybackProgressRepository(
    private val progressByEpisodeId: Map<String, ProgressRecord> = emptyMap(),
) : PlaybackProgressRepository {
    override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> =
        Result.success(progressByEpisodeId[episodeId])

    override suspend fun saveProgress(
        episodeId: String,
        positionMs: Long,
        lastWatched: Long,
        incrementPlayCount: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getAllProgress(): Result<List<ProgressRecord>> = Result.success(emptyList())
    override suspend fun deleteProgress(episodeId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> = Result.success(emptyList())
}

private val noOpDramaSearchAggregator = object : DramaMetadataSearchAggregator {
    override suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult =
        AggregatedMetadataSearchResult(
            plan = MetadataQueryPlanner.plan(context),
            candidates = emptyList(),
        )
}

private class RepositoryBackedDramaSearchAggregator(
    private val repository: SearchableDramaMetadataRepository,
) : DramaMetadataSearchAggregator {
    override suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult {
        val plan = MetadataQueryPlanner.plan(context)
        val rawCandidates = buildList {
            plan.queryTexts.forEach { query ->
                when (val result = repository.searchSeriesCandidates(query, seasonHint = plan.seasonHint)) {
                    is Result.Error -> Unit
                    is Result.Success -> {
                        result.data.forEachIndexed { index, item ->
                            add(
                                MetadataSearchProviderCandidate(
                                    providerRef = item.providerRef,
                                    title = item.title,
                                    localizedTitle = item.title,
                                    originalTitle = item.originalTitle,
                                    aliases = listOfNotNull(item.title, item.originalTitle.takeIf { it.isNotBlank() }).distinct(),
                                    matchedQuery = query,
                                    providerRank = index,
                                    summary = item.summary,
                                    posterUrl = item.posterUrl,
                                    fanartUrl = item.fanartUrl,
                                    firstAirDate = item.firstAirDate,
                                ),
                            )
                        }
                    }
                }
            }
        }
        return MetadataSearchAggregationSupport.aggregate(
            context = context,
            plan = plan,
            candidates = rawCandidates,
            providerPriorities = rawCandidates
                .map { it.providerRef.source.lowercase() }
                .distinct()
                .associateWith { source -> if (source == "tmdb") 1.02f else 1.0f },
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun providerNeutralDramaMetadataSearchResult(
    source: String,
    id: String,
    title: String,
    originalTitle: String = "",
    summary: String = "",
): DramaMetadataSearchResult =
    DramaMetadataSearchResult(
        tmdbId = id.toIntOrNull()?.takeIf { source.equals("TMDB", ignoreCase = true) },
        title = title,
        originalTitle = originalTitle,
        summary = summary,
        providerRef = MetadataProviderRef(source = source, id = id),
    )

private class EmptyDramaMetadataRepository : DramaMetadataRepository {
    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ) = Result.success(null)
}

private class MutableDramaMetadataRepository(
    private val responses: MutableList<Result<DramaSeriesMetadata?>>,
) : DramaMetadataRepository {
    override fun canFetchSeriesMetadataByTitle(): Boolean = true

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> =
        responses.removeFirstOrNull() ?: responses.lastOrNull() ?: Result.success(null)
}

private class BlockingDramaMetadataRepository(
    private val initialResponse: Result<DramaSeriesMetadata?>,
    private val refreshGate: CompletableDeferred<Unit>,
    private val refreshResponse: Result<DramaSeriesMetadata?>,
) : DramaMetadataRepository {
    private var requestCount = 0

    override fun canFetchSeriesMetadataByTitle(): Boolean = true

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        requestCount += 1
        if (requestCount == 1) {
            return initialResponse
        }
        refreshGate.await()
        return refreshResponse
    }
}

private class DeferredInitialDramaMetadataRepository(
    private val gate: CompletableDeferred<Unit>,
    private val response: Result<DramaSeriesMetadata?>,
) : DramaMetadataRepository {
    override fun canFetchSeriesMetadataByTitle(): Boolean = true

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        gate.await()
        return response
    }
}

private class SearchableDramaMetadataRepository(
    private val searchResultsByQuery: Map<String, Result<List<DramaMetadataSearchResult>>> = emptyMap(),
    private val metadataById: Map<Int, Result<DramaSeriesMetadata?>> = emptyMap(),
    private val metadataByProviderRef: Map<MetadataProviderRef, Result<DramaSeriesMetadata?>> = emptyMap(),
) : DramaMetadataRepository {
    val requestedQueries = mutableListOf<String>()
    val requestedIds = mutableListOf<Int>()
    val requestedProviderRefs = mutableListOf<MetadataProviderRef>()

    override fun canFetchMetadataByProviderRef(
        providerRef: MetadataProviderRef,
    ): Boolean = when {
        providerRef.source.equals("TMDB", ignoreCase = true) -> providerRef.id.toIntOrNull() in metadataById
        else -> metadataByProviderRef.containsKey(providerRef)
    }

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> = Result.success(null)

    override suspend fun fetchSeriesMetadataByProviderRef(
        providerRef: MetadataProviderRef,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        requestedProviderRefs += providerRef
        if (providerRef.source.equals("TMDB", ignoreCase = true)) {
            val tmdbId = providerRef.id.toIntOrNull()
            if (tmdbId != null) {
                return fetchSeriesMetadataById(tmdbId, seasonNumbers)
            }
        }
        return metadataByProviderRef[providerRef] ?: Result.success(null)
    }

    suspend fun fetchSeriesMetadataById(
        tmdbId: Int,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        requestedIds += tmdbId
        return metadataById[tmdbId] ?: Result.success(null)
    }

    override suspend fun searchSeriesCandidates(
        query: String,
        seasonHint: Int?,
        maxResults: Int,
    ): Result<List<DramaMetadataSearchResult>> {
        requestedQueries += query
        return searchResultsByQuery[query] ?: Result.success(emptyList())
    }
}

private fun detailEntry(
    animeName: String,
    seasonNumber: Int,
    episodeNumber: Int,
    filePath: String,
    fileName: String,
): MediaIndexEntry =
    MediaIndexEntry(
        sourceId = 9L,
        path = filePath,
        animeName = animeName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = fileName,
    )

private fun dramaSeasonMetadata(
    seasonNumber: Int,
    episodeTitles: List<String>,
): com.miruplay.tv.model.DramaSeasonMetadata =
    com.miruplay.tv.model.DramaSeasonMetadata(
        seasonNumber = seasonNumber,
        episodes = episodeTitles.mapIndexed { index, title ->
            DramaEpisodeMetadata(
                seasonNumber = seasonNumber,
                episodeNumber = index + 1,
                title = title,
            )
        },
    )
