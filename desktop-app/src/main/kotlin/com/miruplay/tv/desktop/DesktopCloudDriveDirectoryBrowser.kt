package com.miruplay.tv.desktop

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CLOUD_DRIVE_ROOT_DISPLAY_NAME
import com.miruplay.tv.model.CLOUD_DRIVE_ROOT_PATH
import com.miruplay.tv.model.CloudDriveDirectoryItem
import com.miruplay.tv.model.cloudDriveDirectoryDisplayPath
import com.miruplay.tv.model.cloudDriveDirectoryItems
import com.miruplay.tv.model.cloudDriveDirectoryParentPath
import com.miruplay.tv.model.cloudDriveRssDirectorySelectedStatus
import com.miruplay.tv.model.cloudDriveRssInboxDirectoryPickerTitle
import com.miruplay.tv.model.cloudDriveRssInboxDirectorySelectedLabel
import com.miruplay.tv.model.cloudDriveRssLibraryDirectoryPickerTitle
import com.miruplay.tv.model.cloudDriveRssLibraryDirectorySelectedLabel
import com.miruplay.tv.model.normalizeCloudDriveDirectoryPath
import com.miruplay.tv.model.scopedCloudDriveDirectoryPath

internal enum class DesktopCloudDriveDirectoryTarget(
    val title: String,
    val selectedLabel: String,
) {
    INBOX(title = cloudDriveRssInboxDirectoryPickerTitle(), selectedLabel = cloudDriveRssInboxDirectorySelectedLabel()),
    LIBRARY(title = cloudDriveRssLibraryDirectoryPickerTitle(), selectedLabel = cloudDriveRssLibraryDirectorySelectedLabel()),
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
    val rootPath: String = CLOUD_DRIVE_ROOT_PATH,
    val path: String = CLOUD_DRIVE_ROOT_PATH,
    val displayPath: String = CLOUD_DRIVE_ROOT_DISPLAY_NAME,
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
        val rootPath = normalizeCloudDriveDirectoryPath(tokenInfo.rootDir)
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
    val scopedPath = scopedCloudDriveDirectoryPath(requestedPath, rootPath)
    return copy(
        path = scopedPath,
        displayPath = cloudDriveDirectoryDisplayPath(scopedPath),
        parentPath = cloudDriveDirectoryParentPath(scopedPath, rootPath),
        entries = emptyList(),
        isLoading = true,
        message = null,
    )
}

internal fun selectDesktopCloudDriveDirectory(
    target: DesktopCloudDriveDirectoryTarget,
    path: String,
): DesktopCloudDriveDirectorySelection {
    val normalized = normalizeCloudDriveDirectoryPath(path)
    return DesktopCloudDriveDirectorySelection(
        target = target,
        path = normalized,
        status = cloudDriveRssDirectorySelectedStatus(target.selectedLabel, normalized),
    )
}

internal fun cloudDriveDirectoryEntries(files: List<CloudDriveFileInfo>): List<DesktopCloudDriveDirectoryEntry> =
    cloudDriveDirectoryItems(
        files.filter { it.isDirectory }
            .map {
                CloudDriveDirectoryItem(
                    name = it.name,
                    path = it.path,
                )
            },
    ).map {
        DesktopCloudDriveDirectoryEntry(
            name = it.name,
            path = it.path,
        )
    }
