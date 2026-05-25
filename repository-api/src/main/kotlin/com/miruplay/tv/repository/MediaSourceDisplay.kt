package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.mediaSourceAlreadyAtRootStatus
import com.miruplay.tv.model.mediaSourceIndexClearedStatus
import com.miruplay.tv.model.mediaSourceIndexedSearchStatus
import com.miruplay.tv.model.mediaSourceLoadingRemoteDirectoryStatus
import com.miruplay.tv.model.mediaSourceLoadedStatus
import com.miruplay.tv.model.mediaSourceLocalLibraryInitialStatus
import com.miruplay.tv.model.mediaSourceLocalRootRequiredStatus
import com.miruplay.tv.model.mediaSourceOpenBeforeClearingIndexStatus
import com.miruplay.tv.model.mediaSourceOpenBeforeScanningStatus
import com.miruplay.tv.model.mediaSourceOpenBeforeSearchingStatus
import com.miruplay.tv.model.mediaSourceOpenRemoteBeforeBrowsingStatus
import com.miruplay.tv.model.mediaSourceReadyStatus
import com.miruplay.tv.model.mediaSourceRemoteBrowserInitialStatus
import com.miruplay.tv.model.mediaSourceRemoveRequiredStatus
import com.miruplay.tv.model.mediaSourceRemovedStatus
import com.miruplay.tv.model.mediaSourceSelectedForPlaybackStatus
import com.miruplay.tv.model.mediaSourceSelectedRemoteForPlaybackStatus
import com.miruplay.tv.model.mediaSourceShowingRemoteDirectoryStatus
import com.miruplay.tv.model.mediaSourceSmbUrlRequiredStatus
import com.miruplay.tv.model.mediaSourceWebDavUrlRequiredStatus
import com.miruplay.tv.model.libraryRescanCompleteStatus
import com.miruplay.tv.model.libraryScanCompleteStatus
import com.miruplay.tv.model.libraryScanningStatus
import com.miruplay.tv.model.tvDisplayLabel

fun MediaSourceInfo.displayLabel(): String =
    tvDisplayLabel()

fun List<MediaSourceInfo>.upsertById(source: MediaSourceInfo): List<MediaSourceInfo> =
    map { if (it.id == source.id) source else it }.let { updated ->
        if (updated.none { it.id == source.id }) updated + source else updated
    }

fun ProgressRecord.mediaDisplayName(): String =
    MediaPathConventions.fileName(episodeId).ifBlank { episodeId }

fun localLibraryInitialStatus(): String =
    mediaSourceLocalLibraryInitialStatus()

fun remoteBrowserInitialStatus(): String =
    mediaSourceRemoteBrowserInitialStatus()

fun MediaSourceInfo.loadedStatus(saved: Boolean = false): String =
    mediaSourceLoadedStatus(saved)

fun MediaSourceInfo.readyStatus(): String =
    mediaSourceReadyStatus()

fun localRootRequiredStatus(): String =
    mediaSourceLocalRootRequiredStatus()

fun webDavUrlRequiredStatus(): String =
    mediaSourceWebDavUrlRequiredStatus()

fun smbUrlRequiredStatus(): String =
    mediaSourceSmbUrlRequiredStatus()

fun openSourceBeforeScanningStatus(): String =
    mediaSourceOpenBeforeScanningStatus()

fun MediaSourceInfo.scanningStatus(): String =
    libraryScanningStatus(name)

fun scanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    libraryScanCompleteStatus(filesIndexed, directoriesVisited)

fun rescanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): String =
    libraryRescanCompleteStatus(filesIndexed, directoriesVisited)

fun openSourceBeforeSearchingStatus(): String =
    mediaSourceOpenBeforeSearchingStatus()

fun openSourceBeforeClearingIndexStatus(): String =
    mediaSourceOpenBeforeClearingIndexStatus()

fun indexClearedStatus(sourceId: Long): String =
    mediaSourceIndexClearedStatus(sourceId)

fun sourceRemoveRequiredStatus(): String =
    mediaSourceRemoveRequiredStatus()

fun sourceRemovedStatus(): String =
    mediaSourceRemovedStatus()

fun remoteRootStatus(): String =
    mediaSourceAlreadyAtRootStatus()

fun openRemoteSourceBeforeBrowsingStatus(): String =
    mediaSourceOpenRemoteBeforeBrowsingStatus()

fun MediaSourceInfo.loadingRemoteDirectoryStatus(path: String): String =
    mediaSourceLoadingRemoteDirectoryStatus(path)

fun MediaSourceInfo.showingRemoteDirectoryStatus(entries: List<FileEntry>): String =
    mediaSourceShowingRemoteDirectoryStatus(entries.size)

fun MediaIndexEntry.selectedForPlaybackStatus(): String =
    mediaSourceSelectedForPlaybackStatus(displayName())

fun FileEntry.selectedRemoteForPlaybackStatus(): String =
    mediaSourceSelectedRemoteForPlaybackStatus(name)

fun indexedSearchStatus(query: String, hasResults: Boolean, displayedResultCount: Int): String =
    mediaSourceIndexedSearchStatus(query, hasResults, displayedResultCount)
