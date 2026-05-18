package com.miruplay.tv.scanner.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.VideoFilenameInference
import com.miruplay.tv.repository.MediaIndexEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Paths

class DesktopMediaLibraryScanner(
    private val config: DesktopScanConfig = DesktopScanConfig(),
    private val nfoReader: DesktopNfoMetadataReader = DesktopNfoMetadataReader(),
) {
    suspend fun scan(
        sourceId: Long,
        source: DesktopMediaSource,
        rootPath: String = "",
    ): Result<DesktopScanReport> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<MediaIndexEntry>()
        val visitedDirectories = mutableSetOf<String>()
        val counters = ScanCounters()
        val result = scanDirectory(
            sourceId = sourceId,
            source = source,
            path = rootPath,
            depth = 0,
            entries = entries,
            visitedDirectories = visitedDirectories,
            counters = counters,
            inheritedTvShow = null,
        )
        if (result is Result.Error) {
            return@withContext result
        }
        Result.success(
            DesktopScanReport(
                sourceId = sourceId,
                rootPath = rootPath,
                entries = entries.sortedWith(compareBy({ !it.isDirectory }, { it.path.lowercase() })),
                filesIndexed = counters.filesIndexed,
                directoriesVisited = counters.directoriesVisited,
            )
        )
    }

    private suspend fun scanDirectory(
        sourceId: Long,
        source: DesktopMediaSource,
        path: String,
        depth: Int,
        entries: MutableList<MediaIndexEntry>,
        visitedDirectories: MutableSet<String>,
        counters: ScanCounters,
        inheritedTvShow: DesktopTvShowNfoMetadata?,
    ): Result<Unit> {
        val canonicalPath = canonicalPathKey(path)
        if (!visitedDirectories.add(canonicalPath)) return Result.success(Unit)
        counters.directoriesVisited += 1
        val tvShow = nfoReader.readTvShowForDirectory(source, path) ?: inheritedTvShow

        val listed = source.listFiles(path)
        if (listed is Result.Error) return Result.failure(listed.error)

        val files = (listed as Result.Success).data
        for (entry in files) {
            if (entry.isDirectory) {
                if (config.includeDirectories) {
                    entries += entry.toDirectoryIndexEntry(sourceId)
                }
                if (depth < config.maxDepth) {
                    val subResult = scanDirectory(
                        sourceId = sourceId,
                        source = source,
                        path = entry.path,
                        depth = depth + 1,
                        entries = entries,
                        visitedDirectories = visitedDirectories,
                        counters = counters,
                        inheritedTvShow = tvShow,
                    )
                    if (subResult is Result.Error) return subResult
                }
            } else if (entry.isVideoFile()) {
                entries += entry.toVideoIndexEntry(sourceId, source, tvShow)
                counters.filesIndexed += 1
            }
        }

        return Result.success(Unit)
    }

    private fun FileEntry.toDirectoryIndexEntry(sourceId: Long): MediaIndexEntry =
        MediaIndexEntry(
            sourceId = sourceId,
            path = path,
            animeName = name,
            isDirectory = true,
            fileSize = 0L,
            lastModified = lastModified,
        )

    private suspend fun FileEntry.toVideoIndexEntry(
        sourceId: Long,
        source: DesktopMediaSource,
        tvShow: DesktopTvShowNfoMetadata?,
    ): MediaIndexEntry {
        val nfo = nfoReader.readEpisodeForVideo(source, path)
        val parentName = MediaPathConventions.parentName(path)
        val inferred = VideoFilenameInference.infer(name, parentName)
        return MediaIndexEntry(
            sourceId = sourceId,
            path = path,
            animeName = nfo?.showTitle
                ?: tvShow?.title
                ?: tvShow?.originalTitle
                ?: nfo?.title
                ?: inferred.title,
            episodeTitle = nfo?.title,
            plot = nfo?.plot,
            seasonNumber = nfo?.seasonNumber ?: inferred.seasonNumber,
            episodeNumber = nfo?.episodeNumber ?: inferred.episodeNumber,
            isDirectory = false,
            fileSize = size,
            lastModified = lastModified,
        )
    }

    private fun FileEntry.isVideoFile(): Boolean =
        MediaFileConventions.isVideoName(name, config.videoExtensions)

    private fun canonicalPathKey(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return ""
        if ("://" in trimmed) return MediaPathConventions.normalizeRemotePath(trimmed)
        return runCatching {
            Paths.get(trimmed).toAbsolutePath().normalize().toString()
        }.getOrElse {
            MediaPathConventions.normalizeRemotePath(trimmed)
        }
    }

    private data class ScanCounters(
        var filesIndexed: Int = 0,
        var directoriesVisited: Int = 0,
    )
}
