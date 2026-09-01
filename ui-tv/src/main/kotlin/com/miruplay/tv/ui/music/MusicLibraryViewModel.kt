package com.miruplay.tv.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.model.MusicAlbum
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MusicRepository
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.ui.library.LibraryScanController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MusicLibraryUiState {
    data object Loading : MusicLibraryUiState()
    data object NoSources : MusicLibraryUiState()
    data object HasSourcesNoContent : MusicLibraryUiState()
    data class HasContent(val albums: List<MusicAlbum>, val scanNotice: String? = null) : MusicLibraryUiState()
    data class ScanError(val message: String) : MusicLibraryUiState()
}

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val musicRepository: MusicRepository,
    private val libraryScanController: LibraryScanController
) : ViewModel() {

    private val _state = MutableStateFlow<MusicLibraryUiState>(MusicLibraryUiState.Loading)
    val state: StateFlow<MusicLibraryUiState> = _state.asStateFlow()
    val scanState: StateFlow<LibraryScanState> = libraryScanController.state

    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            libraryScanController.state.collect { scanState ->
                when (scanState) {
                    is LibraryScanState.Finished, is LibraryScanState.Failed, is LibraryScanState.Cancelled -> refresh(showLoading = false)
                    else -> Unit
                }
            }
        }
    }

    fun refresh(showLoading: Boolean = true) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (showLoading) _state.value = MusicLibraryUiState.Loading
            val sources = mediaRepository.getSources().getOrNull().orEmpty().filter { it.contentMode == MediaContentMode.MUSIC }
            if (sources.isEmpty()) {
                _state.value = MusicLibraryUiState.NoSources
                return@launch
            }
            val albums = musicRepository.getAlbums().getOrNull().orEmpty().filter { it.sourceId in sources.map { s -> s.id } }
            if (albums.isEmpty()) {
                _state.value = MusicLibraryUiState.HasSourcesNoContent
            } else {
                _state.value = MusicLibraryUiState.HasContent(albums.sortedBy { it.title })
            }
        }
    }

    fun scanNow() = libraryScanController.startManualScan()
    fun cancelScan() = libraryScanController.cancel()
}
