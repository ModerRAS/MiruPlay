package com.miruplay.tv.ui.mode

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
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
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
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
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
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
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = FakeLibraryScanController(),
        )
        advanceUntilIdle()

        val state = viewModel.state.value as DramaLibraryUiState.Ready
        assertEquals(listOf("Beta Show", "Alpha Show"), state.featuredSeries.map(DramaSeries::title))
        assertEquals(listOf("Beta Show", "Alpha Show"), state.recentlyAdded.map(DramaSeries::title))
        assertFalse(state.browseSections.isEmpty())
        assertEquals(listOf("A", "B"), state.browseSections.mapNotNull { it.title })
        assertEquals(
            listOf(listOf("Alpha Show"), listOf("Beta Show")),
            state.browseSections.map { section -> section.series.map(DramaSeries::title) },
        )
        assertEquals(2, state.totalSeriesCount)
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
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
        )
        advanceUntilIdle()

        scanController.emit(LibraryScanState.Failed("扫描失败"))
        advanceUntilIdle()

        assertEquals(DramaLibraryUiState.ScanError("扫描失败"), viewModel.state.value)
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
            progressRepository = FakePlaybackProgressRepository(),
            libraryScanController = scanController,
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
): MediaIndexEntry =
    MediaIndexEntry(
        sourceId = sourceId,
        path = filePath,
        animeName = animeName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = fileName,
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
