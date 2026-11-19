package com.miruplay.tv.model

import kotlinx.serialization.Serializable

/**
 * 新番时间表（AirRadar）API 的线格式。
 *
 * 数据源地址只存在于用户运行时配置里，仓库不保存任何默认地址；客户端在用户配置的
 * base URL 上拼接 `/api/schedule/latest`。所有字段都给默认值，服务端新增字段不会
 * 让旧客户端解析失败（客户端同时开启 ignoreUnknownKeys）。
 */
@Serializable
data class ScheduleSnapshot(
    val schemaVersion: Int = 1,
    val generatedAt: String? = null,
    val season: String? = null,
    val seasonStart: String? = null,
    val seasonEnd: String? = null,
    val timezone: String? = null,
    val itemCount: Int = 0,
    val items: List<ScheduleItem> = emptyList(),
)

@Serializable
data class ScheduleItem(
    val subjectId: Int = 0,
    val name: String = "",
    val nameCn: String? = null,
    val airDate: String? = null,
    val image: String? = null,
    /** JST 周几：1=周一 … 7=周日。 */
    val weekday: Int = 0,
    /** JST 时刻，HH:mm。 */
    val time: String = "",
    val timezone: String? = null,
    val confidence: String? = null,
    val sources: List<ScheduleSource> = emptyList(),
    val updatedAt: String? = null,
) {
    /** 优先中文名，缺失时回退日文原名。 */
    val displayName: String
        get() = nameCn?.trim()?.takeIf { it.isNotEmpty() } ?: name.trim()
}

@Serializable
data class ScheduleSource(
    val url: String = "",
    val domain: String = "",
    val provider: String = "",
)

/** 时间表请求失败原因；UI 按类型给可重试文案。 */
sealed interface ScheduleFetchError {
    data object InvalidUrl : ScheduleFetchError
    data object Network : ScheduleFetchError
    data object Timeout : ScheduleFetchError
    data class Http(val code: Int) : ScheduleFetchError
    data object InvalidData : ScheduleFetchError
}

enum class ScheduleConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN;

    companion object {
        fun fromWire(raw: String?): ScheduleConfidence =
            when (raw?.trim()?.lowercase()) {
                "high" -> HIGH
                "medium" -> MEDIUM
                "low" -> LOW
                else -> UNKNOWN
            }
    }
}

/**
 * 一行已换算到设备本地时区的播出信息。
 *
 * [localWeekday] / [localMinutes] 是设备时区里的周几与分钟数；[crossDayOffset] 是本地
 * 日期相对 JST 播出日期的天数差（0 同日，+1 跨到次日，-1 跨到前一日），用于 UI 标注。
 */
data class ScheduleAiring(
    val subjectId: Int,
    val displayName: String,
    val originalName: String,
    val imageUrl: String?,
    val confidence: ScheduleConfidence,
    val sourceCount: Int,
    val airDate: String?,
    val jstWeekday: Int,
    val jstTimeLabel: String,
    val localWeekday: Int,
    val localMinutes: Int,
    val localTimeLabel: String,
    val crossDayOffset: Int,
) {
    /** 本地时间升序排序键：周几 * 1440 + 分钟。 */
    val localSortKey: Int
        get() = localWeekday.coerceIn(1, 7) * MINUTES_PER_DAY + localMinutes.coerceIn(0, MINUTES_PER_DAY - 1)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** 按设备本地周几分组后的一天；[weekday] 使用 ISO 编号 1=周一 … 7=周日。 */
data class ScheduleDay(
    val weekday: Int,
    val airings: List<ScheduleAiring>,
) {
    val isEmpty: Boolean
        get() = airings.isEmpty()

    companion object {
        fun emptyDays(): List<ScheduleDay> = (1..7).map { ScheduleDay(it, emptyList()) }
    }
}

/** 已按设备本地时区整理好的整周时间表。 */
data class WeeklySchedule(
    val season: String?,
    val seasonStart: String?,
    val seasonEnd: String?,
    val generatedAt: String?,
    /** 数据源声明的时区（通常为 Asia/Tokyo）。 */
    val sourceTimezone: String,
    /** 设备当前时区 id。 */
    val localTimezone: String,
    /** 固定 7 项，按周一 … 周日排列。 */
    val days: List<ScheduleDay>,
    /** 时间/周几不合法而被跳过的条目数。 */
    val skippedItemCount: Int = 0,
) {
    val totalCount: Int
        get() = days.sumOf { it.airings.size }

    fun day(weekday: Int): ScheduleDay? = days.firstOrNull { it.weekday == weekday }
}

/** Bangumi subject id -> 本地媒体库里的作品（同 id 只保留第一条）。 */
fun List<Anime>.indexAnimeByBangumiId(): Map<Int, Anime> {
    val index = LinkedHashMap<Int, Anime>()
    for (anime in this) {
        val bangumiId = anime.bangumiId?.takeIf { it > 0 } ?: continue
        index.putIfAbsent(bangumiId, anime)
    }
    return index
}

/** 时间表条目在本地媒体库里的匹配结果；null 表示「未在媒体库」。 */
fun ScheduleAiring.matchedAnime(index: Map<Int, Anime>): Anime? =
    subjectId.takeIf { it > 0 }?.let(index::get)

/** 媒体库匹配统计（用于表头概览）。 */
fun WeeklySchedule.matchedCount(index: Map<Int, Anime>): Int =
    days.sumOf { day -> day.airings.count { it.matchedAnime(index) != null } }
