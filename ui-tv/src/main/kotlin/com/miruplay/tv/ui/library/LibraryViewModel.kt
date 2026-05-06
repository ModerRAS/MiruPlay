package com.miruplay.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _continueWatching = MutableStateFlow<List<ProgressWithEpisode>>(emptyList())
    val continueWatching: StateFlow<List<ProgressWithEpisode>> = _continueWatching.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<Anime>>(emptyList())
    val recentlyAdded: StateFlow<List<Anime>> = _recentlyAdded.asStateFlow()

    private val _allAnime = MutableStateFlow<List<Anime>>(emptyList())
    val allAnime: StateFlow<List<Anime>> = _allAnime.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Load continue watching
            progressRepository.getContinueWatching().onSuccess { records ->
                _continueWatching.value = records.map { record ->
                    ProgressWithEpisode(
                        progress = record,
                        episode = null,
                        anime = null
                    )
                }
            }
            
            _isLoading.value = false
        }
    }
}