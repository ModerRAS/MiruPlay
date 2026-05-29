package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.BANGUMI_RESULT_LIMIT
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.Season
import com.miruplay.tv.model.activeSeasonOrDefault
import com.miruplay.tv.model.detailBangumiManualApplyStartedMessage
import com.miruplay.tv.model.detailBangumiManualCandidateTerms
import com.miruplay.tv.model.detailBangumiManualSearchRequiredMessage
import com.miruplay.tv.model.detailBangumiManualSearchResultMessage
import com.miruplay.tv.model.detailBangumiManualSearchStartedMessage
import com.miruplay.tv.model.detailBangumiManualSelectionRequiredMessage
import com.miruplay.tv.model.detailBangumiMetadataUpdatedMessage
import com.miruplay.tv.model.detailBangumiScraperUnavailableMessage
import com.miruplay.tv.model.detailBangumiSyncCompleteMessage
import com.miruplay.tv.model.detailBangumiSyncStartedMessage
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.episodesForSeason
import com.miruplay.tv.model.metadataSelectedResultTvStatus
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

    private val _manualMatch = MutableStateFlow(BangumiManualMatchUiState())
    val manualMatch: StateFlow<BangumiManualMatchUiState> = _manualMatch.asStateFlow()

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

    fun openRescrapeMatcher() {
        val current = _anime.value ?: return
        val localEpisodes = allEpisodesWithProgress.map { it.first }
        val candidates = detailBangumiManualCandidateTerms(current, localEpisodes)
        _manualMatch.value = BangumiManualMatchUiState(
            isOpen = true,
            query = "",
            candidateTerms = candidates,
            selectedCandidateTerms = candidates.take(1).toSet(),
            statusMessage = null,
        )
    }

    fun closeRescrapeMatcher() {
        _manualMatch.value = BangumiManualMatchUiState()
    }

    fun updateManualMatchQuery(query: String) {
        _manualMatch.value = _manualMatch.value.copy(query = query)
    }

    fun toggleManualMatchCandidate(candidate: String) {
        val current = _manualMatch.value
        val selected = if (candidate in current.selectedCandidateTerms) {
            current.selectedCandidateTerms - candidate
        } else {
            current.selectedCandidateTerms + candidate
        }
        _manualMatch.value = current.copy(selectedCandidateTerms = selected)
    }

    fun searchManualMatches() {
        val currentState = _manualMatch.value
        val queries = manualMatchQueries(currentState)
        if (queries.isEmpty()) {
            _manualMatch.value = currentState.copy(statusMessage = detailBangumiManualSearchRequiredMessage())
            return
        }

        viewModelScope.launch {
            val bangumi = bangumiScraper()
            if (bangumi == null) {
                _manualMatch.value = _manualMatch.value.copy(statusMessage = detailBangumiScraperUnavailableMessage())
                return@launch
            }

            _isSyncing.value = true
            _manualMatch.value = _manualMatch.value.copy(
                isSearching = true,
                statusMessage = detailBangumiManualSearchStartedMessage(queries.size),
            )

            val matches = mutableListOf<ScraperResult>()
            var lastErrorMessage: String? = null
            for (query in queries) {
                when (val result = bangumi.searchAnime(query)) {
                    is Result.Error -> lastErrorMessage = result.error.toUserMessage()
                    is Result.Success -> matches += result.data
                }
            }

            val distinctMatches = matches
                .distinctBy { "${it.source.name}:${it.animeId}" }
                .take(BANGUMI_RESULT_LIMIT)
            _manualMatch.value = _manualMatch.value.copy(
                results = distinctMatches,
                selectedResult = distinctMatches.firstOrNull(),
                isSearching = false,
                statusMessage = if (distinctMatches.isEmpty() && lastErrorMessage != null) {
                    lastErrorMessage
                } else {
                    detailBangumiManualSearchResultMessage(distinctMatches.size)
                },
            )
            _isSyncing.value = false
        }
    }

    fun selectManualMatchResult(result: ScraperResult) {
        _manualMatch.value = _manualMatch.value.copy(
            selectedResult = result,
            statusMessage = metadataSelectedResultTvStatus(result.displayTitle()),
        )
    }

    fun applyManualMatch() {
        val current = _anime.value ?: return
        val match = _manualMatch.value.selectedResult
        if (match == null) {
            _manualMatch.value = _manualMatch.value.copy(statusMessage = detailBangumiManualSelectionRequiredMessage())
            return
        }

        viewModelScope.launch {
            val bangumi = bangumiScraper()
            if (bangumi == null) {
                _manualMatch.value = _manualMatch.value.copy(statusMessage = detailBangumiScraperUnavailableMessage())
                return@launch
            }

            _isSyncing.value = true
            val applyingMessage = detailBangumiManualApplyStartedMessage(match.displayTitle())
            _actionMessage.value = applyingMessage
            _manualMatch.value = _manualMatch.value.copy(isApplying = true, statusMessage = applyingMessage)

            val localEpisodes = metadataRepository.getCachedEpisodes(current.id).getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: allEpisodesWithProgress.map { it.first }
            when (
                val refreshed = BangumiMetadataRefreshCore(
                    metadataRepository = metadataRepository,
                    bangumiScraper = bangumi,
                ).cacheMatchedMetadata(
                    cacheAnimeId = current.id,
                    match = match,
                    localEpisodes = localEpisodes,
                )
            ) {
                is Result.Error -> {
                    _manualMatch.value = _manualMatch.value.copy(
                        isApplying = false,
                        statusMessage = refreshed.error.toUserMessage(),
                    )
                    _actionMessage.value = refreshed.error.toUserMessage()
                    _isSyncing.value = false
                    return@launch
                }
                is Result.Success -> Unit
            }

            _manualMatch.value = BangumiManualMatchUiState()
            _actionMessage.value = detailBangumiMetadataUpdatedMessage()
            _isSyncing.value = false
            loadAnime(current.id)
        }
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

    private fun bangumiScraper(): MetadataScraper? =
        metadataScrapers.firstOrNull { it.sourceName.equals("Bangumi", ignoreCase = true) }

    private fun manualMatchQueries(state: BangumiManualMatchUiState): List<String> =
        (state.selectedCandidateTerms.toList() + state.query)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}

data class BangumiManualMatchUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val candidateTerms: List<String> = emptyList(),
    val selectedCandidateTerms: Set<String> = emptySet(),
    val results: List<ScraperResult> = emptyList(),
    val selectedResult: ScraperResult? = null,
    val isSearching: Boolean = false,
    val isApplying: Boolean = false,
    val statusMessage: String? = null,
)
