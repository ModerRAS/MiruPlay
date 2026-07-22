package com.miruplay.tv.ui.library

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.libraryNoContentAfterScanMessage
import com.miruplay.tv.model.mergeSameAnimeForDisplay
import com.miruplay.tv.repository.LibraryEpisodeResolver
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.preferredMetadataCacheKey
import com.miruplay.tv.repository.toMediaIndexPosterGroups
import com.miruplay.tv.scanner.LibraryScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class ProgressWithEpisode(
    val progress: ProgressRecord?,
    val episode: Episode?,
    val anime: Anime?
)

/**
 * Home screen states:
 * - Loading: initial load
 * - NoSources: no sources configured
 * - HasSources: sources configured but nothing scanned yet
 * - HasContent: anime data loaded -> show library
 * - ScanError: scan failed or produced no content
 */
sealed class LibraryUiState {
    data object Loading : LibraryUiState()
    data object NoSources : LibraryUiState()
    data object HasSources : LibraryUiState()
    data class HasContent(
        val continueWatching: List<ProgressWithEpisode>,
        val recentlyAdded: List<Anime>,
        val allAnime: List<Anime>,
        val posterWallArrangement: PosterWallArrangement,
        val scanNotice: String? = null,
    ) : LibraryUiState()
    data class ScanError(val message: String) : LibraryUiState()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val libraryScanTask: LibraryScanController,
    private val scanPreferences: ScanPreferencesManager
) : ViewModel() {

    private val libraryEpisodeResolver = LibraryEpisodeResolver(
        mediaSources = mediaRepository,
        metadata = metadataRepository,
        index = indexRepository,
        progress = progressRepository,
        mergeSameAnimeEnabled = { scanPreferences.mergeSameAnimeEnabled },
    )

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    val scanState: StateFlow<LibraryScanState> = libraryScanTask.state
    private var refreshJob: Job? = null
    private var scanContentRefreshJob: Job? = null
    private var queuedScanContentVersion = -1
    private var scanRefreshSessionActive = false

    init {
        observeScanTask()
        refresh()
    }

    fun refresh(showLoading: Boolean = true) {
        if (refreshJob?.isActive == true) {
            MiruLog.d("LibraryViewModel", "Library refresh skipped because a refresh is already active")
            return
        }

        refreshJob = viewModelScope.launch {
            val scanState = libraryScanTask.state.value
            val scanInProgress = scanState is LibraryScanState.Scanning
            val snapshot = loadLibraryContent(showLoading = showLoading && !scanInProgress)
            if (snapshot.hasSources && !scanInProgress) {
                libraryScanTask.startAutoScanIfDue()
            }
        }
    }

    fun scanNow() {
        libraryScanTask.startManualScan()
    }

    private fun observeScanTask() {
        viewModelScope.launch {
            libraryScanTask.state.collectLatest { scanState ->
                when (scanState) {
                    is LibraryScanState.Idle -> Unit
                    is LibraryScanState.Scanning -> {
                        if (!scanRefreshSessionActive) {
                            scanRefreshSessionActive = true
                            queuedScanContentVersion = -1
                            applyScanNotice(null)
                        }
                        scheduleContentRefreshDuringScan(scanState)
                    }
                    is LibraryScanState.Finished -> {
                        scanRefreshSessionActive = false
                        Log.d("LibraryViewModel", "scan finished: results=${scanState.results.size}")
                        MiruLog.i(
                            "LibraryViewModel",
                            "Scan task state finished",
                            mapOf("result_count" to scanState.results.size.toString())
                        )
                        val snapshot = loadLibraryContent(showLoading = false)
                        if (snapshot.hasSources && !snapshot.hasContent) {
                            _state.value = LibraryUiState.ScanError(
                                scanState.sourceFailures.firstOrNull() ?: libraryNoContentAfterScanMessage()
                            )
                        } else {
                            applyScanNotice(libraryScanNotice(scanState.results, scanState.sourceFailures))
                        }
                    }
                    is LibraryScanState.Failed -> {
                        scanRefreshSessionActive = false
                        MiruLog.w(
                            "LibraryViewModel",
                            "Scan task state failed",
                            attributes = mapOf("message" to scanState.message)
                        )
                        val snapshot = loadLibraryContent(showLoading = false)
                        if (snapshot.hasSources && !snapshot.hasContent) {
                            _state.value = LibraryUiState.ScanError(scanState.message)
                        } else {
                            applyScanNotice("扫描失败：${scanState.message}")
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
            currentState is LibraryUiState.Loading ||
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
        val startedAt = SystemClock.elapsedRealtime()
        val scanNotice = (_state.value as? LibraryUiState.HasContent)?.scanNotice
        if (showLoading) {
            _state.value = LibraryUiState.Loading
        }

        try {
            val sources = mediaRepository.getSources()
                .getOrNull()
                .orEmpty()
                .filter { it.contentMode == MediaContentMode.ANIME }
            Log.d("LibraryViewModel", "loadLibraryContent: sources=${sources.size}")
            MiruLog.d("LibraryViewModel", "Library content loading", mapOf("source_count" to sources.size.toString()))
            if (sources.isEmpty()) {
                _state.value = LibraryUiState.NoSources
                return LibraryLoadSnapshot(hasSources = false, hasContent = false)
            }

            val continueWatching = loadContinueWatching()
            val allAnimeList = loadCachedAnime(sources)
            val displayAnime = withContext(Dispatchers.Default) {
                if (scanPreferences.mergeSameAnimeEnabled) {
                    allAnimeList.mergeSameAnimeForDisplay()
                } else {
                    allAnimeList.distinctBy { it.id }
                }
            }

            if (displayAnime.isEmpty() && continueWatching.isEmpty()) {
                _state.value = LibraryUiState.HasSources
                return LibraryLoadSnapshot(hasSources = true, hasContent = false)
            }

            _state.value = LibraryUiState.HasContent(
                continueWatching = continueWatching,
                recentlyAdded = displayAnime.takeLast(10),
                allAnime = displayAnime,
                posterWallArrangement = scanPreferences.posterWallArrangement,
                scanNotice = scanNotice,
            )
            MiruLog.i(
                "LibraryViewModel",
                "Library content loaded",
                mapOf(
                    "anime_count" to displayAnime.size.toString(),
                    "continue_watching_count" to continueWatching.size.toString(),
                    "duration_ms" to (SystemClock.elapsedRealtime() - startedAt).toString()
                )
            )
            return LibraryLoadSnapshot(hasSources = true, hasContent = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = LibraryUiState.ScanError(libraryLoadErrorMessage())
            MiruLog.e(
                "LibraryViewModel",
                "Library content load failed",
                e,
                attributes = mapOf("duration_ms" to (SystemClock.elapsedRealtime() - startedAt).toString())
            )
            return LibraryLoadSnapshot(hasSources = true, hasContent = false)
        }
    }

    private suspend fun loadContinueWatching(): List<ProgressWithEpisode> =
        libraryEpisodeResolver.loadContinueWatchingEpisodes().map { item ->
            ProgressWithEpisode(
                progress = item.progress,
                episode = item.episode,
                anime = item.anime,
            )
        }

    private suspend fun loadCachedAnime(sources: List<MediaSourceInfo>): List<Anime> {
        val metadataKeys = sources.flatMap { source ->
            indexRepository.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .toMediaIndexPosterGroups(scanPreferences.mergeSameAnimeEnabled)
                .map { it.preferredMetadataCacheKey() }
        }.distinct()
        MiruLog.d(
            "LibraryViewModel",
            "Preferred anime metadata keys loaded",
            mapOf("key_count" to metadataKeys.size.toString())
        )
        return metadataRepository.getCachedMetadata(metadataKeys).getOrNull().orEmpty()
    }

    private fun applyScanNotice(message: String?) {
        val content = _state.value as? LibraryUiState.HasContent ?: return
        _state.value = content.copy(scanNotice = message)
    }

    private fun libraryLoadErrorMessage(): String {
        return "加载媒体库失败，请稍后重试"
    }

    fun cancelScan() {
        libraryScanTask.cancel()
    }
}

private data class LibraryLoadSnapshot(
    val hasSources: Boolean,
    val hasContent: Boolean
)

internal fun libraryScanNotice(
    results: List<ScanResult>,
    sourceFailures: List<String>,
): String? {
    val summarized = results.mapNotNull(ScanResult::summary)
    val unsummarized = results.filter { it.summary.isNullOrBlank() }
    val success = summarized + unsummarized
        .takeIf(List<ScanResult>::isNotEmpty)
        ?.let { listOf("扫描完成：${it.sumOf(ScanResult::episodesFound)} 个文件") }
        .orEmpty()
    val failure = sourceFailures.takeIf(List<String>::isNotEmpty)?.let {
        val suffix = if (it.size > 1) "（共 ${it.size} 个源失败）" else ""
        "扫描失败：${it.first()}$suffix"
    }
    return (success + listOfNotNull(failure)).joinToString("；").takeIf(String::isNotBlank)
}

private const val SCAN_CONTENT_REFRESH_DELAY_MS = 500L
