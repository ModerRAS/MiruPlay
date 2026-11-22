package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ScheduleTimeConversionTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val utcPlus12 = ZoneId.of("UTC+12")
    private val utcMinus4 = ZoneId.of("UTC-04:00")

    @Test
    fun `parseTimeOfDay accepts hh-mm and h-mm`() {
        assertEquals(LocalTime.of(23, 0), ScheduleTimeConversion.parseTimeOfDay("23:00"))
        assertEquals(LocalTime.of(1, 5), ScheduleTimeConversion.parseTimeOfDay("1:05"))
        assertEquals(LocalTime.of(1, 28), ScheduleTimeConversion.parseTimeOfDay("01:28:00"))
        assertEquals(LocalTime.MIDNIGHT, ScheduleTimeConversion.parseTimeOfDay("00:00"))
    }

    @Test
    fun `parseTimeOfDay rejects malformed clock values`() {
        assertNull(ScheduleTimeConversion.parseTimeOfDay(null))
        assertNull(ScheduleTimeConversion.parseTimeOfDay(""))
        assertNull(ScheduleTimeConversion.parseTimeOfDay("24:00"))
        assertNull(ScheduleTimeConversion.parseTimeOfDay("23:60"))
        assertNull(ScheduleTimeConversion.parseTimeOfDay("abc"))
        assertNull(ScheduleTimeConversion.parseTimeOfDay("2300"))
    }

    @Test
    fun `jst slot converts to shanghai same weekday minute minus one`() {
        val slot = ScheduleTimeConversion.toLocalSlot(
            weekday = 3,
            time = "23:00",
            localZone = shanghai,
            now = Instant.parse("2026-10-06T00:00:00Z"), // JST 周二 09:00
        )!!
        assertEquals(3, slot.weekday)
        assertEquals(22 * 60, slot.minutes)
        assertEquals("22:00", slot.timeLabel)
        assertEquals(0, slot.crossDayOffset)
    }

    @Test
    fun `jst early-morning slot crosses back to previous day in western zone`() {
        // JST 周日 01:30（锚到 2026-10-04）→ UTC-4 是周六 12:30（2026-10-03）。
        val slot = ScheduleTimeConversion.toLocalSlot(
            weekday = 7,
            time = "01:30",
            localZone = utcMinus4,
            now = Instant.parse("2026-10-01T00:00:00Z"),
        )!!
        assertEquals(6, slot.weekday)
        assertEquals("12:30", slot.timeLabel)
        assertEquals(-1, slot.crossDayOffset)
    }

    @Test
    fun `jst late-night slot crosses forward to next day in eastern zone`() {
        // JST 周日 23:00（锚到 2026-10-04）→ UTC+12 是周一 02:00（2026-10-05）。
        val slot = ScheduleTimeConversion.toLocalSlot(
            weekday = 7,
            time = "23:00",
            localZone = utcPlus12,
            now = Instant.parse("2026-10-01T00:00:00Z"),
        )!!
        assertEquals(1, slot.weekday)
        assertEquals("02:00", slot.timeLabel)
        assertEquals(1, slot.crossDayOffset)
    }

    @Test
    fun `dst zone uses real zone rules around us fall-back`() {
        // 2026-11-01 美国夏令时结束：JST 11-02 01:30 → 纽约 EST(-5) 11-01 11:30，跨回前一天。
        val slot = ScheduleTimeConversion.toLocalSlot(
            weekday = 1,
            time = "01:30",
            localZone = ZoneId.of("America/New_York"),
            now = Instant.parse("2026-10-30T00:00:00Z"),
        )!!
        assertEquals(7, slot.weekday)
        assertEquals("11:30", slot.timeLabel)
        assertEquals(-1, slot.crossDayOffset)
    }

    @Test
    fun `slot anchors to next occurrence when todays airing already passed`() {
        // now = 2026-10-07 23:30 JST（周三晚上）→ 周三 23:00 已过，应锚到下周三（10-14）。
        val slot = ScheduleTimeConversion.toLocalSlot(
            weekday = 3,
            time = "23:00",
            localZone = shanghai,
            now = Instant.parse("2026-10-07T14:30:00Z"),
        )!!
        assertEquals(3, slot.weekday)
        assertEquals("22:00", slot.timeLabel)
    }

    @Test
    fun `buildWeeklySchedule groups by local weekday and sorts by local time`() {
        val snapshot = ScheduleSnapshot(
            season = "2026-fall",
            seasonStart = "2026-10-01",
            seasonEnd = "2026-12-31",
            timezone = "Asia/Tokyo",
            items = listOf(
                // airDate 故意与 weekday 不一致：契约以 weekday + time 为准。
                ScheduleItem(subjectId = 1, name = "A", airDate = "2026-10-05", weekday = 3, time = "23:00"),
                ScheduleItem(subjectId = 2, name = "B", airDate = "2026-10-09", weekday = 4, time = "01:28"),
                ScheduleItem(subjectId = 3, name = "C", weekday = 3, time = "19:00"),
                ScheduleItem(subjectId = 4, name = "D", weekday = 3, time = "bad-clock", airDate = "2026-10-07"),
                ScheduleItem(subjectId = 5, name = "E", weekday = 9, time = "10:00", airDate = "2026-10-07"),
            ),
        )
        val weekly = ScheduleTimeConversion.buildWeeklySchedule(
            snapshot = snapshot,
            localZone = shanghai,
            now = Instant.parse("2026-10-01T00:00:00Z"), // JST 周四 09:00
        )
        assertEquals(7, weekly.days.size)
        assertEquals(1, weekly.skippedItemCount) // 只有 5 的周几非法被跳过
        assertEquals(4, weekly.totalCount) // 4 的时钟非法降级为「时间未知」而不是丢弃
        val wednesday = weekly.day(3)!!
        assertEquals(listOf(3, 1, 4), wednesday.airings.map { it.subjectId }) // 18:00 → 22:00 → 时间未知（排最后）
        assertEquals(listOf("18:00", "22:00", null), wednesday.airings.map { it.localTimeLabel })
        assertFalse(wednesday.airings.last().timeKnown)
        // JST 周四 01:28 → 上海周四 00:28，仍在周四（当天 01:28 已过，锚到下周四语义相同）。
        assertEquals(4, weekly.day(4)!!.airings.single().localWeekday)
        assertEquals("00:28", weekly.day(4)!!.airings.single().localTimeLabel)
        assertEquals("2026-fall", weekly.season)
        assertEquals("Asia/Shanghai", weekly.localTimezone)
        assertEquals("Asia/Tokyo", weekly.sourceTimezone)
    }

    @Test
    fun `null time and timeKnown false become time-unknown rows grouped by weekday`() {
        val snapshot = ScheduleSnapshot(
            timezone = "Asia/Tokyo",
            items = listOf(
                ScheduleItem(subjectId = 11, name = "Known", weekday = 2, time = "18:00"),
                ScheduleItem(
                    subjectId = 12,
                    name = "UnknownNull",
                    weekday = 2,
                    time = null,
                    timeKnown = false,
                    disputed = true,
                ),
                ScheduleItem(subjectId = 13, name = "UnknownFlag", weekday = 2, time = "19:00", timeKnown = false),
            ),
        )
        val weekly = ScheduleTimeConversion.buildWeeklySchedule(
            snapshot = snapshot,
            localZone = shanghai,
            now = Instant.parse("2026-10-01T00:00:00Z"),
        )
        assertEquals(0, weekly.skippedItemCount)
        assertEquals(3, weekly.totalCount)
        val tuesday = weekly.day(2)!!
        assertEquals(listOf(11, 13, 12), tuesday.airings.map { it.subjectId }) // 时间未知的排在已知时间之后
        val known = tuesday.airings[0]
        assertTrue(known.timeKnown)
        assertEquals("17:00", known.localTimeLabel)
        val unknown = tuesday.airings.drop(1)
        assertTrue(unknown.all { !it.timeKnown })
        assertTrue(unknown.all { it.localTimeLabel == null && it.jstTimeLabel == null })
        assertEquals(2, unknown.map { it.localWeekday }.distinct().single())
        assertTrue(tuesday.airings.first { it.subjectId == 12 }.disputed)
    }

    @Test
    fun `inconsistent airDate does not override the weekday`() {
        // 线上 AirRadar 数据实测有 airDate 与 weekday 不一致的条目：
        // airDate 2026-10-05 是周一，但 weekday=5、time=10:00 —— 必须显示为周五。
        val snapshot = ScheduleSnapshot(
            timezone = "Asia/Tokyo",
            items = listOf(
                ScheduleItem(
                    subjectId = 7,
                    name = "Contradictory",
                    airDate = "2026-10-05",
                    weekday = 5,
                    time = "10:00",
                ),
            ),
        )
        val weekly = ScheduleTimeConversion.buildWeeklySchedule(
            snapshot = snapshot,
            localZone = shanghai,
            now = Instant.parse("2026-10-05T00:00:00Z"),
        )
        assertTrue(weekly.day(1)!!.isEmpty)
        val friday = weekly.day(5)!!.airings.single()
        assertEquals("09:00", friday.localTimeLabel)
        assertEquals(5, friday.localWeekday)
    }

    @Test
    fun `weekly grouping follows the converted local weekday`() {
        val snapshot = ScheduleSnapshot(
            timezone = "Asia/Tokyo",
            items = listOf(
                ScheduleItem(subjectId = 8, name = "Cross", weekday = 7, time = "01:30", airDate = "2026-10-01"),
            ),
        )
        val weekly = ScheduleTimeConversion.buildWeeklySchedule(
            snapshot = snapshot,
            localZone = utcMinus4,
            now = Instant.parse("2026-10-01T00:00:00Z"),
        )
        // JST 周日 01:30 → UTC-4 周六 12:30：分组要落在周六，且周日为空。
        assertEquals("12:30", weekly.day(6)!!.airings.single().localTimeLabel)
        assertTrue(weekly.day(7)!!.isEmpty)
    }
}
