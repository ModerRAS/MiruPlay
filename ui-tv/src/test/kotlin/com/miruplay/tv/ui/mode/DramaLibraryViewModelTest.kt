package com.miruplay.tv.ui.mode

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.boundMetadataProviderRef
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.DramaMetadataRepository
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.SCAN_PREFERENCES_DEFAULT_INTERVAL_MS
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesSnapshot
import com.miruplay.tv.repository.dramaSeriesCacheKey
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.ui.library.LibraryScanController
import kotlinx.coroutines.flow.MutableStateFlow
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
class DramaLibraryViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh returns no sources when drama source list is empty`() = runTest {
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 1L,
                        name = "Anime Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.ANIME,
                        connectionInfo = mapOf("path" to "/anime"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        assertEquals(DramaLibraryUiState.NoSources, viewModel.state.value)
    }

    @Test
    fun `refresh returns has sources when drama source exists but index has no series`() = runTest {
        val scanController = FakeLibraryScanController()
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 2L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        assertEquals(DramaLibraryUiState.HasSources, viewModel.state.value)
        assertEquals(1, scanController.autoScanCount)
    }

    @Test
    fun `refresh returns ready state with continue watching item`() = runTest {
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 3L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    3L to listOf(
                        mediaIndexEntry(
                            sourceId = 3L,
                            animeName = "示例电视剧",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/series-1/s01e01.mkv",
                            fileName = "s01e01.mkv",
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(
                continueWatchingRecords = listOf(
                    ProgressRecord(
                        episodeId = "3:/drama/series-1/s01e01.mkv",
                        positionMs = 30_000L,
                        lastWatched = 123L,
                    ),
                ),
            ),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is DramaLibraryUiState.Ready)
        state as DramaLibraryUiState.Ready
        assertEquals(1, state.series.size)
        assertEquals("示例电视剧", state.series.single().title)
        assertEquals(1, state.continueWatching.size)
        assertEquals("示例电视剧", state.continueWatching.single().series.title)
        assertEquals(30_000L, state.continueWatching.single().progress?.positionMs)
    }

    @Test
    fun `refresh returns ready state with recently added and featured series ordering`() = runTest {
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 3L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    3L to listOf(
                        mediaIndexEntry(
                            sourceId = 3L,
                            animeName = "Alpha Show",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/alpha/s01e01.mkv",
                            fileName = "alpha-s01e01.mkv",
                            lastModified = 100L,
                        ),
                        mediaIndexEntry(
                            sourceId = 3L,
                            animeName = "Beta Show",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/beta/s01e01.mkv",
                            fileName = "beta-s01e01.mkv",
                            lastModified = 300L,
                        ),
                        mediaIndexEntry(
                            sourceId = 3L,
                            animeName = "Beta Show",
                            episodeNumber = 2,
                            seasonNumber = 1,
                            filePath = "/drama/beta/s01e02.mkv",
                            fileName = "beta-s01e02.mkv",
                            lastModified = 350L,
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        val state = viewModel.state.value as DramaLibraryUiState.Ready
        assertEquals(listOf("Beta Show", "Alpha Show"), state.featuredSeries.map(DramaSeries::title))
        assertEquals(listOf("Beta Show", "Alpha Show"), state.recentlyAdded.map(DramaSeries::title))
        assertEquals(
            listOf(listOf("Alpha Show", "Beta Show")),
            state.browseSections.map { section -> section.series.map(DramaSeries::title) },
        )
        assertEquals(listOf<Any?>(null), state.browseSections.map { it.title })
        assertEquals(2, state.totalSeriesCount)
    }

    @Test
    fun `refresh surfaces cached tmdb metadata already saved in index`() = runTest {
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 5L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    5L to listOf(
                        mediaIndexEntry(
                            sourceId = 5L,
                            animeName = "庆余年",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/demo/s01e01.mkv",
                            fileName = "demo-s01e01.mkv",
                            plot = "本地已保存简介",
                            metadataSource = "TMDB",
                            metadataId = "101",
                            metadataTitle = "庆余年 第一季",
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = TrackingDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        val state = viewModel.state.value as DramaLibraryUiState.Ready
        val series = state.series.single()
        assertEquals("庆余年 第一季", series.title)
        assertEquals("本地已保存简介", series.summary)
        assertEquals(101, series.tmdbId)
        assertEquals("本地已保存简介", state.featuredSeries.single().summary)
        assertEquals("庆余年 第一季", state.recentlyAdded.single().title)
    }

    @Test
    fun `refresh merges cached drama series artwork and summary from metadata cache`() = runTest {
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 8L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    8L to listOf(
                        mediaIndexEntry(
                            sourceId = 8L,
                            animeName = "庆余年",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/demo/s01e01.mkv",
                            fileName = "demo-s01e01.mkv",
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = TrackingDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(
                initialMetadata = mapOf(
                    dramaSeriesCacheKey("庆余年") to Anime(
                        id = dramaSeriesCacheKey("庆余年"),
                        title = "庆余年 第一季",
                        titleCn = "Joy of Life",
                        summary = "缓存后的简介",
                        posterUrl = "poster-url",
                        fanartUrl = "fanart-url",
                        airDate = "2024-01-01",
                        tmdbId = 202,
                    ),
                ),
            ),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        val state = viewModel.state.value as DramaLibraryUiState.Ready
        val series = state.series.single()
        assertEquals("庆余年 第一季", series.title)
        assertEquals("Joy of Life", series.originalTitle)
        assertEquals("缓存后的简介", series.summary)
        assertEquals("poster-url", series.posterUrl)
        assertEquals("fanart-url", series.fanartUrl)
        assertEquals("2024-01-01", series.firstAirDate)
        assertEquals(202, series.tmdbId)
    }

    @Test
    fun `refresh keeps explicit tvmaze binding over stale cached tmdb id`() = runTest {
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 9L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    9L to listOf(
                        mediaIndexEntry(
                            sourceId = 9L,
                            animeName = "庆余年",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/demo/s01e01.mkv",
                            fileName = "demo-s01e01.mkv",
                            metadataSource = "TVMaze",
                            metadataId = "maze-321",
                            metadataTitle = "庆余年 第一季",
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = TrackingDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(
                initialMetadata = mapOf(
                    dramaSeriesCacheKey("庆余年") to Anime(
                        id = dramaSeriesCacheKey("庆余年"),
                        title = "庆余年 第一季",
                        titleCn = "Joy of Life",
                        summary = "缓存后的简介",
                        posterUrl = "poster-url",
                        fanartUrl = "fanart-url",
                        airDate = "2024-01-01",
                        tmdbId = 202,
                    ),
                ),
            ),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        val state = viewModel.state.value as DramaLibraryUiState.Ready
        val series = state.series.single()
        assertEquals("TVMaze", series.boundMetadataProviderRef()?.source)
        assertEquals("maze-321", series.boundMetadataProviderRef()?.id)
        assertNull(series.tmdbId)
        assertEquals("缓存后的简介", series.summary)
        assertEquals("poster-url", series.posterUrl)
        assertEquals("fanart-url", series.fanartUrl)
    }

    @Test
    fun `refresh does not trigger tmdb fetch for every library card`() = runTest {
        val metadataRepository = TrackingDramaMetadataRepository()
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 7L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    7L to listOf(
                        mediaIndexEntry(
                            sourceId = 7L,
                            animeName = "Show One",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/show-one/s01e01.mkv",
                            fileName = "show-one-s01e01.mkv",
                        ),
                        mediaIndexEntry(
                            sourceId = 7L,
                            animeName = "Show Two",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/show-two/s01e01.mkv",
                            fileName = "show-two-s01e01.mkv",
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = metadataRepository,
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value is DramaLibraryUiState.Ready)
        assertTrue(metadataRepository.requestedTitles.isEmpty())
        assertTrue(metadataRepository.requestedIds.isEmpty())
    }

    @Test
    fun `release season browse sections fall back to unknown bucket when local air date is missing`() = runTest {
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 6L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    6L to listOf(
                        mediaIndexEntry(
                            sourceId = 6L,
                            animeName = "Spring Show",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/spring/s01e01.mkv",
                            fileName = "spring-s01e01.mkv",
                        ),
                        mediaIndexEntry(
                            sourceId = 6L,
                            animeName = "Summer Show",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/drama/summer/s01e01.mkv",
                            fileName = "summer-s01e01.mkv",
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = FakeDramaMetadataRepository(
                metadataByTitle = mapOf(
                    "Spring Show" to Result.success(
                        DramaSeriesMetadata(
                            series = DramaSeries(
                                id = "tmdb:201",
                                title = "Spring Show",
                                firstAirDate = "2024-04-12",
                            ),
                            seasons = emptyList(),
                        ),
                    ),
                    "Summer Show" to Result.success(
                        DramaSeriesMetadata(
                            series = DramaSeries(
                                id = "tmdb:202",
                                title = "Summer Show",
                                firstAirDate = "2024-07-03",
                            ),
                            seasons = emptyList(),
                        ),
                    ),
                ),
            ),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
            scanPreferences = FakeScanPreferencesRepository(
                snapshot = ScanPreferencesSnapshot(
                    posterWallArrangement = PosterWallArrangement.RELEASE_SEASON,
                ),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.state.value as DramaLibraryUiState.Ready
        assertEquals(
            listOf("未识别播出日期"),
            state.browseSections.mapNotNull { it.title },
        )
        assertEquals(
            listOf(listOf("Spring Show", "Summer Show")),
            state.browseSections.map { section -> section.series.map(DramaSeries::title) },
        )
    }

    @Test
    fun `scan failure with no content surfaces scan error`() = runTest {
        val scanController = FakeLibraryScanController()
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 4L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        scanController.emit(LibraryScanState.Failed("扫描失败"))
        advanceUntilIdle()

        assertEquals(DramaLibraryUiState.ScanError("扫描失败"), viewModel.state.value)
    }

    @Test
    fun `finished scan with source failure and no content surfaces source error`() = runTest {
        val scanController = FakeLibraryScanController()
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 5L,
                        name = "Drama Source",
                        type = MediaSourceType.WEBDAV,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("url" to "http://example.invalid/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        scanController.emit(
            LibraryScanState.Finished(
                results = emptyList(),
                sourceFailures = listOf("HTTP 错误 404：Not Found"),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            DramaLibraryUiState.ScanError("HTTP 错误 404：Not Found"),
            viewModel.state.value,
        )
    }

    @Test
    fun `finished scan with partial source failures keeps library content and surfaces notice`() = runTest {
        val scanController = FakeLibraryScanController()
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 6L,
                        name = "Drama Source",
                        type = MediaSourceType.WEBDAV,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("url" to "http://example.invalid/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(
                entriesBySourceId = mapOf(
                    6L to listOf(
                        mediaIndexEntry(
                            sourceId = 6L,
                            animeName = "医馆笑传",
                            episodeNumber = 1,
                            seasonNumber = 1,
                            filePath = "/医馆笑传/医馆笑传.S01/医馆笑传.S01E01.mp4",
                            fileName = "医馆笑传.S01E01",
                        ),
                    ),
                ),
            ),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        scanController.emit(
            LibraryScanState.Finished(
                results = emptyList(),
                sourceFailures = listOf("HTTP 错误 404：Not Found"),
            ),
        )
        advanceUntilIdle()

        val readyState = viewModel.state.value as DramaLibraryUiState.Ready
        assertEquals("有 1 个电视剧源扫描失败：HTTP 错误 404：Not Found", readyState.scanNotice)
        assertEquals(listOf("医馆笑传"), readyState.featuredSeries.map(DramaSeries::title))
    }

    @Test
    fun `scan actions are forwarded to shared library scan controller`() = runTest {
        val scanController = FakeLibraryScanController()
        val viewModel = DramaLibraryViewModel(
            mediaSources = FakeMediaSourceRepository(
                sources = listOf(
                    MediaSourceInfo(
                        id = 3L,
                        name = "Drama Source",
                        type = MediaSourceType.LOCAL,
                        contentMode = MediaContentMode.DRAMA,
                        connectionInfo = mapOf("path" to "/drama"),
                    ),
                ),
            ),
            mediaIndexRepository = FakeMediaIndexRepository(),
            dramaMetadataRepository = FakeDramaMetadataRepository(),
            metadataRepository = FakeMetadataRepository(),
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
            scanPreferences = FakeScanPreferencesRepository(),
        )
        advanceUntilIdle()

        viewModel.scanNow()
        viewModel.cancelScan()

        assertEquals(1, scanController.autoScanCount)
        assertEquals(1, scanController.manualScanCount)
        assertEquals(1, scanController.cancelCount)
    }
}

private class FakeMediaSourceRepository(
    private val sources: List<MediaSourceInfo>,
) : MediaSourceRepository {
    override suspend fun addSource(source: MediaSourceInfo): Result<Long> = Result.success(0L)
    override suspend fun getSources(): Result<List<MediaSourceInfo>> = Result.success(sources)
    override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
        Result.success(sources.first { it.id == sourceId })
    override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> = Result.success(Unit)
    override suspend fun removeSource(sourceId: Long): Result<Unit> = Result.success(Unit)
}

private class FakeScanPreferencesRepository(
    private var snapshot: ScanPreferencesSnapshot = ScanPreferencesSnapshot(),
) : ScanPreferencesRepository {
    override suspend fun getPreferences(): ScanPreferencesSnapshot = snapshot

    override suspend fun setAutoScanEnabled(enabled: Boolean) {
        snapshot = snapshot.copy(autoScanEnabled = enabled)
    }

    override suspend fun setAutoScanIntervalMs(intervalMs: Long) {
        snapshot = snapshot.copy(autoScanIntervalMs = intervalMs.coerceAtLeast(SCAN_PREFERENCES_DEFAULT_INTERVAL_MS))
    }

    override suspend fun setLastScanAt(timestampMs: Long) {
        snapshot = snapshot.copy(lastScanAt = timestampMs)
    }

    override suspend fun setMergeSameAnimeEnabled(enabled: Boolean) {
        snapshot = snapshot.copy(mergeSameAnimeEnabled = enabled)
    }

    override suspend fun setPosterWallArrangement(arrangement: PosterWallArrangement) {
        snapshot = snapshot.copy(posterWallArrangement = arrangement)
    }
}

private class FakeMediaIndexRepository(
    private val entriesBySourceId: Map<Long, List<MediaIndexEntry>> = emptyMap(),
) : MediaIndexRepository {
    override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
        Result.success(Unit)

    override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> =
        Result.success(Unit)

    override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> =
        Result.success(entriesBySourceId[sourceId].orEmpty())

    override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
        Result.success(emptyList())

    override suspend fun clearIndex(sourceId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
        Result.success(Unit)

    override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> =
        Result.success(emptyList())

    override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = Result.success(Unit)
}

private class FakeDramaMetadataRepository(
    private val metadataByTitle: Map<String, Result<DramaSeriesMetadata?>> = emptyMap(),
) : DramaMetadataRepository {
    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> = metadataByTitle[title] ?: Result.success(null)
}

private class FakeMetadataRepository(
    initialMetadata: Map<String, Anime> = emptyMap(),
) : MetadataRepository {
    private val metadataById = initialMetadata.toMutableMap()

    override suspend fun cacheMetadata(anime: Anime): Result<Unit> =
        Result.success(Unit).also {
            metadataById[anime.id] = anime
        }

    override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
        Result.success(metadataById[animeId])

    override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
        Result.success(animeIds.mapNotNull(metadataById::get))

    override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
        Result.success(null)

    override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
        Result.success(emptyList())

    override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> =
        Result.success(Unit)

    override suspend fun invalidateCache(animeId: String): Result<Unit> =
        Result.success(Unit).also {
            metadataById.remove(animeId)
        }
}

private class FakePlaybackProgressRepository(
    private val progressByEpisodeId: Map<String, ProgressRecord> = emptyMap(),
    private val continueWatchingRecords: List<ProgressRecord> = emptyList(),
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
    override suspend fun getContinueWatching(limit: Int): Result<List<ProgressRecord>> =
        Result.success(continueWatchingRecords.take(limit))
}

private fun mediaIndexEntry(
    sourceId: Long,
    animeName: String,
    episodeNumber: Int,
    seasonNumber: Int,
    filePath: String,
    fileName: String,
    lastModified: Long = 0L,
    plot: String? = null,
    metadataSource: String? = null,
    metadataId: String? = null,
    metadataTitle: String? = null,
): MediaIndexEntry =
    MediaIndexEntry(
        sourceId = sourceId,
        path = filePath,
        animeName = animeName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = fileName,
        plot = plot,
        metadataSource = metadataSource,
        metadataId = metadataId,
        metadataTitle = metadataTitle,
        lastModified = lastModified,
    )

private class FakeLibraryScanController : LibraryScanController {
    private val mutableState = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    override val state = mutableState
    var manualScanCount = 0
    var autoScanCount = 0
    var cancelCount = 0

    override fun startManualScan() {
        manualScanCount += 1
    }

    override fun startAutoScanIfDue() {
        autoScanCount += 1
    }

    override fun cancel() {
        cancelCount += 1
    }

    fun emit(scanState: LibraryScanState) {
        mutableState.value = scanState
    }
}

private class TrackingDramaMetadataRepository(
    private val metadataByTitle: Map<String, Result<DramaSeriesMetadata?>> = emptyMap(),
    private val metadataById: Map<Int, Result<DramaSeriesMetadata?>> = emptyMap(),
) : DramaMetadataRepository {
    val requestedTitles = mutableListOf<String>()
    val requestedIds = mutableListOf<Int>()

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        requestedTitles += title
        return metadataByTitle[title] ?: Result.success(null)
    }

    suspend fun fetchSeriesMetadataById(
        tmdbId: Int,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        requestedIds += tmdbId
        return metadataById[tmdbId] ?: Result.success(null)
    }
}
