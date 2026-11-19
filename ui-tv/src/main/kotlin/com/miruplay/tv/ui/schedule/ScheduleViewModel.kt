package com.miruplay.tv.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.data.schedule.ScheduleLoadResult
import com.miruplay.tv.data.schedule.ScheduleRepository
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScheduleAiring
import com.miruplay.tv.model.ScheduleDay
import com.miruplay.tv.model.ScheduleFetchError
import com.miruplay.tv.model.ScheduleTimeConversion
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.indexAnimeByBangumiId
import com.miruplay.tv.model.matchedAnime
import com.miruplay.tv.repository.MetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ScheduleRowUi(
    val airing: ScheduleAiring,
    val matchedAnimeId: String?,
    val matchedAnimeTitle: String?,
) {
    val isMatched: Boolean
        get() = matchedAnimeId != null
}

data class ScheduleDayUi(
    val weekday: Int,
    val isToday: Boolean,
    val rows: List<ScheduleRowUi>,
)

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data object Unconfigured : ScheduleUiState
    data object Empty : ScheduleUiState
    data class Error(val error: ScheduleFetchError) : ScheduleUiState
    data class Content(
        val season: String?,
        val seasonStart: String?,
        val seasonEnd: String?,
        val generatedAtLocal: String?,
        val localTimezone: String,
        val days: List<ScheduleDayUi>,
        val fromCache: Boolean,
        val stale: Boolean,
        val matchedCount: Int,
        val totalCount: Int,
        val skippedItemCount: Int,
    ) : ScheduleUiState
}

/**
 * 时间表页状态机：
 * - 未配置地址 → Unconfigured（引导去设置）
 * - 404 / 无可解析条目 → Empty
 * - 请求失败且无缓存 → Error（可重试）
 * - 成功 → Content（已把 JST 周几/时刻换算成本地时区并按本地时间升序分组）
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val metadataRepository: MetadataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun refresh() {
        load(forceRefresh = true)
    }

    fun retry() {
        load(forceRefresh = false)
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.value = ScheduleUiState.Loading
            when (val result = scheduleRepository.load(forceRefresh = forceRefresh)) {
                ScheduleLoadResult.NotConfigured -> _state.value = ScheduleUiState.Unconfigured
                ScheduleLoadResult.Empty -> _state.value = ScheduleUiState.Empty
                is ScheduleLoadResult.Failure -> _state.value = ScheduleUiState.Error(result.error)
                is ScheduleLoadResult.Success -> _state.value = buildContent(result)
            }
        }
    }

    private suspend fun buildContent(
        result: ScheduleLoadResult.Success,
    ): ScheduleUiState {
        val localZone = ZoneId.systemDefault()
        val weekly = withContext(Dispatchers.Default) {
            ScheduleTimeConversion.buildWeeklySchedule(result.snapshot, localZone)
        }
        val animeIndex = metadataRepository.getCachedAnimeWithBangumiId()
            .getOrNull()
            .orEmpty()
            .indexAnimeByBangumiId()
        if (weekly.totalCount == 0) {
            return ScheduleUiState.Empty
        }

        val today = LocalDate.now(localZone).dayOfWeek.value
        val days = weekly.days.map { day -> buildDay(day, animeIndex, today) }
        val matchedCount = days.sumOf { day -> day.rows.count { it.isMatched } }
        return ScheduleUiState.Content(
            season = weekly.season,
            seasonStart = weekly.seasonStart,
            seasonEnd = weekly.seasonEnd,
            generatedAtLocal = formatGeneratedAt(weekly.generatedAt, localZone),
            localTimezone = weekly.localTimezone,
            days = days,
            fromCache = result.fromCache,
            stale = result.stale,
            matchedCount = matchedCount,
            totalCount = weekly.totalCount,
            skippedItemCount = weekly.skippedItemCount,
        )
    }

    private fun buildDay(
        day: ScheduleDay,
        animeIndex: Map<Int, Anime>,
        today: Int,
    ): ScheduleDayUi = ScheduleDayUi(
        weekday = day.weekday,
        isToday = day.weekday == today,
        rows = day.airings.map { airing ->
            val anime = airing.matchedAnime(animeIndex)
            ScheduleRowUi(
                airing = airing,
                matchedAnimeId = anime?.id,
                matchedAnimeTitle = anime?.displayTitle(),
            )
        },
    )

    private fun formatGeneratedAt(raw: String?, localZone: ZoneId): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val instant = runCatching { Instant.parse(text) }.getOrNull() ?: return null
        return GENERATED_AT_FORMATTER.format(instant.atZone(localZone))
    }

    companion object {
        private val GENERATED_AT_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM-dd HH:mm")
    }
}
