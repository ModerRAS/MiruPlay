package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.rescanCompleteStatus
import com.miruplay.tv.scanner.desktop.DesktopMediaLibraryScanner
import com.miruplay.tv.sync.rss.cloudRssRescanStartedStatus

internal data class DesktopCloudRssRescanResult(
    val sourceId: Long,
    val startedStatus: String,
    val completedStatus: String,
    val videoEntries: List<MediaIndexEntry>,
    val targetStatus: DesktopCloudRssRescanTargetStatus,
)

internal enum class DesktopCloudRssRescanTargetStatus {
    LIBRARY,
    REMOTE,
}

internal suspend fun rescanCloudRssLinkedSource(
    sourceInfo: MediaSourceInfo,
    reason: String,
    indexRepository: MediaIndexRepository,
    scanner: DesktopMediaLibraryScanner = DesktopMediaLibraryScanner(),
): Result<DesktopCloudRssRescanResult> {
    val source = desktopSourceFromInfo(sourceInfo)
    return when (val scan = scanner.scan(sourceInfo.id, source)) {
        is Result.Success -> {
            when (val indexed = indexRepository.rebuildIndex(sourceInfo.id, scan.data.entries)) {
                is Result.Success -> {
                    Result.success(
                        DesktopCloudRssRescanResult(
                            sourceId = sourceInfo.id,
                            startedStatus = sourceInfo.cloudRssRescanStartedStatus(reason),
                            completedStatus = rescanCompleteStatus(
                                filesIndexed = scan.data.filesIndexed,
                                directoriesVisited = scan.data.directoriesVisited,
                            ),
                            videoEntries = scan.data.entries.filterNot { it.isDirectory },
                            targetStatus = when (sourceInfo.type) {
                                MediaSourceType.LOCAL -> DesktopCloudRssRescanTargetStatus.LIBRARY
                                MediaSourceType.WEBDAV,
                                MediaSourceType.SMB -> DesktopCloudRssRescanTargetStatus.REMOTE
                            },
                        )
                    )
                }
                is Result.Error -> indexed
            }
        }
        is Result.Error -> scan
    }
}
