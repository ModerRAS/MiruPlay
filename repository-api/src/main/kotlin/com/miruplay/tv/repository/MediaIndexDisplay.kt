package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaPathConventions

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

fun MediaIndexEntry.metadataQuery(): String? =
    animeName?.takeIf { it.isNotBlank() }
        ?: metadataTitle?.takeIf { it.isNotBlank() }
        ?: MediaPathConventions.stem(path).takeIf { it.isNotBlank() }

fun MediaIndexEntry.hasSameMediaKeyAs(other: MediaIndexEntry): Boolean =
    sourceId == other.sourceId && path == other.path

fun List<MediaIndexEntry>.replaceByMediaKey(updated: MediaIndexEntry): List<MediaIndexEntry> =
    map { entry -> if (entry.hasSameMediaKeyAs(updated)) updated else entry }

fun List<MediaIndexEntry>.replaceByMediaKeys(updatedEntries: List<MediaIndexEntry>): List<MediaIndexEntry> {
    if (updatedEntries.isEmpty()) return this
    val byKey = updatedEntries.associateBy { it.sourceId to it.path }
    return map { entry -> byKey[entry.sourceId to entry.path] ?: entry }
}
