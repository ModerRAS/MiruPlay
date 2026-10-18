package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.UiMessage
import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.asError
import com.miruplay.tv.core.common.asInfo
import com.miruplay.tv.core.common.uiText
import com.miruplay.tv.model.playbackPlayLabel
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaSeason
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchIntent
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.activeSeasonOrDefault
import com.miruplay.tv.model.boundMetadataProviderRef
import com.miruplay.tv.model.continueActionLabel
import com.miruplay.tv.model.continueEpisodeProgress
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.providerDisplayLabel
import com.miruplay.tv.model.providerStableKey
import com.miruplay.tv.model.toDramaSearchResult
import com.miruplay.tv.repository.LibraryDramaDetail
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.DramaMetadataRepository
import com.miruplay.tv.repository.DramaMetadataSearchAggregator
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
    private val dramaSearchAggregator: DramaMetadataSearchAggregator,
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

    private val _primaryActionLabel = MutableStateFlow(playbackPlayLabel())
    val primaryActionLabel: StateFlow<UiText> = _primaryActionLabel.asStateFlow()

    private val _actionMessage = MutableStateFlow<UiMessage?>(null)
    val actionMessage: StateFlow<UiMessage?> = _actionMessage.asStateFlow()

    private val _isRefreshingMetadata = MutableStateFlow(false)
    val isRefreshingMetadata: StateFlow<Boolean> = _isRefreshingMetadata.asStateFlow()

    private val _hasTmdbTokenConfigured = MutableStateFlow(false)
    val hasTmdbTokenConfigured: StateFlow<Boolean> = _hasTmdbTokenConfigured.asStateFlow()

    private val _canRefreshBoundMetadata = MutableStateFlow(false)
    val canRefreshBoundMetadata: StateFlow<Boolean> = _canRefreshBoundMetadata.asStateFlow()

    private val _manualMatch = MutableStateFlow(DramaManualMatchUiState())
    val manualMatch: StateFlow<DramaManualMatchUiState> = _manualMatch.asStateFlow()

    private val _heroTitle = MutableStateFlow("")
    val heroTitle: StateFlow<String> = _heroTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSeriesId: String? = null
    private var currentDetail: LibraryDramaDetail? = null
    private var metadataMessage: UiText? = null

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
                if (currentSeriesId != seriesId || !canRefreshMetadataOnline(currentDetail) || _series.value == null) {
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
            statusMessage = UiText("drama_selected_title", listOf(result.displayTitle()), "已选择 ${result.displayTitle()}。").asInfo(),
        )
    }

    fun searchManualMatches() {
        val currentState = _manualMatch.value
        val queries = manualMatchQueries(currentState)
        if (queries.isEmpty()) {
            _manualMatch.value = currentState.copy(
                statusMessage = uiText("drama_match_prompt", "先选一个候选标题，或者自己输入搜索词。").asError(),
            )
            return
        }

        viewModelScope.launch {
            _manualMatch.value = currentState.copy(
                isSearching = true,
                statusMessage = UiText("drama_searching_online", listOf(queries.size), "正在搜索在线候选，共 ${queries.size} 个关键词。").asError(),
            )
            val currentSeries = _series.value
            val currentEpisodes = currentDetail?.episodes.orEmpty()
            val aggregated = dramaSearchAggregator.search(
                MetadataSearchContext(
                    contentMode = MediaContentMode.DRAMA,
                    intent = MetadataSearchIntent.MANUAL_MATCH,
                    title = currentSeries?.title.orEmpty(),
                    localizedTitle = currentSeries?.displayTitle().orEmpty(),
                    originalTitle = currentSeries?.originalTitle.orEmpty(),
                    aliases = currentState.selectedCandidateTerms.toList(),
                    filePathSamples = currentEpisodes.map(DramaEpisode::filePath),
                    manualQuery = currentState.query,
                    metadataTitle = currentSeries?.displayTitle(),
                    boundProviderRef = currentSeries?.boundMetadataProviderRef(),
                    seasonHint = _selectedSeason.value.takeIf { it > 0 },
                    episodeCountHint = currentEpisodes.size.takeIf { it > 0 },
                    seasonCountHint = _seasons.value.size.takeIf { it > 0 },
                ),
            )
            val distinctResults = aggregated.candidates
                .mapNotNull { it.toDramaSearchResult() }
                .distinctBy(DramaMetadataSearchResult::providerStableKey)
                .take(DRAMA_MANUAL_MATCH_RESULT_LIMIT)
            _manualMatch.value = _manualMatch.value.copy(
                results = distinctResults,
                selectedResult = distinctResults.firstOrNull(),
                isSearching = false,
                statusMessage = when {
                    distinctResults.isNotEmpty() -> UiText("drama_found_candidates", listOf(distinctResults.size), "找到 ${distinctResults.size} 个聚合候选。").asInfo()
                    !canRefreshMetadataOnline(currentDetail) -> uiText("drama_no_refresh_candidates", "没有找到更合适的在线候选；当前也没有可直接刷新的在线详情源。如果还没绑定在线来源，也可以配置 TMDB Token 来启用按标题直接刷新。").asError()
                    else -> uiText("drama_no_online_candidates", "没有找到更合适的在线候选。").asError()
                },
            )
        }
    }

    fun applyManualMatch() {
        val seriesId = currentSeriesId ?: return
        val selectedResult = _manualMatch.value.selectedResult
        if (selectedResult == null) {
            _manualMatch.value = _manualMatch.value.copy(
                statusMessage = uiText("drama_select_to_apply", "先选中一个在线结果，再应用。").asError(),
            )
            return
        }
        if (_isRefreshingMetadata.value) return

        viewModelScope.launch {
            _isRefreshingMetadata.value = true
            _manualMatch.value = _manualMatch.value.copy(
                isApplying = true,
                statusMessage = UiText("drama_applying_title", listOf(selectedResult.displayTitle()), "正在应用 ${selectedResult.displayTitle()}。").asError(),
            )
            _actionMessage.value = uiText("drama_applying_result", "正在应用手动匹配结果。").asError()
            try {
                val seasonNumbers = _seasons.value.map { it.seasonNumber }
                when (
                    val metadataResult = dramaMetadataRepository.fetchSeriesMetadataByProviderRef(
                        providerRef = selectedResult.providerRef,
                        seasonNumbers = seasonNumbers,
                    )
                ) {
                    is Result.Error -> {
                        val message = metadataResult.error.toUiText().asError()
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
                            val message = UiText("drama_provider_no_detail", listOf(selectedResult.providerDisplayLabel()), "${selectedResult.providerDisplayLabel()} 当前没有返回可应用的详情，暂时不能应用。").asError()
                            _manualMatch.value = _manualMatch.value.copy(
                                isApplying = false,
                                statusMessage = message,
                            )
                            _actionMessage.value = message
                            return@launch
                        }
                        val baseDetail = currentDetail ?: resolver.loadSeriesDetail(
                            seriesId = seriesId,
                            includeOnlineMetadata = false,
                        )
                        if (baseDetail == null) {
                            _manualMatch.value = _manualMatch.value.copy(
                                isApplying = false,
                                statusMessage = uiText("drama_detail_expired", "本地详情已经失效，请先返回重进。").asError(),
                            )
                            _actionMessage.value = uiText("drama_detail_expired", "本地详情已经失效，请先返回重进。").asError()
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
                            tmdbConfigured = isTmdbConfigured(),
                            canRefreshMetadataOnline = canRefreshMetadataOnline(resolvedDetail),
                            persistMessage = persistMessage,
                            successMessageOverride = uiText("drama_apply_success", "已应用手动匹配，电视剧信息已更新。"),
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
            UiText("drama_season_empty", listOf(seasonNumber), "第 $seasonNumber 季还没有可播放剧集。").asError()
        } else {
            val count = episodesForSeason.size
            UiText("drama_season_switched", listOf(seasonNumber, count), "已切换到第 $seasonNumber 季，共 $count 集。").asInfo()
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
        val tmdbConfigured = isTmdbConfigured()
        val canRefreshMetadataOnline = canRefreshMetadataOnline(detail)
        val persistMessage = persistResolvedMetadata(
            detail = detail,
            persistIndexEntries = showRefreshFeedback,
        )
        publishDetailState(
            detail = detail,
            showRefreshFeedback = showRefreshFeedback,
            surfacePassiveMetadataFailure = surfacePassiveMetadataFailure,
            tmdbConfigured = tmdbConfigured,
            canRefreshMetadataOnline = canRefreshMetadataOnline,
            persistMessage = persistMessage,
        )
        _isLoading.value = false
    }

    private fun isTmdbConfigured(): Boolean =
        credentials.tmdbAccessToken?.trim().isNullOrBlank().not()

    private suspend fun persistResolvedMetadata(
        detail: LibraryDramaDetail?,
        persistIndexEntries: Boolean,
    ): UiText? {
        val resolvedMetadata = detail?.resolvedMetadata ?: return null
        resolver.cacheSeriesMetadata(detail.series.id, detail.series).onError { error ->
            return UiText("drama_cache_save_failed", listOf(error.toUiText()), "电视剧信息已刷新，但保存系列缓存失败：${error.toUiText().fallback}")
        }
        if (!persistIndexEntries) {
            return null
        }
        val providerRef = resolvedMetadata.series.boundMetadataProviderRef() ?: return null
        val updatedEntries = detail.indexEntries.map { entry ->
            entry.withResolvedDramaMetadata(
                metadata = resolvedMetadata,
                providerRef = providerRef,
            )
        }
        for (entry in updatedEntries) {
            mediaIndexRepository.upsertEntry(detail.sourceId, entry).onError { error ->
                return UiText("drama_index_save_failed", listOf(error.toUiText()), "电视剧信息已刷新，但保存到本地索引失败：${error.toUiText().fallback}")
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
        canRefreshMetadataOnline: Boolean,
        persistMessage: UiText?,
        successMessageOverride: UiText? = null,
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
        _canRefreshBoundMetadata.value = detail?.series
            ?.boundMetadataProviderRef()
            ?.let(dramaMetadataRepository::canFetchMetadataByProviderRef)
            ?: false
        val failedDetailMessage = metadataMessage
        _actionMessage.value = when {
            showRefreshFeedback && failedDetailMessage != null -> UiText(
                "drama_refresh_failed_detail",
                listOf(failedDetailMessage),
                "刷新电视剧信息失败：${failedDetailMessage.fallback}",
            ).asError()
            showRefreshFeedback && !canRefreshMetadataOnline ->
                uiText(
                    "drama_no_refresh_source",
                    "当前没有可直接刷新的在线详情源；在线手动匹配仍可继续使用。如果还没绑定在线来源，也可以配置 TMDB Token 来启用按标题直接刷新。",
                ).asError()
            showRefreshFeedback && persistMessage != null -> persistMessage.asInfo()
            showRefreshFeedback && successMessageOverride != null -> successMessageOverride.asInfo()
            showRefreshFeedback && detail?.resolvedMetadata != null ->
                uiText("drama_refreshed", "电视剧信息已刷新。").asInfo()
            showRefreshFeedback -> uiText("drama_no_online_info", "未获取到电视剧在线信息。").asError()
            detail == null -> null
            episodesForSeason.isEmpty() -> uiText("drama_no_episodes", "当前详情还没有可播放剧集。").asError()
            surfacePassiveMetadataFailure && metadataMessage != null -> metadataMessage?.asError()
            else -> null
        }
    }

    private fun canRefreshMetadataOnline(detail: LibraryDramaDetail?): Boolean {
        val boundProviderRef = detail?.series?.boundMetadataProviderRef()
        return when {
            boundProviderRef != null && dramaMetadataRepository.canFetchMetadataByProviderRef(boundProviderRef) -> true
            dramaMetadataRepository.canFetchSeriesMetadataByTitle() -> true
            else -> false
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
    val statusMessage: UiMessage? = null,
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
    providerRef: MetadataProviderRef,
): MediaIndexEntry {
    val metadataEpisode = metadata.seasons
        .firstOrNull { it.seasonNumber == (seasonNumber ?: 1) }
        ?.episodes
        ?.firstOrNull { it.episodeNumber == (episodeNumber ?: 1) }
    return copy(
        episodeTitle = metadataEpisode?.title?.takeIf { it.isNotBlank() } ?: episodeTitle,
        plot = metadata.series.summary.takeIf { it.isNotBlank() } ?: plot,
        metadataSource = providerRef.source,
        metadataId = providerRef.id,
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
