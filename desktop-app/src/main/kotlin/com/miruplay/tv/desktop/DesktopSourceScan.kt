package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.cloudRssRescanStartedStatus
import com.miruplay.tv.model.sourcePickerTitle
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.mediaFilesOnly
import com.miruplay.tv.repository.rescanCompleteStatus
import com.miruplay.tv.repository.scanCompleteStatus
import com.miruplay.tv.scanner.desktop.DesktopMediaLibraryScanner

internal data class DesktopSourceScanResult(
    val sourceId: Long,
    val completedStatus: String,
    val videoEntries: List<MediaIndexEntry>,
    val filesIndexed: Int,
    val directoriesVisited: Int,
)

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

internal suspend fun scanAndIndexDesktopSource(
    sourceInfo: MediaSourceInfo,
    indexRepository: MediaIndexRepository,
    scanner: DesktopMediaLibraryScanner = DesktopMediaLibraryScanner(),
): Result<DesktopSourceScanResult> {
    val source = desktopSourceFromInfo(sourceInfo)
    return when (val scan = scanner.scan(sourceInfo.id, source)) {
        is Result.Success -> {
            when (val indexed = indexRepository.rebuildIndex(sourceInfo.id, scan.data.entries)) {
                is Result.Success -> {
                    Result.success(
                        DesktopSourceScanResult(
                            sourceId = sourceInfo.id,
                            completedStatus = scanCompleteStatus(
                                filesIndexed = scan.data.filesIndexed,
                                directoriesVisited = scan.data.directoriesVisited,
                            ),
                            videoEntries = scan.data.entries.mediaFilesOnly(),
                            filesIndexed = scan.data.filesIndexed,
                            directoriesVisited = scan.data.directoriesVisited,
                        )
                    )
                }
                is Result.Error -> indexed
            }
        }
        is Result.Error -> scan
    }
}

internal suspend fun rescanCloudRssLinkedSource(
    sourceInfo: MediaSourceInfo,
    reason: String,
    indexRepository: MediaIndexRepository,
    scanner: DesktopMediaLibraryScanner = DesktopMediaLibraryScanner(),
): Result<DesktopCloudRssRescanResult> {
    return when (val scan = scanAndIndexDesktopSource(sourceInfo, indexRepository, scanner)) {
        is Result.Success -> {
            Result.success(
                DesktopCloudRssRescanResult(
                    sourceId = scan.data.sourceId,
                    startedStatus = cloudRssRescanStartedStatus(reason, sourceInfo.sourcePickerTitle()),
                    completedStatus = rescanCompleteStatus(
                        filesIndexed = scan.data.filesIndexed,
                        directoriesVisited = scan.data.directoriesVisited,
                    ),
                    videoEntries = scan.data.videoEntries,
                    targetStatus = when (sourceInfo.type) {
                        MediaSourceType.LOCAL -> DesktopCloudRssRescanTargetStatus.LIBRARY
                        MediaSourceType.WEBDAV,
                        MediaSourceType.SMB -> DesktopCloudRssRescanTargetStatus.REMOTE
                    },
                )
            )
        }
        is Result.Error -> scan
    }
}
