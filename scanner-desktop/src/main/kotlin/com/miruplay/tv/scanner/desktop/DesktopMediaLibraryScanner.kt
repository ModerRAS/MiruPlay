package com.miruplay.tv.scanner.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.NfoMetadata
import com.miruplay.tv.model.TvShowNfoMetadata
import com.miruplay.tv.model.VideoFilenameInference
import com.miruplay.tv.model.VideoFilenameMetadata
import com.miruplay.tv.repository.MediaIndexEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopMediaLibraryScanner(
    private val config: DesktopScanConfig = DesktopScanConfig(),
    private val nfoReader: DesktopNfoMetadataReader = DesktopNfoMetadataReader(),
    private val filenameMetadataParser: FilenameMetadataParser? = null,
) {
    suspend fun scan(
        sourceId: Long,
        source: MediaSource,
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
        source: MediaSource,
        path: String,
        depth: Int,
        entries: MutableList<MediaIndexEntry>,
        visitedDirectories: MutableSet<String>,
        counters: ScanCounters,
        inheritedTvShow: TvShowNfoMetadata?,
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
        source: MediaSource,
        tvShow: TvShowNfoMetadata?,
    ): MediaIndexEntry {
        val nfo = nfoReader.readEpisodeForVideo(source, path)
        val inferred = inferVideoMetadata(name, path)
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
            seasonNumber = nfo?.season ?: inferred.seasonNumber,
            episodeNumber = nfo?.episode ?: inferred.episodeNumber,
            isDirectory = false,
            fileSize = size,
            lastModified = lastModified,
        )
    }

    private fun inferVideoMetadata(fileName: String, path: String): VideoFilenameMetadata {
        val parentName = MediaPathConventions.parentName(path)
        val fallback = VideoFilenameInference.infer(fileName, parentName)
        val parser = filenameMetadataParser ?: return fallback

        val pathParsed = parseWithModel(parser, modelPathText(path, fileName))
        val fileParsed = parseWithModel(parser, MediaPathConventions.stem(fileName))
        val folderParsed = pathSegments(path)
            .dropLast(1)
            .takeLast(maxModelContextSegments)
            .asReversed()
            .mapNotNull { parseWithModel(parser, it) }

        val folderTitle = folderParsed.firstNotNullOfOrNull { it.title?.takeIf(String::isNotBlank) }
        val fileTitle = fileParsed?.title?.takeIf(String::isNotBlank)
        return VideoFilenameMetadata(
            title = pathParsed?.title?.takeIf(String::isNotBlank)
                ?: folderTitle
                ?: fileTitle
                ?: fallback.title,
            seasonNumber = pathParsed?.season
                ?: folderParsed.firstNotNullOfOrNull(FilenameParseResult::season)
                ?: fileParsed?.season
                ?: fallback.seasonNumber,
            episodeNumber = pathParsed?.episode
                ?: fileParsed?.episode
                ?: folderParsed.firstNotNullOfOrNull(FilenameParseResult::episode)
                ?: fallback.episodeNumber,
        )
    }

    private fun parseWithModel(parser: FilenameMetadataParser, text: String): FilenameParseResult? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        return runCatching { parser.parse(trimmed, maxModelTextLength) }.getOrNull()
    }

    private fun pathSegments(path: String): List<String> =
        path.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun modelPathText(path: String, fileName: String): String {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isBlank()) return fileName
        if (normalized.length <= maxModelTextLength) return normalized

        val tail = pathSegments(normalized)
            .takeLast(maxModelContextSegments + 1)
            .joinToString("/")
        return tail
            .takeIf { it.isNotBlank() }
            ?.takeLast(maxModelTextLength)
            ?: normalized.takeLast(maxModelTextLength)
    }

    private fun FileEntry.isVideoFile(): Boolean =
        MediaFileConventions.isVideoName(name, config.videoExtensions)

    private fun canonicalPathKey(path: String): String =
        MediaPathConventions.canonicalMediaKey(path)

    private data class ScanCounters(
        var filesIndexed: Int = 0,
        var directoriesVisited: Int = 0,
    )

    private companion object {
        const val maxModelTextLength = 128
        const val maxModelContextSegments = 4
    }
}
