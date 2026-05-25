package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.Season
import com.miruplay.tv.model.activeSeasonOrDefault
import com.miruplay.tv.model.detailBangumiMetadataUpdatedMessage
import com.miruplay.tv.model.detailBangumiRescrapeStartedMessage
import com.miruplay.tv.model.detailBangumiScraperUnavailableMessage
import com.miruplay.tv.model.detailBangumiSyncCompleteMessage
import com.miruplay.tv.model.detailBangumiSyncStartedMessage
import com.miruplay.tv.model.episodesForSeason
import com.miruplay.tv.model.toSeasons
import com.miruplay.tv.repository.LibraryAnimeResolver
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.sync.BangumiMetadataRefreshCore
import com.miruplay.tv.sync.BangumiSyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val bangumiSyncEngine: BangumiSyncEngine,
    private val scanPreferences: ScanPreferencesRepository,
    private val metadataScrapers: Set<@JvmSuppressWildcards MetadataScraper>
) : ViewModel() {
    private val libraryAnimeResolver = LibraryAnimeResolver(
        mediaSources = mediaRepository,
        metadata = metadataRepository,
        index = indexRepository,
        mergeSameAnimeEnabled = { scanPreferences.getPreferences().mergeSameAnimeEnabled },
    )

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

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun loadAnime(animeId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val detail = libraryAnimeResolver.loadAnimeDetail(animeId)
            _anime.value = detail?.anime
            updateEpisodes(detail?.episodes.orEmpty())
            _isLoading.value = false
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
        _episodesWithProgress.value = allEpisodesWithProgress.episodesForSeason(seasonNumber)
    }

    private suspend fun updateEpisodes(epList: List<Episode>) {
        _seasons.value = epList.toSeasons()
        val selectedSeason = epList.activeSeasonOrDefault(_selectedSeason.value)
        _selectedSeason.value = selectedSeason

        val withProgress = epList.map { episode ->
            val progress = progressRepository.getProgress(episode.id).getOrNull()
            Pair(episode, progress)
        }
        allEpisodesWithProgress = withProgress
        _episodesWithProgress.value = withProgress.episodesForSeason(selectedSeason)
    }

    fun rescrapeMetadata() {
        val current = _anime.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _actionMessage.value = detailBangumiRescrapeStartedMessage()
            val bangumi = metadataScrapers.firstOrNull { it.sourceName.equals("Bangumi", ignoreCase = true) }
            if (bangumi == null) {
                _actionMessage.value = detailBangumiScraperUnavailableMessage()
                _isSyncing.value = false
                return@launch
            }

            val candidates = listOfNotNull(current.titleCn, current.title, current.id)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val localEpisodes = metadataRepository.getCachedEpisodes(current.id).getOrNull().orEmpty()
            when (
                val refreshed = BangumiMetadataRefreshCore(
                    metadataRepository = metadataRepository,
                    bangumiScraper = bangumi,
                ).refresh(
                    cacheAnimeId = current.id,
                    query = current.id,
                    candidates = candidates,
                    localEpisodes = localEpisodes,
                )
            ) {
                is Result.Error -> {
                    _actionMessage.value = refreshed.error.toUserMessage()
                    _isSyncing.value = false
                    return@launch
                }
                is Result.Success -> Unit
            }
            _actionMessage.value = detailBangumiMetadataUpdatedMessage()
            _isSyncing.value = false
            loadAnime(current.id)
        }
    }

    fun syncBangumi() {
        val current = _anime.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _actionMessage.value = detailBangumiSyncStartedMessage()
            bangumiSyncEngine.syncAnime(current.id).onSuccess { summary ->
                _actionMessage.value = detailBangumiSyncCompleteMessage(summary.pushedEpisodes, summary.pulledEpisodes)
                loadAnime(current.id)
            }.onError { error ->
                _actionMessage.value = error.toUserMessage()
            }
            _isSyncing.value = false
        }
    }
}
