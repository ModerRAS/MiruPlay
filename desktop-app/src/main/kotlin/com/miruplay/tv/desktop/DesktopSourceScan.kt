package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.cloudRssRescanStartedStatus
import com.miruplay.tv.model.localRootPath
import com.miruplay.tv.model.scanResultDisplayName
import com.miruplay.tv.model.sourcePickerTitle
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexMetadataCache
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.mediaFilesOnly
import com.miruplay.tv.repository.rescanCompleteStatus
import com.miruplay.tv.repository.scanCompleteStatus
import com.miruplay.tv.scanner.desktop.DesktopMediaLibraryScanner

internal data class DesktopSourceScanResult(
    val sourceId: Long,
    val scanResult: ScanResult,
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
    metadataRepository: MetadataRepository,
    scanner: DesktopMediaLibraryScanner = DesktopMediaLibraryScanner(),
): Result<DesktopSourceScanResult> {
    val source = desktopSourceFromInfo(sourceInfo)
    val rootPath = when (sourceInfo.type) {
        MediaSourceType.LOCAL -> sourceInfo.localRootPath().orEmpty()
        MediaSourceType.WEBDAV,
        MediaSourceType.SMB -> ""
    }
    return when (val scan = scanner.scan(sourceInfo.id, source, rootPath = rootPath)) {
        is Result.Success -> {
            when (val indexed = indexRepository.rebuildIndex(sourceInfo.id, scan.data.entries)) {
                is Result.Success -> {
                    val videoEntries = scan.data.entries.mediaFilesOnly()
                    MediaIndexMetadataCache(metadataRepository).cache(
                        source = sourceInfo,
                        entries = scan.data.entries,
                    )
                    Result.success(
                        DesktopSourceScanResult(
                            sourceId = sourceInfo.id,
                            scanResult = ScanResult(
                                animeName = sourceInfo.scanResultDisplayName(scan.data.rootPath),
                                episodesFound = videoEntries.size,
                                newEpisodes = videoEntries.size,
                                updatedEpisodes = 0,
                            ),
                            completedStatus = scanCompleteStatus(
                                filesIndexed = scan.data.filesIndexed,
                                directoriesVisited = scan.data.directoriesVisited,
                            ),
                            videoEntries = videoEntries,
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
    metadataRepository: MetadataRepository,
    scanner: DesktopMediaLibraryScanner = DesktopMediaLibraryScanner(),
): Result<DesktopCloudRssRescanResult> {
    return when (val scan = scanAndIndexDesktopSource(sourceInfo, indexRepository, metadataRepository, scanner)) {
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

internal sealed class DesktopCloudRssLinkedSourceSelection {
    data object MissingLink : DesktopCloudRssLinkedSourceSelection()
    data class MissingSource(val sourceId: Long) : DesktopCloudRssLinkedSourceSelection()
    data class Ready(val sourceInfo: MediaSourceInfo) : DesktopCloudRssLinkedSourceSelection()
}

internal suspend fun resolveCloudRssLinkedSource(
    sourceId: Long?,
    savedSources: List<MediaSourceInfo>,
    mediaSources: MediaSourceRepository,
    onSourcesLoaded: (List<MediaSourceInfo>) -> Unit = {},
): Result<DesktopCloudRssLinkedSourceSelection> {
    if (sourceId == null || sourceId <= 0L) {
        return Result.success(DesktopCloudRssLinkedSourceSelection.MissingLink)
    }
    savedSources.firstOrNull { it.id == sourceId }?.let { source ->
        return Result.success(DesktopCloudRssLinkedSourceSelection.Ready(source))
    }
    return when (val sources = mediaSources.getSources()) {
        is Result.Success -> {
            onSourcesLoaded(sources.data)
            val source = sources.data.firstOrNull { it.id == sourceId }
            if (source != null) {
                Result.success(DesktopCloudRssLinkedSourceSelection.Ready(source))
            } else {
                Result.success(DesktopCloudRssLinkedSourceSelection.MissingSource(sourceId))
            }
        }
        is Result.Error -> sources
    }
}

internal sealed class DesktopCloudRssLinkedSourceRescanSelection {
    data object MissingLink : DesktopCloudRssLinkedSourceRescanSelection()
    data class MissingSource(val sourceId: Long) : DesktopCloudRssLinkedSourceRescanSelection()
    data class Ready(
        val sourceInfo: MediaSourceInfo,
        val result: DesktopCloudRssRescanResult,
    ) : DesktopCloudRssLinkedSourceRescanSelection()
}

internal suspend fun resolveAndRescanCloudRssLinkedSource(
    sourceId: Long?,
    reason: String,
    savedSources: List<MediaSourceInfo>,
    mediaSources: MediaSourceRepository,
    indexRepository: MediaIndexRepository,
    metadataRepository: MetadataRepository,
    onSourcesLoaded: (List<MediaSourceInfo>) -> Unit = {},
    onRescanStarting: (MediaSourceInfo) -> Unit = {},
    scanner: DesktopMediaLibraryScanner = DesktopMediaLibraryScanner(),
): Result<DesktopCloudRssLinkedSourceRescanSelection> {
    return when (
        val selected = resolveCloudRssLinkedSource(
            sourceId = sourceId,
            savedSources = savedSources,
            mediaSources = mediaSources,
            onSourcesLoaded = onSourcesLoaded,
        )
    ) {
        is Result.Success -> when (val selection = selected.data) {
            DesktopCloudRssLinkedSourceSelection.MissingLink -> {
                Result.success(DesktopCloudRssLinkedSourceRescanSelection.MissingLink)
            }
            is DesktopCloudRssLinkedSourceSelection.MissingSource -> {
                Result.success(DesktopCloudRssLinkedSourceRescanSelection.MissingSource(selection.sourceId))
            }
            is DesktopCloudRssLinkedSourceSelection.Ready -> {
                onRescanStarting(selection.sourceInfo)
                rescanCloudRssLinkedSource(
                    sourceInfo = selection.sourceInfo,
                    reason = reason,
                    indexRepository = indexRepository,
                    metadataRepository = metadataRepository,
                    scanner = scanner,
                ).map { rescan ->
                    DesktopCloudRssLinkedSourceRescanSelection.Ready(
                        sourceInfo = selection.sourceInfo,
                        result = rescan,
                    )
                }
            }
        }
        is Result.Error -> selected
    }
}
