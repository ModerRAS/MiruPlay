package com.miruplay.tv.model

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

fun directoryBrowserTitleLabel(isLocal: Boolean): String =
    if (isLocal) "选择本地媒体文件夹" else "选择目录"

fun directoryBrowserParentLabel(): String = "上一级"

fun directoryBrowserParentActionLabel(isLocal: Boolean): String =
    if (isLocal) directoryBrowserParentLabel() else "返回上级"

fun directoryBrowserLoadingMessage(isLocal: Boolean): String =
    if (isLocal) "正在读取目录..." else "正在读取 CloudDrive2 目录..."

fun directoryBrowserEmptyMessage(isLocal: Boolean): String =
    if (isLocal) "没有可进入的子文件夹。" else "当前目录没有可进入的子目录。"

fun directoryBrowserCancelActionLabel(): String = "取消"

fun directoryBrowserCloseActionLabel(): String = "关闭"

fun directoryBrowserUseCurrentActionLabel(isLocal: Boolean): String =
    if (isLocal) directoryBrowserSelectCurrentActionLabel() else "使用当前目录"

fun directoryBrowserSelectCurrentActionLabel(): String = "选择当前目录"

fun directoryBrowserRootDisplayName(isLocal: Boolean): String =
    if (isLocal) DIRECTORY_BROWSER_ROOT_DISPLAY_NAME else CLOUD_DRIVE_ROOT_DISPLAY_NAME
