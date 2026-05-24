package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CLOUD_DRIVE_ROOT_DISPLAY_NAME
import com.miruplay.tv.model.CLOUD_DRIVE_ROOT_PATH
import com.miruplay.tv.model.CloudDriveDirectoryPickerFormResult
import com.miruplay.tv.model.CloudDriveDirectoryItem
import com.miruplay.tv.model.cloudDriveDirectoryDisplayPath
import com.miruplay.tv.model.cloudDriveDirectoryItems
import com.miruplay.tv.model.cloudDriveDirectoryParentPath
import com.miruplay.tv.model.cloudDriveRssDirectoryBrowsingStatus
import com.miruplay.tv.model.cloudDriveRssDirectorySelectedStatus
import com.miruplay.tv.model.cloudDriveRssInboxDirectoryPickerTitle
import com.miruplay.tv.model.cloudDriveRssInboxDirectorySelectedLabel
import com.miruplay.tv.model.cloudDriveRssLibraryDirectoryPickerTitle
import com.miruplay.tv.model.cloudDriveRssLibraryDirectorySelectedLabel
import com.miruplay.tv.model.cloudDriveTokenLoginRequiredStatus
import com.miruplay.tv.model.normalizeCloudDriveDirectoryPath
import com.miruplay.tv.model.scopedCloudDriveDirectoryPath
import com.miruplay.tv.model.validateCloudDriveDirectoryPickerForm

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

sealed class CloudDriveDirectoryOpenResult {
    data class Ready(
        val state: CloudDriveDirectoryBrowserState,
        val loadPath: String,
    ) : CloudDriveDirectoryOpenResult()

    data class Invalid(
        val status: String,
    ) : CloudDriveDirectoryOpenResult()

    data class Failed(
        val error: AppError,
        val status: String,
    ) : CloudDriveDirectoryOpenResult()
}

sealed class CloudDriveDirectoryLoadResult {
    data object Ignored : CloudDriveDirectoryLoadResult()

    data class Loading(
        val state: CloudDriveDirectoryBrowserState,
    ) : CloudDriveDirectoryLoadResult()

    data class Loaded(
        val state: CloudDriveDirectoryBrowserState,
        val status: String,
    ) : CloudDriveDirectoryLoadResult()

    data class Failed(
        val state: CloudDriveDirectoryBrowserState,
        val status: String,
    ) : CloudDriveDirectoryLoadResult()
}

class CloudDriveDirectoryBrowserCoordinator(
    private val client: CloudDriveClient,
) {
    suspend fun open(
        target: CloudDriveDirectoryTarget,
        endpointUrl: String,
        tokenInput: String,
        savedToken: String?,
        initialPath: String,
    ): CloudDriveDirectoryOpenResult {
        val form = when (
            val result = validateCloudDriveDirectoryPickerForm(
                endpointUrl = endpointUrl,
                tokenInput = tokenInput,
                savedToken = savedToken,
            )
        ) {
            is CloudDriveDirectoryPickerFormResult.Ready -> result.request
            is CloudDriveDirectoryPickerFormResult.Invalid -> {
                return CloudDriveDirectoryOpenResult.Invalid(result.status)
            }
        }

        return when (
            val prepared = prepareCloudDriveDirectoryBrowser(
                client = client,
                target = target,
                endpointUrl = form.endpointUrl,
                token = form.token,
                initialPath = initialPath,
            )
        ) {
            is Result.Success -> CloudDriveDirectoryOpenResult.Ready(
                state = prepared.data,
                loadPath = prepared.data.path,
            )
            is Result.Error -> CloudDriveDirectoryOpenResult.Failed(
                error = prepared.error,
                status = prepared.error.toUserMessage(),
            )
        }
    }

    fun loading(
        state: CloudDriveDirectoryBrowserState,
        path: String,
    ): CloudDriveDirectoryLoadResult =
        when {
            !state.open -> CloudDriveDirectoryLoadResult.Ignored
            state.endpointUrl.isBlank() || state.token.isBlank() -> CloudDriveDirectoryLoadResult.Failed(
                state = state.copy(
                    isLoading = false,
                    message = cloudDriveTokenLoginRequiredStatus(),
                ),
                status = cloudDriveTokenLoginRequiredStatus(),
            )
            else -> CloudDriveDirectoryLoadResult.Loading(state.loadingFor(path))
        }

    suspend fun load(
        loadingState: CloudDriveDirectoryBrowserState,
    ): CloudDriveDirectoryLoadResult {
        if (!loadingState.open) return CloudDriveDirectoryLoadResult.Ignored
        return when (
            val loaded = loadCloudDriveDirectory(
                client = client,
                state = loadingState,
                requestedPath = loadingState.path,
            )
        ) {
            is Result.Success -> CloudDriveDirectoryLoadResult.Loaded(
                state = loaded.data,
                status = cloudDriveRssDirectoryBrowsingStatus(loaded.data.path),
            )
            is Result.Error -> CloudDriveDirectoryLoadResult.Failed(
                state = loadingState.copy(
                    isLoading = false,
                    message = loaded.error.toUserMessage(),
                ),
                status = loaded.error.toUserMessage(),
            )
        }
    }

    fun applyLoadedIfCurrent(
        currentState: CloudDriveDirectoryBrowserState,
        result: CloudDriveDirectoryLoadResult,
    ): CloudDriveDirectoryLoadResult =
        if (result.isCurrentFor(currentState)) result else CloudDriveDirectoryLoadResult.Ignored
}

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

private fun CloudDriveDirectoryLoadResult.isCurrentFor(
    currentState: CloudDriveDirectoryBrowserState,
): Boolean =
    when (this) {
        CloudDriveDirectoryLoadResult.Ignored -> false
        is CloudDriveDirectoryLoadResult.Loading -> currentState.matchesDirectoryRequest(state)
        is CloudDriveDirectoryLoadResult.Loaded -> currentState.matchesDirectoryRequest(state)
        is CloudDriveDirectoryLoadResult.Failed -> currentState.matchesDirectoryRequest(state)
    }

private fun CloudDriveDirectoryBrowserState.matchesDirectoryRequest(
    state: CloudDriveDirectoryBrowserState,
): Boolean =
    open &&
        target == state.target &&
        endpointUrl == state.endpointUrl &&
        token == state.token &&
        path == state.path
