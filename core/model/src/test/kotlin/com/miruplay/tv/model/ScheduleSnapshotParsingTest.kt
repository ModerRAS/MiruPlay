package com.miruplay.tv.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ScheduleSnapshotParsingTest {

    /** 与线上契约同构的 fixture；图片/来源域名均为示例占位，不含真实数据源地址。 */
    private val fixture = """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-10-07T03:04:05Z",
          "season": "2026-fall",
          "seasonStart": "2026-10-01",
          "seasonEnd": "2026-12-31",
          "timezone": "Asia/Tokyo",
          "itemCount": 3,
          "items": [
            {
              "subjectId": 393010,
              "name": "サンプル・アニメ",
              "nameCn": "示例动画",
              "airDate": "2026-10-07",
              "image": "https://example.com/pic/393010.jpg",
              "weekday": 3,
              "time": "23:00",
              "timezone": "Asia/Tokyo",
              "confidence": "high",
              "sources": [
                {"url": "https://example.com/a", "domain": "example.com", "provider": "official_site"},
                {"url": "https://example.org/b", "domain": "example.org", "provider": "search"}
              ],
              "updatedAt": "2026-10-07T03:04:05Z"
            },
            {
              "subjectId": 393011,
              "name": "夜中アニメ",
              "nameCn": null,
              "airDate": "2026-10-09",
              "image": null,
              "weekday": 5,
              "time": "01:28",
              "timezone": "Asia/Tokyo",
              "confidence": "medium",
              "sources": [],
              "updatedAt": "2026-10-07T03:04:05Z"
            },
            {
              "subjectId": 393012,
              "name": "Unknown Confidence Show",
              "airDate": "2026-10-05",
              "weekday": 1,
              "time": "19:30",
              "confidence": "some-future-value",
              "sources": []
            }
          ]
        }
    """.trimIndent()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `fixture decodes per api contract`() {
        val snapshot = json.decodeFromString<ScheduleSnapshot>(fixture)
        assertEquals(1, snapshot.schemaVersion)
        assertEquals("2026-fall", snapshot.season)
        assertEquals("Asia/Tokyo", snapshot.timezone)
        assertEquals(3, snapshot.items.size)

        val first = snapshot.items[0]
        assertEquals(393010, first.subjectId)
        assertEquals("示例动画", first.displayName)
        assertEquals("https://example.com/pic/393010.jpg", first.image)
        assertEquals(3, first.weekday)
        assertEquals("23:00", first.time)
        assertEquals(2, first.sources.size)
        assertEquals(ScheduleConfidence.HIGH, ScheduleConfidence.fromWire(first.confidence))

        // nameCn 缺失时回退日文原名
        assertEquals("夜中アニメ", snapshot.items[1].displayName)
        // 未知 confidence 值降级为 UNKNOWN 而不是解析失败
        assertEquals(ScheduleConfidence.UNKNOWN, ScheduleConfidence.fromWire(snapshot.items[2].confidence))
    }

    @Test
    fun `new api nullable time and time flags parse and keep rows`() {
        // 2026-11 新 API：无具体时间的行返回 time:null + timeKnown:false，并新增 disputed。
        val payload = """
            {
              "schemaVersion": 1,
              "season": "2026-fall",
              "seasonStart": "2026-09-21",
              "seasonEnd": "2026-12-31",
              "timezone": "Asia/Tokyo",
              "itemCount": 3,
              "items": [
                {"subjectId": 101, "name": "Known", "weekday": 1, "time": "23:00", "timeKnown": true, "disputed": false},
                {"subjectId": 102, "name": "UnknownNull", "weekday": 1, "time": null, "timeKnown": false, "disputed": false},
                {"subjectId": 103, "name": "Disputed", "weekday": 3, "time": "19:00", "timeKnown": true, "disputed": true}
              ]
            }
        """.trimIndent()
        val snapshot = json.decodeFromString<ScheduleSnapshot>(payload)
        assertEquals("2026-09-21", snapshot.seasonStart)
        assertEquals(3, snapshot.items.size)
        assertNull(snapshot.items[1].time)
        assertFalse(snapshot.items[1].timeKnown)
        assertTrue(snapshot.items[2].disputed)

        val weekly = ScheduleTimeConversion.buildWeeklySchedule(
            snapshot = snapshot,
            localZone = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2026-10-01T00:00:00Z"),
        )
        assertEquals(0, weekly.skippedItemCount)
        assertEquals(3, weekly.totalCount) // 解析失败会被误报网络不可用；这里必须全部保留
        val monday = weekly.day(1)!!.airings
        assertEquals(2, monday.size)
        assertTrue(monday.first().timeKnown)
        assertFalse(monday.last().timeKnown)
        assertNull(monday.last().localTimeLabel)
        assertTrue(weekly.day(3)!!.airings.single().disputed)
    }

    @Test
    fun `explicit null for defaulted fields is coerced instead of failing`() {
        val tolerated = json.decodeFromString<ScheduleSnapshot>(
            """{"schemaVersion":1,"timezone":null,"items":[{"subjectId":7,"name":null,"weekday":1,"time":"20:00"}]}""",
        )
        assertEquals("", tolerated.items.single().name)
        assertEquals("20:00", tolerated.items.single().time)
    }

    @Test
    fun `future unknown top-level fields do not break parsing`() {
        val tolerant = json.decodeFromString<ScheduleSnapshot>(
            """{"schemaVersion":1,"items":[],"brandNewField":{"nested":true}}""",
        )
        assertTrue(tolerant.items.isEmpty())
        assertNull(tolerant.season)
    }

    @Test
    fun `weekly schedule from fixture groups into local weekdays`() {
        val snapshot = json.decodeFromString<ScheduleSnapshot>(fixture)
        val weekly = ScheduleTimeConversion.buildWeeklySchedule(
            snapshot = snapshot,
            localZone = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2026-10-06T00:00:00Z"),
        )
        assertEquals(3, weekly.totalCount)
        // JST 周三 23:00 → 上海周三 22:00；JST 周五 01:28 → 上海周五 00:28；JST 周一 19:30 → 上海周一 18:30。
        assertEquals("22:00", weekly.day(3)!!.airings.single().localTimeLabel)
        assertEquals("00:28", weekly.day(5)!!.airings.single().localTimeLabel)
        assertEquals("18:30", weekly.day(1)!!.airings.single().localTimeLabel)
        assertEquals("示例动画", weekly.day(3)!!.airings.single().displayName)
    }

    @Test
    fun `library matching uses bangumi subject id`() {
        val snapshot = json.decodeFromString<ScheduleSnapshot>(fixture)
        val weekly = ScheduleTimeConversion.buildWeeklySchedule(
            snapshot = snapshot,
            localZone = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2026-10-06T00:00:00Z"),
        )
        val library = listOf(
            Anime(id = "anime-1", title = "示例动画", bangumiId = 393010),
            Anime(id = "anime-2", title = "没有 Bangumi id 的作品", bangumiId = null),
            Anime(id = "anime-3", title = "重复 id 的后一条", bangumiId = 393010),
            Anime(id = "anime-4", title = "其它作品", bangumiId = 999999),
        )
        val index = library.indexAnimeByBangumiId()
        assertEquals(2, index.size) // 393010 与 999999
        assertEquals("anime-1", index.getValue(393010).id) // 重复 id 保留第一条

        val matched = weekly.day(3)!!.airings.single()
        assertEquals("anime-1", matched.matchedAnime(index)?.id)
        assertNull(weekly.day(5)!!.airings.single().matchedAnime(index)) // 393011 不在媒体库
        assertEquals(1, weekly.matchedCount(index))
    }
}
