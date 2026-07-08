package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.PerformanceLog
import com.miruplay.tv.model.BANGUMI_RESULT_LIMIT
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchIntent
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
import com.miruplay.tv.model.toPreferredScraperResult
import com.miruplay.tv.model.toSeasons
import com.miruplay.tv.repository.AnimeMetadataSearchAggregator
import com.miruplay.tv.repository.LibraryAnimeResolver
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.withExternalMetadata
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.sync.BangumiMetadataRefreshCore
import com.miruplay.tv.sync.BangumiSyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val bangumiSyncEngine: BangumiSyncEngine,
    private val scanPreferences: ScanPreferencesRepository,
    private val animeSearchAggregator: AnimeMetadataSearchAggregator,
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

            val detail = PerformanceLog.measureSuspend(
                tag = DETAIL_PERFORMANCE_TAG,
                operation = "detail.load_anime",
                attributes = mapOf("anime_id" to animeId),
                resultAttributes = { result ->
                    mapOf(
                        "found" to (result != null).toString(),
                        "episode_count" to result?.episodes.orEmpty().size.toString(),
                    )
                },
            ) {
                libraryAnimeResolver.loadAnimeDetail(animeId)
            }
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
            _isSyncing.value = true
            _manualMatch.value = _manualMatch.value.copy(
                isSearching = true,
                statusMessage = detailBangumiManualSearchStartedMessage(queries.size),
            )

            try {
                val localEpisodes = allEpisodesWithProgress.map { it.first }
                val aggregated = withTimeout(manualMatchSearchTimeoutMs(queries.size)) {
                    PerformanceLog.measureSuspend(
                        tag = DETAIL_PERFORMANCE_TAG,
                        operation = "detail.metadata.aggregate_manual_search",
                        attributes = mapOf(
                            "query_count" to queries.size.toString(),
                            "local_episode_count" to localEpisodes.size.toString(),
                        ),
                        resultAttributes = { result ->
                            mapOf("candidate_count" to result.candidates.size.toString())
                        },
                    ) {
                        animeSearchAggregator.search(
                            MetadataSearchContext(
                                contentMode = MediaContentMode.ANIME,
                                intent = MetadataSearchIntent.MANUAL_MATCH,
                                aliases = queries,
                                seasonHint = _selectedSeason.value.takeIf { it > 1 },
                                episodeCountHint = localEpisodes.size.takeIf { it > 0 },
                            ),
                        )
                    }
                }

                val distinctMatches = aggregated.candidates
                    .mapNotNull { it.toPreferredScraperResult(preferredSources = listOf("Bangumi")) }
                    .distinctBy { "${it.source.name}:${it.animeId}" }
                    .take(BANGUMI_RESULT_LIMIT)
                _manualMatch.value = _manualMatch.value.copy(
                    results = distinctMatches,
                    selectedResult = distinctMatches.firstOrNull(),
                    isSearching = false,
                    statusMessage = if (distinctMatches.isEmpty()) {
                        "没有找到更合适的聚合候选。"
                    } else {
                        "找到 ${distinctMatches.size} 个聚合候选。"
                    },
                )
            } catch (error: TimeoutCancellationException) {
                _manualMatch.value = _manualMatch.value.copy(
                    isSearching = false,
                    statusMessage = "搜索超时，请减少关键词或检查网络/代理。",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _manualMatch.value = _manualMatch.value.copy(
                    isSearching = false,
                    statusMessage = "搜索失败：${error.message ?: error::class.simpleName.orEmpty()}",
                )
            } finally {
                _isSyncing.value = false
            }
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
            val scraper = scraperFor(match)
            if (scraper == null) {
                _manualMatch.value = _manualMatch.value.copy(statusMessage = "${match.source.name} 刮削器不可用")
                return@launch
            }

            _isSyncing.value = true
            val applyingMessage = detailBangumiManualApplyStartedMessage(match.displayTitle())
            _actionMessage.value = applyingMessage
            _manualMatch.value = _manualMatch.value.copy(isApplying = true, statusMessage = applyingMessage)

            val localEpisodes = metadataRepository.getCachedEpisodes(current.id).getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: allEpisodesWithProgress.map { it.first }
            val indexedEntries = indexedEntriesFor(localEpisodes)
            var reloadAnimeId = current.id
            when (
                val refreshed = PerformanceLog.measureSuspendResult(
                    tag = DETAIL_PERFORMANCE_TAG,
                    operation = "detail.bangumi.apply_manual_match",
                    attributes = mapOf(
                        "cache_anime_id" to current.id,
                        "match_anime_id" to match.animeId,
                        "local_episode_count" to localEpisodes.size.toString(),
                        "indexed_entry_count" to indexedEntries.size.toString(),
                    ),
                ) {
                    val core = BangumiMetadataRefreshCore(
                        metadataRepository = metadataRepository,
                        bangumiScraper = scraper,
                    )
                    if (indexedEntries.isNotEmpty()) {
                        val updatedEntries = indexedEntries
                            .distinctBy { it.sourceId to it.path }
                            .map { it.withExternalMetadata(match) }
                        for (updatedEntry in updatedEntries) {
                            when (val updated = indexRepository.upsertEntry(updatedEntry.sourceId, updatedEntry)) {
                                is Result.Error -> return@measureSuspendResult updated
                                is Result.Success -> Unit
                            }
                        }
                        core.cacheMatchedIndexMetadata(
                            entry = updatedEntries.first(),
                            relatedEntries = updatedEntries,
                            match = match,
                        )
                    } else {
                        core.cacheMatchedMetadata(
                            cacheAnimeId = current.id,
                            match = match,
                            localEpisodes = localEpisodes,
                        )
                    }
                }
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
                is Result.Success -> reloadAnimeId = refreshed.data.cacheAnimeId
            }

            _manualMatch.value = BangumiManualMatchUiState()
            _actionMessage.value = detailBangumiMetadataUpdatedMessage()
            _isSyncing.value = false
            loadAnime(reloadAnimeId)
        }
    }

    private suspend fun indexedEntriesFor(episodes: List<Episode>): List<MediaIndexEntry> {
        val pathsBySource = episodes
            .mapNotNull { episode ->
                val sourceId = episode.id.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                val path = episode.id.substringAfter(':', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                sourceId to path
            }
            .groupBy({ it.first }, { it.second })
        return pathsBySource.flatMap { (sourceId, paths) ->
            val pathSet = paths.toSet()
            indexRepository.queryIndex(sourceId, "").getOrNull().orEmpty()
                .filter { it.path in pathSet }
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
            PerformanceLog.measureSuspendResult(
                tag = DETAIL_PERFORMANCE_TAG,
                operation = "detail.bangumi.sync",
                attributes = mapOf("anime_id" to current.id),
            ) {
                bangumiSyncEngine.syncAnime(current.id)
            }.onSuccess { summary ->
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

    private fun scraperFor(result: ScraperResult): MetadataScraper? =
        metadataScrapers.firstOrNull { it.sourceName.equals(result.source.name, ignoreCase = true) }

    private fun manualMatchQueries(state: BangumiManualMatchUiState): List<String> =
        (state.selectedCandidateTerms.toList() + state.query)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}

private const val DETAIL_PERFORMANCE_TAG = "DetailPerformance"
private const val MANUAL_MATCH_SEARCH_TIMEOUT_MS_PER_QUERY = 35_000L
private const val MANUAL_MATCH_SEARCH_TIMEOUT_MAX_MS = 90_000L

private fun manualMatchSearchTimeoutMs(queryCount: Int): Long =
    (MANUAL_MATCH_SEARCH_TIMEOUT_MS_PER_QUERY * queryCount.coerceAtLeast(1))
        .coerceAtMost(MANUAL_MATCH_SEARCH_TIMEOUT_MAX_MS)

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
