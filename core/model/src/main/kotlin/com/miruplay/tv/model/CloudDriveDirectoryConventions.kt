package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.uiText

const val CLOUD_DRIVE_ROOT_PATH = "/"
const val CLOUD_DRIVE_ROOT_DISPLAY_NAME = "CloudDrive 根目录"
const val DIRECTORY_BROWSER_ROOT_DISPLAY_NAME = "设备存储"

data class CloudDriveDirectoryItem(
    val name: String,
    val path: String,
)

fun normalizeCloudDriveDirectoryPath(path: String): String {
    val trimmed = path.trim().replace('\\', '/').trimEnd('/')
    return when {
        trimmed.isBlank() -> CLOUD_DRIVE_ROOT_PATH
        trimmed.startsWith('/') -> trimmed
        else -> "/$trimmed"
    }
}

fun cloudDriveDirectoryDisplayPath(path: String): String =
    normalizeCloudDriveDirectoryPath(path).let { normalized ->
        if (normalized == CLOUD_DRIVE_ROOT_PATH) CLOUD_DRIVE_ROOT_DISPLAY_NAME else normalized
    }

fun scopedCloudDriveDirectoryPath(
    requestedPath: String,
    rootPath: String,
): String {
    val requested = normalizeCloudDriveDirectoryPath(requestedPath)
    val root = normalizeCloudDriveDirectoryPath(rootPath)
    return when {
        root == CLOUD_DRIVE_ROOT_PATH -> requested
        requested == CLOUD_DRIVE_ROOT_PATH -> root
        requested == root || requested.startsWith("$root/") -> requested
        else -> root
    }
}

fun cloudDriveDirectoryParentPath(
    path: String,
    rootPath: String,
): String? {
    val normalizedPath = normalizeCloudDriveDirectoryPath(path)
    val normalizedRoot = normalizeCloudDriveDirectoryPath(rootPath)
    if (normalizedPath == normalizedRoot || normalizedPath == CLOUD_DRIVE_ROOT_PATH) return null
    val parent = normalizedPath.substringBeforeLast('/', "")
    if (parent == normalizedPath) return null
    return when {
        normalizedRoot == CLOUD_DRIVE_ROOT_PATH -> parent.ifBlank { CLOUD_DRIVE_ROOT_PATH }
        parent == normalizedRoot || parent.startsWith("$normalizedRoot/") -> parent
        else -> normalizedRoot
    }
}

fun cloudDriveDirectoryItems(
    entries: Iterable<CloudDriveDirectoryItem>,
): List<CloudDriveDirectoryItem> =
    entries.asSequence()
        .filter { it.path.isNotBlank() }
        .map { item ->
            val normalizedPath = normalizeCloudDriveDirectoryPath(item.path)
            item.copy(
                name = item.name.ifBlank { normalizedPath.substringAfterLast('/') },
                path = normalizedPath,
            )
        }
        .filter { it.name.isNotBlank() && !it.name.startsWith(".") }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .toList()

fun directoryBrowserTitleLabel(isLocal: Boolean): UiText =
    if (isLocal) {
        uiText("dir_browser_title_local", "选择本地媒体文件夹")
    } else {
        uiText("dir_browser_title_remote", "选择目录")
    }

fun directoryBrowserParentLabel(): UiText =
    uiText("dir_browser_parent", "上一级")

fun directoryBrowserParentActionLabel(isLocal: Boolean): UiText =
    if (isLocal) {
        directoryBrowserParentLabel()
    } else {
        uiText("dir_browser_parent_remote", "返回上级")
    }

fun directoryBrowserLoadingMessage(isLocal: Boolean): UiText =
    if (isLocal) {
        uiText("dir_browser_loading_local", "正在读取目录...")
    } else {
        uiText("dir_browser_loading_remote", "正在读取 CloudDrive2 目录...")
    }

fun directoryBrowserEmptyMessage(isLocal: Boolean): UiText =
    if (isLocal) {
        uiText("dir_browser_empty_local", "没有可进入的子文件夹。")
    } else {
        uiText("dir_browser_empty_remote", "当前目录没有可进入的子目录。")
    }

fun directoryBrowserCancelActionLabel(): UiText =
    uiText("dir_browser_cancel", "取消")

fun directoryBrowserCloseActionLabel(): UiText =
    uiText("dir_browser_close", "关闭")

fun directoryBrowserUseCurrentActionLabel(isLocal: Boolean): UiText =
    if (isLocal) {
        directoryBrowserSelectCurrentActionLabel()
    } else {
        uiText("dir_browser_use_current_remote", "使用当前目录")
    }

fun directoryBrowserSelectCurrentActionLabel(): UiText =
    uiText("dir_browser_use_current", "选择当前目录")

fun directoryBrowserRootDisplayName(isLocal: Boolean): String =
    if (isLocal) DIRECTORY_BROWSER_ROOT_DISPLAY_NAME else CLOUD_DRIVE_ROOT_DISPLAY_NAME
