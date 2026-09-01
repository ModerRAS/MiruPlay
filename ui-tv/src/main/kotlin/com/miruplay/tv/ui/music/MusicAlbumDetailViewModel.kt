package com.miruplay.tv.ui.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.model.MusicAlbum
import com.miruplay.tv.model.MusicTrack
import com.miruplay.tv.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MusicAlbumDetailUiState {
    data object Loading : MusicAlbumDetailUiState()
    data class HasContent(val album: MusicAlbum, val tracks: List<MusicTrack>) : MusicAlbumDetailUiState()
    data class Error(val message: String) : MusicAlbumDetailUiState()
}

@HiltViewModel
class MusicAlbumDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val albumId: String = savedStateHandle.get<String>("albumId") ?: ""
    private val _state = MutableStateFlow<MusicAlbumDetailUiState>(MusicAlbumDetailUiState.Loading)
    val state: StateFlow<MusicAlbumDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = MusicAlbumDetailUiState.Loading
            val albumRes = musicRepository.getAlbumById(albumId)
            if (albumRes is com.miruplay.tv.core.common.Result.Error) {
                _state.value = MusicAlbumDetailUiState.Error(albumRes.error.toUserMessage())
                return@launch
            }
            val album = (albumRes as com.miruplay.tv.core.common.Result.Success).data
            val tracks = musicRepository.getTracksByAlbum(albumId).getOrNull().orEmpty().sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: 999 }, { it.title }))
            _state.value = MusicAlbumDetailUiState.HasContent(album, tracks)
        }
    }
}
