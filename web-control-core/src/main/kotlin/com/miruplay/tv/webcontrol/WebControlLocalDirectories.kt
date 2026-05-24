package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.LocalDirectoryBrowser

fun LocalDirectoryBrowser.Listing.toWebControlDirectoryDto(): LocalDirectoryDto =
    LocalDirectoryDto(
        path = path,
        displayPath = displayPath,
        parentPath = parentPath,
        entries = entries.map { it.toWebControlDirectoryEntryDto() },
    )

private fun LocalDirectoryBrowser.Entry.toWebControlDirectoryEntryDto(): LocalDirectoryEntryDto =
    LocalDirectoryEntryDto(
        name = name,
        path = path,
        canRead = canRead,
    )
