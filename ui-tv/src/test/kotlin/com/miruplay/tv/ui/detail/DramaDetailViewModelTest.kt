package com.miruplay.tv.ui.detail

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.DramaMetadataRepository
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
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
            progressRepository = DetailFakePlaybackProgressRepository(
                progressByEpisodeId = mapOf(
                    "9:/drama/series-1/s01e01.mkv" to ProgressRecord(
                        episodeId = "9:/drama/series-1/s01e01.mkv",
                        positionMs = 15_000L,
                        lastWatched = 456L,
                    ),
                ),
            ),
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
            progressRepository = DetailFakePlaybackProgressRepository(),
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
            progressRepository = DetailFakePlaybackProgressRepository(),
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
            progressRepository = DetailFakePlaybackProgressRepository(),
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
            progressRepository = DetailFakePlaybackProgressRepository(),
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
            progressRepository = DetailFakePlaybackProgressRepository(),
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
            progressRepository = DetailFakePlaybackProgressRepository(),
        )

        viewModel.loadSeries("示例剧")
        advanceUntilIdle()

        assertNull(viewModel.continueEpisode.value)
        assertEquals("播放", viewModel.primaryActionLabel.value)
        assertEquals("9:/drama/series-1/s01e01.mkv", viewModel.primaryActionEpisode.value?.id)
        assertTrue(viewModel.hasPlayableEpisodes.value)
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
    override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
        Result.success(Unit)

    override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> =
        Result.success(Unit)

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
    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> =
        responses.removeFirstOrNull() ?: responses.lastOrNull() ?: Result.success(null)
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
