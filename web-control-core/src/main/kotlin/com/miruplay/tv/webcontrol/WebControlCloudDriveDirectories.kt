package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.cloudDriveTokenLoginRequiredStatus
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryEntry
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
import com.miruplay.tv.sync.rss.loadCloudDriveDirectory
import com.miruplay.tv.sync.rss.prepareCloudDriveDirectoryBrowser

suspend fun browseWebControlCloudDriveDirectory(
    client: CloudDriveClient,
    endpointUrl: String,
    fallbackEndpointUrl: suspend () -> String,
    token: String?,
    path: String,
    target: CloudDriveDirectoryTarget = CloudDriveDirectoryTarget.INBOX,
): Result<CloudDriveDirectoryDto> {
    val resolvedEndpoint = endpointUrl.trim().ifBlank { fallbackEndpointUrl().trim() }
    if (resolvedEndpoint.isBlank()) {
        throw IllegalArgumentException("请先填写 CloudDrive2 地址")
    }
    val apiToken = token?.trim().takeUnless { it.isNullOrBlank() }
        ?: throw IllegalArgumentException(cloudDriveTokenLoginRequiredStatus())

    return prepareCloudDriveDirectoryBrowser(
        client = client,
        target = target,
        endpointUrl = resolvedEndpoint,
        token = apiToken,
        initialPath = path,
    ).flatMap { prepared ->
        loadCloudDriveDirectory(
            client = client,
            state = prepared,
            requestedPath = prepared.path,
        )
    }.map { state ->
        state.toWebControlDirectoryDto()
    }
}

fun CloudDriveDirectoryBrowserState.toWebControlDirectoryDto(): CloudDriveDirectoryDto =
    CloudDriveDirectoryDto(
        path = path,
        displayPath = displayPath,
        parentPath = parentPath,
        entries = entries.toWebControlDirectoryEntryDtos(),
    )

private fun List<CloudDriveDirectoryEntry>.toWebControlDirectoryEntryDtos(): List<CloudDriveDirectoryEntryDto> =
    map {
        CloudDriveDirectoryEntryDto(
            name = it.name,
            path = it.path,
            canRead = true,
        )
    }
