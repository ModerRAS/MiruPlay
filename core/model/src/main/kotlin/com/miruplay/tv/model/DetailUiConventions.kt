package com.miruplay.tv.model

fun detailPlayActionLabel(): String = "播放"

fun detailBackToLibraryActionLabel(): String = "返回海报墙"

fun detailRescrapeActionLabel(): String = "重新刮削"

fun detailSyncProgressActionLabel(isSyncing: Boolean): String =
    if (isSyncing) "同步中" else "同步进度"

fun detailBangumiRescrapeStartedMessage(): String = "正在重新匹配 Bangumi..."

fun detailBangumiScraperUnavailableMessage(): String = "Bangumi 刮削器不可用"

fun detailBangumiNoReliableMatchMessage(): String = "没有找到可靠的 Bangumi 匹配"

fun detailBangumiDetailsFailedMessage(): String = "Bangumi 详情获取失败"

fun detailBangumiMetadataUpdatedMessage(): String = "Bangumi 元数据已更新"

fun detailBangumiSyncStartedMessage(): String = "正在同步 Bangumi..."

fun detailBangumiSyncCompleteMessage(pushedEpisodes: Int, pulledEpisodes: Int): String =
    "同步完成：上传 ${pushedEpisodes.coerceAtLeast(0)} 集，拉取 ${pulledEpisodes.coerceAtLeast(0)} 集"

fun detailEpisodeSectionTitle(): String = "选集"

fun detailHeroEmptySubtitle(): String = "从媒体库海报墙选择内容后显示详情。"

fun detailHeroEmptyTitle(): String = "选择一部番剧"

fun detailEpisodeEmptySubtitle(): String = "当前详情没有可播放索引项"

fun detailEpisodeShelfSubtitle(episodeCount: Int): String =
    if (episodeCount <= 0) detailEpisodeEmptySubtitle() else "${detailEpisodeCountLabel(episodeCount)} · 同番选集"

fun detailEpisodeEmptyMessage(): String = "扫描媒体库后会在这里显示同番选集。"

fun detailSeasonLabel(seasonNumber: Int): String =
    "第 $seasonNumber 季"

fun detailEpisodeNumberLabel(episodeNumber: Int): String =
    "第 $episodeNumber 集"

fun detailEpisodeBadge(episodeNumber: Int?): String =
    episodeNumber?.toString()?.padStart(2, '0') ?: "--"

fun detailEpisodeTitleLabel(episodeNumber: Int?, episodeTitle: String?): String {
    val number = episodeNumber?.let(::detailEpisodeNumberLabel) ?: "未编号"
    val title = episodeTitle?.takeIf { it.isNotBlank() }
    return if (title == null) number else "$number · $title"
}

fun detailEpisodeCountLabel(episodeCount: Int): String =
    "全 ${episodeCount.coerceAtLeast(0)} 话"

fun detailHeroStatLabels(
    episodeCount: Int,
    seasonNumber: Int? = null,
    metadataSource: String? = null,
): List<String> =
    buildList {
        if (episodeCount > 0) {
            add(detailEpisodeCountLabel(episodeCount))
        }
        seasonNumber?.let { add(detailSeasonLabel(it)) }
        metadataSource
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it.trim()) }
    }

fun detailEpisodePageUnitLabel(): String = "集"

fun recentPlaybackPageUnitLabel(): String = "条记录"

fun mediaDetailsPageUnitLabel(): String = "条详情"

fun detailRatingLabel(rating: Float): String =
    "评分 ${"%.1f".format(rating)}"

fun detailContinueActionLabel(episodeNumber: Int?): String =
    episodeNumber?.let { "继续观看 $it" } ?: detailPlayActionLabel()

fun detailPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
    unitLabel: String,
): String? {
    val pageSize = visibleCount.takeIf { it > 0 } ?: 1
    return pagedListPageSummary(
        pageStart = pageStart,
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = pageSize,
        unitLabel = unitLabel,
    )
}

fun detailBangumiCollectionLabel(type: Int): String =
    when (type) {
        1 -> "想看"
        2 -> "看过"
        3 -> "在看"
        4 -> "搁置"
        5 -> "抛弃"
        else -> "已关联"
    }

fun detailBangumiCollectionPillLabel(type: Int): String =
    "Bangumi ${detailBangumiCollectionLabel(type)}"

fun mediaDetailSourceLabel(): String = "媒体源"

fun mediaDetailSourceEmptyValue(): String = "无"

fun mediaDetailIndexedTitleLabel(): String = "索引标题"

fun mediaDetailIndexedTypeLabel(): String = "索引类型"

fun mediaDetailAnimeLabel(): String = "番剧"

fun mediaDetailSeasonLabel(): String = "季度"

fun mediaDetailEpisodeLabel(): String = "集数"

fun mediaDetailEpisodeTitleLabel(): String = "单集标题"

fun mediaDetailMetadataSourceLabel(): String = "元数据来源"

fun mediaDetailMetadataIdLabel(): String = "元数据 ID"

fun mediaDetailMetadataTitleLabel(): String = "元数据标题"

fun mediaDetailIndexedSizeLabel(): String = "索引大小"

fun mediaDetailIndexedModifiedLabel(): String = "索引修改时间"

fun mediaDetailBrowserItemLabel(): String = "浏览条目"

fun mediaDetailBrowserKindLabel(): String = "浏览类型"

fun mediaDetailMimeLabel(): String = "MIME"

fun mediaDetailBrowserSizeLabel(): String = "浏览大小"

fun mediaDetailBrowserModifiedLabel(): String = "浏览修改时间"

fun mediaDetailResumeLabel(): String = "播放进度"

fun mediaDetailPlayCountLabel(): String = "播放次数"

fun mediaDetailLastWatchedLabel(): String = "上次观看"

fun mediaDetailPlotLabel(): String = "简介"

fun mediaDetailPathLabel(): String = "路径"

fun mediaDetailUnknownValue(): String = "未知"

fun mediaDetailNotLinkedValue(): String = "未关联"

fun mediaDetailDirectoryValue(): String = "目录"

fun mediaDetailVideoValue(): String = "视频"

fun mediaDetailFileValue(): String = "文件"

fun mediaDetailIndexedKindValue(isDirectory: Boolean): String =
    if (isDirectory) mediaDetailDirectoryValue() else mediaDetailVideoValue()
