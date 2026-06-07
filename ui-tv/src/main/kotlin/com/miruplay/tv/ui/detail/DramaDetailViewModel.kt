package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaSeason
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.activeSeasonOrDefault
import com.miruplay.tv.model.continueActionLabel
import com.miruplay.tv.model.continueEpisodeProgress
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.repository.DramaMetadataRepository
import com.miruplay.tv.repository.LibraryDramaResolver
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
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
    mediaIndexRepository: MediaIndexRepository,
    dramaMetadataRepository: DramaMetadataRepository,
    private val progressRepository: PlaybackProgressRepository,
) : ViewModel() {
    private val resolver = LibraryDramaResolver(
        mediaSources = mediaSources,
        index = mediaIndexRepository,
        metadata = dramaMetadataRepository,
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

    private val _heroTitle = MutableStateFlow("")
    val heroTitle: StateFlow<String> = _heroTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSeriesId: String? = null
    private var metadataMessage: String? = null

    fun loadSeries(seriesId: String, showRefreshFeedback: Boolean = false) {
        currentSeriesId = seriesId
        viewModelScope.launch {
            _isLoading.value = true
            if (!showRefreshFeedback) {
                _actionMessage.value = null
            }
            val detail = resolver.loadSeriesDetail(seriesId)
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
            _actionMessage.value = when {
                showRefreshFeedback && !metadataMessage.isNullOrBlank() -> "刷新电视剧信息失败：$metadataMessage"
                showRefreshFeedback && detail?.series?.tmdbId != null -> "电视剧信息已刷新。"
                showRefreshFeedback -> "未获取到电视剧在线信息。"
                detail == null -> null
                episodesForSeason.isEmpty() -> "当前详情还没有可播放剧集。"
                !metadataMessage.isNullOrBlank() -> metadataMessage
                else -> null
            }
            _isLoading.value = false
        }
    }

    fun refreshSeries() {
        currentSeriesId?.let { loadSeries(it, showRefreshFeedback = true) }
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
