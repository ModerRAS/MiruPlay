package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DramaModelsTest {
    @Test
    fun `displayTitle prefers title then original title then id`() {
        val titleFirst = DramaSeries(
            id = "show-1",
            title = "Drama Title",
            originalTitle = "Original Title",
        )
        val originalTitleFallback = DramaSeries(
            id = "show-2",
            title = "",
            originalTitle = "Original Title",
        )
        val idFallback = DramaSeries(
            id = "show-3",
            title = "",
            originalTitle = "",
        )

        assertEquals("Drama Title", titleFirst.displayTitle())
        assertEquals("Original Title", originalTitleFallback.displayTitle())
        assertEquals("show-3", idFallback.displayTitle())
    }

    @Test
    fun `drama subtitles and stat labels use tv series wording`() {
        val series = DramaSeries(
            id = "show-1",
            title = "Drama Title",
            firstAirDate = "2024-01-01",
            seasonCount = 2,
            episodeCount = 24,
        )

        assertEquals("共 2 季 · 24 集", series.dramaPosterSubtitle())
        assertEquals("2024-01-01 · 共 2 季 · 24 集", series.dramaFeatureSubtitle())
        assertEquals("全 24 集", dramaEpisodeCountLabel(24))
        assertEquals("共 2 季", dramaSeasonCountLabel(2))
    }

    @Test
    fun `drama subtitles clamp negative counts and fall back to title`() {
        val emptySeries = DramaSeries(
            id = "show-2",
            title = "Empty Drama",
            seasonCount = -1,
            episodeCount = -3,
        )

        assertEquals("", emptySeries.dramaPosterSubtitle())
        assertEquals("Empty Drama", emptySeries.dramaFeatureSubtitle())
        assertEquals("全 0 集", dramaEpisodeCountLabel(-1))
        assertEquals("共 0 季", dramaSeasonCountLabel(-1))
    }

    @Test
    fun `refresh action label reflects metadata refresh state`() {
        assertEquals("刷新信息", dramaRefreshActionLabel(isRefreshing = false))
        assertEquals("刷新中", dramaRefreshActionLabel(isRefreshing = true))
    }

    @Test
    fun `metadata status message explains current drama metadata state`() {
        assertEquals(
            "正在刷新在线信息，当前页面会继续保留本地剧集列表。",
            dramaMetadataStatusMessage(
                hasTmdbMatch = false,
                hasTmdbToken = true,
                isRefreshing = true,
            ),
        )
        assertEquals(
            "已记住 TMDB 条目，后续刷新会优先按已保存编号更新。",
            dramaMetadataStatusMessage(
                hasTmdbMatch = true,
                hasTmdbToken = true,
                isRefreshing = false,
            ),
        )
        assertEquals(
            "当前先显示本地索引结果，点“刷新信息”可补全海报、简介和单集标题。",
            dramaMetadataStatusMessage(
                hasTmdbMatch = false,
                hasTmdbToken = true,
                isRefreshing = false,
            ),
        )
        assertEquals(
            "当前只显示本地索引结果。先在设置里填 TMDB 令牌，再点“刷新信息”补全海报、简介和单集标题。",
            dramaMetadataStatusMessage(
                hasTmdbMatch = false,
                hasTmdbToken = false,
                isRefreshing = false,
            ),
        )
    }
}
