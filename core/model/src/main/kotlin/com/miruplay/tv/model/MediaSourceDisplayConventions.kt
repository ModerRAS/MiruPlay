package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.core.common.uiText

private const val DEFAULT_SOURCE_PICKER_LOCATION_LIMIT = 78
const val REMOTE_BROWSER_PAGE_SIZE = 8

fun MediaSourceType.tvLabel(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_type_local", "本地")
        MediaSourceType.WEBDAV -> rawUiText("WebDAV")
        MediaSourceType.SMB -> rawUiText("SMB")
        MediaSourceType.RSS -> rawUiText("RSS")
    }

fun MediaSourceType.tvBadgeLabel(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_badge_local", "本地")
        MediaSourceType.WEBDAV -> rawUiText("DAV")
        MediaSourceType.SMB -> rawUiText("SMB")
        MediaSourceType.RSS -> rawUiText("RSS")
    }

fun MediaSourceType.defaultSourceName(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_default_local", "本地下载")
        MediaSourceType.WEBDAV -> uiText("mediasource_default_webdav", "WebDAV 媒体库")
        MediaSourceType.SMB -> uiText("mediasource_default_smb", "SMB 共享")
        MediaSourceType.RSS -> uiText("mediasource_default_rss", "RSS 订阅")
    }

fun MediaSourceType.genericSourceName(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_generic_local", "本地媒体源")
        MediaSourceType.WEBDAV -> uiText("mediasource_generic_webdav", "WebDAV 媒体源")
        MediaSourceType.SMB -> uiText("mediasource_generic_smb", "SMB 媒体源")
        MediaSourceType.RSS -> uiText("mediasource_generic_rss", "RSS 在线源")
    }

fun MediaSourceType.tvSourceHint(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_hint_local", "设备文件夹")
        MediaSourceType.WEBDAV -> uiText("mediasource_hint_webdav", "HTTP 文件服务")
        MediaSourceType.SMB -> uiText("mediasource_hint_smb", "局域网共享")
        MediaSourceType.RSS -> uiText("mediasource_hint_rss", "在线 RSS")
    }

fun MediaSourceType.tvLocationLabel(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_location_local", "媒体文件夹")
        MediaSourceType.WEBDAV -> uiText("mediasource_location_webdav", "WebDAV 地址")
        MediaSourceType.SMB -> uiText("mediasource_location_smb", "SMB 地址")
        MediaSourceType.RSS -> uiText("mediasource_location_rss", "RSS 地址")
    }

fun mediaSourceListTitleLabel(): UiText = uiText("mediasource_list_title", "媒体源")

fun mediaSourceEmptyListMessage(): UiText = uiText("mediasource_empty_list", "还没有配置媒体源")

fun mediaSourceConfiguredCountLabel(count: Int): UiText {
    val safe = count.coerceAtLeast(0)
    return UiText("mediasource_configured_count", listOf(safe), "已配置 $safe 个源")
}

fun mediaSourceFormTitleLabel(isEditing: Boolean): UiText =
    if (isEditing) {
        uiText("mediasource_form_title_edit", "编辑媒体源")
    } else {
        uiText("mediasource_form_title_add", "添加媒体源")
    }

fun mediaSourceFormDescriptionLabel(isEditing: Boolean): UiText =
    if (isEditing) {
        uiText("mediasource_form_desc_edit", "修改媒体库位置或凭据，保存后会覆盖当前配置。")
    } else {
        uiText("mediasource_form_desc_add", "选择媒体库所在位置，保存后可在首页手动扫描。")
    }

fun mediaSourceNewActionLabel(): UiText = uiText("mediasource_action_new", "新建")

fun mediaSourceDisplayNameFieldLabel(): UiText = uiText("mediasource_field_display_name", "显示名称")

fun mediaSourceLocalLibraryRootFieldLabel(): UiText =
    uiText("mediasource_field_local_root", "本地媒体库路径")

fun mediaSourceIndexQueryFieldLabel(): UiText = uiText("mediasource_field_index_query", "索引搜索")

fun MediaSourceType.openSourceActionLabel(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_open_local", "打开本地")
        MediaSourceType.WEBDAV -> uiText("mediasource_open_webdav", "打开 WebDAV")
        MediaSourceType.SMB -> uiText("mediasource_open_smb", "打开 SMB")
        MediaSourceType.RSS -> uiText("mediasource_open_rss", "打开 RSS")
    }

fun mediaSourceScanActionLabel(): UiText = uiText("mediasource_action_scan", "扫描")

fun mediaSourceSearchActionLabel(): UiText = uiText("mediasource_action_search", "搜索")

fun mediaSourceClearIndexActionLabel(): UiText = uiText("mediasource_action_clear_index", "清空索引")

fun mediaSourceRemoveActionLabel(): UiText = uiText("mediasource_action_remove", "移除媒体源")

fun MediaSourceType.sourceUsernameFieldLabel(): UiText {
    val label = tvLabel()
    return UiText("mediasource_username_field", listOf(label), "${label.fallback} 用户名")
}

fun MediaSourceType.sourcePasswordFieldLabel(): UiText {
    val label = tvLabel()
    return UiText("mediasource_password_field", listOf(label), "${label.fallback} 密码")
}

fun mediaSourceUsernameOptionalFieldLabel(): UiText =
    uiText("mediasource_username_optional", "用户名（可选）")

fun mediaSourcePasswordOptionalFieldLabel(isEditing: Boolean): UiText =
    if (isEditing) {
        uiText("mediasource_password_keep", "密码（留空则保留）")
    } else {
        uiText("mediasource_password_optional", "密码（可选）")
    }

fun mediaSourceSmbDomainFieldLabel(): UiText = uiText("mediasource_smb_domain", "SMB 域")

fun mediaSourceScanSourceActionLabel(): UiText = uiText("mediasource_action_scan_source", "扫描媒体源")

fun mediaSourceRemoteBrowserTitleLabel(): UiText = uiText("mediasource_remote_browser_title", "远程浏览")

fun mediaSourceUpActionLabel(): UiText = uiText("mediasource_action_up", "上级")

fun mediaSourceRemoteBrowserEmptyMessage(): UiText =
    uiText("mediasource_remote_browser_empty", "先打开一个远程媒体源以浏览文件。")

fun mediaSourceRemoteBrowserItemTypeLabel(isDirectory: Boolean): UiText =
    if (isDirectory) mediaDetailDirectoryValue() else mediaDetailVideoValue()

fun mediaSourceRemoteBrowserPageUnitLabel(): UiText = uiText("mediasource_page_unit", "个条目")

fun remoteBrowserPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = REMOTE_BROWSER_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

fun remoteBrowserCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = REMOTE_BROWSER_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

fun remoteBrowserPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): UiText? =
    pagedListPageSummary(
        pageStart = remoteBrowserCoercedPageStart(pageStart, itemCount),
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = REMOTE_BROWSER_PAGE_SIZE,
        unitLabel = mediaSourceRemoteBrowserPageUnitLabel(),
    )

fun mediaSourceSavedPickerTitleLabel(): UiText = uiText("mediasource_saved_picker_title", "已保存媒体源")

fun mediaSourceSavedPickerSubtitleLabel(): UiText =
    uiText("mediasource_saved_picker_subtitle", "选择已配置媒体源")

fun mediaSourceSavedPickerEmptyMessage(): UiText =
    uiText("mediasource_saved_picker_empty", "没有已保存媒体源")

fun mediaSourceLocationMissingLabel(): UiText = uiText("mediasource_location_missing", "未配置路径")

fun MediaSourceType.sourceEndpointPlaceholderLabel(): UiText =
    when (this) {
        MediaSourceType.LOCAL -> uiText("mediasource_endpoint_local", "填写本地媒体库路径")
        MediaSourceType.WEBDAV -> uiText("mediasource_endpoint_webdav", "填写 WebDAV 地址")
        MediaSourceType.SMB -> uiText("mediasource_endpoint_smb", "填写 SMB 共享地址")
        MediaSourceType.RSS -> uiText("mediasource_endpoint_rss", "填写 RSS 地址")
    }

fun mediaSourceTestConnectionActionLabel(isTesting: Boolean): UiText =
    if (isTesting) {
        uiText("mediasource_testing", "测试中")
    } else {
        uiText("mediasource_test_connection", "测试连接")
    }

fun mediaSourceSaveActionLabel(isEditing: Boolean): UiText =
    if (isEditing) {
        uiText("mediasource_save_update", "更新源")
    } else {
        uiText("mediasource_save_add", "保存源")
    }

fun mediaSourceLocalFolderEmptyLabel(): UiText = uiText("mediasource_folder_empty", "尚未选择文件夹")

fun mediaSourceLocalFolderAuthorizedLabel(): UiText =
    uiText("mediasource_folder_authorized", "已授权访问")

fun mediaSourceLocalLibraryFallbackName(): UiText = uiText("mediasource_local_fallback", "本地媒体库")

fun mediaSourceLocalPathDisplayName(path: String): String =
    path.trim()
        .replace('\\', '/')
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { mediaSourceLocalLibraryFallbackName().fallback }

fun mediaSourceLocalScanDisplayName(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("content://", ignoreCase = true)) {
        val tail = trimmed.substringAfterLast('/')
        val decoded = MediaPathConventions.decodePath(tail)
        return decoded
            .substringAfter(':', decoded)
            .substringAfterLast('/')
    }
    return MediaPathConventions.fileName(trimmed).ifBlank { trimmed }
}

fun mediaSourceChooseFolderActionLabel(): UiText = uiText("mediasource_choose_folder", "选择文件夹")

fun mediaSourceConnectionSuccessMessage(): UiText =
    uiText("mediasource_connection_success", "连接正常，可以保存并返回首页扫描。")

fun mediaSourceConnectionFailedMessage(): UiText =
    uiText("mediasource_connection_failed", "无法连接到服务器")

fun mediaSourceConnectionTestingMessage(): UiText =
    uiText("mediasource_connection_testing", "正在验证连接...")

fun mediaSourceLocalLibraryInitialStatus(): UiText =
    uiText("mediasource_status_initial_local", "添加本地媒体源，或载入已保存的媒体源。")

fun mediaSourceRemoteBrowserInitialStatus(): UiText =
    uiText("mediasource_status_initial_remote", "打开 WebDAV 或 SMB 媒体源后即可浏览文件。")

fun MediaSourceInfo.mediaSourceLoadedStatus(saved: Boolean = false): UiText {
    val label = type.tvLabel()
    val source = "${tvDisplayName()} · ${label.fallback}"
    return if (saved) {
        UiText("mediasource_status_loaded_saved", listOf(source), "已载入已保存媒体源：$source")
    } else {
        UiText("mediasource_status_loaded", listOf(source), "已载入媒体源：$source")
    }
}

fun MediaSourceInfo.mediaSourceReadyStatus(): UiText {
    val label = type.tvLabel()
    val prefix = if (type == MediaSourceType.LOCAL) {
        UiText(
            "mediasource_ready_prefix_local",
            listOf(label),
            "${label.fallback}媒体源",
        )
    } else {
        UiText(
            "mediasource_ready_prefix_remote",
            listOf(label),
            "${label.fallback} 媒体源",
        )
    }
    return UiText(
        "mediasource_status_ready",
        listOf(prefix, tvDisplayName()),
        "${prefix.fallback}已就绪：${tvDisplayName()}",
    )
}

fun mediaSourceLocalRootRequiredStatus(): UiText =
    uiText("mediasource_status_require_local_root", "请先填写本地媒体库路径。")

fun mediaSourceWebDavUrlRequiredStatus(): UiText =
    uiText("mediasource_status_require_webdav_url", "请先填写 WebDAV 地址。")

fun mediaSourceSmbUrlRequiredStatus(): UiText =
    uiText("mediasource_status_require_smb_url", "请先填写 SMB 地址。")

fun mediaSourceOpenBeforeScanningStatus(): UiText =
    uiText("mediasource_status_open_before_scan", "请先打开媒体源，再开始扫描。")

fun mediaSourceOpenBeforeSearchingStatus(): UiText =
    uiText("mediasource_status_open_before_search", "请先打开或扫描媒体源，再搜索。")

fun mediaSourceOpenBeforeClearingIndexStatus(): UiText =
    uiText("mediasource_status_open_before_clear", "请先打开或扫描媒体源，再清空索引。")

fun mediaSourceIndexClearedStatus(sourceId: Long): UiText =
    UiText("mediasource_status_index_cleared", listOf(sourceId), "已清空媒体源 #$sourceId 的索引。")

fun mediaSourceRemoveRequiredStatus(): UiText =
    uiText("mediasource_status_require_open_before_remove", "请先打开媒体源，再移除。")

fun mediaSourceRemovedStatus(): UiText =
    uiText("mediasource_status_removed", "媒体源已移除，关联索引已清空。")

fun mediaSourceAlreadyAtRootStatus(): UiText =
    uiText("mediasource_status_already_root", "已经在媒体源根目录。")

fun mediaSourceOpenRemoteBeforeBrowsingStatus(): UiText =
    uiText("mediasource_status_open_remote_before_browse", "请先打开远程媒体源，再浏览。")

fun MediaSourceInfo.mediaSourceLoadingRemoteDirectoryStatus(path: String): UiText {
    val label = type.tvLabel()
    val dir = path.ifBlank { "/" }
    return UiText(
        "mediasource_status_loading_remote",
        listOf(label, dir),
        "正在载入 ${label.fallback}：$dir",
    )
}

fun MediaSourceInfo.mediaSourceShowingRemoteDirectoryStatus(itemCount: Int): UiText {
    val safe = itemCount.coerceAtLeast(0)
    val name = tvDisplayName()
    return UiText(
        "mediasource_status_showing_remote",
        listOf(name, safe),
        "$name 中显示 $safe 个条目。",
    )
}

fun mediaSourceSelectedForPlaybackStatus(displayName: String): UiText =
    UiText("mediasource_status_selected_playback", listOf(displayName), "已选择播放：$displayName")

fun mediaSourceSelectedRemoteForPlaybackStatus(name: String): UiText =
    UiText(
        "mediasource_status_selected_remote",
        listOf(name),
        "已选择远程媒体：$name。mpv 将通过本地桥接串流。",
    )

fun mediaSourceIndexedSearchStatus(
    query: String,
    hasResults: Boolean,
    displayedResultCount: Int,
): UiText =
    if (!hasResults) {
        val q = query.trim()
        UiText("mediasource_indexed_no_match", listOf(q), "没有匹配 \"$q\" 的索引媒体。")
    } else {
        val safe = displayedResultCount.coerceAtLeast(0)
        UiText("mediasource_indexed_results", listOf(safe), "显示 $safe 条索引视频结果。")
    }

fun MediaSourceInfo.tvConnectionStatusLabel(): UiText =
    if (isConnected) {
        uiText("mediasource_connection_ok", "可连接")
    } else {
        uiText("mediasource_connection_pending", "待验证")
    }

fun MediaSourceInfo.tvDisplayName(fallbackName: String? = null): String =
    name.ifBlank {
        fallbackName?.takeIf { it.isNotBlank() } ?: type.defaultSourceName().fallback
    }

fun MediaSourceInfo.tvDisplayLabel(fallbackName: String? = null): UiText {
    val label = type.tvLabel()
    val displayName = tvDisplayName(fallbackName)
    return UiText(
        "mediasource_display_label",
        listOf(displayName, label),
        "$displayName · ${label.fallback}",
    )
}

fun MediaSourceInfo.tvDisplayStatusLabel(): UiText {
    val label = type.tvLabel()
    val status = tvConnectionStatusLabel()
    return UiText(
        "mediasource_display_status",
        listOf(label, status),
        "${label.fallback} · ${status.fallback}",
    )
}

fun MediaSourceInfo.scanResultDisplayName(rootPath: String? = null): String =
    when (type) {
        MediaSourceType.LOCAL -> connectionDisplayName().ifBlank {
            val localPath = rootPath ?: localRootPath().orEmpty()
            mediaSourceLocalScanDisplayName(localPath)
                .ifBlank { tvDisplayName(fallbackName = mediaSourceLocalLibraryFallbackName().fallback) }
        }
        MediaSourceType.WEBDAV,
        MediaSourceType.SMB,
        MediaSourceType.RSS -> tvDisplayName(fallbackName = type.defaultSourceName().fallback)
    }

fun MediaSourceInfo.sourcePickerTitle(): UiText =
    tvDisplayLabel(fallbackName = type.genericSourceName().fallback)

fun MediaSourceInfo.sourcePickerSubtitle(maxLength: Int = DEFAULT_SOURCE_PICKER_LOCATION_LIMIT): String =
    sourceLocation()
        .orEmpty()
        .ifBlank { mediaSourceLocationMissingLabel().fallback }
        .compactMiddleText(maxLength)

private const val REMOTE_SOURCE_PREVIEW_LIMIT = 70
private const val REMOTE_BROWSER_PATH_LIMIT = 86

fun remoteSourcePreview(
    value: String,
    fallback: String,
    maxLength: Int = REMOTE_SOURCE_PREVIEW_LIMIT,
): String =
    value.trim()
        .ifBlank { fallback }
        .compactMiddleText(maxLength)

fun remoteBrowserPathPreview(
    path: String,
    maxLength: Int = REMOTE_BROWSER_PATH_LIMIT,
): String =
    path.trim()
        .ifBlank { "/" }
        .compactMiddleText(maxLength)

fun String.compactMiddleText(maxLength: Int): String {
    val safeMaxLength = maxLength.coerceAtLeast(5)
    if (length <= safeMaxLength) return this

    val marker = "..."
    val available = safeMaxLength - marker.length
    val headLength = available / 2
    val tailLength = available - headLength
    return take(headLength) + marker + takeLast(tailLength)
}

fun localizedMediaSourceStatusText(status: String): String? {
    localizedLibraryScanStatusText(status)?.let { return it }
    return when (status.trim()) {
        mediaSourceLocalLibraryInitialStatus().fallback,
        "Add a local library source or load an existing one." ->
            "添加本地媒体源，或载入已保存的媒体源。"
        mediaSourceRemoteBrowserInitialStatus().fallback,
        "Open a WebDAV or SMB source to browse it." ->
            "打开 WebDAV 或 SMB 媒体源后即可浏览文件。"
        mediaSourceLocalRootRequiredStatus().fallback,
        "Enter a local library root first." ->
            "请先填写本地媒体库路径。"
        mediaSourceWebDavUrlRequiredStatus().fallback,
        "Enter a WebDAV URL first." ->
            "请先填写 WebDAV 地址。"
        mediaSourceSmbUrlRequiredStatus().fallback,
        "Enter an SMB URL first." ->
            "请先填写 SMB 地址。"
        mediaSourceOpenBeforeScanningStatus().fallback,
        "Open a source before scanning." ->
            "请先打开媒体源，再开始扫描。"
        mediaSourceOpenBeforeSearchingStatus().fallback,
        "Open or scan a source before searching." ->
            "请先打开或扫描媒体源，再搜索。"
        mediaSourceOpenBeforeClearingIndexStatus().fallback,
        "Open or scan a source before clearing its index." ->
            "请先打开或扫描媒体源，再清空索引。"
        mediaSourceRemoveRequiredStatus().fallback,
        "Open a source before removing it." ->
            "请先打开媒体源，再移除。"
        mediaSourceRemovedStatus().fallback,
        "Source removed. Associated index entries were cleared." ->
            "媒体源已移除，关联索引已清空。"
        mediaSourceAlreadyAtRootStatus().fallback,
        "Already at the source root." ->
            "已经在媒体源根目录。"
        mediaSourceOpenRemoteBeforeBrowsingStatus().fallback,
        "Open a remote source before browsing." ->
            "请先打开远程媒体源，再浏览。"
        else -> localizedDynamicMediaSourceStatusText(status.trim())
    }
}

fun mediaSourceStatusText(status: String): String =
    localizedMediaSourceStatusText(status) ?: status

private fun localizedDynamicMediaSourceStatusText(status: String): String? {
    localizedSharedMediaSourceStatusRegexes.forEach { regex ->
        if (regex.matches(status)) return status
    }
    loadedSourceStatusRegex.matchEntire(status)?.let { match ->
        val saved = match.groupValues[1].isNotBlank()
        val type = match.groupValues[2].toMediaSourceTypeLabel()
        val name = match.groupValues[3]
        return if (saved) {
            "已载入已保存媒体源：$name · $type"
        } else {
            "已载入媒体源：$name · $type"
        }
    }
    readySourceStatusRegex.matchEntire(status)?.let { match ->
        val type = match.groupValues[1].toMediaSourceTypeLabel()
        val sourceType = if (type == "本地") "${type}媒体源" else "$type 媒体源"
        return "${sourceType}已就绪：${match.groupValues[2]}"
    }
    indexClearedStatusRegex.matchEntire(status)?.let { match ->
        return "已清空媒体源 #${match.groupValues[1]} 的索引。"
    }
    loadingRemoteStatusRegex.matchEntire(status)?.let { match ->
        return "正在载入 ${match.groupValues[1].toMediaSourceTypeLabel()}：${match.groupValues[2]}"
    }
    showingRemoteStatusRegex.matchEntire(status)?.let { match ->
        return "${match.groupValues[2]} 中显示 ${match.groupValues[1]} 个条目。"
    }
    remotePlaybackStatusRegex.matchEntire(status)?.let { match ->
        return "已选择远程媒体：${match.groupValues[1]}。mpv 将通过本地桥接串流。"
    }
    selectedPlaybackStatusRegex.matchEntire(status)?.let { match ->
        return "已选择播放：${match.groupValues[1]}"
    }
    indexedNoMatchStatusRegex.matchEntire(status)?.let { match ->
        return "没有匹配 \"${match.groupValues[1]}\" 的索引媒体。"
    }
    indexedResultStatusRegex.matchEntire(status)?.let { match ->
        return "显示 ${match.groupValues[1]} 条索引视频结果。"
    }
    return null
}

private fun String.toMediaSourceTypeLabel(): String =
    runCatching { MediaSourceType.valueOf(uppercase()) }
        .getOrNull()
        ?.tvLabel()?.fallback
        ?: this

private val loadedSourceStatusRegex = Regex("""^Loaded( saved)? (local|WebDAV|SMB) source: (.+)$""")
private val readySourceStatusRegex = Regex("""^(Local|WebDAV|SMB) source ready: (.+)$""")
private val indexClearedStatusRegex = Regex("""^Index cleared for source id: (\d+)\.$""")
private val loadingRemoteStatusRegex = Regex("""^Loading (LOCAL|WEBDAV|SMB) (.+)\.\.\.$""")
private val showingRemoteStatusRegex = Regex("""^Showing (\d+) item\(s\) from (.+)\.$""")
private val remotePlaybackStatusRegex = Regex("""^Selected remote media: (.+)\. mpv will stream through the local bridge\.$""")
private val selectedPlaybackStatusRegex = Regex("""^Selected (.+) for playback\.$""")
private val indexedNoMatchStatusRegex = Regex("""^No indexed media matched "(.*)"\.$""")
private val indexedResultStatusRegex = Regex("""^Showing (\d+) indexed video result\(s\)\.$""")
private val localizedSharedMediaSourceStatusRegexes = listOf(
    Regex("""^已载入(已保存)?媒体源：.+ · .+$"""),
    Regex("""^(本地媒体源|WebDAV 媒体源|SMB 媒体源)已就绪：.+$"""),
    Regex("""^已清空媒体源 #\d+ 的索引。$"""),
    Regex("""^正在载入 (本地|WebDAV|SMB)：.+$"""),
    Regex("""^.+ 中显示 \d+ 个条目。$"""),
    Regex("""^已选择播放：.+$"""),
    Regex("""^已选择远程媒体：.+。mpv 将通过本地桥接串流。$"""),
    Regex("""^没有匹配 ".+" 的索引媒体。$"""),
    Regex("""^显示 \d+ 条索引视频结果。$"""),
)
