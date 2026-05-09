package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.MetadataRepository
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.Season
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _anime = MutableStateFlow<Anime?>(null)
    val anime: StateFlow<Anime?> = _anime.asStateFlow()

    private val _seasons = MutableStateFlow<List<Season>>(emptyList())
    val seasons: StateFlow<List<Season>> = _seasons.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _episodesWithProgress = MutableStateFlow<List<Pair<Episode, ProgressRecord?>>>(emptyList())
    val episodesWithProgress: StateFlow<List<Pair<Episode, ProgressRecord?>>> = _episodesWithProgress.asStateFlow()

    private var allEpisodesWithProgress: List<Pair<Episode, ProgressRecord?>> = emptyList()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAnime(animeId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            metadataRepository.getCachedMetadata(animeId).onSuccess { cached ->
                if (cached != null) {
                    _anime.value = cached
                }
            }

            metadataRepository.getCachedEpisodes(animeId).onSuccess { epList ->
                // Group episodes into seasons
                val seasonMap = epList.groupBy { it.seasonNumber }
                _seasons.value = seasonMap.map { (seasonNum, eps) ->
                    Season(
                        seasonNumber = seasonNum,
                        title = "Season $seasonNum",
                        episodes = eps,
                        episodeCount = eps.size
                    )
                }

                // Load progress for each episode
                val withProgress = epList.map { episode ->
                    val progress = progressRepository.getProgress(episode.id).getOrNull()
                    Pair(episode, progress)
                }
                allEpisodesWithProgress = withProgress
                // Apply current season filter
                _episodesWithProgress.value = withProgress.filter { it.first.seasonNumber == _selectedSeason.value }
            }

            _isLoading.value = false
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
        // Filter from full list by season
        _episodesWithProgress.value = allEpisodesWithProgress.filter { it.first.seasonNumber == seasonNumber }
    }

    fun rescrapeMetadata() {
        // Will trigger metadata refresh - implemented in T34
    }
}