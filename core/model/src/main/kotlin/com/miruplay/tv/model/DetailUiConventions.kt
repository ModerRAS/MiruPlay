package com.miruplay.tv.model

const val DETAIL_EPISODE_PAGE_SIZE = 6
const val RECENT_PLAYBACK_PAGE_SIZE = 6
const val MEDIA_DETAILS_PAGE_SIZE = 6
const val DETAIL_BANGUMI_MANUAL_CANDIDATE_LIMIT = 6

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

fun detailBangumiManualMatchTitleLabel(): String = "选择 Bangumi 条目"

fun detailBangumiManualCloseActionLabel(): String = "关闭"

fun detailBangumiCandidateTermsSectionTitle(): String = "候选词"

fun detailBangumiManualSearchRequiredMessage(): String = "请选择候选词或输入 Bangumi 搜索词"

fun detailBangumiManualSelectionRequiredMessage(): String = "请选择一个 Bangumi 条目"

fun detailBangumiManualSearchStartedMessage(queryCount: Int): String =
    "正在搜索 ${queryCount.coerceAtLeast(0)} 个 Bangumi 搜索词..."

fun detailBangumiManualSearchResultMessage(resultCount: Int): String =
    if (resultCount <= 0) "没有可显示的 Bangumi 搜索结果" else "找到 ${resultCount.coerceAtLeast(0)} 个 Bangumi 匹配"

fun detailBangumiManualApplyStartedMessage(title: String): String =
    "正在应用：$title"

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

fun detailEpisodePageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = DETAIL_EPISODE_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

fun detailEpisodeCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = DETAIL_EPISODE_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

fun detailEpisodePageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): String? =
    pagedListPageSummary(
        pageStart = detailEpisodeCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = DETAIL_EPISODE_PAGE_SIZE,
        unitLabel = detailEpisodePageUnitLabel(),
    )

fun recentPlaybackPageUnitLabel(): String = "条记录"

fun recentPlaybackPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = RECENT_PLAYBACK_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

fun recentPlaybackCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = RECENT_PLAYBACK_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

fun recentPlaybackPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): String? =
    pagedListPageSummary(
        pageStart = recentPlaybackCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = RECENT_PLAYBACK_PAGE_SIZE,
        unitLabel = recentPlaybackPageUnitLabel(),
    )

fun mediaDetailsPageUnitLabel(): String = "条详情"

fun mediaDetailsPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = MEDIA_DETAILS_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

fun mediaDetailsCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = MEDIA_DETAILS_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

fun mediaDetailsPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): String? =
    pagedListPageSummary(
        pageStart = mediaDetailsCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = MEDIA_DETAILS_PAGE_SIZE,
        unitLabel = mediaDetailsPageUnitLabel(),
    )

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

fun detailBangumiManualCandidateTerms(
    anime: Anime,
    episodes: List<Episode>,
    limit: Int = DETAIL_BANGUMI_MANUAL_CANDIDATE_LIMIT,
): List<String> =
    buildList {
        add(anime.titleCn)
        add(anime.title)
        add(anime.id)
        anime.bangumiId?.toString()?.let(::add)
        episodes.take(4).forEach { episode ->
            episode.detailBangumiSearchPaths().forEach { path ->
                add(MediaPathConventions.animeNameFromEpisodePath(path))
                add(MediaPathConventions.parentName(path))
                add(MediaPathConventions.stem(path))
            }
        }
    }
        .mapNotNull { it?.detailBangumiNormalizedCandidate() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(limit.coerceAtLeast(1))

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

private fun Episode.detailBangumiSearchPaths(): List<String> =
    listOf(filePath, id, fileName)
        .map { path -> path.trim().withoutSourcePrefix() }
        .filter { it.isNotBlank() }

private fun String.withoutSourcePrefix(): String {
    val prefix = substringBefore(':')
    return if (prefix.toLongOrNull() != null) substringAfter(':') else this
}

private fun String.detailBangumiNormalizedCandidate(): String? {
    val normalized = MediaPathConventions.decodePath(this)
        .replace(Regex("""\[[^\]]*]"""), " ")
        .replace(Regex("""【[^】]*】"""), " ")
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""(?i)\b(1080p|2160p|720p|bdrip|web[- ]?dl|x264|x265|hevc|aac|flac)\b"""), " ")
        .replace(Regex("""(?i)\b(mkv|mp4|avi|mov|wmv|flv|m4v)\b"""), " ")
        .replace(Regex("""(?i)\b(s\d{1,2}e\d{1,3}|episode\s*\d{1,3}|ep\s*\d{1,3}|e\d{1,3})\b"""), " ")
        .replace(Regex("""(?i)\b(season\s*\d{1,2}|s\d{1,2})\b"""), " ")
        .replace(Regex("""第\s*\d+\s*[季期集话話]"""), " ")
        .replace(Regex("""[._]+"""), " ")
        .replace(Regex("""\s*-\s*\d{1,3}\s*$"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '_', '.', '·')
    return normalized.takeIf { it.length >= 2 }
}
