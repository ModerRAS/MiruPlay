package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.core.common.UiText
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

fun MediaSourceInfo.displayLabel(): UiText =
    tvDisplayLabel()

fun List<MediaSourceInfo>.upsertById(source: MediaSourceInfo): List<MediaSourceInfo> =
    map { if (it.id == source.id) source else it }.let { updated ->
        if (updated.none { it.id == source.id }) updated + source else updated
    }

fun ProgressRecord.mediaDisplayName(): String =
    MediaPathConventions.fileName(episodeId).ifBlank { episodeId }

fun localLibraryInitialStatus(): UiText =
    mediaSourceLocalLibraryInitialStatus()

fun remoteBrowserInitialStatus(): UiText =
    mediaSourceRemoteBrowserInitialStatus()

fun MediaSourceInfo.loadedStatus(saved: Boolean = false): UiText =
    mediaSourceLoadedStatus(saved)

fun MediaSourceInfo.readyStatus(): UiText =
    mediaSourceReadyStatus()

fun localRootRequiredStatus(): UiText =
    mediaSourceLocalRootRequiredStatus()

fun webDavUrlRequiredStatus(): UiText =
    mediaSourceWebDavUrlRequiredStatus()

fun smbUrlRequiredStatus(): UiText =
    mediaSourceSmbUrlRequiredStatus()

fun openSourceBeforeScanningStatus(): UiText =
    mediaSourceOpenBeforeScanningStatus()

fun MediaSourceInfo.scanningStatus(): UiText =
    libraryScanningStatus(name)

fun scanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): UiText =
    libraryScanCompleteStatus(filesIndexed, directoriesVisited)

fun rescanCompleteStatus(filesIndexed: Int, directoriesVisited: Int): UiText =
    libraryRescanCompleteStatus(filesIndexed, directoriesVisited)

fun openSourceBeforeSearchingStatus(): UiText =
    mediaSourceOpenBeforeSearchingStatus()

fun openSourceBeforeClearingIndexStatus(): UiText =
    mediaSourceOpenBeforeClearingIndexStatus()

fun indexClearedStatus(sourceId: Long): UiText =
    mediaSourceIndexClearedStatus(sourceId)

fun sourceRemoveRequiredStatus(): UiText =
    mediaSourceRemoveRequiredStatus()

fun sourceRemovedStatus(): UiText =
    mediaSourceRemovedStatus()

fun remoteRootStatus(): UiText =
    mediaSourceAlreadyAtRootStatus()

fun openRemoteSourceBeforeBrowsingStatus(): UiText =
    mediaSourceOpenRemoteBeforeBrowsingStatus()

fun MediaSourceInfo.loadingRemoteDirectoryStatus(path: String): UiText =
    mediaSourceLoadingRemoteDirectoryStatus(path)

fun MediaSourceInfo.showingRemoteDirectoryStatus(entries: List<FileEntry>): UiText =
    mediaSourceShowingRemoteDirectoryStatus(entries.size)

fun MediaIndexEntry.selectedForPlaybackStatus(): UiText =
    mediaSourceSelectedForPlaybackStatus(displayName())

fun FileEntry.selectedRemoteForPlaybackStatus(): UiText =
    mediaSourceSelectedRemoteForPlaybackStatus(name)

fun indexedSearchStatus(query: String, hasResults: Boolean, displayedResultCount: Int): UiText =
    mediaSourceIndexedSearchStatus(query, hasResults, displayedResultCount)
