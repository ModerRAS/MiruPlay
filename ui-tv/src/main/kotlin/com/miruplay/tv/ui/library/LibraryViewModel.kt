package com.miruplay.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.data.repository.IndexRepository
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.repository.MetadataRepository
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.isCompleted
import com.miruplay.tv.model.mergeSameAnimeForDisplay
import com.miruplay.tv.scanner.ScanCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class ProgressWithEpisode(
    val progress: ProgressRecord?,
    val episode: Episode?,
    val anime: Anime?
)

/**
 * Home screen states:
 * - Loading: initial load
 * - NoSources: no sources configured -> show "添加媒体源开始使用"
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
        val continueWatching: List<ProgressWithEpisode>,
        val recentlyAdded: List<Anime>,
        val allAnime: List<Anime>
    ) : LibraryUiState()
    data class ScanError(val message: String) : LibraryUiState()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: IndexRepository,
    private val progressRepository: ProgressRepository,
    private val scanCoordinator: ScanCoordinator,
    private val scanPreferences: ScanPreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        scanCoordinator.setProgressCallback(ScanCoordinator.ScanProgressCallback { path, files, newEps ->
            val current = _state.value
            if (current is LibraryUiState.Scanning) {
                _state.value = current.copy(
                    currentPath = path,
                    filesScanned = current.filesScanned + files,
                    newEpisodes = current.newEpisodes + newEps
                )
            }
        })
        refresh()
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val snapshot = loadLibraryContent(showLoading = true)
            if (snapshot.hasSources && scanPreferences.shouldAutoScan()) {
                scanAndLoadContent()
            }
        }
    }

    fun scanNow() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanAndLoadContent()
        }
    }

    private suspend fun scanAndLoadContent() {
        val sources = mediaRepository.getSources().getOrNull() ?: emptyList()
        Log.d("LibraryViewModel", "scanAndLoadContent: sources=${sources.size}")
        if (sources.isEmpty()) {
            _state.value = LibraryUiState.NoSources
            return
        }

        _state.value = LibraryUiState.Scanning()
        var scanError: String? = null
        val scanResults = withTimeoutOrNull(120_000L) {
            scanCoordinator.scanAllSources()
        }
        Log.d("LibraryViewModel", "scanAndLoadContent: scanResults=${scanResults?.getOrNull()?.size ?: -1}")
        when {
            scanResults == null -> {
                Log.w("LibraryViewModel", "Scan timed out after 120s")
                scanError = "扫描超时，已保留本地缓存内容"
            }
            scanResults is Result.Success -> {
                scanPreferences.lastScanAt = System.currentTimeMillis()
            }
            scanResults is Result.Error -> {
                scanError = "扫描失败：${scanResults.error::class.simpleName}"
            }
        }

        val snapshot = loadLibraryContent(showLoading = false)
        if (snapshot.hasSources && !snapshot.hasContent && scanError != null) {
            _state.value = LibraryUiState.ScanError(scanError)
        } else if (snapshot.hasSources && !snapshot.hasContent) {
            _state.value = LibraryUiState.ScanError("未找到番剧内容，请检查媒体源路径")
        }
    }

    private suspend fun loadLibraryContent(showLoading: Boolean): LibraryLoadSnapshot {
        if (showLoading) {
            _state.value = LibraryUiState.Loading
        }

        val sources = mediaRepository.getSources().getOrNull() ?: emptyList()
        Log.d("LibraryViewModel", "loadLibraryContent: sources=${sources.size}")
        if (sources.isEmpty()) {
            _state.value = LibraryUiState.NoSources
            return LibraryLoadSnapshot(hasSources = false, hasContent = false)
        }

        val continueWatching = loadContinueWatching()
        val allAnimeList = loadCachedAnime(sources)
        val displayAnime = if (scanPreferences.mergeSameAnimeEnabled) {
            allAnimeList.mergeSameAnimeForDisplay()
        } else {
            allAnimeList.distinctBy { it.id }
        }

        if (displayAnime.isEmpty() && continueWatching.isEmpty()) {
            _state.value = LibraryUiState.HasSources
            return LibraryLoadSnapshot(hasSources = true, hasContent = false)
        }

        _state.value = LibraryUiState.HasContent(
            continueWatching = continueWatching,
            recentlyAdded = displayAnime.takeLast(10),
            allAnime = displayAnime
        )
        return LibraryLoadSnapshot(hasSources = true, hasContent = true)
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
            val animeName = episodePath.extractAnimeNameFromPath()
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
        val allAnimeList = mutableListOf<Anime>()
        for (source in sources) {
            val animeNames = indexRepository.getAnimeInIndex(source.id).getOrNull() ?: continue
            for (name in animeNames) {
                val cached = metadataRepository.getCachedMetadata(name).getOrNull()
                if (cached != null) {
                    allAnimeList.add(cached)
                }
            }
        }
        return allAnimeList
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            loadLibraryContent(showLoading = false)
        }
    }

    override fun onCleared() {
        scanCoordinator.setProgressCallback(null)
        scanJob?.cancel()
        super.onCleared()
    }
}

private data class LibraryLoadSnapshot(
    val hasSources: Boolean,
    val hasContent: Boolean
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
