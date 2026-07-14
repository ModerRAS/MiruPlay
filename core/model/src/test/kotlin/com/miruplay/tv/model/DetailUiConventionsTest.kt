package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailUiConventionsTest {
    @Test
    fun `shared detail action and section labels match TV copy`() {
        assertEquals("播放", detailPlayActionLabel())
        assertEquals("返回海报墙", detailBackToLibraryActionLabel())
        assertEquals("重新刮削", detailRescrapeActionLabel())
        assertEquals("同步进度", detailSyncProgressActionLabel(isSyncing = false))
        assertEquals("同步中", detailSyncProgressActionLabel(isSyncing = true))
        assertEquals("正在重新匹配 Bangumi...", detailBangumiRescrapeStartedMessage())
        assertEquals("Bangumi 刮削器不可用", detailBangumiScraperUnavailableMessage())
        assertEquals("没有找到可靠的 Bangumi 匹配", detailBangumiNoReliableMatchMessage())
        assertEquals("Bangumi 详情获取失败", detailBangumiDetailsFailedMessage())
        assertEquals("Bangumi 元数据已更新", detailBangumiMetadataUpdatedMessage())
        assertEquals("选择 Bangumi 条目", detailBangumiManualMatchTitleLabel())
        assertEquals("关闭", detailBangumiManualCloseActionLabel())
        assertEquals("候选词", detailBangumiCandidateTermsSectionTitle())
        assertEquals("请选择候选词或输入 Bangumi 搜索词", detailBangumiManualSearchRequiredMessage())
        assertEquals("请选择一个 Bangumi 条目", detailBangumiManualSelectionRequiredMessage())
        assertEquals("正在搜索 2 个 Bangumi 搜索词...", detailBangumiManualSearchStartedMessage(2))
        assertEquals("没有可显示的 Bangumi 搜索结果", detailBangumiManualSearchResultMessage(0))
        assertEquals("找到 3 个 Bangumi 匹配", detailBangumiManualSearchResultMessage(3))
        assertEquals("正在应用：葬送的芙莉莲", detailBangumiManualApplyStartedMessage("葬送的芙莉莲"))
        assertEquals("正在同步 Bangumi...", detailBangumiSyncStartedMessage())
        assertEquals("同步完成：上传 0 集，拉取 3 集", detailBangumiSyncCompleteMessage(-1, 3))
        assertEquals("选集", detailEpisodeSectionTitle())
        assertEquals("选择一部番剧", detailHeroEmptyTitle())
        assertEquals("从媒体库海报墙选择内容后显示详情。", detailHeroEmptySubtitle())
        assertEquals("扫描媒体库后会在这里显示同番选集。", detailEpisodeEmptyMessage())
    }

    @Test
    fun `shared detail episode labels clamp and format consistently`() {
        assertEquals("当前详情没有可播放索引项", detailEpisodeShelfSubtitle(0))
        assertEquals("全 12 话 · 同番选集", detailEpisodeShelfSubtitle(12))
        assertEquals("第 2 季", detailSeasonLabel(2))
        assertEquals("第 3 集", detailEpisodeNumberLabel(3))
        assertEquals("03", detailEpisodeBadge(3))
        assertEquals("--", detailEpisodeBadge(null))
        assertEquals("第 3 集", detailEpisodeTitleLabel(3, ""))
        assertEquals("第 3 集 · First Light", detailEpisodeTitleLabel(3, "First Light"))
        assertEquals("未编号 · Special", detailEpisodeTitleLabel(null, "Special"))
        assertEquals("全 0 话", detailEpisodeCountLabel(-1))
        assertEquals("集", detailEpisodePageUnitLabel())
        assertEquals("条记录", recentPlaybackPageUnitLabel())
        assertEquals("条详情", mediaDetailsPageUnitLabel())
        assertEquals("评分 8.6", detailRatingLabel(8.55f))
        assertEquals("播放", detailContinueActionLabel(null))
        assertEquals("继续观看 7", detailContinueActionLabel(7))
    }

    @Test
    fun `manual Bangumi candidate terms include metadata and cleaned local paths`() {
        val anime = Anime(
            id = "local-frieren",
            title = "葬送のフリーレン",
            titleCn = "葬送的芙莉莲",
            bangumiId = 431767,
        )
        val episodes = listOf(
            Episode(
                id = "1:/storage/emulated/0/Download/Frieren/%5BSubs%5D%20Frieren%20-%2001%20%5B1080p%5D.mkv",
                animeId = "local-frieren",
                seasonNumber = 1,
                episodeNumber = 1,
                title = "",
                filePath = "/storage/emulated/0/Download/Frieren/[Subs] Frieren - 01 [1080p].mkv",
                fileName = "[Subs] Frieren - 01 [1080p].mkv",
            )
        )

        assertEquals(
            listOf("葬送的芙莉莲", "葬送のフリーレン", "431767", "Frieren"),
            detailBangumiManualCandidateTerms(anime, episodes),
        )
    }

    @Test
    fun `manual Bangumi candidate terms keep season qualifiers for matching`() {
        val anime = Anime(id = "local-dr-stone", title = "", titleCn = null)
        val episodes = listOf(
            Episode(
                id = "1:/storage/emulated/0/Download/Dr.STONE 新石纪 第四季/Dr.STONE 新石纪 第四季 - 01.mkv",
                animeId = "local-dr-stone",
                seasonNumber = 4,
                episodeNumber = 1,
                title = "",
                filePath = "/storage/emulated/0/Download/Dr.STONE 新石纪 第四季/Dr.STONE 新石纪 第四季 - 01.mkv",
                fileName = "Dr.STONE 新石纪 第四季 - 01.mkv",
            )
        )

        assertEquals(
            listOf("Dr STONE 新石纪 第四季", "Dr STONE 新石纪"),
            detailBangumiManualCandidateTerms(anime, episodes),
        )
    }

    @Test
    fun `shared detail hero stat labels keep episode season and metadata order`() {
        assertEquals(
            listOf("全 3 话", "第 1 季", "Bangumi"),
            detailHeroStatLabels(
                episodeCount = 3,
                seasonNumber = 1,
                metadataSource = "Bangumi",
            ),
        )
        assertEquals(listOf("全 3 话"), detailHeroStatLabels(episodeCount = 3))
        assertEquals(
            listOf("第 1 季", "Bangumi"),
            detailHeroStatLabels(
                episodeCount = 0,
                seasonNumber = 1,
                metadataSource = "Bangumi",
            ),
        )
    }

    @Test
    fun `shared detail page summary hides complete pages`() {
        assertEquals("显示 7-12 / 14 集，按上/下继续翻页。", detailPageSummary(6, 6, 14, "集"))
        assertEquals("显示 13-14 / 14 条记录，按上/下继续翻页。", detailPageSummary(12, 2, 14, "条记录"))
        assertEquals(null, detailPageSummary(0, 5, 5, "集"))
    }

    @Test
    fun `detail pagination helpers keep page sizes and units`() {
        assertEquals(6, DETAIL_EPISODE_PAGE_SIZE)
        assertEquals(6, RECENT_PLAYBACK_PAGE_SIZE)
        assertEquals(6, MEDIA_DETAILS_PAGE_SIZE)

        assertEquals(12, detailEpisodePageStartForIndex(index = 30, itemCount = 14))
        assertEquals(6, detailEpisodeCoercedPageStart(pageStart = 9, itemCount = 14))
        assertEquals("显示 7-12 / 14 集，按上/下继续翻页。", detailEpisodePageSummary(6, 6, 14))
        assertEquals(null, detailEpisodePageSummary(0, 5, 5))

        assertEquals(12, recentPlaybackPageStartForIndex(index = 30, itemCount = 14))
        assertEquals(6, recentPlaybackCoercedPageStart(pageStart = 8, itemCount = 14))
        assertEquals("显示 13-14 / 14 条记录，按上/下继续翻页。", recentPlaybackPageSummary(12, 2, 14))
        assertEquals(null, recentPlaybackPageSummary(0, 5, 5))

        assertEquals(12, mediaDetailsPageStartForIndex(index = 30, itemCount = 13))
        assertEquals(6, mediaDetailsCoercedPageStart(pageStart = 11, itemCount = 13))
        assertEquals("显示 13-13 / 13 条详情，按上/下继续翻页。", mediaDetailsPageSummary(12, 1, 13))
        assertEquals(null, mediaDetailsPageSummary(0, 5, 5))
    }

    @Test
    fun `shared bangumi collection labels match TV detail pills`() {
        assertEquals("想看", detailBangumiCollectionLabel(1))
        assertEquals("看过", detailBangumiCollectionLabel(2))
        assertEquals("在看", detailBangumiCollectionLabel(3))
        assertEquals("搁置", detailBangumiCollectionLabel(4))
        assertEquals("抛弃", detailBangumiCollectionLabel(5))
        assertEquals("已关联", detailBangumiCollectionLabel(99))
        assertEquals("Bangumi 在看", detailBangumiCollectionPillLabel(3))
    }

    @Test
    fun `shared media detail row labels use TV-facing copy`() {
        assertEquals("媒体源", mediaDetailSourceLabel())
        assertEquals("无", mediaDetailSourceEmptyValue())
        assertEquals("索引标题", mediaDetailIndexedTitleLabel())
        assertEquals("索引类型", mediaDetailIndexedTypeLabel())
        assertEquals("番剧", mediaDetailAnimeLabel())
        assertEquals("季度", mediaDetailSeasonLabel())
        assertEquals("集数", mediaDetailEpisodeLabel())
        assertEquals("单集标题", mediaDetailEpisodeTitleLabel())
        assertEquals("元数据来源", mediaDetailMetadataSourceLabel())
        assertEquals("元数据 ID", mediaDetailMetadataIdLabel())
        assertEquals("元数据标题", mediaDetailMetadataTitleLabel())
        assertEquals("播放进度", mediaDetailResumeLabel())
        assertEquals("播放次数", mediaDetailPlayCountLabel())
        assertEquals("路径", mediaDetailPathLabel())
        assertEquals("未知", mediaDetailUnknownValue())
        assertEquals("未关联", mediaDetailNotLinkedValue())
        assertEquals("目录", mediaDetailDirectoryValue())
        assertEquals("文件", mediaDetailFileValue())
        assertEquals("视频", mediaDetailVideoValue())
        assertEquals("目录", mediaDetailIndexedKindValue(isDirectory = true))
        assertEquals("视频", mediaDetailIndexedKindValue(isDirectory = false))
    }
}
