package com.miruplay.tv.ui.detail

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.AggregatedMetadataCandidate
import com.miruplay.tv.model.AggregatedMetadataSearchResult
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MatchRecommendation
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataQueryPlan
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchProviderCandidate
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.AnimeMetadataSearchAggregator
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeCollection
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiSubjectCollection
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import com.miruplay.tv.repository.BangumiUser
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesSnapshot
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.sync.BangumiSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeDetailViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `manual match uses aggregated bangumi candidate after local rerank`() = runTest {
        val aggregator = RecordingAnimeSearchAggregator(
            result = AggregatedMetadataSearchResult(
                plan = MetadataQueryPlan(emptyList()),
                candidates = listOf(
                    AggregatedMetadataCandidate(
                        contentMode = MediaContentMode.ANIME,
                        title = "Sousou no Frieren",
                        localizedTitle = "葬送的芙莉莲",
                        originalTitle = "Frieren: Beyond Journey's End",
                        summary = "冒险重新开始。",
                        providerCandidates = listOf(
                            MetadataSearchProviderCandidate(
                                providerRef = MetadataProviderRef(source = "AniList", id = "154587"),
                                title = "Frieren: Beyond Journey's End",
                                originalTitle = "Sousou no Frieren",
                                matchedQuery = "Frieren",
                                providerScore = 0.99f,
                                providerRank = 0,
                            ),
                            MetadataSearchProviderCandidate(
                                providerRef = MetadataProviderRef(source = "Bangumi", id = "431767"),
                                title = "Sousou no Frieren",
                                localizedTitle = "葬送的芙莉莲",
                                matchedQuery = "Frieren",
                                providerScore = 0.83f,
                                providerRank = 1,
                            ),
                        ),
                        rerankScore = 0.97f,
                        recommendation = MatchRecommendation.AUTO_ACCEPT,
                    ),
                ),
            ),
        )
        val viewModel = animeDetailViewModel(aggregator)

        viewModel.loadAnime("Frieren")
        advanceUntilIdle()
        viewModel.openRescrapeMatcher()
        viewModel.searchManualMatches()
        advanceUntilIdle()

        assertEquals("", aggregator.lastContext?.title)
        assertEquals(emptyList<String>(), aggregator.lastContext?.filePathSamples)
        assertEquals(listOf("Frieren"), aggregator.lastContext?.aliases)
        assertEquals(1, viewModel.manualMatch.value.results.size)
        assertEquals(ScraperSource.BANGUMI, viewModel.manualMatch.value.selectedResult?.source)
        assertEquals("431767", viewModel.manualMatch.value.selectedResult?.animeId)
        assertEquals("葬送的芙莉莲", viewModel.manualMatch.value.selectedResult?.titleCn)
        assertEquals("Frieren", viewModel.manualMatch.value.selectedResult?.matchedTitle)
        assertEquals("找到 1 个聚合候选。", viewModel.manualMatch.value.statusMessage)
    }

    @Test
    fun `manual match cannot override MLIP metadata`() = runTest {
        val indexRepository = AnimeDetailFakeMediaIndexRepository(
            entries = mutableListOf(
                MediaIndexEntry(
                    sourceId = 7L,
                    path = "/anime/Frieren/Frieren - 01.mkv",
                    animeName = "Frieren",
                    episodeTitle = "Episode 1",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    metadataSource = "MLIP",
                    metadataId = "mlip:7:series-uuid",
                    metadataTitle = "Frieren",
                )
            )
        )
        val viewModel = animeDetailViewModel(
            aggregator = RecordingAnimeSearchAggregator(
                AggregatedMetadataSearchResult(
                    plan = MetadataQueryPlan(emptyList()),
                    candidates = listOf(
                        AggregatedMetadataCandidate(
                            contentMode = MediaContentMode.ANIME,
                            title = "Sousou no Frieren",
                            localizedTitle = "葬送的芙莉莲",
                            providerCandidates = listOf(
                                MetadataSearchProviderCandidate(
                                    providerRef = MetadataProviderRef(source = "Bangumi", id = "431767"),
                                    title = "Sousou no Frieren",
                                    localizedTitle = "葬送的芙莉莲",
                                    matchedQuery = "Frieren",
                                    providerScore = 0.95f,
                                    providerRank = 0,
                                )
                            ),
                            rerankScore = 0.95f,
                            recommendation = MatchRecommendation.AUTO_ACCEPT,
                        )
                    ),
                ),
            ),
            indexRepository = indexRepository,
        )

        viewModel.loadAnime("Frieren")
        advanceUntilIdle()
        viewModel.openRescrapeMatcher()
        viewModel.searchManualMatches()
        advanceUntilIdle()
        viewModel.applyManualMatch()
        advanceUntilIdle()

        assertEquals(emptyList<MediaIndexEntry>(), indexRepository.upsertedEntries)
        assertEquals(
            "MLIP 元数据由 library.db 管理，请在远端修正后重新扫描。",
            viewModel.manualMatch.value.statusMessage,
        )
        assertEquals("Frieren", viewModel.anime.value?.id)
    }

    @Test
    fun `manual match search failure resets busy state`() = runTest {
        val viewModel = animeDetailViewModel(FailingAnimeSearchAggregator())

        viewModel.loadAnime("Frieren")
        advanceUntilIdle()
        viewModel.openRescrapeMatcher()
        viewModel.searchManualMatches()
        advanceUntilIdle()

        assertEquals(false, viewModel.manualMatch.value.isSearching)
        assertEquals(false, viewModel.isSyncing.value)
        assertEquals("搜索失败：network stalled", viewModel.manualMatch.value.statusMessage)
    }
}

private fun animeDetailViewModel(
    aggregator: AnimeMetadataSearchAggregator,
    metadataRepository: AnimeDetailFakeMetadataRepository = AnimeDetailFakeMetadataRepository(),
    indexRepository: AnimeDetailFakeMediaIndexRepository = AnimeDetailFakeMediaIndexRepository(
        entries = mutableListOf(
            MediaIndexEntry(
                sourceId = 7L,
                path = "/anime/Frieren/Frieren - 01.mkv",
                animeName = "Frieren",
                episodeTitle = "Episode 1",
                seasonNumber = 1,
                episodeNumber = 1,
            ),
        ),
    ),
): AnimeDetailViewModel = AnimeDetailViewModel(
    mediaRepository = AnimeDetailFakeMediaSourceRepository(),
    metadataRepository = metadataRepository,
    indexRepository = indexRepository,
    progressRepository = AnimeDetailFakePlaybackProgressRepository(),
    bangumiSyncEngine = BangumiSyncEngine(
        bangumiService = AnimeDetailFakeBangumiCollectionService(),
        metadataRepository = metadataRepository,
        progressRepository = AnimeDetailFakePlaybackProgressRepository(),
    ),
    scanPreferences = AnimeDetailFakeScanPreferencesRepository(),
    animeSearchAggregator = aggregator,
    metadataScrapers = setOf(NoOpBangumiScraper()),
)

private class RecordingAnimeSearchAggregator(
    private val result: AggregatedMetadataSearchResult,
) : AnimeMetadataSearchAggregator {
    var lastContext: MetadataSearchContext? = null

    override suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult {
        lastContext = context
        return result
    }
}

private class FailingAnimeSearchAggregator : AnimeMetadataSearchAggregator {
    override suspend fun search(context: MetadataSearchContext): AggregatedMetadataSearchResult {
        throw IllegalStateException("network stalled")
    }
}

private class AnimeDetailFakeMediaSourceRepository : MediaSourceRepository {
    private val sources = listOf(
        MediaSourceInfo(
            id = 7L,
            name = "Anime Source",
            type = MediaSourceType.LOCAL,
            contentMode = MediaContentMode.ANIME,
            connectionInfo = mapOf("path" to "/anime"),
        ),
    )

    override suspend fun addSource(source: MediaSourceInfo): Result<Long> = Result.success(0L)
    override suspend fun removeSource(sourceId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun getSources(): Result<List<MediaSourceInfo>> = Result.success(sources)
    override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> = Result.success(Unit)
    override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
        Result.success(sources.first { it.id == sourceId })
}

private class AnimeDetailFakeMediaIndexRepository(
    private val entries: MutableList<MediaIndexEntry>,
) : MediaIndexRepository {
    val upsertedEntries = mutableListOf<MediaIndexEntry>()

    override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = Result.success(Unit)
    override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> {
        upsertedEntries += entry
        entries.removeAll { it.sourceId == sourceId && it.path == entry.path }
        entries += entry
        return Result.success(Unit)
    }
    override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> = Result.success(entries)
    override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> = Result.success(emptyList())
    override suspend fun clearIndex(sourceId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = Result.success(Unit)
    override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> = Result.success(emptyList())
    override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = Result.success(Unit)
}

private class AnimeDetailFakeMetadataRepository : MetadataRepository {
    private val anime = mutableMapOf<String, Anime>()
    private val episodes = mutableMapOf<String, List<Episode>>()

    override suspend fun cacheMetadata(anime: Anime): Result<Unit> {
        this.anime[anime.id] = anime
        return Result.success(Unit)
    }
    override suspend fun getCachedMetadata(animeId: String): Result<Anime?> = Result.success(anime[animeId])
    override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
        Result.success(animeIds.mapNotNull { anime[it] })
    override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
        Result.success(episodes.values.flatten().firstOrNull { it.id == episodeId })
    override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> = Result.success(episodes[animeId].orEmpty())
    override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> {
        this.episodes[animeId] = episodes
        return Result.success(Unit)
    }
    override suspend fun invalidateCache(animeId: String): Result<Unit> {
        anime.remove(animeId)
        episodes.remove(animeId)
        return Result.success(Unit)
    }
}

private class AnimeDetailFakePlaybackProgressRepository : PlaybackProgressRepository {
    override suspend fun saveProgress(
        episodeId: String,
        positionMs: Long,
        lastWatched: Long,
        incrementPlayCount: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getProgress(episodeId: String): Result<ProgressRecord?> = Result.success(null)
    override suspend fun getAllProgress(): Result<List<ProgressRecord>> = Result.success(emptyList())
    override suspend fun deleteProgress(episodeId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> = Result.success(emptyList())
}

private class AnimeDetailFakeScanPreferencesRepository : ScanPreferencesRepository {
    override suspend fun getPreferences(): ScanPreferencesSnapshot = ScanPreferencesSnapshot()
    override suspend fun setAutoScanEnabled(enabled: Boolean) = Unit
    override suspend fun setAutoScanIntervalMs(intervalMs: Long) = Unit
    override suspend fun setLastScanAt(timestampMs: Long) = Unit
    override suspend fun setMergeSameAnimeEnabled(enabled: Boolean) = Unit
    override suspend fun setPosterWallArrangement(arrangement: com.miruplay.tv.model.PosterWallArrangement) = Unit
}

private class AnimeDetailFakeBangumiCollectionService : BangumiCollectionService {
    override val hasToken: Boolean = false

    override suspend fun getCurrentUser(): Result<BangumiUser> =
        Result.success(BangumiUser(id = 1, username = "user", nickname = "User"))

    override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> = Result.success(null)

    override suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> =
        Result.success(emptyList())

    override suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateEpisodeCollection(
        episodeId: Int,
        type: BangumiEpisodeCollectionType,
    ): Result<Unit> = Result.success(Unit)
}

private class NoOpBangumiScraper : MetadataScraper {
    override val sourceName: String = "Bangumi"

    override suspend fun searchAnime(query: String) = Result.success(emptyList<com.miruplay.tv.model.ScraperResult>())
    override suspend fun getAnimeDetails(animeId: String) = Result.success(Anime(id = animeId, title = animeId))
    override suspend fun getEpisodes(animeId: String) = Result.success(emptyList<com.miruplay.tv.scraper.EpisodeMetadata>())
    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>) =
        Result.success(null)
}
