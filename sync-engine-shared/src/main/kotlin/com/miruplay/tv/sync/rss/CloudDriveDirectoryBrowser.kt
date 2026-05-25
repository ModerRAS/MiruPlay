package com.miruplay.tv.sync.rss

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

enum class CloudDriveDirectoryTarget(
    val title: String,
    val selectedLabel: String,
) {
    INBOX(title = cloudDriveRssInboxDirectoryPickerTitle(), selectedLabel = cloudDriveRssInboxDirectorySelectedLabel()),
    LIBRARY(title = cloudDriveRssLibraryDirectoryPickerTitle(), selectedLabel = cloudDriveRssLibraryDirectorySelectedLabel()),
}

data class CloudDriveDirectoryEntry(
    val name: String,
    val path: String,
)

data class CloudDriveDirectoryBrowserState(
    val open: Boolean = false,
    val target: CloudDriveDirectoryTarget = CloudDriveDirectoryTarget.INBOX,
    val endpointUrl: String = "",
    val token: String = "",
    val rootPath: String = CLOUD_DRIVE_ROOT_PATH,
    val path: String = CLOUD_DRIVE_ROOT_PATH,
    val displayPath: String = CLOUD_DRIVE_ROOT_DISPLAY_NAME,
    val parentPath: String? = null,
    val entries: List<CloudDriveDirectoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

data class CloudDriveDirectorySelection(
    val target: CloudDriveDirectoryTarget,
    val path: String,
    val status: String,
)

suspend fun prepareCloudDriveDirectoryBrowser(
    client: CloudDriveClient,
    target: CloudDriveDirectoryTarget,
    endpointUrl: String,
    token: String,
    initialPath: String,
): Result<CloudDriveDirectoryBrowserState> {
    val endpoint = endpointUrl.trim()
    val apiToken = token.trim()
    return client.getApiTokenInfo(endpoint, apiToken).map { tokenInfo ->
        val rootPath = normalizeCloudDriveDirectoryPath(tokenInfo.rootDir)
        CloudDriveDirectoryBrowserState(
            open = true,
            target = target,
            endpointUrl = endpoint,
            token = apiToken,
            rootPath = rootPath,
        ).loadingFor(initialPath.ifBlank { rootPath })
    }
}

suspend fun loadCloudDriveDirectory(
    client: CloudDriveClient,
    state: CloudDriveDirectoryBrowserState,
    requestedPath: String,
): Result<CloudDriveDirectoryBrowserState> {
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

fun CloudDriveDirectoryBrowserState.loadingFor(
    requestedPath: String,
): CloudDriveDirectoryBrowserState {
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

fun selectCloudDriveDirectory(
    target: CloudDriveDirectoryTarget,
    path: String,
): CloudDriveDirectorySelection {
    val normalized = normalizeCloudDriveDirectoryPath(path)
    return CloudDriveDirectorySelection(
        target = target,
        path = normalized,
        status = cloudDriveRssDirectorySelectedStatus(target.selectedLabel, normalized),
    )
}

fun cloudDriveDirectoryEntries(files: List<CloudDriveFileInfo>): List<CloudDriveDirectoryEntry> =
    cloudDriveDirectoryItems(
        files.filter { it.isDirectory }
            .map {
                CloudDriveDirectoryItem(
                    name = it.name,
                    path = it.path,
                )
            },
    ).map {
        CloudDriveDirectoryEntry(
            name = it.name,
            path = it.path,
        )
    }
