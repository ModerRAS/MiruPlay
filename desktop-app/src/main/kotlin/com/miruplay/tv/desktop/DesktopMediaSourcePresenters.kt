package com.miruplay.tv.desktop

import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopSmbMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopWebDavMediaSource
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.loadedPlaybackStatus
import com.miruplay.tv.model.recentPlaybackInitialStatus as sharedRecentPlaybackInitialStatus
import com.miruplay.tv.model.recentPlaybackLoadedStatus as sharedRecentPlaybackLoadedStatus
import com.miruplay.tv.model.recentPlaybackRequiredStatus as sharedRecentPlaybackRequiredStatus
import com.miruplay.tv.model.recentPlaybackShowingStatus as sharedRecentPlaybackShowingStatus
import com.miruplay.tv.model.resumeStartSecondsText
import com.miruplay.tv.repository.displayLabel
import com.miruplay.tv.repository.indexClearedStatus
import com.miruplay.tv.repository.indexedSearchStatus as sharedIndexedSearchStatus
import com.miruplay.tv.repository.loadedStatus
import com.miruplay.tv.repository.loadingRemoteDirectoryStatus
import com.miruplay.tv.repository.localLibraryInitialStatus as sharedLocalLibraryInitialStatus
import com.miruplay.tv.repository.localRootRequiredStatus as sharedLocalRootRequiredStatus
import com.miruplay.tv.repository.mediaDisplayName
import com.miruplay.tv.repository.openRemoteSourceBeforeBrowsingStatus as sharedOpenRemoteSourceBeforeBrowsingStatus
import com.miruplay.tv.repository.openSourceBeforeClearingIndexStatus as sharedOpenSourceBeforeClearingIndexStatus
import com.miruplay.tv.repository.openSourceBeforeScanningStatus as sharedOpenSourceBeforeScanningStatus
import com.miruplay.tv.repository.openSourceBeforeSearchingStatus as sharedOpenSourceBeforeSearchingStatus
import com.miruplay.tv.repository.readyStatus
import com.miruplay.tv.repository.remoteBrowserInitialStatus as sharedRemoteBrowserInitialStatus
import com.miruplay.tv.repository.remoteRootStatus as sharedRemoteRootStatus
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.selectedForPlaybackStatus
import com.miruplay.tv.repository.selectedRemoteForPlaybackStatus
import com.miruplay.tv.repository.showingRemoteDirectoryStatus
import com.miruplay.tv.repository.smbUrlRequiredStatus as sharedSmbUrlRequiredStatus
import com.miruplay.tv.repository.sourceRemoveRequiredStatus as sharedSourceRemoveRequiredStatus
import com.miruplay.tv.repository.sourceRemovedStatus as sharedSourceRemovedStatus
import com.miruplay.tv.repository.upsertById
import com.miruplay.tv.repository.webDavUrlRequiredStatus as sharedWebDavUrlRequiredStatus

internal fun sourceLabel(source: MediaSourceInfo): String =
    source.displayLabel()

internal fun localLibraryInitialStatus(): String =
    sharedLocalLibraryInitialStatus()

internal fun remoteBrowserInitialStatus(): String =
    sharedRemoteBrowserInitialStatus()

internal fun loadedSourceStatus(source: MediaSourceInfo, saved: Boolean = false): String =
    source.loadedStatus(saved)

internal fun readySourceStatus(source: MediaSourceInfo): String =
    source.readyStatus()

internal fun localRootRequiredStatus(): String =
    sharedLocalRootRequiredStatus()

internal fun webDavUrlRequiredStatus(): String =
    sharedWebDavUrlRequiredStatus()

internal fun smbUrlRequiredStatus(): String =
    sharedSmbUrlRequiredStatus()

internal fun openSourceBeforeScanningStatus(): String =
    sharedOpenSourceBeforeScanningStatus()

internal fun openSourceBeforeSearchingStatus(): String =
    sharedOpenSourceBeforeSearchingStatus()

internal fun openSourceBeforeClearingIndexStatus(): String =
    sharedOpenSourceBeforeClearingIndexStatus()

internal fun clearedIndexStatus(sourceId: Long): String =
    indexClearedStatus(sourceId)

internal fun sourceRemoveRequiredStatus(): String =
    sharedSourceRemoveRequiredStatus()

internal fun sourceRemovedStatus(): String =
    sharedSourceRemovedStatus()

internal fun remoteRootStatus(): String =
    sharedRemoteRootStatus()

internal fun openRemoteSourceBeforeBrowsingStatus(): String =
    sharedOpenRemoteSourceBeforeBrowsingStatus()

internal fun remoteLoadingStatus(source: MediaSourceInfo, path: String): String =
    source.loadingRemoteDirectoryStatus(path)

internal fun remoteShowingStatus(source: MediaSourceInfo, entries: List<FileEntry>): String =
    source.showingRemoteDirectoryStatus(entries)

internal fun indexedSearchStatus(query: String, hasResults: Boolean, displayedResultCount: Int): String =
    sharedIndexedSearchStatus(query, hasResults, displayedResultCount)

internal fun selectedIndexEntryPlaybackStatus(entry: MediaIndexEntry): String =
    entry.selectedForPlaybackStatus()

internal fun selectedRemotePlaybackStatus(entry: FileEntry): String =
    entry.selectedRemoteForPlaybackStatus()

internal fun webDavSourceInfo(
    url: String,
    username: String,
    password: String,
): MediaSourceInfo =
    MediaSourceInfoConventions.webDav(
        url = url,
        username = username,
        password = password,
        isConnected = true,
    )

internal fun smbSourceInfo(
    url: String,
    domain: String,
    username: String,
    password: String,
): MediaSourceInfo =
    MediaSourceInfoConventions.smb(
        url = url,
        domain = domain,
        username = username,
        password = password,
        isConnected = true,
    )

internal fun desktopSourceFromInfo(info: MediaSourceInfo): DesktopMediaSource =
    when (info.type) {
        MediaSourceType.LOCAL -> DesktopLocalMediaSource(info)
        MediaSourceType.WEBDAV -> desktopWebDavSourceFromInfo(info)
        MediaSourceType.SMB -> DesktopSmbMediaSource(info)
    }

internal fun desktopWebDavSourceFromInfo(info: MediaSourceInfo): DesktopWebDavMediaSource =
    DesktopWebDavMediaSource.create(
        name = info.name,
        url = requireNotNull(info.connectionInfo["url"]) { "WebDAV source requires connectionInfo[url]" },
        username = info.connectionInfo["username"].orEmpty(),
        password = info.connectionInfo["password"].orEmpty(),
    )

internal fun remoteParent(path: String): String? {
    return MediaPathConventions.remoteParent(path)
}

internal fun List<MediaSourceInfo>.upsertSource(source: MediaSourceInfo): List<MediaSourceInfo> =
    upsertById(source)

internal fun recentDisplayName(record: ProgressRecord): String =
    record.mediaDisplayName()

internal fun recentInitialStatus(): String =
    sharedRecentPlaybackInitialStatus()

internal fun recentLoadedStatus(records: List<ProgressRecord>): String =
    sharedRecentPlaybackLoadedStatus(records)

internal fun recentShowingStatus(records: List<ProgressRecord>): String =
    sharedRecentPlaybackShowingStatus(records)

internal fun recentRequiredStatus(): String =
    sharedRecentPlaybackRequiredStatus()

internal fun recentResumeStartSeconds(record: ProgressRecord): String =
    record.resumeStartSecondsText()

internal fun recentLoadedPlaybackStatus(record: ProgressRecord): String =
    record.loadedPlaybackStatus(record.mediaDisplayName())
