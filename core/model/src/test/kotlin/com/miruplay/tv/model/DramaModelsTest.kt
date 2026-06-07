package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                hasBoundMetadata = false,
                hasTmdbToken = true,
                isRefreshing = true,
            ),
        )
        assertEquals(
            "已记住 TMDB 元数据条目，后续刷新会优先按已保存来源更新。",
            dramaMetadataStatusMessage(
                hasBoundMetadata = true,
                hasTmdbToken = true,
                isRefreshing = false,
                boundProviderLabel = "TMDB",
            ),
        )
        assertEquals(
            "已记住 TVMaze 元数据条目；当前自动详情补全仍优先使用已支持的详情源。",
            dramaMetadataStatusMessage(
                hasBoundMetadata = true,
                hasTmdbToken = false,
                isRefreshing = false,
                boundProviderLabel = "TVMaze",
            ),
        )
        assertEquals(
            "已记住 TVMaze 元数据条目，后续刷新会优先按已保存来源更新。",
            dramaMetadataStatusMessage(
                hasBoundMetadata = true,
                hasTmdbToken = false,
                isRefreshing = false,
                boundProviderLabel = "TVMaze",
                canRefreshBoundMetadata = true,
            ),
        )
        assertEquals(
            "当前先显示本地索引结果。若还没绑定在线来源，可用“刷新信息”按标题走 TMDB 补全海报、简介和单集标题。",
            dramaMetadataStatusMessage(
                hasBoundMetadata = false,
                hasTmdbToken = true,
                isRefreshing = false,
            ),
        )
        assertEquals(
            "当前只显示本地索引结果。现在可以先用“在线手动匹配”搜索多源候选；如果还没绑定可直刷的在线来源，也可以在设置里配置 TMDB Token 来启用按标题直接刷新。",
            dramaMetadataStatusMessage(
                hasBoundMetadata = false,
                hasTmdbToken = false,
                isRefreshing = false,
            ),
        )
    }

    @Test
    fun `provider refs default from tmdb ids for compatibility`() {
        val series = DramaSeries(
            id = "show-1",
            title = "Drama Title",
            tmdbId = 321,
        )
        val searchResult = DramaMetadataSearchResult(
            tmdbId = 321,
            title = "Drama Title",
        )

        assertEquals("TMDB", series.boundMetadataProviderRef()?.source)
        assertEquals("321", series.boundMetadataProviderRef()?.id)
        assertEquals("TMDB", searchResult.providerDisplayLabel())
        assertEquals("tmdb:321", searchResult.providerStableKey())
    }

    @Test
    fun `normalizedMetadataBinding clears stale tmdb id when provider ref is non tmdb`() {
        val normalized = DramaSeries(
            id = "show-1",
            title = "Drama Title",
            tmdbId = 321,
            metadataProviderRef = MetadataProviderRef(source = "TVMaze", id = "maze-321"),
        ).normalizedMetadataBinding()

        assertEquals("TVMaze", normalized.boundMetadataProviderRef()?.source)
        assertEquals("maze-321", normalized.boundMetadataProviderRef()?.id)
        assertNull(normalized.tmdbId)
        assertNull(normalized.tmdbCompatibilityId())
    }
}
