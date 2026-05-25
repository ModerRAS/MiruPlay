package com.miruplay.tv.model

fun libraryTitleLabel(): String = "探索"

fun librarySubtitleLabel(): String = "本地媒体库 · Bangumi 元数据"

fun libraryScanActionLabel(): String = "扫描"

fun librarySettingsActionLabel(): String = "设置"

fun libraryAddSourceActionLabel(): String = "添加源"

fun libraryScanNowActionLabel(): String = "扫描媒体库"

fun libraryManualScanActionLabel(): String = "手动扫描"

fun libraryCancelScanActionLabel(): String = "取消扫描"

fun libraryNoSourcesMessage(): String = "添加媒体源开始使用"

fun libraryHasSourcesEmptyMessage(): String = "已配置媒体源\n点击扫描建立媒体库"

fun libraryNoContentAfterScanMessage(): String = "未找到番剧内容，请检查媒体源路径"

fun libraryScanningTitle(): String = "正在扫描媒体库..."

fun libraryFilesScannedLabel(filesScanned: Int): String =
    "已扫描 ${filesScanned.coerceAtLeast(0)} 个文件"

fun libraryScanFailedMessage(reason: String?): String {
    val cleanReason = reason?.trim().orEmpty()
    return if (cleanReason.isBlank()) {
        "扫描失败"
    } else {
        "扫描失败：$cleanReason"
    }
}

fun libraryScanningStatus(sourceName: String): String =
    localizedLibraryScanningStatus(sourceName)

fun libraryScanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    localizedLibraryScanCompleteStatus(filesIndexed, directoriesVisited)

fun libraryRescanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    localizedLibraryRescanCompleteStatus(filesIndexed, directoriesVisited)

fun localizedLibraryScanningStatus(sourceName: String): String =
    "正在扫描：${sourceName.trim().ifBlank { libraryTitleLabel() }}"

fun localizedLibraryScanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    "扫描完成：${filesIndexed.coerceAtLeast(0)} 个视频，${directoriesVisited.coerceAtLeast(0)} 个目录。"

fun localizedLibraryRescanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    "重扫完成：${filesIndexed.coerceAtLeast(0)} 个视频，${directoriesVisited.coerceAtLeast(0)} 个目录。"

fun localizedLibraryScanStatusText(status: String): String? {
    val trimmed = status.trim()
    if (trimmed.isLocalizedLibraryScanStatus()) {
        return trimmed
    }
    scanningStatusRegex.matchEntire(trimmed)?.let { match ->
        return localizedLibraryScanningStatus(match.groupValues[1])
    }
    scanCompleteStatusRegex.matchEntire(trimmed)?.let { match ->
        return localizedLibraryScanCompleteStatus(
            filesIndexed = match.groupValues[1].toIntOrNull() ?: 0,
            directoriesVisited = match.groupValues[2].toIntOrNull() ?: 0,
        )
    }
    rescanCompleteStatusRegex.matchEntire(trimmed)?.let { match ->
        return localizedLibraryRescanCompleteStatus(
            filesIndexed = match.groupValues[1].toIntOrNull() ?: 0,
            directoriesVisited = match.groupValues[2].toIntOrNull() ?: 0,
        )
    }
    return null
}

fun libraryCollectedCountLabel(count: Int): String =
    "已收录 ${count.coerceAtLeast(0)} 部"

fun librarySearchFieldLabel(): String = "搜索媒体库"

fun librarySearchActionLabel(): String = "搜索"

fun librarySearchResultCountLabel(count: Int): String =
    "${count.coerceAtLeast(0)} 部"

fun libraryFeaturedSectionTitle(): String = "最高热度"

fun libraryContinueWatchingSectionTitle(): String = "继续观看"

fun libraryContinueWatchingSubtitle(episodeNumber: Int?): String =
    episodeNumber?.let { "${libraryContinueWatchingSectionTitle()} ${it.toString().padStart(2, '0')}" }
        ?: libraryContinueWatchingSectionTitle()

fun recentPlaybackRefreshActionLabel(): String = "刷新"

fun recentPlaybackClearActionLabel(): String = "清除条目"

fun recentPlaybackEmptyMessage(): String = "开始播放后会在这里显示最近记录。"

fun mediaDetailsSectionTitle(): String = "媒体详情"

fun mediaDetailsEmptyMessage(): String = "选择媒体后会在这里显示详细信息。"

fun libraryRecentlyAddedSectionTitle(): String = "最近添加"

fun libraryPosterWallSectionTitle(): String = "海报墙"

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
