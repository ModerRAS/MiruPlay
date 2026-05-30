package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterWallArrangementTest {
    @Test
    fun `title arrangement sorts by display title`() {
        val anime = listOf(
            Anime(id = "frieren", title = "Frieren"),
            Anime(id = "bocchi", title = "Bocchi", titleCn = "孤独摇滚"),
            Anime(id = "akebi", title = "Akebi"),
        )

        assertEquals(
            listOf("akebi", "frieren", "bocchi"),
            anime.sortedForPosterWall(PosterWallArrangement.TITLE).map { it.id },
        )
    }

    @Test
    fun `release season arrangement groups by year and 1 4 7 9 month slots`() {
        val anime = listOf(
            Anime(id = "winter", title = "Winter", airDate = "2024-01-05"),
            Anime(id = "spring", title = "Spring", airDate = "2024-04-01"),
            Anime(id = "summer", title = "Summer", airDate = "2023-07-09"),
            Anime(id = "autumn", title = "Autumn", airDate = "2024-10-02"),
            Anime(id = "late", title = "Late", airDate = "2024-09-29"),
            Anime(id = "unknown", title = "Unknown"),
        )

        val sections = anime.posterWallSections(PosterWallArrangement.RELEASE_SEASON)

        assertEquals(
            listOf("2024年9月新番", "2024年4月新番", "2024年1月新番", "2023年7月新番", "未识别播出日期"),
            sections.map { it.title },
        )
        assertEquals(listOf("autumn", "late"), sections.first().anime.map { it.id })
        assertEquals(listOf("unknown"), sections.last().anime.map { it.id })
    }

    @Test
    fun `release season parses localized date separators`() {
        assertEquals(AnimeReleaseSeason(2025, 4), Anime(id = "a", title = "A", airDate = "2025年6月1日").releaseSeason())
        assertEquals(AnimeReleaseSeason(2025, 9), Anime(id = "b", title = "B", airDate = "2025/12/31").releaseSeason())
        assertNull(Anime(id = "c", title = "C", airDate = "2025").releaseSeason())
    }

    @Test
    fun `arrangement labels explain current poster wall behavior`() {
        assertEquals("按标题", posterWallArrangementLabel(PosterWallArrangement.TITLE))
        assertEquals("按新番季", posterWallArrangementLabel(PosterWallArrangement.RELEASE_SEASON))
        assertEquals(
            "海报墙当前按标题排序：中文名优先，其次使用原名。",
            posterWallArrangementStatus(PosterWallArrangement.TITLE),
        )
    }
}
