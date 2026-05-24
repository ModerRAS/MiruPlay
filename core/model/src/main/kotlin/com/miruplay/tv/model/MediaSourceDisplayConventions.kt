package com.miruplay.tv.model

private const val DEFAULT_SOURCE_PICKER_LOCATION_LIMIT = 78

fun MediaSourceType.tvLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地"
        MediaSourceType.WEBDAV -> "WebDAV"
        MediaSourceType.SMB -> "SMB"
    }

fun MediaSourceType.tvBadgeLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地"
        MediaSourceType.WEBDAV -> "DAV"
        MediaSourceType.SMB -> "SMB"
    }

fun MediaSourceType.defaultSourceName(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地下载"
        MediaSourceType.WEBDAV -> "WebDAV 媒体库"
        MediaSourceType.SMB -> "SMB 共享"
    }

fun MediaSourceType.genericSourceName(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地媒体源"
        MediaSourceType.WEBDAV -> "WebDAV 媒体源"
        MediaSourceType.SMB -> "SMB 媒体源"
    }

fun MediaSourceType.tvSourceHint(): String =
    when (this) {
        MediaSourceType.LOCAL -> "设备文件夹"
        MediaSourceType.WEBDAV -> "HTTP 文件服务"
        MediaSourceType.SMB -> "局域网共享"
    }

fun MediaSourceType.tvLocationLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "媒体文件夹"
        MediaSourceType.WEBDAV -> "WebDAV 地址"
        MediaSourceType.SMB -> "SMB 地址"
    }

fun mediaSourceListTitleLabel(): String = "媒体源"

fun mediaSourceEmptyListMessage(): String = "还没有配置媒体源"

fun mediaSourceConfiguredCountLabel(count: Int): String = "已配置 $count 个源"

fun mediaSourceFormTitleLabel(isEditing: Boolean): String =
    if (isEditing) "编辑媒体源" else "添加媒体源"

fun mediaSourceFormDescriptionLabel(isEditing: Boolean): String =
    if (isEditing) {
        "修改媒体库位置或凭据，保存后会覆盖当前配置。"
    } else {
        "选择媒体库所在位置，保存后可在首页手动扫描。"
    }

fun mediaSourceNewActionLabel(): String = "新建"

fun mediaSourceDisplayNameFieldLabel(): String = "显示名称"

fun mediaSourceLocalLibraryRootFieldLabel(): String = "本地媒体库路径"

fun mediaSourceIndexQueryFieldLabel(): String = "索引搜索"

fun MediaSourceType.openSourceActionLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "打开本地"
        MediaSourceType.WEBDAV -> "打开 WebDAV"
        MediaSourceType.SMB -> "打开 SMB"
    }

fun mediaSourceScanActionLabel(): String = "扫描"

fun mediaSourceSearchActionLabel(): String = "搜索"

fun mediaSourceClearIndexActionLabel(): String = "清空索引"

fun mediaSourceRemoveActionLabel(): String = "移除媒体源"

fun MediaSourceType.sourceUsernameFieldLabel(): String = "${tvLabel()} 用户名"

fun MediaSourceType.sourcePasswordFieldLabel(): String = "${tvLabel()} 密码"

fun mediaSourceUsernameOptionalFieldLabel(): String = "用户名（可选）"

fun mediaSourcePasswordOptionalFieldLabel(isEditing: Boolean): String =
    if (isEditing) "密码（留空则保留）" else "密码（可选）"

fun mediaSourceSmbDomainFieldLabel(): String = "SMB 域"

fun mediaSourceScanSourceActionLabel(): String = "扫描媒体源"

fun mediaSourceRemoteBrowserTitleLabel(): String = "远程浏览"

fun mediaSourceUpActionLabel(): String = "上级"

fun mediaSourceRemoteBrowserEmptyMessage(): String = "先打开一个远程媒体源以浏览文件。"

fun mediaSourceRemoteBrowserItemTypeLabel(isDirectory: Boolean): String =
    if (isDirectory) mediaDetailDirectoryValue() else mediaDetailVideoValue()

fun mediaSourceRemoteBrowserPageUnitLabel(): String = "个条目"

fun mediaSourceSavedPickerTitleLabel(): String = "已保存媒体源"

fun mediaSourceSavedPickerSubtitleLabel(): String = "选择已配置媒体源"

fun mediaSourceSavedPickerEmptyMessage(): String = "没有已保存媒体源"

fun mediaSourceLocationMissingLabel(): String = "未配置路径"

fun MediaSourceType.sourceEndpointPlaceholderLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "填写本地媒体库路径"
        MediaSourceType.WEBDAV -> "填写 WebDAV 地址"
        MediaSourceType.SMB -> "填写 SMB 共享地址"
    }

fun mediaSourceTestConnectionActionLabel(isTesting: Boolean): String =
    if (isTesting) "测试中" else "测试连接"

fun mediaSourceSaveActionLabel(isEditing: Boolean): String =
    if (isEditing) "更新源" else "保存源"

fun mediaSourceLocalFolderEmptyLabel(): String = "尚未选择文件夹"

fun mediaSourceLocalFolderAuthorizedLabel(): String = "已授权访问"

fun mediaSourceLocalLibraryFallbackName(): String = "本地媒体库"

fun mediaSourceLocalPathDisplayName(path: String): String =
    path.trim()
        .replace('\\', '/')
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { mediaSourceLocalLibraryFallbackName() }

fun mediaSourceChooseFolderActionLabel(): String = "选择文件夹"

fun mediaSourceConnectionSuccessMessage(): String = "连接正常，可以保存并返回首页扫描。"

fun mediaSourceConnectionTestingMessage(): String = "正在验证连接..."

fun mediaSourceLocalLibraryInitialStatus(): String =
    "添加本地媒体源，或载入已保存的媒体源。"

fun mediaSourceRemoteBrowserInitialStatus(): String =
    "打开 WebDAV 或 SMB 媒体源后即可浏览文件。"

fun MediaSourceInfo.mediaSourceLoadedStatus(saved: Boolean = false): String {
    val source = "${tvDisplayName()} · ${type.tvLabel()}"
    return if (saved) {
        "已载入已保存媒体源：$source"
    } else {
        "已载入媒体源：$source"
    }
}

fun MediaSourceInfo.mediaSourceReadyStatus(): String {
    val sourceType = if (type == MediaSourceType.LOCAL) {
        "${type.tvLabel()}媒体源"
    } else {
        "${type.tvLabel()} 媒体源"
    }
    return "${sourceType}已就绪：${tvDisplayName()}"
}

fun mediaSourceLocalRootRequiredStatus(): String =
    "请先填写本地媒体库路径。"

fun mediaSourceWebDavUrlRequiredStatus(): String =
    "请先填写 WebDAV 地址。"

fun mediaSourceSmbUrlRequiredStatus(): String =
    "请先填写 SMB 地址。"

fun mediaSourceOpenBeforeScanningStatus(): String =
    "请先打开媒体源，再开始扫描。"

fun mediaSourceOpenBeforeSearchingStatus(): String =
    "请先打开或扫描媒体源，再搜索。"

fun mediaSourceOpenBeforeClearingIndexStatus(): String =
    "请先打开或扫描媒体源，再清空索引。"

fun mediaSourceIndexClearedStatus(sourceId: Long): String =
    "已清空媒体源 #$sourceId 的索引。"

fun mediaSourceRemoveRequiredStatus(): String =
    "请先打开媒体源，再移除。"

fun mediaSourceRemovedStatus(): String =
    "媒体源已移除，关联索引已清空。"

fun mediaSourceAlreadyAtRootStatus(): String =
    "已经在媒体源根目录。"

fun mediaSourceOpenRemoteBeforeBrowsingStatus(): String =
    "请先打开远程媒体源，再浏览。"

fun MediaSourceInfo.mediaSourceLoadingRemoteDirectoryStatus(path: String): String =
    "正在载入 ${type.tvLabel()}：${path.ifBlank { "/" }}"

fun MediaSourceInfo.mediaSourceShowingRemoteDirectoryStatus(itemCount: Int): String =
    "${tvDisplayName()} 中显示 ${itemCount.coerceAtLeast(0)} 个条目。"

fun mediaSourceSelectedForPlaybackStatus(displayName: String): String =
    "已选择播放：$displayName"

fun mediaSourceSelectedRemoteForPlaybackStatus(name: String): String =
    "已选择远程媒体：$name。mpv 将通过本地桥接串流。"

fun mediaSourceIndexedSearchStatus(
    query: String,
    hasResults: Boolean,
    displayedResultCount: Int,
): String =
    if (!hasResults) {
        "没有匹配 \"${query.trim()}\" 的索引媒体。"
    } else {
        "显示 ${displayedResultCount.coerceAtLeast(0)} 条索引视频结果。"
    }

fun MediaSourceInfo.tvConnectionStatusLabel(): String =
    if (isConnected) "可连接" else "待验证"

fun MediaSourceInfo.tvDisplayName(fallbackName: String? = null): String =
    name.ifBlank {
        fallbackName?.takeIf { it.isNotBlank() } ?: type.defaultSourceName()
    }

fun MediaSourceInfo.tvDisplayLabel(fallbackName: String? = null): String =
    "${tvDisplayName(fallbackName)} · ${type.tvLabel()}"

fun MediaSourceInfo.tvDisplayStatusLabel(): String =
    "${type.tvLabel()} · ${tvConnectionStatusLabel()}"

fun MediaSourceInfo.sourcePickerTitle(): String =
    tvDisplayLabel(fallbackName = type.genericSourceName())

fun MediaSourceInfo.sourcePickerSubtitle(maxLength: Int = DEFAULT_SOURCE_PICKER_LOCATION_LIMIT): String =
    sourceLocation()
        .orEmpty()
        .ifBlank { mediaSourceLocationMissingLabel() }
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
        mediaSourceLocalLibraryInitialStatus(),
        "Add a local library source or load an existing one." ->
            "添加本地媒体源，或载入已保存的媒体源。"
        mediaSourceRemoteBrowserInitialStatus(),
        "Open a WebDAV or SMB source to browse it." ->
            "打开 WebDAV 或 SMB 媒体源后即可浏览文件。"
        mediaSourceLocalRootRequiredStatus(),
        "Enter a local library root first." ->
            "请先填写本地媒体库路径。"
        mediaSourceWebDavUrlRequiredStatus(),
        "Enter a WebDAV URL first." ->
            "请先填写 WebDAV 地址。"
        mediaSourceSmbUrlRequiredStatus(),
        "Enter an SMB URL first." ->
            "请先填写 SMB 地址。"
        mediaSourceOpenBeforeScanningStatus(),
        "Open a source before scanning." ->
            "请先打开媒体源，再开始扫描。"
        mediaSourceOpenBeforeSearchingStatus(),
        "Open or scan a source before searching." ->
            "请先打开或扫描媒体源，再搜索。"
        mediaSourceOpenBeforeClearingIndexStatus(),
        "Open or scan a source before clearing its index." ->
            "请先打开或扫描媒体源，再清空索引。"
        mediaSourceRemoveRequiredStatus(),
        "Open a source before removing it." ->
            "请先打开媒体源，再移除。"
        mediaSourceRemovedStatus(),
        "Source removed. Associated index entries were cleared." ->
            "媒体源已移除，关联索引已清空。"
        mediaSourceAlreadyAtRootStatus(),
        "Already at the source root." ->
            "已经在媒体源根目录。"
        mediaSourceOpenRemoteBeforeBrowsingStatus(),
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
        ?.tvLabel()
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
