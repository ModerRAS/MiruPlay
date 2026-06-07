package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaSeason
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.activeSeasonOrDefault
import com.miruplay.tv.model.continueActionLabel
import com.miruplay.tv.model.continueEpisodeProgress
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.repository.LibraryDramaDetail
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.DramaMetadataRepository
import com.miruplay.tv.repository.LibraryDramaResolver
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.merge
import com.miruplay.tv.repository.toDramaSeasons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DramaDetailViewModel @Inject constructor(
    mediaSources: MediaSourceRepository,
    private val mediaIndexRepository: MediaIndexRepository,
    private val dramaMetadataRepository: DramaMetadataRepository,
    metadataRepository: MetadataRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val credentials: AppCredentialStore,
) : ViewModel() {
    private val resolver = LibraryDramaResolver(
        mediaSources = mediaSources,
        index = mediaIndexRepository,
        metadata = dramaMetadataRepository,
        metadataCache = metadataRepository,
    )

    private val _series = MutableStateFlow<DramaSeries?>(null)
    val series: StateFlow<DramaSeries?> = _series.asStateFlow()

    private val _seasons = MutableStateFlow<List<DramaSeason>>(emptyList())
    val seasons: StateFlow<List<DramaSeason>> = _seasons.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _episodesWithProgress = MutableStateFlow<List<Pair<DramaEpisode, ProgressRecord?>>>(emptyList())
    val episodesWithProgress: StateFlow<List<Pair<DramaEpisode, ProgressRecord?>>> = _episodesWithProgress.asStateFlow()

    private var allEpisodesWithProgress: List<Pair<DramaEpisode, ProgressRecord?>> = emptyList()

    private val _continueEpisode = MutableStateFlow<DramaEpisode?>(null)
    val continueEpisode: StateFlow<DramaEpisode?> = _continueEpisode.asStateFlow()

    private val _primaryActionEpisode = MutableStateFlow<DramaEpisode?>(null)
    val primaryActionEpisode: StateFlow<DramaEpisode?> = _primaryActionEpisode.asStateFlow()

    private val _hasPlayableEpisodes = MutableStateFlow(false)
    val hasPlayableEpisodes: StateFlow<Boolean> = _hasPlayableEpisodes.asStateFlow()

    private val _primaryActionLabel = MutableStateFlow("播放")
    val primaryActionLabel: StateFlow<String> = _primaryActionLabel.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isRefreshingMetadata = MutableStateFlow(false)
    val isRefreshingMetadata: StateFlow<Boolean> = _isRefreshingMetadata.asStateFlow()

    private val _hasTmdbTokenConfigured = MutableStateFlow(false)
    val hasTmdbTokenConfigured: StateFlow<Boolean> = _hasTmdbTokenConfigured.asStateFlow()

    private val _manualMatch = MutableStateFlow(DramaManualMatchUiState())
    val manualMatch: StateFlow<DramaManualMatchUiState> = _manualMatch.asStateFlow()

    private val _heroTitle = MutableStateFlow("")
    val heroTitle: StateFlow<String> = _heroTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSeriesId: String? = null
    private var currentDetail: LibraryDramaDetail? = null
    private var metadataMessage: String? = null

    fun loadSeries(seriesId: String, showRefreshFeedback: Boolean = false) {
        currentSeriesId = seriesId
        viewModelScope.launch {
            if (!showRefreshFeedback) {
                loadSeriesInternal(
                    seriesId = seriesId,
                    showLoading = true,
                    showRefreshFeedback = false,
                    includeOnlineMetadata = false,
                    surfacePassiveMetadataFailure = true,
                )
                if (currentSeriesId != seriesId || !isTmdbConfigured() || _series.value == null) {
                    return@launch
                }
                _isRefreshingMetadata.value = true
                try {
                    loadSeriesInternal(
                        seriesId = seriesId,
                        showLoading = false,
                        showRefreshFeedback = false,
                        includeOnlineMetadata = true,
                        surfacePassiveMetadataFailure = false,
                    )
                } finally {
                    _isRefreshingMetadata.value = false
                }
                return@launch
            }
            loadSeriesInternal(
                seriesId = seriesId,
                showLoading = false,
                showRefreshFeedback = true,
                includeOnlineMetadata = true,
                surfacePassiveMetadataFailure = true,
            )
        }
    }

    fun refreshSeries() {
        val seriesId = currentSeriesId ?: return
        if (_isRefreshingMetadata.value) return
        viewModelScope.launch {
            _isRefreshingMetadata.value = true
            try {
                loadSeriesInternal(
                    seriesId = seriesId,
                    showLoading = false,
                    showRefreshFeedback = true,
                    includeOnlineMetadata = true,
                    surfacePassiveMetadataFailure = true,
                )
            } finally {
                _isRefreshingMetadata.value = false
            }
        }
    }

    fun openManualMatch() {
        val currentSeries = _series.value ?: return
        val candidateTerms = buildManualMatchCandidates(currentSeries)
        _manualMatch.value = DramaManualMatchUiState(
            isOpen = true,
            query = "",
            candidateTerms = candidateTerms,
            selectedCandidateTerms = candidateTerms.take(1).toSet(),
        )
    }

    fun closeManualMatch() {
        _manualMatch.value = DramaManualMatchUiState()
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

    fun selectManualMatchResult(result: DramaMetadataSearchResult) {
        _manualMatch.value = _manualMatch.value.copy(
            selectedResult = result,
            statusMessage = "已选择 ${result.displayTitle()}。",
        )
    }

    fun searchManualMatches() {
        val currentState = _manualMatch.value
        if (!isTmdbConfigured()) {
            _manualMatch.value = currentState.copy(
                statusMessage = "还没配置 TMDB 令牌，暂时不能手动匹配。",
            )
            return
        }
        val queries = manualMatchQueries(currentState)
        if (queries.isEmpty()) {
            _manualMatch.value = currentState.copy(
                statusMessage = "先选一个候选标题，或者自己输入搜索词。",
            )
            return
        }

        viewModelScope.launch {
            _manualMatch.value = currentState.copy(
                isSearching = true,
                statusMessage = "正在搜索 TMDB，共 ${queries.size} 个关键词。",
            )
            val seasonHint = _selectedSeason.value.takeIf { it > 0 }
            val results = mutableListOf<DramaMetadataSearchResult>()
            var lastErrorMessage: String? = null
            for (query in queries) {
                when (val result = dramaMetadataRepository.searchSeriesCandidates(query, seasonHint = seasonHint)) {
                    is Result.Error -> lastErrorMessage = result.error.toUserMessage()
                    is Result.Success -> results += result.data
                }
            }
            val distinctResults = results
                .distinctBy { it.tmdbId }
                .take(DRAMA_MANUAL_MATCH_RESULT_LIMIT)
            _manualMatch.value = _manualMatch.value.copy(
                results = distinctResults,
                selectedResult = distinctResults.firstOrNull(),
                isSearching = false,
                statusMessage = when {
                    distinctResults.isNotEmpty() -> "找到 ${distinctResults.size} 个 TMDB 结果。"
                    !lastErrorMessage.isNullOrBlank() -> lastErrorMessage
                    else -> "没有找到更合适的 TMDB 结果。"
                },
            )
        }
    }

    fun applyManualMatch() {
        val seriesId = currentSeriesId ?: return
        val selectedResult = _manualMatch.value.selectedResult
        if (selectedResult == null) {
            _manualMatch.value = _manualMatch.value.copy(
                statusMessage = "先选中一个 TMDB 结果，再应用。",
            )
            return
        }
        if (_isRefreshingMetadata.value) return

        viewModelScope.launch {
            _isRefreshingMetadata.value = true
            _manualMatch.value = _manualMatch.value.copy(
                isApplying = true,
                statusMessage = "正在应用 ${selectedResult.displayTitle()}。",
            )
            _actionMessage.value = "正在应用手动匹配结果。"
            try {
                val seasonNumbers = _seasons.value.map { it.seasonNumber }
                when (
                    val metadataResult = dramaMetadataRepository.fetchSeriesMetadataById(
                        tmdbId = selectedResult.tmdbId,
                        seasonNumbers = seasonNumbers,
                    )
                ) {
                    is Result.Error -> {
                        val message = metadataResult.error.toUserMessage()
                        _manualMatch.value = _manualMatch.value.copy(
                            isApplying = false,
                            statusMessage = message,
                        )
                        _actionMessage.value = message
                        return@launch
                    }
                    is Result.Success -> {
                        val metadata = metadataResult.data
                        if (metadata == null) {
                            _manualMatch.value = _manualMatch.value.copy(
                                isApplying = false,
                                statusMessage = "TMDB 没返回详情，暂时不能应用。",
                            )
                            _actionMessage.value = "TMDB 没返回详情，暂时不能应用。"
                            return@launch
                        }
                        val baseDetail = currentDetail ?: resolver.loadSeriesDetail(
                            seriesId = seriesId,
                            includeOnlineMetadata = false,
                        )
                        if (baseDetail == null) {
                            _manualMatch.value = _manualMatch.value.copy(
                                isApplying = false,
                                statusMessage = "本地详情已经失效，请先返回重进。",
                            )
                            _actionMessage.value = "本地详情已经失效，请先返回重进。"
                            return@launch
                        }
                        val resolvedDetail = baseDetail.withResolvedMetadata(metadata)
                        val persistMessage = persistResolvedMetadata(
                            detail = resolvedDetail,
                            persistIndexEntries = true,
                        )
                        _manualMatch.value = DramaManualMatchUiState()
                        publishDetailState(
                            detail = resolvedDetail,
                            showRefreshFeedback = true,
                            surfacePassiveMetadataFailure = false,
                            tmdbConfigured = true,
                            persistMessage = persistMessage,
                            successMessageOverride = "已应用手动匹配，电视剧信息已更新。",
                        )
                    }
                }
            } finally {
                _isRefreshingMetadata.value = false
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
        val episodesForSeason = allEpisodesWithProgress.filter { it.first.seasonNumber == seasonNumber }
        _episodesWithProgress.value = episodesForSeason
        _actionMessage.value = if (episodesForSeason.isEmpty()) {
            "第 $seasonNumber 季还没有可播放剧集。"
        } else {
            "已切换到第 $seasonNumber 季，共 ${episodesForSeason.size} 集。"
        }
    }

    private fun resolvePrimaryActionEpisode(
        continuePlaybackEpisode: Episode?,
        fallbackPlaybackEpisode: Episode?,
    ): DramaEpisode? {
        val targetId = continuePlaybackEpisode?.id ?: fallbackPlaybackEpisode?.id ?: return null
        return allEpisodesWithProgress.firstOrNull { it.first.id == targetId }?.first
    }

    private suspend fun loadSeriesInternal(
        seriesId: String,
        showLoading: Boolean,
        showRefreshFeedback: Boolean,
        includeOnlineMetadata: Boolean,
        surfacePassiveMetadataFailure: Boolean,
    ) {
        if (showLoading) {
            _isLoading.value = true
        }
        if (!showRefreshFeedback) {
            _actionMessage.value = null
        }
        val detail = resolver.loadSeriesDetail(
            seriesId = seriesId,
            includeOnlineMetadata = includeOnlineMetadata,
        )
        val tmdbConfigured = credentials.tmdbAccessToken?.trim().isNullOrBlank().not()
        val persistMessage = persistResolvedMetadata(
            detail = detail,
            persistIndexEntries = showRefreshFeedback,
        )
        publishDetailState(
            detail = detail,
            showRefreshFeedback = showRefreshFeedback,
            surfacePassiveMetadataFailure = surfacePassiveMetadataFailure,
            tmdbConfigured = tmdbConfigured,
            persistMessage = persistMessage,
        )
        _isLoading.value = false
    }

    private fun isTmdbConfigured(): Boolean =
        credentials.tmdbAccessToken?.trim().isNullOrBlank().not()

    private suspend fun persistResolvedMetadata(
        detail: LibraryDramaDetail?,
        persistIndexEntries: Boolean,
    ): String? {
        val resolvedMetadata = detail?.resolvedMetadata ?: return null
        resolver.cacheSeriesMetadata(detail.series.id, detail.series).onError { error ->
            return "电视剧信息已刷新，但保存系列缓存失败：${error.toUserMessage()}"
        }
        if (!persistIndexEntries) {
            return null
        }
        val tmdbId = resolvedMetadata.series.tmdbId ?: return null
        val updatedEntries = detail.indexEntries.map { entry ->
            entry.withResolvedDramaMetadata(
                metadata = resolvedMetadata,
                tmdbId = tmdbId,
            )
        }
        for (entry in updatedEntries) {
            mediaIndexRepository.upsertEntry(detail.sourceId, entry).onError { error ->
                return "电视剧信息已刷新，但保存到本地索引失败：${error.toUserMessage()}"
            }
        }
        currentDetail = detail.copy(indexEntries = updatedEntries)
        return null
    }

    private suspend fun publishDetailState(
        detail: LibraryDramaDetail?,
        showRefreshFeedback: Boolean,
        surfacePassiveMetadataFailure: Boolean,
        tmdbConfigured: Boolean,
        persistMessage: String?,
        successMessageOverride: String? = null,
    ) {
        currentDetail = detail
        _series.value = detail?.series
        _heroTitle.value = detail?.series?.displayTitle().orEmpty()
        metadataMessage = detail?.metadataMessage
        val episodes = detail?.episodes.orEmpty()
        val playbackEpisodes = episodes.map { it.toPlaybackEpisode() }
        _seasons.value = episodes.toDramaSeasons()
        val defaultSeason = playbackEpisodes.activeSeasonOrDefault(_selectedSeason.value)
        _selectedSeason.value = defaultSeason
        allEpisodesWithProgress = episodes.map { episode ->
            episode to progressRepository.getProgress(episode.id).getOrNull()
        }
        val playbackPairs = allEpisodesWithProgress.toPlaybackEpisodePairs()
        val continuePlaybackEpisode = playbackPairs.continueProgressEpisode()
        _continueEpisode.value = continuePlaybackEpisode?.let { target ->
            allEpisodesWithProgress.firstOrNull { it.first.id == target.id }?.first
        }
        _primaryActionEpisode.value = resolvePrimaryActionEpisode(
            continuePlaybackEpisode = continuePlaybackEpisode,
            fallbackPlaybackEpisode = playbackPairs.firstOrNull()?.first,
        )
        _hasPlayableEpisodes.value = playbackPairs.isNotEmpty()
        _primaryActionLabel.value = playbackPairs.continueActionLabel()
        val episodesForSeason = allEpisodesWithProgress.filter { it.first.seasonNumber == defaultSeason }
        _episodesWithProgress.value = episodesForSeason
        _hasTmdbTokenConfigured.value = tmdbConfigured
        _actionMessage.value = when {
            showRefreshFeedback && !tmdbConfigured ->
                "还没配置 TMDB 令牌，暂时只能显示本地信息。"
            showRefreshFeedback && !metadataMessage.isNullOrBlank() -> "刷新电视剧信息失败：$metadataMessage"
            showRefreshFeedback && !persistMessage.isNullOrBlank() -> persistMessage
            showRefreshFeedback && !successMessageOverride.isNullOrBlank() -> successMessageOverride
            showRefreshFeedback && detail?.series?.tmdbId != null -> "电视剧信息已刷新。"
            showRefreshFeedback -> "未获取到电视剧在线信息。"
            detail == null -> null
            episodesForSeason.isEmpty() -> "当前详情还没有可播放剧集。"
            surfacePassiveMetadataFailure && !metadataMessage.isNullOrBlank() -> metadataMessage
            else -> null
        }
    }

    private fun buildManualMatchCandidates(series: DramaSeries): List<String> =
        buildList {
            add(series.displayTitle())
            add(series.originalTitle)
            addAll(
                currentDetail?.indexEntries
                    .orEmpty()
                    .mapNotNull { it.animeName?.trim() }
                    .filter { it.isNotBlank() },
            )
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun manualMatchQueries(state: DramaManualMatchUiState): List<String> =
        (state.selectedCandidateTerms.toList() + state.query)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}

data class DramaManualMatchUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val candidateTerms: List<String> = emptyList(),
    val selectedCandidateTerms: Set<String> = emptySet(),
    val results: List<DramaMetadataSearchResult> = emptyList(),
    val selectedResult: DramaMetadataSearchResult? = null,
    val isSearching: Boolean = false,
    val isApplying: Boolean = false,
    val statusMessage: String? = null,
)

private const val DRAMA_MANUAL_MATCH_RESULT_LIMIT = 12

private fun LibraryDramaDetail.withResolvedMetadata(
    metadata: DramaSeriesMetadata,
): LibraryDramaDetail {
    val mergedEpisodes = episodes.merge(metadata)
    val mergedSeasonCount = mergedEpisodes
        .map { it.seasonNumber }
        .distinct()
        .ifEmpty { listOf(1) }
        .size
    return copy(
        series = series.merge(metadata.series).copy(
            episodeCount = mergedEpisodes.size,
            seasonCount = mergedSeasonCount,
        ),
        episodes = mergedEpisodes,
        metadataMessage = null,
        resolvedMetadata = metadata,
    )
}

private fun MediaIndexEntry.withResolvedDramaMetadata(
    metadata: com.miruplay.tv.model.DramaSeriesMetadata,
    tmdbId: Int,
): MediaIndexEntry {
    val metadataEpisode = metadata.seasons
        .firstOrNull { it.seasonNumber == (seasonNumber ?: 1) }
        ?.episodes
        ?.firstOrNull { it.episodeNumber == (episodeNumber ?: 1) }
    return copy(
        episodeTitle = metadataEpisode?.title?.takeIf { it.isNotBlank() } ?: episodeTitle,
        plot = metadata.series.summary.takeIf { it.isNotBlank() } ?: plot,
        metadataSource = "TMDB",
        metadataId = tmdbId.toString(),
        metadataTitle = metadata.series.displayTitle().ifBlank { metadataTitle },
        scrapeStatus = com.miruplay.tv.repository.MediaScrapeStatus.SCRAPED,
        scrapeMessage = null,
        scrapedAt = System.currentTimeMillis(),
    )
}

private fun List<Pair<DramaEpisode, ProgressRecord?>>.toPlaybackEpisodePairs() =
    map { (episode, progress) ->
        episode.toPlaybackEpisode() to progress
    }

private fun List<Pair<Episode, ProgressRecord?>>.continueProgressEpisode(): Episode? =
    filter { (episode, progress) -> episode.continueEpisodeProgress(progress) }
        .maxByOrNull { (_, progress) -> progress?.lastWatched ?: 0L }
        ?.first

private fun DramaEpisode.toPlaybackEpisode() =
    com.miruplay.tv.model.Episode(
        id = id,
        animeId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = title,
        filePath = filePath,
        fileName = fileName,
    )
