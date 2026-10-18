package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.core.common.uiText

const val DETAIL_EPISODE_PAGE_SIZE = 6
const val RECENT_PLAYBACK_PAGE_SIZE = 6
const val MEDIA_DETAILS_PAGE_SIZE = 6
const val DETAIL_BANGUMI_MANUAL_CANDIDATE_LIMIT = 6

fun detailPlayActionLabel(): UiText = uiText("detail_play", "播放")

fun detailBackToLibraryActionLabel(): UiText = uiText("detail_back_to_library", "返回海报墙")

fun detailRescrapeActionLabel(): UiText = uiText("detail_rescrape", "重新刮削")

fun detailSyncProgressActionLabel(isSyncing: Boolean): UiText =
    if (isSyncing) {
        uiText("detail_syncing", "同步中")
    } else {
        uiText("detail_sync_progress", "同步进度")
    }

fun detailBangumiRescrapeStartedMessage(): UiText =
    uiText("detail_bangumi_rescrape_started", "正在重新匹配 Bangumi...")

fun detailBangumiScraperUnavailableMessage(): UiText =
    uiText("detail_bangumi_scraper_unavailable", "Bangumi 刮削器不可用")

fun detailBangumiNoReliableMatchMessage(): UiText =
    uiText("detail_bangumi_no_reliable_match", "没有找到可靠的 Bangumi 匹配")

fun detailBangumiDetailsFailedMessage(): UiText =
    uiText("detail_bangumi_details_failed", "Bangumi 详情获取失败")

fun detailBangumiMetadataUpdatedMessage(): UiText =
    uiText("detail_bangumi_metadata_updated", "Bangumi 元数据已更新")

fun detailBangumiManualMatchTitleLabel(): UiText =
    uiText("detail_bangumi_manual_title", "选择 Bangumi 条目")

fun detailBangumiManualCloseActionLabel(): UiText =
    uiText("detail_bangumi_manual_close", "关闭")

fun detailBangumiCandidateTermsSectionTitle(): UiText =
    uiText("detail_bangumi_candidate_terms", "候选词")

fun detailBangumiManualSearchRequiredMessage(): UiText =
    uiText("detail_bangumi_manual_search_required", "请选择候选词或输入 Bangumi 搜索词")

fun detailBangumiManualSelectionRequiredMessage(): UiText =
    uiText("detail_bangumi_manual_selection_required", "请选择一个 Bangumi 条目")

fun detailBangumiManualSearchStartedMessage(queryCount: Int): UiText {
    val safe = queryCount.coerceAtLeast(0)
    return UiText("detail_bangumi_manual_search_started", listOf(safe), "正在搜索 $safe 个 Bangumi 搜索词...")
}

fun detailBangumiManualSearchResultMessage(resultCount: Int): UiText =
    if (resultCount <= 0) {
        uiText("detail_bangumi_manual_search_empty", "没有可显示的 Bangumi 搜索结果")
    } else {
        val safe = resultCount.coerceAtLeast(0)
        UiText("detail_bangumi_manual_search_found", listOf(safe), "找到 $safe 个 Bangumi 匹配")
    }

fun detailBangumiManualApplyStartedMessage(title: String): UiText =
    UiText("detail_bangumi_manual_apply_started", listOf(title), "正在应用：$title")

fun detailBangumiSyncStartedMessage(): UiText =
    uiText("detail_bangumi_sync_started", "正在同步 Bangumi...")

fun detailBangumiSyncCompleteMessage(pushedEpisodes: Int, pulledEpisodes: Int): UiText {
    val pushed = pushedEpisodes.coerceAtLeast(0)
    val pulled = pulledEpisodes.coerceAtLeast(0)
    return UiText(
        "detail_bangumi_sync_complete",
        listOf(pushed, pulled),
        "同步完成：上传 $pushed 集，拉取 $pulled 集",
    )
}

fun detailEpisodeSectionTitle(): UiText = uiText("detail_episode_section", "选集")

fun detailHeroEmptySubtitle(): UiText =
    uiText("detail_hero_empty_subtitle", "从媒体库海报墙选择内容后显示详情。")

fun detailHeroEmptyTitle(): UiText = uiText("detail_hero_empty_title", "选择一部番剧")

fun detailEpisodeEmptySubtitle(): UiText =
    uiText("detail_episode_empty_subtitle", "当前详情没有可播放索引项")

fun detailEpisodeShelfSubtitle(episodeCount: Int): UiText =
    if (episodeCount <= 0) {
        detailEpisodeEmptySubtitle()
    } else {
        val count = detailEpisodeCountLabel(episodeCount)
        val shelf = uiText("detail_episode_shelf_suffix", "同番选集")
        UiText(
            "detail_episode_shelf",
            listOf(count, shelf),
            "${count.fallback} · ${shelf.fallback}",
        )
    }

fun detailEpisodeEmptyMessage(): UiText =
    uiText("detail_episode_empty_message", "扫描媒体库后会在这里显示同番选集。")

fun detailSeasonLabel(seasonNumber: Int): UiText =
    UiText("detail_season_label", listOf(seasonNumber), "第 $seasonNumber 季")

fun detailEpisodeNumberLabel(episodeNumber: Int): UiText =
    UiText("detail_episode_number_label", listOf(episodeNumber), "第 $episodeNumber 集")

fun detailEpisodeBadge(episodeNumber: Int?): String =
    episodeNumber?.toString()?.padStart(2, '0') ?: "--"

fun detailEpisodeTitleLabel(episodeNumber: Int?, episodeTitle: String?): UiText {
    val number = episodeNumber?.let(::detailEpisodeNumberLabel)
        ?: uiText("detail_episode_unnumbered", "未编号")
    val title = episodeTitle?.takeIf { it.isNotBlank() } ?: return number
    return UiText(
        "detail_episode_title",
        listOf(number, title),
        "${number.fallback} · $title",
    )
}

fun detailEpisodeCountLabel(episodeCount: Int): UiText {
    val safe = episodeCount.coerceAtLeast(0)
    return UiText("detail_episode_count", listOf(safe), "全 $safe 话")
}

fun detailHeroStatLabels(
    episodeCount: Int,
    seasonNumber: Int? = null,
    metadataSource: String? = null,
): List<UiText> =
    buildList {
        if (episodeCount > 0) {
            add(detailEpisodeCountLabel(episodeCount))
        }
        seasonNumber?.let { add(detailSeasonLabel(it)) }
        metadataSource
            ?.takeIf { it.isNotBlank() }
            ?.let { add(rawUiText(it.trim())) }
    }

fun detailEpisodePageUnitLabel(): UiText = uiText("detail_page_unit_episode", "集")

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
): UiText? =
    pagedListPageSummary(
        pageStart = detailEpisodeCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = DETAIL_EPISODE_PAGE_SIZE,
        unitLabel = detailEpisodePageUnitLabel(),
    )

fun recentPlaybackPageUnitLabel(): UiText = uiText("detail_page_unit_record", "条记录")

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
): UiText? =
    pagedListPageSummary(
        pageStart = recentPlaybackCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = RECENT_PLAYBACK_PAGE_SIZE,
        unitLabel = recentPlaybackPageUnitLabel(),
    )

fun mediaDetailsPageUnitLabel(): UiText = uiText("detail_page_unit_detail", "条详情")

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
): UiText? =
    pagedListPageSummary(
        pageStart = mediaDetailsCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = MEDIA_DETAILS_PAGE_SIZE,
        unitLabel = mediaDetailsPageUnitLabel(),
    )

fun detailRatingLabel(rating: Float): UiText {
    val text = "%.1f".format(rating)
    return UiText("detail_rating", listOf(text), "评分 $text")
}

fun detailContinueActionLabel(episodeNumber: Int?): UiText =
    episodeNumber?.let {
        UiText("detail_continue_action", listOf(it), "继续观看 $it")
    } ?: detailPlayActionLabel()

fun detailPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
    unitLabel: UiText,
): UiText? {
    val pageSize = visibleCount.takeIf { it > 0 } ?: 1
    return pagedListPageSummary(
        pageStart = pageStart,
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = pageSize,
        unitLabel = unitLabel,
    )
}

fun detailBangumiCollectionLabel(type: Int): UiText =
    when (type) {
        1 -> uiText("detail_collection_wish", "想看")
        2 -> uiText("detail_collection_done", "看过")
        3 -> uiText("detail_collection_doing", "在看")
        4 -> uiText("detail_collection_on_hold", "搁置")
        5 -> uiText("detail_collection_dropped", "抛弃")
        else -> uiText("detail_collection_linked", "已关联")
    }

fun detailBangumiCollectionPillLabel(type: Int): UiText {
    val label = detailBangumiCollectionLabel(type)
    return UiText("detail_collection_pill", listOf(label), "Bangumi ${label.fallback}")
}

fun detailBangumiManualCandidateTerms(
    anime: Anime,
    episodes: List<Episode>,
    limit: Int = DETAIL_BANGUMI_MANUAL_CANDIDATE_LIMIT,
): List<String> =
    buildList {
        add(anime.titleCn)
        add(anime.title)
        add(anime.id.takeIf { it.toIntOrNull() != null })
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
        .flatMap { listOfNotNull(it, it.detailBangumiSeasonlessCandidate()) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(limit.coerceAtLeast(1))

fun mediaDetailSourceLabel(): UiText = uiText("detail_info_source", "媒体源")

fun mediaDetailSourceEmptyValue(): UiText = uiText("detail_info_source_empty", "无")

fun mediaDetailIndexedTitleLabel(): UiText = uiText("detail_info_indexed_title", "索引标题")

fun mediaDetailIndexedTypeLabel(): UiText = uiText("detail_info_indexed_type", "索引类型")

fun mediaDetailAnimeLabel(): UiText = uiText("detail_info_anime", "番剧")

fun mediaDetailSeasonLabel(): UiText = uiText("detail_info_season", "季度")

fun mediaDetailEpisodeLabel(): UiText = uiText("detail_info_episode", "集数")

fun mediaDetailEpisodeTitleLabel(): UiText = uiText("detail_info_episode_title", "单集标题")

fun mediaDetailMetadataSourceLabel(): UiText = uiText("detail_info_metadata_source", "元数据来源")

fun mediaDetailMetadataIdLabel(): UiText = uiText("detail_info_metadata_id", "元数据 ID")

fun mediaDetailMetadataTitleLabel(): UiText = uiText("detail_info_metadata_title", "元数据标题")

fun mediaDetailIndexedSizeLabel(): UiText = uiText("detail_info_indexed_size", "索引大小")

fun mediaDetailIndexedModifiedLabel(): UiText = uiText("detail_info_indexed_modified", "索引修改时间")

fun mediaDetailBrowserItemLabel(): UiText = uiText("detail_info_browser_item", "浏览条目")

fun mediaDetailBrowserKindLabel(): UiText = uiText("detail_info_browser_kind", "浏览类型")

fun mediaDetailMimeLabel(): UiText = rawUiText("MIME")

fun mediaDetailBrowserSizeLabel(): UiText = uiText("detail_info_browser_size", "浏览大小")

fun mediaDetailBrowserModifiedLabel(): UiText = uiText("detail_info_browser_modified", "浏览修改时间")

fun mediaDetailResumeLabel(): UiText = uiText("detail_info_resume", "播放进度")

fun mediaDetailPlayCountLabel(): UiText = uiText("detail_info_play_count", "播放次数")

fun mediaDetailLastWatchedLabel(): UiText = uiText("detail_info_last_watched", "上次观看")

fun mediaDetailPlotLabel(): UiText = uiText("detail_info_plot", "简介")

fun mediaDetailPathLabel(): UiText = uiText("detail_info_path", "路径")

fun mediaDetailUnknownValue(): UiText = uiText("detail_info_unknown", "未知")

fun mediaDetailNotLinkedValue(): UiText = uiText("detail_info_not_linked", "未关联")

fun mediaDetailDirectoryValue(): UiText = uiText("detail_info_directory", "目录")

fun mediaDetailVideoValue(): UiText = uiText("detail_info_video", "视频")

fun mediaDetailFileValue(): UiText = uiText("detail_info_file", "文件")

fun mediaDetailIndexedKindValue(isDirectory: Boolean): UiText =
    if (isDirectory) mediaDetailDirectoryValue() else mediaDetailVideoValue()

fun detailExtrasSectionTitle(): UiText = uiText("detail_extras_title", "特典")

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
        .replace(Regex("""第\s*\d+\s*[集话話]"""), " ")
        .replace(Regex("""[._]+"""), " ")
        .replace(Regex("""\s*-\s*\d{1,3}\s*$"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '_', '.', '·')
    return normalized.takeIf { it.length >= 2 }
}

private fun String.detailBangumiSeasonlessCandidate(): String? {
    val seasonless = replace(Regex("""(?i)\bseason\s*\d+\b"""), " ")
        .replace(Regex("""(?i)\bs\s*\d+\b"""), " ")
        .replace(Regex("""第\s*[一二三四五六七八九十\d]+\s*[季期]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    return seasonless.takeIf { it.length >= 2 && !it.equals(this, ignoreCase = true) }
}
