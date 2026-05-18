package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord

fun MediaSourceInfo.displayLabel(): String =
    "$name · ${type.name}"

fun List<MediaSourceInfo>.upsertById(source: MediaSourceInfo): List<MediaSourceInfo> =
    map { if (it.id == source.id) source else it }.let { updated ->
        if (updated.none { it.id == source.id }) updated + source else updated
    }

fun ProgressRecord.mediaDisplayName(): String =
    MediaPathConventions.fileName(episodeId).ifBlank { episodeId }

fun localLibraryInitialStatus(): String =
    "Add a local library source or load an existing one."

fun remoteBrowserInitialStatus(): String =
    "Open a WebDAV or SMB source to browse it."

fun MediaSourceInfo.loadedStatus(saved: Boolean = false): String {
    val prefix = if (saved) "Loaded saved" else "Loaded"
    return when (type) {
        MediaSourceType.LOCAL -> "$prefix local source: $name"
        MediaSourceType.WEBDAV -> "$prefix WebDAV source: $name"
        MediaSourceType.SMB -> "$prefix SMB source: $name"
    }
}

fun MediaSourceInfo.readyStatus(): String =
    when (type) {
        MediaSourceType.LOCAL -> "Local source ready: $name"
        MediaSourceType.WEBDAV -> "WebDAV source ready: $name"
        MediaSourceType.SMB -> "SMB source ready: $name"
    }

fun localRootRequiredStatus(): String =
    "Enter a local library root first."

fun webDavUrlRequiredStatus(): String =
    "Enter a WebDAV URL first."

fun smbUrlRequiredStatus(): String =
    "Enter an SMB URL first."

fun openSourceBeforeScanningStatus(): String =
    "Open a source before scanning."

fun MediaSourceInfo.scanningStatus(): String =
    "Scanning $name..."

fun scanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    "Scan complete: $filesIndexed videos, $directoriesVisited directories."

fun rescanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    "Rescan complete: $filesIndexed videos, $directoriesVisited directories."

fun openSourceBeforeSearchingStatus(): String =
    "Open or scan a source before searching."

fun openSourceBeforeClearingIndexStatus(): String =
    "Open or scan a source before clearing its index."

fun indexClearedStatus(sourceId: Long): String =
    "Index cleared for source id: $sourceId."

fun sourceRemoveRequiredStatus(): String =
    "Open a source before removing it."

fun sourceRemovedStatus(): String =
    "Source removed. Associated index entries were cleared."

fun remoteRootStatus(): String =
    "Already at the source root."

fun openRemoteSourceBeforeBrowsingStatus(): String =
    "Open a remote source before browsing."

fun MediaSourceInfo.loadingRemoteDirectoryStatus(path: String): String =
    "Loading ${type.name} ${path.ifBlank { "/" }}..."

fun MediaSourceInfo.showingRemoteDirectoryStatus(entries: List<FileEntry>): String =
    "Showing ${entries.size} item(s) from $name."

fun MediaIndexEntry.selectedForPlaybackStatus(): String =
    "Selected ${displayName()} for playback."

fun FileEntry.selectedRemoteForPlaybackStatus(): String =
    "Selected remote media: $name. mpv will stream through the local bridge."

fun indexedSearchStatus(query: String, hasResults: Boolean, displayedResultCount: Int): String =
    if (!hasResults) {
        "No indexed media matched \"${query.trim()}\"."
    } else {
        "Showing $displayedResultCount indexed video result(s)."
    }
