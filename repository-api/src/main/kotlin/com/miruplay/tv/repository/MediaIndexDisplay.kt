package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry

fun MediaIndexEntry.displayName(): String {
    val title = animeName?.takeIf { it.isNotBlank() }
        ?: metadataTitle?.takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
    val episode = episodeNumber?.let { " EP$it" }.orEmpty()
    val episodeTitle = episodeTitle?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
    return "$title$episode$episodeTitle"
}

fun MediaIndexEntry.displayLine(): String {
    val kind = if (isDirectory) "DIR" else "VID"
    return "[$kind] ${displayName()}  $path"
}

fun MediaIndexEntry.toBrowserEntry(): FileEntry =
    FileEntry(
        name = displayName(),
        path = path,
        isDirectory = isDirectory,
        size = fileSize,
        lastModified = lastModified,
    )
