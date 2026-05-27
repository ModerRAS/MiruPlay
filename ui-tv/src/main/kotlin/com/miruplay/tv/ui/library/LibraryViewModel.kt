package com.miruplay.tv.ui.library

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.libraryNoContentAfterScanMessage
import com.miruplay.tv.repository.LibraryAnimeResolver
import com.miruplay.tv.repository.LibraryContinueWatchingEpisode
import com.miruplay.tv.repository.LibraryEpisodeResolver
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/**
 * Home screen states:
 * - Loading: initial load
 * - NoSources: no sources configured
 * - HasSources: sources configured but nothing scanned yet
 * - Scanning: actively scanning with progress
 * - HasContent: anime data loaded -> show library
 * - ScanError: scan failed or produced no content
 */
sealed class LibraryUiState {
    data object Loading : LibraryUiState()
    data object NoSources : LibraryUiState()
    data object HasSources : LibraryUiState()
    data class Scanning(
        val currentPath: String = "",
        val filesScanned: Int = 0,
        val newEpisodes: Int = 0
    ) : LibraryUiState()
    data class HasContent(
        val continueWatching: List<LibraryContinueWatchingEpisode>,
        val recentlyAdded: List<Anime>,
        val allAnime: List<Anime>
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
    private val libraryEpisodeResolver = LibraryEpisodeResolver(
        mediaSources = mediaRepository,
        metadata = metadataRepository,
        index = indexRepository,
        progress = progressRepository,
        mergeSameAnimeEnabled = { scanPreferences.getPreferences().mergeSameAnimeEnabled },
    )
    private val libraryAnimeResolver = LibraryAnimeResolver(
        mediaSources = mediaRepository,
        metadata = metadataRepository,
        index = indexRepository,
        mergeSameAnimeEnabled = { scanPreferences.getPreferences().mergeSameAnimeEnabled },
    )

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        observeScanTask()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val scanState = libraryScanTask.state.value
            if (scanState is LibraryScanState.Scanning) {
                _state.value = scanState.toUiState()
                return@launch
            }

            val snapshot = loadLibraryContent(showLoading = true)
            if (snapshot.hasSources) {
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
                        _state.value = scanState.toUiState()
                    }
                    is LibraryScanState.Finished -> {
                        Log.d("LibraryViewModel", "scan finished: results=${scanState.results.size}")
                        val snapshot = loadLibraryContent(showLoading = false)
                        if (snapshot.hasSources && !snapshot.hasContent) {
                            _state.value = LibraryUiState.ScanError("未找到番剧内容，请检查媒体源路径")
                        }
                    }
                    is LibraryScanState.Failed -> {
                        val snapshot = loadLibraryContent(showLoading = false)
                        if (snapshot.hasSources && !snapshot.hasContent) {
                            _state.value = LibraryUiState.ScanError(scanState.message)
                        }
                    }
                    is LibraryScanState.Cancelled -> {
                        loadLibraryContent(showLoading = false)
                    }
                }
            }
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
        val displayAnime = libraryAnimeResolver.loadDisplayAnime()

            if (displayAnime.isEmpty() && continueWatching.isEmpty()) {
                _state.value = LibraryUiState.HasSources
                return LibraryLoadSnapshot(hasSources = true, hasContent = false)
            }

            _state.value = LibraryUiState.HasContent(
                continueWatching = continueWatching,
                recentlyAdded = displayAnime.takeLast(10),
                allAnime = displayAnime
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

    private suspend fun loadContinueWatching(): List<LibraryContinueWatchingEpisode> =
        libraryEpisodeResolver.loadContinueWatchingEpisodes().filter { it.anime != null }

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

private fun LibraryScanState.Scanning.toUiState(): LibraryUiState.Scanning =
    LibraryUiState.Scanning(
        currentPath = currentPath,
        filesScanned = filesScanned,
        newEpisodes = newEpisodes
    )

private fun String.extractAnimeNameFromPath(): String? {
    val parts = split("/", "\\").filter { it.isNotBlank() }
    val downloadIndex = parts.indexOfLast { it.equals("Download", ignoreCase = true) }
    return if (downloadIndex >= 0 && downloadIndex < parts.lastIndex) {
        parts[downloadIndex + 1]
    } else {
        parts.firstOrNull()
    }
}
