package com.miruplay.tv.webcontrol

import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryEntry

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
