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
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.isCompleted
import com.miruplay.tv.model.libraryNoContentAfterScanMessage
import com.miruplay.tv.model.mergeSameAnimeForDisplay
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
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
    ) : LibraryUiState()
    data class ScanError(val message: String) : LibraryUiState()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val libraryScanTask: LibraryScanTask,
    private val scanPreferences: ScanPreferencesManager
) : ViewModel() {

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
                            _state.value = LibraryUiState.ScanError(libraryNoContentAfterScanMessage())
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
        if (showLoading) {
            _state.value = LibraryUiState.Loading
        }

        try {
            val sources = mediaRepository.getSources().getOrNull() ?: emptyList()
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

    private suspend fun loadContinueWatching(): List<ProgressWithEpisode> {
        val progressRecords = progressRepository.getContinueWatching().getOrNull() ?: emptyList()
        return progressRecords.mapNotNull { record ->
            val cachedEpisode = metadataRepository.getCachedEpisode(record.episodeId).getOrNull()
            if (cachedEpisode != null) {
                if (cachedEpisode.isCompleted(record)) return@mapNotNull null
                val anime = metadataRepository.getCachedMetadata(cachedEpisode.animeId).getOrNull()
                    ?: return@mapNotNull null
                return@mapNotNull ProgressWithEpisode(
                    progress = record,
                    episode = cachedEpisode.copy(
                        watchedPosition = record.positionMs,
                        lastWatchedTimestamp = record.lastWatched,
                        playCount = record.playCount
                    ),
                    anime = anime
                )
            }

            val pathParts = record.episodeId.split(":", limit = 2)
            val episodePath = pathParts.getOrNull(1) ?: record.episodeId
            val sourceId = pathParts.getOrNull(0)?.toLongOrNull()
            val animeName = MediaPathConventions.animeNameFromEpisodePath(episodePath)
            if (animeName == null || sourceId == null) return@mapNotNull null

            val anime = metadataRepository.getCachedMetadata(animeName).getOrNull()
                ?: return@mapNotNull null

            val matchedEntry = indexRepository.queryIndex(sourceId, animeName)
                .getOrNull()
                .orEmpty()
                .find { it.path == episodePath }
                ?: return@mapNotNull null

            val episode = Episode(
                id = record.episodeId,
                animeId = animeName,
                seasonNumber = matchedEntry.seasonNumber ?: 1,
                episodeNumber = matchedEntry.episodeNumber ?: 1,
                title = "",
                filePath = episodePath,
                fileName = episodePath.substringAfterLast("/"),
                duration = 0L,
                watchedPosition = record.positionMs,
                lastWatchedTimestamp = record.lastWatched,
                playCount = record.playCount,
                thumbnailPath = null
            )

            if (episode.isCompleted(record)) return@mapNotNull null

            ProgressWithEpisode(progress = record, episode = episode, anime = anime)
        }
    }

    private suspend fun loadCachedAnime(sources: List<MediaSourceInfo>): List<Anime> {
        val sourceAnimeNames = sources.flatMap { source ->
            indexRepository.getAnimeInIndex(source.id).getOrNull().orEmpty()
        }
        MiruLog.d(
            "LibraryViewModel",
            "Cached anime names loaded",
            mapOf("name_count" to sourceAnimeNames.size.toString())
        )
        return metadataRepository.getCachedMetadata(sourceAnimeNames).getOrNull().orEmpty()
    }

    private fun libraryLoadErrorMessage(): String {
        return "加载媒体库失败，请稍后重试"
    }

    fun cancelScan() {
        libraryScanTask.cancel()
        viewModelScope.launch {
            loadLibraryContent(showLoading = false)
        }
    }
}

private data class LibraryLoadSnapshot(
    val hasSources: Boolean,
    val hasContent: Boolean
)

private const val SCAN_CONTENT_REFRESH_DELAY_MS = 500L
