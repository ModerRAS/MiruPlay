package com.miruplay.tv.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** 换算到设备本地时区后的一个时间段。 */
data class ScheduleLocalSlot(
    /** ISO 周几：1=周一 … 7=周日。 */
    val weekday: Int,
    /** 当天 0 点起的分钟数。 */
    val minutes: Int,
    /** "HH:mm"。 */
    val timeLabel: String,
    /** 本地日期相对 JST 播出日期的天数差。 */
    val crossDayOffset: Int,
) {
    val sortKey: Int
        get() = weekday * ScheduleAiring.MINUTES_PER_DAY + minutes
}

/**
 * JST 周几 + 时刻 → 设备本地时区的周几 + 时刻。
 *
 * 跨日必须正确：例如 JST 周日 01:30 在 UTC-4 是周六 12:30。
 *
 * 契约以「JST 周几 + 时刻」（ScheduleItem.weekday / ScheduleItem.time）为权威播出时刻：
 * 锚定到 [now] 之后最近一次满足该周几 + 时刻的 JST 日期，再做完整时区换算。
 * 不使用 airDate：线上数据实测存在 airDate 与 weekday 不一致的条目（例如 airDate 是
 * 周一但 weekday=5），按 airDate 换算会把周五的番显示成周一。
 *
 * 纯函数、不依赖系统默认时区，测试可注入 [sourceZone] / [localZone] / [now]。
 */
object ScheduleTimeConversion {

    val JAPAN_TV_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?$""")

    /** 解析 "HH:mm" / "H:mm" / "HH:mm:ss"；小时必须 0..23（AirRadar 已把深夜番归一化为真实时钟）。 */
    fun parseTimeOfDay(raw: String?): LocalTime? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val match = TIME_PATTERN.matchEntire(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /** 1..7（ISO）→ 周几；非法返回 null。 */
    fun normalizeWeekday(weekday: Int): Int? = weekday.takeIf { it in 1..7 }

    fun parseZoneOrNull(raw: String?): ZoneId? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** 解析 JST 播出日期（ISO yyyy-MM-dd）；缺失/非法返回 null。 */
    fun parseAirDate(raw: String?): LocalDate? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /** 把一条「JST 周几 + HH:mm」换算到 [localZone]（airDate 不可信，按 weekday + time 锚定）。 */
    fun toLocalSlot(
        weekday: Int,
        time: String,
        sourceZone: ZoneId = JAPAN_TV_ZONE,
        localZone: ZoneId,
        now: Instant = Instant.now(),
    ): ScheduleLocalSlot? {
        val normalizedWeekday = normalizeWeekday(weekday) ?: return null
        val localTime = parseTimeOfDay(time) ?: return null
        val jstDate = nextAiringDate(
            weekday = normalizedWeekday,
            time = localTime,
            sourceZone = sourceZone,
            now = now,
        )
        return convert(jstDate, localTime, sourceZone, localZone)
    }

    /** 锚定到 [now] 之后（含正在进行的当天时刻）最近一次满足周几 + 时刻的 JST 日期。 */
    private fun nextAiringDate(
        weekday: Int,
        time: LocalTime,
        sourceZone: ZoneId,
        now: Instant,
    ): LocalDate {
        val nowInSource = now.atZone(sourceZone)
        val today = nowInSource.toLocalDate()
        val delta = Math.floorMod(weekday - today.dayOfWeek.value, 7)
        var candidate = today.plusDays(delta.toLong())
        val candidateDateTime = ZonedDateTime.of(candidate, time, sourceZone)
        if (candidateDateTime.toInstant().isBefore(now)) {
            candidate = candidate.plusDays(7L)
        }
        return candidate
    }

    private fun convert(
        jstDate: LocalDate,
        time: LocalTime,
        sourceZone: ZoneId,
        localZone: ZoneId,
    ): ScheduleLocalSlot {
        val localDateTime = ZonedDateTime.of(jstDate, time, sourceZone)
            .withZoneSameInstant(localZone)
            .toLocalDateTime()
        val weekday = localDateTime.dayOfWeek.value
        val minutes = localDateTime.hour * 60 + localDateTime.minute
        val crossDayOffset = ChronoUnit.DAYS.between(jstDate, localDateTime.toLocalDate()).toInt()
        return ScheduleLocalSlot(
            weekday = weekday,
            minutes = minutes,
            timeLabel = formatClock(localDateTime.hour, localDateTime.minute),
            crossDayOffset = crossDayOffset,
        )
    }

    /**
     * 把一季的原始快照整理成「周一 … 周日」的分组周表：
     * 条目按本地时间升序（「时间未知」的排在当天最后），7 天分组始终存在。
     */
    fun buildWeeklySchedule(
        snapshot: ScheduleSnapshot,
        localZone: ZoneId,
        now: Instant = Instant.now(),
    ): WeeklySchedule {
        val snapshotZone = parseZoneOrNull(snapshot.timezone) ?: JAPAN_TV_ZONE
        var skipped = 0
        val groups = (1..7).associateWith { mutableListOf<ScheduleAiring>() }

        for (item in snapshot.items) {
            val weekday = normalizeWeekday(item.weekday)
            if (weekday == null) {
                skipped++
                continue
            }
            val itemZone = parseZoneOrNull(item.timezone) ?: snapshotZone
            val parsedTime = item.time?.let(::parseTimeOfDay)
            // 新 API：time=null + timeKnown=false 表示「播出时刻未知」。这类行要正常展示
            // （按周几分组 + 时间未知文案），绝不能当成解析失败进而误报网络错误。
            // timeKnown=true 但 time 非法/缺失时同样降级为时间未知，而不是丢弃。
            val slot = if (item.timeKnown && parsedTime != null) {
                toLocalSlot(
                    weekday = weekday,
                    time = item.time.orEmpty(),
                    sourceZone = itemZone,
                    localZone = localZone,
                    now = now,
                )
            } else {
                null
            }
            val airing = if (slot != null && parsedTime != null) {
                ScheduleAiring(
                    subjectId = item.subjectId,
                    displayName = item.displayName,
                    originalName = item.name.trim(),
                    imageUrl = item.image?.trim()?.takeIf { it.isNotEmpty() },
                    confidence = ScheduleConfidence.fromWire(item.confidence),
                    sourceCount = item.sources.size,
                    airDate = item.airDate?.trim()?.takeIf { it.isNotEmpty() },
                    jstWeekday = weekday,
                    jstTimeLabel = formatClock(parsedTime.hour, parsedTime.minute),
                    localWeekday = slot.weekday,
                    localMinutes = slot.minutes,
                    localTimeLabel = slot.timeLabel,
                    crossDayOffset = slot.crossDayOffset,
                    timeKnown = true,
                    disputed = item.disputed,
                )
            } else {
                ScheduleAiring(
                    subjectId = item.subjectId,
                    displayName = item.displayName,
                    originalName = item.name.trim(),
                    imageUrl = item.image?.trim()?.takeIf { it.isNotEmpty() },
                    confidence = ScheduleConfidence.fromWire(item.confidence),
                    sourceCount = item.sources.size,
                    airDate = item.airDate?.trim()?.takeIf { it.isNotEmpty() },
                    jstWeekday = weekday,
                    jstTimeLabel = null,
                    localWeekday = weekday,
                    localMinutes = null,
                    localTimeLabel = null,
                    crossDayOffset = 0,
                    timeKnown = false,
                    disputed = item.disputed,
                )
            }
            groups.getValue(airing.localWeekday).add(airing)
        }

        val days = (1..7).map { weekday ->
            ScheduleDay(
                weekday = weekday,
                airings = groups.getValue(weekday).sortedWith(
                    compareBy<ScheduleAiring>(
                        { !it.timeKnown },
                        { it.localMinutes ?: Int.MAX_VALUE },
                        { it.displayName },
                    ),
                ),
            )
        }
        return WeeklySchedule(
            season = snapshot.season?.trim()?.takeIf { it.isNotEmpty() },
            seasonStart = snapshot.seasonStart?.trim()?.takeIf { it.isNotEmpty() },
            seasonEnd = snapshot.seasonEnd?.trim()?.takeIf { it.isNotEmpty() },
            generatedAt = snapshot.generatedAt?.trim()?.takeIf { it.isNotEmpty() },
            sourceTimezone = snapshotZone.id,
            localTimezone = localZone.id,
            days = days,
            skippedItemCount = skipped,
        )
    }

    private fun formatClock(hour: Int, minute: Int): String =
        String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)

    /** 本地周几的中文/英文标签由 UI 层用 UiText 处理；这里只提供 ISO 编号。 */
    fun isoWeekdayOf(dayOfWeek: DayOfWeek): Int = dayOfWeek.value
}
