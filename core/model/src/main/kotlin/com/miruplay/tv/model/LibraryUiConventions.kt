package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.uiText

fun libraryTitleLabel(): UiText = uiText("library_title", "探索")

fun librarySubtitleLabel(): UiText = uiText("library_subtitle", "本地媒体库 · Bangumi 元数据")

fun libraryScanActionLabel(): UiText = uiText("library_action_scan", "扫描")

fun librarySettingsActionLabel(): UiText = uiText("library_action_settings", "设置")

fun libraryAddSourceActionLabel(): UiText = uiText("library_action_add_source", "添加源")

fun libraryScanNowActionLabel(): UiText = uiText("library_action_scan_now", "扫描媒体库")

fun libraryManualScanActionLabel(): UiText = uiText("library_action_manual_scan", "手动扫描")

fun libraryCancelScanActionLabel(): UiText = uiText("library_action_cancel_scan", "取消扫描")

fun libraryNoSourcesMessage(): UiText = uiText("library_no_sources", "添加媒体源开始使用")

fun libraryHasSourcesEmptyMessage(): UiText =
    uiText("library_sources_empty", "已配置媒体源\n点击扫描建立媒体库")

fun libraryNoContentAfterScanMessage(): UiText =
    uiText("library_no_content_after_scan", "未找到番剧内容，请检查媒体源路径")

fun libraryScanningTitle(): UiText = uiText("library_scanning_title", "正在扫描媒体库...")

fun libraryFilesScannedLabel(filesScanned: Int): UiText {
    val safe = filesScanned.coerceAtLeast(0)
    return UiText("library_files_scanned", listOf(safe), "已扫描 $safe 个文件")
}

fun libraryScanFailedMessage(reason: String?): UiText {
    val cleanReason = reason?.trim().orEmpty()
    return if (cleanReason.isBlank()) {
        uiText("library_scan_failed", "扫描失败")
    } else {
        UiText("library_scan_failed_detail", listOf(cleanReason), "扫描失败：$cleanReason")
    }
}

fun libraryScanningStatus(sourceName: String): UiText =
    localizedLibraryScanningStatus(sourceName)

fun libraryScanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): UiText =
    localizedLibraryScanCompleteStatus(filesIndexed, directoriesVisited)

fun libraryRescanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): UiText =
    localizedLibraryRescanCompleteStatus(filesIndexed, directoriesVisited)

fun localizedLibraryScanningStatus(sourceName: String): UiText {
    val name = sourceName.trim().ifBlank { libraryTitleLabel().fallback }
    return UiText("library_scanning_status", listOf(name), "正在扫描：$name")
}

fun localizedLibraryScanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): UiText {
    val files = filesIndexed.coerceAtLeast(0)
    val dirs = directoriesVisited.coerceAtLeast(0)
    return UiText(
        "library_scan_complete_status",
        listOf(files, dirs),
        "扫描完成：$files 个视频，$dirs 个目录。",
    )
}

fun localizedLibraryRescanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): UiText {
    val files = filesIndexed.coerceAtLeast(0)
    val dirs = directoriesVisited.coerceAtLeast(0)
    return UiText(
        "library_rescan_complete_status",
        listOf(files, dirs),
        "重扫完成：$files 个视频，$dirs 个目录。",
    )
}

fun localizedLibraryScanStatusText(status: String): String? {
    val trimmed = status.trim()
    if (trimmed.isLocalizedLibraryScanStatus()) {
        return trimmed
    }
    scanningStatusRegex.matchEntire(trimmed)?.let { match ->
        return localizedLibraryScanningStatus(match.groupValues[1]).fallback
    }
    scanCompleteStatusRegex.matchEntire(trimmed)?.let { match ->
        return localizedLibraryScanCompleteStatus(
            filesIndexed = match.groupValues[1].toIntOrNull() ?: 0,
            directoriesVisited = match.groupValues[2].toIntOrNull() ?: 0,
        ).fallback
    }
    rescanCompleteStatusRegex.matchEntire(trimmed)?.let { match ->
        return localizedLibraryRescanCompleteStatus(
            filesIndexed = match.groupValues[1].toIntOrNull() ?: 0,
            directoriesVisited = match.groupValues[2].toIntOrNull() ?: 0,
        ).fallback
    }
    return null
}

fun libraryCollectedCountLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("library_collected_count", listOf(safe), "已收录 $safe 部")
}

fun librarySearchFieldLabel(): UiText = uiText("library_search_field", "搜索媒体库")

fun librarySearchActionLabel(): UiText = uiText("library_search_action", "搜索")

fun librarySearchResultCountLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("library_search_result_count", listOf(safe), "$safe 部")
}

fun libraryFeaturedSectionTitle(): UiText = uiText("library_featured_title", "最高热度")

fun libraryContinueWatchingSectionTitle(): UiText = uiText("library_continue_title", "继续观看")

fun libraryContinueWatchingSubtitle(episodeNumber: Int?): UiText =
    episodeNumber?.let {
        UiText("library_continue_subtitle", listOf(libraryContinueWatchingSectionTitle(), it), "${libraryContinueWatchingSectionTitle().fallback} ${it.toString().padStart(2, '0')}")
    }
        ?: libraryContinueWatchingSectionTitle()

fun recentPlaybackRefreshActionLabel(): UiText = uiText("library_recent_refresh", "刷新")

fun recentPlaybackClearActionLabel(): UiText = uiText("library_recent_clear", "清除条目")

fun recentPlaybackEmptyMessage(): UiText =
    uiText("library_recent_empty", "开始播放后会在这里显示最近记录。")

fun mediaDetailsSectionTitle(): UiText = uiText("library_details_title", "媒体详情")

fun mediaDetailsEmptyMessage(): UiText =
    uiText("library_details_empty", "选择媒体后会在这里显示详细信息。")

data class RecentPlaybackLabels(
    val title: UiText,
    val refreshAction: UiText,
    val clearAction: UiText,
    val emptyState: UiText,
)

fun recentPlaybackLabels(): RecentPlaybackLabels =
    RecentPlaybackLabels(
        title = libraryContinueWatchingSectionTitle(),
        refreshAction = recentPlaybackRefreshActionLabel(),
        clearAction = recentPlaybackClearActionLabel(),
        emptyState = recentPlaybackEmptyMessage(),
    )

data class MediaDetailsLabels(
    val title: UiText,
    val emptyState: UiText,
)

fun mediaDetailsLabels(): MediaDetailsLabels =
    MediaDetailsLabels(
        title = mediaDetailsSectionTitle(),
        emptyState = mediaDetailsEmptyMessage(),
    )

fun libraryRecentlyAddedSectionTitle(): UiText = uiText("library_recently_added", "最近添加")

fun libraryPosterWallSectionTitle(): UiText = uiText("library_poster_wall", "海报墙")

data class LibrarySourceLabels(
    val localLibraryRoot: UiText,
    val indexQuery: UiText,
    val openLocal: UiText,
    val scan: UiText,
    val search: UiText,
    val clearIndex: UiText,
    val removeSource: UiText,
    val webDavUrl: UiText,
    val webDavUser: UiText,
    val webDavPassword: UiText,
    val openWebDav: UiText,
    val smbUrl: UiText,
    val smbDomain: UiText,
    val smbUser: UiText,
    val smbPassword: UiText,
    val openSmb: UiText,
    val scanSource: UiText,
    val remoteBrowser: UiText,
    val up: UiText,
    val remoteEmpty: UiText,
)

fun librarySourceLabels(): LibrarySourceLabels =
    LibrarySourceLabels(
        localLibraryRoot = mediaSourceLocalLibraryRootFieldLabel(),
        indexQuery = mediaSourceIndexQueryFieldLabel(),
        openLocal = MediaSourceType.LOCAL.openSourceActionLabel(),
        scan = mediaSourceScanActionLabel(),
        search = mediaSourceSearchActionLabel(),
        clearIndex = mediaSourceClearIndexActionLabel(),
        removeSource = mediaSourceRemoveActionLabel(),
        webDavUrl = MediaSourceType.WEBDAV.tvLocationLabel(),
        webDavUser = MediaSourceType.WEBDAV.sourceUsernameFieldLabel(),
        webDavPassword = MediaSourceType.WEBDAV.sourcePasswordFieldLabel(),
        openWebDav = MediaSourceType.WEBDAV.openSourceActionLabel(),
        smbUrl = MediaSourceType.SMB.tvLocationLabel(),
        smbDomain = mediaSourceSmbDomainFieldLabel(),
        smbUser = MediaSourceType.SMB.sourceUsernameFieldLabel(),
        smbPassword = MediaSourceType.SMB.sourcePasswordFieldLabel(),
        openSmb = MediaSourceType.SMB.openSourceActionLabel(),
        scanSource = mediaSourceScanSourceActionLabel(),
        remoteBrowser = mediaSourceRemoteBrowserTitleLabel(),
        up = mediaSourceUpActionLabel(),
        remoteEmpty = mediaSourceRemoteBrowserEmptyMessage(),
    )

private val scanningStatusRegex = Regex("""^Scanning (.+)\.\.\.$""")
private val scanCompleteStatusRegex = Regex("""^Scan complete: (\d+) videos, (\d+) directories\.$""")
private val rescanCompleteStatusRegex = Regex("""^Rescan complete: (\d+) videos, (\d+) directories\.$""")
private val localizedScanningStatusRegex = Regex("""^正在扫描：.+$""")
private val localizedScanCompleteStatusRegex = Regex("""^扫描完成：\d+ 个视频，\d+ 个目录。$""")
private val localizedRescanCompleteStatusRegex = Regex("""^重扫完成：\d+ 个视频，\d+ 个目录。$""")

private fun String.isLocalizedLibraryScanStatus(): Boolean =
    localizedScanningStatusRegex.matches(this) ||
        localizedScanCompleteStatusRegex.matches(this) ||
        localizedRescanCompleteStatusRegex.matches(this)
