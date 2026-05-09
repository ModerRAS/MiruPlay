package com.miruplay.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.data.repository.IndexRepository
import com.miruplay.tv.data.repository.IndexRepositoryEntity
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.repository.MetadataRepository
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.scanner.ScanCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
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
    private val scanCoordinator: ScanCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var scanJob: kotlinx.coroutines.Job? = null

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
            _state.value = LibraryUiState.Loading

            val sources = mediaRepository.getSources().getOrNull() ?: emptyList()
            Log.d("LibraryViewModel", "refresh: sources=${sources.size}")
            if (sources.isEmpty()) {
                _state.value = LibraryUiState.NoSources
                return@launch
            }

            // Start scanning with progress reporting
            _state.value = LibraryUiState.Scanning()
            val scanResults = withTimeoutOrNull(120_000L) {  // 2 minute timeout
                scanCoordinator.scanAllSources()
            }
            Log.d("LibraryViewModel", "refresh: scanResults=${scanResults?.getOrNull()?.size ?: -1}")

            if (scanResults == null) {
                Log.w("LibraryViewModel", "Scan timed out after 120s")
                // Still try to load whatever we have
            }

            // Load continue watching from Room — resolve anime/episode from index
            val progressRecords = progressRepository.getContinueWatching().getOrNull() ?: emptyList()
            val continueWatching = progressRecords.mapNotNull { record ->
                // episodeId format is "sourceId:path" — extract sourceId and anime name from path
                val pathParts = record.episodeId.split(":", limit = 2)
                val episodePath = pathParts.getOrNull(1) ?: record.episodeId
                val sourceId = pathParts.getOrNull(0)?.toLongOrNull()
                
                // Extract anime name from path: /storage/emulated/0/Download/{AnimeName}/{episode}.mp4
                val animeName = episodePath.split("/").filter { it.isNotBlank() }
                    .dropWhile { it != "Download" }
                    .drop(1)
                    .firstOrNull()
                
                if (animeName == null || sourceId == null) return@mapNotNull null
                
                val anime = metadataRepository.getCachedMetadata(animeName).getOrNull()
                    ?: return@mapNotNull null
                
                // Find the matching episode in the index (search by anime name, find the exact path)
                val indexEntries = indexRepository.queryIndex(sourceId, animeName)
                    .getOrNull()
                    ?: emptyList()
                val matchedEntry = indexEntries.find { it.path == episodePath }
                
                val episode = matchedEntry?.let {
                    Episode(
                        id = record.episodeId,
                        animeId = animeName,
                        seasonNumber = it.seasonNumber ?: 1,
                        episodeNumber = it.episodeNumber ?: 1,
                        title = "",
                        filePath = episodePath,
                        fileName = episodePath.substringAfterLast("/"),
                        duration = 0L,
                        watchedPosition = record.positionMs,
                        lastWatchedTimestamp = record.lastWatched,
                        playCount = record.playCount,
                        thumbnailPath = null
                    )
                }
                
                ProgressWithEpisode(progress = record, episode = episode, anime = anime)
            }

            // Load cached anime metadata from all sources
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

            if (allAnimeList.isEmpty() && continueWatching.isEmpty()) {
                _state.value = LibraryUiState.ScanError("未找到番剧内容，请检查媒体源路径")
            } else {
                _state.value = LibraryUiState.HasContent(
                    continueWatching = continueWatching,
                    recentlyAdded = allAnimeList.takeLast(10),
                    allAnime = allAnimeList.distinctBy { it.id }
                )
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.value = LibraryUiState.HasSources
    }

    override fun onCleared() {
        scanCoordinator.setProgressCallback(null)
        scanJob?.cancel()
        super.onCleared()
    }
}
