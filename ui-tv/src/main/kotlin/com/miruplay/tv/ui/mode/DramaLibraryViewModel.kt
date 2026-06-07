package com.miruplay.tv.ui.mode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.isCompleted
import com.miruplay.tv.repository.LibraryDramaDetail
import com.miruplay.tv.repository.LibraryDramaResolver
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.ui.library.LibraryScanController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class DramaProgressItem(
    val series: DramaSeries,
    val episode: DramaEpisode,
    val progress: ProgressRecord?,
)

data class DramaBrowseSection(
    val title: String?,
    val series: List<DramaSeries>,
)

sealed class DramaLibraryUiState {
    data object Loading : DramaLibraryUiState()
    data object NoSources : DramaLibraryUiState()
    data object HasSources : DramaLibraryUiState()
    data class ScanError(val message: String) : DramaLibraryUiState()
    data class Ready(
        val continueWatching: List<DramaProgressItem>,
        val featuredSeries: List<DramaSeries>,
        val recentlyAdded: List<DramaSeries>,
        val browseSections: List<DramaBrowseSection>,
        val series: List<DramaSeries>,
        val totalSeriesCount: Int,
    ) : DramaLibraryUiState()
}

@HiltViewModel
class DramaLibraryViewModel @Inject constructor(
    private val mediaSources: MediaSourceRepository,
    mediaIndexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val libraryScanController: LibraryScanController,
) : ViewModel() {
    private val resolver = LibraryDramaResolver(
        mediaSources = mediaSources,
        index = mediaIndexRepository,
    )

    private val _state = MutableStateFlow<DramaLibraryUiState>(DramaLibraryUiState.Loading)
    val state: StateFlow<DramaLibraryUiState> = _state.asStateFlow()
    val scanState: StateFlow<LibraryScanState> = libraryScanController.state

    private var refreshJob: Job? = null
    private var scanContentRefreshJob: Job? = null
    private var queuedScanContentVersion = -1
    private var scanRefreshSessionActive = false

    init {
        observeScanState()
        refresh()
    }

    fun scanNow() {
        libraryScanController.startManualScan()
    }

    fun cancelScan() {
        libraryScanController.cancel()
        viewModelScope.launch {
            loadLibraryContent(showLoading = false)
        }
    }

    fun refresh(showLoading: Boolean = true) {
        if (refreshJob?.isActive == true) {
            MiruLog.d("DramaLibraryViewModel", "Drama library refresh skipped because a refresh is already active")
            return
        }

        refreshJob = viewModelScope.launch {
            val currentScanState = libraryScanController.state.value
            val scanInProgress = currentScanState is LibraryScanState.Scanning
            val snapshot = loadLibraryContent(showLoading = showLoading && !scanInProgress)
            if (snapshot.hasSources && !scanInProgress) {
                libraryScanController.startAutoScanIfDue()
            }
        }
    }

    private fun observeScanState() {
        viewModelScope.launch {
            libraryScanController.state.collectLatest { currentScanState ->
                when (currentScanState) {
                    is LibraryScanState.Idle -> Unit
                    is LibraryScanState.Scanning -> {
                        if (!scanRefreshSessionActive) {
                            scanRefreshSessionActive = true
                            queuedScanContentVersion = -1
                        }
                        scheduleContentRefreshDuringScan(currentScanState)
                    }
                    is LibraryScanState.Finished -> {
                        scanRefreshSessionActive = false
                        val snapshot = loadLibraryContent(showLoading = false)
                        if (snapshot.hasSources && !snapshot.hasContent) {
                            _state.value = DramaLibraryUiState.ScanError("未找到电视剧内容，请检查媒体源路径")
                        }
                    }
                    is LibraryScanState.Failed -> {
                        scanRefreshSessionActive = false
                        val snapshot = loadLibraryContent(showLoading = false)
                        if (snapshot.hasSources && !snapshot.hasContent) {
                            _state.value = DramaLibraryUiState.ScanError(currentScanState.message)
                        }
                    }
                    is LibraryScanState.Cancelled -> {
                        scanRefreshSessionActive = false
                        loadLibraryContent(showLoading = false)
                    }
                }
            }
        }
    }

    private fun scheduleContentRefreshDuringScan(scanState: LibraryScanState.Scanning) {
        val currentState = _state.value
        val shouldRefresh =
            currentState is DramaLibraryUiState.Loading ||
                scanState.contentVersion > queuedScanContentVersion
        if (!shouldRefresh) return

        queuedScanContentVersion = scanState.contentVersion
        if (scanContentRefreshJob?.isActive == true) return

        scanContentRefreshJob = viewModelScope.launch {
            delay(SCAN_CONTENT_REFRESH_DELAY_MS)
            loadLibraryContent(showLoading = false)
        }
    }

    private suspend fun loadLibraryContent(showLoading: Boolean): LibraryLoadSnapshot {
        val startedAt = monotonicNowMs()
        if (showLoading) {
            _state.value = DramaLibraryUiState.Loading
        }

        try {
            val dramaSources = mediaSources.getSources()
                .getOrNull()
                .orEmpty()
                .filter { it.contentMode == MediaContentMode.DRAMA }
            if (dramaSources.isEmpty()) {
                _state.value = DramaLibraryUiState.NoSources
                return LibraryLoadSnapshot(hasSources = false, hasContent = false)
            }

            val indexedSeries = resolver.loadSeries().distinctBy { it.id }
            val detailBySeriesId = indexedSeries.associate { series ->
                series.id to resolver.loadSeriesDetail(series.id)
            }
            val resolvedSeries = indexedSeries.map { series ->
                detailBySeriesId[series.id]?.series ?: series
            }
            val continueWatching = loadContinueWatching(detailBySeriesId)

            if (resolvedSeries.isEmpty() && continueWatching.isEmpty()) {
                _state.value = DramaLibraryUiState.HasSources
                return LibraryLoadSnapshot(hasSources = true, hasContent = false)
            }

            val featuredSeries = resolvedSeries
                .sortedWith(
                    compareByDescending<DramaSeries> { it.episodeCount }
                        .thenByDescending { it.seasonCount }
                        .thenBy { it.displayTitle() },
                )
                .take(8)
            val recentlyAdded = resolvedSeries
                .sortedWith(
                    compareByDescending<DramaSeries> { detailBySeriesId[it.id].latestEpisodeSortKey() }
                        .thenByDescending { it.episodeCount }
                        .thenBy { it.displayTitle() },
                )
                .take(10)
            val sortedSeries = resolvedSeries.sortedBy { it.displayTitle() }
            val browseSections = sortedSeries
                .groupBy { series -> series.displayTitle().trim().firstOrNull()?.uppercase() ?: "#" }
                .toSortedMap()
                .map { (title, series) ->
                    DramaBrowseSection(
                        title = title,
                        series = series,
                    )
                }

            _state.value = DramaLibraryUiState.Ready(
                continueWatching = continueWatching,
                featuredSeries = featuredSeries,
                recentlyAdded = recentlyAdded,
                browseSections = browseSections,
                series = sortedSeries,
                totalSeriesCount = resolvedSeries.size,
            )
            MiruLog.i(
                "DramaLibraryViewModel",
                "Drama library loaded",
                mapOf(
                    "series_count" to resolvedSeries.size.toString(),
                    "continue_count" to continueWatching.size.toString(),
                    "duration_ms" to (monotonicNowMs() - startedAt).toString(),
                ),
            )
            return LibraryLoadSnapshot(hasSources = true, hasContent = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = DramaLibraryUiState.ScanError("加载电视剧媒体库失败，请稍后重试")
            MiruLog.e(
                "DramaLibraryViewModel",
                "Drama library load failed",
                e,
                mapOf("duration_ms" to (monotonicNowMs() - startedAt).toString()),
            )
            return LibraryLoadSnapshot(hasSources = true, hasContent = false)
        }
    }

    private suspend fun loadContinueWatching(
        detailBySeriesId: Map<String, LibraryDramaDetail?>,
    ): List<DramaProgressItem> {
        val progressByEpisodeId = progressRepository.getContinueWatching()
            .getOrNull()
            .orEmpty()
            .associateBy { it.episodeId }

        return detailBySeriesId.values
            .mapNotNull { detail ->
                val resolvedDetail = detail ?: return@mapNotNull null
                resolvedDetail.episodes
                    .mapNotNull { episode ->
                        val progress = progressByEpisodeId[episode.id]
                            ?.takeIf { it.positionMs > 0L }
                            ?.takeUnless { episode.toPlaybackEpisode().isCompleted(it) }
                            ?: return@mapNotNull null
                        DramaProgressItem(resolvedDetail.series, episode, progress)
                    }
                    .maxByOrNull { it.progress?.lastWatched ?: 0L }
            }
            .sortedByDescending { it.progress?.lastWatched ?: 0L }
    }

    private fun LibraryDramaDetail?.latestEpisodeSortKey(): String =
        this?.episodes?.maxWithOrNull(
            compareBy<DramaEpisode>({ it.seasonNumber }, { it.episodeNumber }, { it.id }),
        )?.id.orEmpty()
}

private data class LibraryLoadSnapshot(
    val hasSources: Boolean,
    val hasContent: Boolean,
)

private fun DramaEpisode.toPlaybackEpisode() =
    com.miruplay.tv.model.Episode(
        id = id,
        animeId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = title,
        filePath = filePath,
        fileName = fileName,
    )

private const val SCAN_CONTENT_REFRESH_DELAY_MS = 500L

private fun monotonicNowMs(): Long =
    System.nanoTime() / 1_000_000L
