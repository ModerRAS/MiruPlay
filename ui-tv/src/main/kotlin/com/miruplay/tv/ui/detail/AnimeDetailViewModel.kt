package com.miruplay.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.Season
import com.miruplay.tv.model.mergeAnimeGroupForDisplay
import com.miruplay.tv.model.sameAnimeGroupFor
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.scraper.MetadataScraper
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
    private val scanPreferences: ScanPreferencesManager,
    private val metadataScrapers: Set<@JvmSuppressWildcards MetadataScraper>
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

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun loadAnime(animeId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val cached = metadataRepository.getCachedMetadata(animeId).getOrNull()
            val relatedAnime = if (scanPreferences.mergeSameAnimeEnabled && cached != null) {
                loadRelatedAnime(cached)
            } else {
                listOfNotNull(cached)
            }.ifEmpty {
                listOfNotNull(cached)
            }

            val episodeAnimeIds = relatedAnime.map { it.id }.ifEmpty { listOf(animeId) }
            val epList = episodeAnimeIds
                .flatMap { id -> metadataRepository.getCachedEpisodes(id).getOrNull().orEmpty() }
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<Episode>(
                        { it.seasonNumber },
                        { it.episodeNumber },
                        { it.filePath }
                    )
                )

            if (cached != null) {
                val displayAnime = if (relatedAnime.size > 1) {
                    relatedAnime.mergeAnimeGroupForDisplay().copy(id = animeId)
                } else {
                    cached
                }
                _anime.value = displayAnime.copy(
                    episodeCount = maxOf(
                        displayAnime.episodeCount,
                        epList.distinctBy { it.seasonNumber to it.episodeNumber }.size
                    )
                )
            }

            updateEpisodes(epList)
            _isLoading.value = false
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeason.value = seasonNumber
        // Filter from full list by season
        _episodesWithProgress.value = allEpisodesWithProgress.filter { it.first.seasonNumber == seasonNumber }
    }

    private suspend fun loadRelatedAnime(anchor: Anime): List<Anime> {
        val sources = mediaRepository.getSources().getOrNull().orEmpty()
        val candidates = mutableListOf(anchor)
        for (source in sources) {
            val names = indexRepository.getAnimeInIndex(source.id).getOrNull().orEmpty()
            for (name in names) {
                val cached = metadataRepository.getCachedMetadata(name).getOrNull() ?: continue
                candidates += cached
            }
        }
        return candidates
            .distinctBy { it.id }
            .sameAnimeGroupFor(anchor)
    }

    private suspend fun updateEpisodes(epList: List<Episode>) {
        val seasonMap = epList.groupBy { it.seasonNumber }
        _seasons.value = seasonMap.map { (seasonNum, eps) ->
            Season(
                seasonNumber = seasonNum,
                title = "Season $seasonNum",
                episodes = eps,
                episodeCount = eps.size
            )
        }

        val selectedSeason = if (seasonMap.containsKey(_selectedSeason.value)) {
            _selectedSeason.value
        } else {
            seasonMap.keys.minOrNull() ?: 1
        }
        _selectedSeason.value = selectedSeason

        val withProgress = epList.map { episode ->
            val progress = progressRepository.getProgress(episode.id).getOrNull()
            Pair(episode, progress)
        }
        allEpisodesWithProgress = withProgress
        _episodesWithProgress.value = withProgress.filter { it.first.seasonNumber == selectedSeason }
    }

    fun rescrapeMetadata() {
        val current = _anime.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _actionMessage.value = "正在重新匹配 Bangumi..."
            val bangumi = metadataScrapers.firstOrNull { it.sourceName.equals("Bangumi", ignoreCase = true) }
            if (bangumi == null) {
                _actionMessage.value = "Bangumi 刮削器不可用"
                _isSyncing.value = false
                return@launch
            }

            val candidates = listOfNotNull(current.titleCn, current.title, current.id)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            var match = bangumi.searchByAlias(current.id, candidates).getOrNull()
            if (match == null) {
                for (candidate in candidates) {
                    match = bangumi.searchAnime(candidate).getOrNull()
                        ?.firstOrNull { it.confidence >= 0.62f }
                    if (match != null) break
                }
            }

            if (match == null) {
                _actionMessage.value = "没有找到可靠的 Bangumi 匹配"
                _isSyncing.value = false
                return@launch
            }

            val details = bangumi.getAnimeDetails(match.animeId).getOrNull()
            if (details == null) {
                _actionMessage.value = "Bangumi 详情获取失败"
                _isSyncing.value = false
                return@launch
            }

            val remoteEpisodes = bangumi.getEpisodes(match.animeId).getOrNull()
                .orEmpty()
                .associateBy { it.episodeNumber }
            val localEpisodes = metadataRepository.getCachedEpisodes(current.id).getOrNull().orEmpty()
            val mergedEpisodes = localEpisodes.map { episode ->
                val remote = remoteEpisodes[episode.episodeNumber]
                if (remote == null) {
                    episode
                } else {
                    episode.copy(
                        title = remote.title ?: episode.title,
                        duration = episode.duration.takeIf { it > 0 } ?: remote.durationMs,
                        bangumiEpisodeId = remote.bangumiEpisodeId
                    )
                }
            }

            metadataRepository.cacheMetadata(
                details.copy(
                    id = current.id,
                    episodeCount = maxOf(details.episodeCount, mergedEpisodes.size)
                )
            )
            metadataRepository.cacheEpisodes(current.id, mergedEpisodes)
            _actionMessage.value = "Bangumi 元数据已更新"
            _isSyncing.value = false
            loadAnime(current.id)
        }
    }

    fun syncBangumi() {
        val current = _anime.value ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            _actionMessage.value = "正在同步 Bangumi..."
            bangumiSyncEngine.syncAnime(current.id).onSuccess { summary ->
                _actionMessage.value = "同步完成：上传 ${summary.pushedEpisodes} 集，拉取 ${summary.pulledEpisodes} 集"
                loadAnime(current.id)
            }.onError { error ->
                _actionMessage.value = error.toUserMessage()
            }
            _isSyncing.value = false
        }
    }
}
