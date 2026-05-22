package com.miruplay.tv.desktop

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.core.common.Result

internal enum class DesktopCloudDriveDirectoryTarget(
    val title: String,
    val selectedLabel: String,
) {
    INBOX(title = "选择收件目录", selectedLabel = "收件目录"),
    LIBRARY(title = "选择媒体库目录", selectedLabel = "媒体库目录"),
}

internal data class DesktopCloudDriveDirectoryEntry(
    val name: String,
    val path: String,
)

internal data class DesktopCloudDriveDirectoryBrowserState(
    val open: Boolean = false,
    val target: DesktopCloudDriveDirectoryTarget = DesktopCloudDriveDirectoryTarget.INBOX,
    val endpointUrl: String = "",
    val token: String = "",
    val rootPath: String = "/",
    val path: String = "/",
    val displayPath: String = "CloudDrive 根目录",
    val parentPath: String? = null,
    val entries: List<DesktopCloudDriveDirectoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

internal data class DesktopCloudDriveDirectorySelection(
    val target: DesktopCloudDriveDirectoryTarget,
    val path: String,
    val status: String,
)

internal suspend fun prepareDesktopCloudDriveDirectoryBrowser(
    client: CloudDriveClient,
    target: DesktopCloudDriveDirectoryTarget,
    endpointUrl: String,
    token: String,
    initialPath: String,
): Result<DesktopCloudDriveDirectoryBrowserState> {
    val endpoint = endpointUrl.trim()
    val apiToken = token.trim()
    return client.getApiTokenInfo(endpoint, apiToken).map { tokenInfo ->
        val rootPath = normalizeDesktopCloudDrivePath(tokenInfo.rootDir)
        DesktopCloudDriveDirectoryBrowserState(
            open = true,
            target = target,
            endpointUrl = endpoint,
            token = apiToken,
            rootPath = rootPath,
        ).loadingFor(initialPath.ifBlank { rootPath })
    }
}

internal suspend fun loadDesktopCloudDriveDirectory(
    client: CloudDriveClient,
    state: DesktopCloudDriveDirectoryBrowserState,
    requestedPath: String,
): Result<DesktopCloudDriveDirectoryBrowserState> {
    if (!state.open) return Result.success(state)
    val loadingState = state.loadingFor(requestedPath)
    return client.listFolder(
        endpoint = CloudDriveEndpoint(loadingState.endpointUrl, loadingState.token),
        path = loadingState.path,
        forceRefresh = false,
    ).map { files ->
        loadingState.copy(
            entries = cloudDriveDirectoryEntries(files),
            isLoading = false,
            message = null,
        )
    }
}

internal fun DesktopCloudDriveDirectoryBrowserState.loadingFor(
    requestedPath: String,
): DesktopCloudDriveDirectoryBrowserState {
    val scopedPath = desktopCloudDriveScopedPath(requestedPath, rootPath)
    return copy(
        path = scopedPath,
        displayPath = desktopCloudDriveDisplayPath(scopedPath),
        parentPath = desktopCloudDriveParentPath(scopedPath, rootPath),
        entries = emptyList(),
        isLoading = true,
        message = null,
    )
}

internal fun selectDesktopCloudDriveDirectory(
    target: DesktopCloudDriveDirectoryTarget,
    path: String,
): DesktopCloudDriveDirectorySelection {
    val normalized = normalizeDesktopCloudDrivePath(path)
    return DesktopCloudDriveDirectorySelection(
        target = target,
        path = normalized,
        status = "已选择${target.selectedLabel}：$normalized",
    )
}

internal fun normalizeDesktopCloudDrivePath(path: String): String {
    val trimmed = path.trim().replace('\\', '/').trimEnd('/')
    return when {
        trimmed.isBlank() -> "/"
        trimmed.startsWith('/') -> trimmed
        else -> "/$trimmed"
    }
}

internal fun desktopCloudDriveDisplayPath(path: String): String =
    normalizeDesktopCloudDrivePath(path).let { normalized ->
        if (normalized == "/") "CloudDrive 根目录" else normalized
    }

internal fun desktopCloudDriveParentPath(
    path: String,
    rootPath: String,
): String? {
    val normalizedPath = normalizeDesktopCloudDrivePath(path)
    val normalizedRoot = normalizeDesktopCloudDrivePath(rootPath)
    if (normalizedPath == normalizedRoot || normalizedPath == "/") return null
    val parent = normalizedPath.substringBeforeLast('/', "")
    if (parent.isBlank() || parent == normalizedPath) return null
    return when {
        normalizedRoot == "/" -> parent.ifBlank { "/" }
        parent == normalizedRoot || parent.startsWith("$normalizedRoot/") -> parent
        else -> normalizedRoot
    }
}

internal fun desktopCloudDriveScopedPath(
    requestedPath: String,
    rootPath: String,
): String {
    val requested = normalizeDesktopCloudDrivePath(requestedPath)
    val root = normalizeDesktopCloudDrivePath(rootPath)
    return when {
        root == "/" -> requested
        requested == "/" -> root
        requested == root || requested.startsWith("$root/") -> requested
        else -> root
    }
}

internal fun cloudDriveDirectoryEntries(files: List<CloudDriveFileInfo>): List<DesktopCloudDriveDirectoryEntry> =
    files.asSequence()
        .filter { it.isDirectory }
        .filter { !it.name.startsWith(".") }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.path.substringAfterLast('/') } })
        .map {
            DesktopCloudDriveDirectoryEntry(
                name = it.name.ifBlank { it.path.substringAfterLast('/') },
                path = normalizeDesktopCloudDrivePath(it.path),
            )
        }
        .toList()
