package com.miruplay.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.isCompleted
import com.miruplay.tv.model.mergeSameAnimeForDisplay
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
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val libraryScanTask: LibraryScanTask,
    private val scanPreferences: ScanPreferencesManager
) : ViewModel() {

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
                        MiruLog.i(
                            "LibraryViewModel",
                            "Scan task state finished",
                            mapOf("result_count" to scanState.results.size.toString())
                        )
                        val snapshot = loadLibraryContent(showLoading = false)
                        if (snapshot.hasSources && !snapshot.hasContent) {
                            _state.value = LibraryUiState.ScanError("未找到番剧内容，请检查媒体源路径")
                        }
                    }
                    is LibraryScanState.Failed -> {
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
                        loadLibraryContent(showLoading = false)
                    }
                }
            }
        }
    }

    private suspend fun loadLibraryContent(showLoading: Boolean): LibraryLoadSnapshot {
        if (showLoading) {
            _state.value = LibraryUiState.Loading
        }

        val sources = mediaRepository.getSources().getOrNull() ?: emptyList()
        Log.d("LibraryViewModel", "loadLibraryContent: sources=${sources.size}")
        MiruLog.d("LibraryViewModel", "Library content loading", mapOf("source_count" to sources.size.toString()))
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
        MiruLog.i(
            "LibraryViewModel",
            "Library content loaded",
            mapOf(
                "anime_count" to displayAnime.size.toString(),
                "continue_watching_count" to continueWatching.size.toString()
            )
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
