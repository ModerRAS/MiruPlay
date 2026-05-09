package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.IndexRepository
import com.miruplay.tv.data.repository.IndexRepositoryEntity
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.repository.MetadataRepository
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.metadata.NfoParser
import com.miruplay.tv.metadata.XmlNfoParser
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates scanning across MediaSource, Scanner, Index, and Metadata layers.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val indexRepository: IndexRepository,
    private val metadataRepository: MetadataRepository
) {
    /**
     * Full scan of a media source, updates index + metadata in one pass.
     * Cancellable via coroutineContext.
     */
    suspend fun scanSource(sourceId: Long): Result<ScanResult> = withContext(Dispatchers.IO) {
        val sourceResult = mediaRepository.getSourceById(sourceId)
        if (sourceResult !is Result.Success) {
            return@withContext Result.failure((sourceResult as Result.Error).error)
        }
        val sourceInfo = sourceResult.data
        val msResult = mediaSourceFactory.create(sourceInfo)
        if (msResult !is Result.Success) {
            return@withContext Result.failure((msResult as Result.Error).error)
        }
        val ms = msResult.data
        val rootPath = sourceInfo.connectionInfo["path"] ?: "/"

        // Get the real root path once (resolves symlinks)
        val realRootPath = try {
            File(rootPath).canonicalPath
        } catch (e: Exception) {
            rootPath
        }

        // Report starting
        progressCallback?.onProgress(sourceInfo.name, 0, 0)

        // Single recursive traversal: build index + parse NFOs
        val detector = DefaultEpisodeDetector()
        val indexEntities = mutableListOf<IndexRepositoryEntity>()
        var totalFiles = 0
        var newEpisodes = 0

        traverseAndProcess(
            ms = ms,
            path = rootPath,
            sourceId = sourceId,
            detector = detector,
            indexEntities = indexEntities,
            totalFiles = { totalFiles += 1 },
            newEpisodes = { newEpisodes += 1 },
            rootPath = realRootPath
        )

        // Save index
        if (indexEntities.isNotEmpty()) {
            indexRepository.rebuildIndex(sourceId, indexEntities)

            // Also cache episodes in the episode table so AnimeDetailViewModel can read them
            val episodesByAnime = indexEntities
                .filter { !it.isDirectory && it.episodeNumber != null }
                .groupBy { it.animeName ?: "Unknown" }

            for ((animeName, entries) in episodesByAnime) {
                val episodes = entries.sortedBy { it.episodeNumber }.mapIndexed { idx, entry ->
                    Episode(
                        id = entry.path,
                        animeId = animeName,
                        seasonNumber = entry.seasonNumber ?: 1,
                        episodeNumber = entry.episodeNumber ?: (idx + 1),
                        title = "",
                        filePath = entry.path,
                        fileName = entry.path.substringAfterLast('/')
                    )
                }
                metadataRepository.cacheEpisodes(animeName, episodes)

                // Update or create anime metadata with episode count
                metadataRepository.getCachedMetadata(animeName).onSuccess { cachedAnime ->
                    if (cachedAnime != null) {
                        metadataRepository.cacheMetadata(cachedAnime.copy(episodeCount = episodes.size))
                    } else {
                        // Create minimal anime metadata if none exists (no NFO was found)
                        metadataRepository.cacheMetadata(Anime(
                            id = animeName,
                            title = animeName,
                            titleCn = animeName,
                            episodeCount = episodes.size
                        ))
                    }
                }
            }
        }

        // Report done
        Log.d("ScanCoordinator", "Scan done: ${sourceInfo.name} -> $totalFiles files, $newEpisodes new episodes")

        Result.success(ScanResult(
            animeName = rootPath.substringAfterLast('/').ifEmpty { rootPath },
            episodesFound = totalFiles,
            newEpisodes = newEpisodes,
            updatedEpisodes = 0
        ))
    }

    /** Progress callback for scan operations */
    fun interface ScanProgressCallback {
        fun onProgress(currentPath: String, filesScanned: Int, newEpisodes: Int)
    }

    private var progressCallback: ScanProgressCallback? = null

    fun setProgressCallback(callback: ScanProgressCallback?) {
        progressCallback = callback
    }

    /**
     * Single recursive traversal: list files, build index, parse NFOs.
     * Cancellable via coroutineContext.isActive checks.
     * path: The directory to scan (stays within root due to absolutePath)
     * rootPath: The canonical root path for boundary checks
     */
    private suspend fun traverseAndProcess(
        ms: MediaSource,
        path: String,
        sourceId: Long,
        detector: DefaultEpisodeDetector,
        indexEntities: MutableList<IndexRepositoryEntity>,
        totalFiles: (Int) -> Unit,
        newEpisodes: (Int) -> Unit,
        depth: Int = 0,
        rootPath: String
    ) {
        // Guard: skip hidden directories
        val pathName = path.substringAfterLast('/')
        if (pathName.startsWith(".")) return

        // Guard: skip Android system media directories
        if (pathName in skipDirs) return

        // Guard: skip /mnt directory entirely
        if (path.startsWith("/mnt/")) return

        // Check for cancellation
        if (!currentCoroutineContext().isActive) return

        try {
            val files = ms.listFiles(path).getOrNull() ?: return

            // Get parent directory name once — used as anime name for files within this dir
            val parentDirName = path.substringAfterLast('/').ifEmpty { "Unknown" }

            for (file in files) {
                if (!currentCoroutineContext().isActive) return

                // Belt-and-suspenders: if file.path escapes root boundary, skip it
                if (rootPath.isNotEmpty() && !file.path.startsWith(rootPath)) {
                    Log.w("ScanCoordinator", "Path escaped root boundary: ${file.path} (root=$rootPath), skipping")
                    continue
                }

                if (file.isDirectory) {
                    // Skip trickplay, hidden, and system directories
                    if (file.name.endsWith(".trickplay") || file.name.startsWith(".")) continue
                    if (file.name in skipDirs) continue
                    
                    // Recurse into subdirectory
                    traverseAndProcess(ms, file.path, sourceId, detector, indexEntities, totalFiles, newEpisodes, depth + 1, rootPath)
                } else {
                    val fileName = file.name
                    val ext = fileName.substringAfterLast('.', "").lowercase()

                    if (ext in videoExtensions) {
                        totalFiles(1)
                        val match = detector.detectEpisode(fileName)
                        // Use parent directory name as anime name (reliable for Kodi-style folder structures)
                        indexEntities.add(IndexRepositoryEntity(
                            sourceId = sourceId,
                            path = file.path,
                            animeName = parentDirName,
                            seasonNumber = match?.seasonNumber ?: 1,
                            episodeNumber = match?.episodeNumber,
                            isDirectory = false,
                            fileSize = file.size,
                            lastModified = file.lastModified
                        ))
                        if (match != null) newEpisodes(1)

                        // Report progress every 5 video files
                        if (file.path.hashCode() % 5 == 0) {
                            progressCallback?.onProgress(parentDirName, 1, if (match != null) 1 else 0)
                        }
                    } else if (ext == "nfo") {
                        parseAndCacheRemoteNfo(ms, file.path, parentDirName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ScanCoordinator", "Error traversing path: $path", e)
        }
    }

    /**
     * Download and parse a single NFO file, cache metadata using showDirName as the cache key.
     * This ensures the cache key matches the animeName stored in the index.
     */
    private suspend fun parseAndCacheRemoteNfo(ms: MediaSource, nfoPath: String, showDirName: String) {
        try {
            val stream = ms.openStream(nfoPath).getOrNull() ?: return
            val xml = stream.bufferedReader().use { it.readText() }
            val parser = XmlNfoParser()

            when {
                xml.contains("<episodedetails") -> {
                    parser.parseEpisodeNfoFromContent(xml).onSuccess { nfo ->
                        // Use show directory name as cache key for index consistency
                        val showTitle = nfo.showTitle ?: nfo.title ?: showDirName
                        metadataRepository.cacheMetadata(Anime(
                            id = showDirName,  // Key matches animeName in the index
                            title = showTitle,
                            titleCn = showTitle,
                            summary = nfo.plot
                        ))
                    }
                }
                xml.contains("<tvshow") -> {
                    parser.parseTvShowNfoFromContent(xml).onSuccess { tv ->
                        metadataRepository.cacheMetadata(Anime(
                            id = showDirName,  // Key matches animeName in the index
                            title = tv.title,
                            titleCn = tv.originalTitle,
                            summary = tv.plot
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ScanCoordinator", "Error parsing NFO: $nfoPath", e)
        }
    }

    companion object {
        private val videoExtensions = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v")
        private val skipDirs = setOf(
            "proc", "sys", "dev", "selinux", "acct", "apex", "bin", "cache", "config",
            "d", "data", "data_mirror", "debug_ramdisk", "etc", "init", "lib",
            "linkerconfig", "mnt", "odm", "oem", "plat_file_contexts", "plat_sepolicy",
            "postinstall", "proc", "product", "sys", "system", "system_ext",
            "vendor", "vendor_dlkm", "version"
        )
    }

    /**
     * Quick scan all configured sources
     */
    suspend fun scanAllSources(): Result<List<ScanResult>> = withContext(Dispatchers.IO) {
        val sourcesResult = mediaRepository.getSources()
        if (sourcesResult !is Result.Success) {
            return@withContext Result.success(emptyList())
        }
        val results = mutableListOf<ScanResult>()
        for (sourceInfo in sourcesResult.data) {
            val scanResult = scanSource(sourceInfo.id)
            if (scanResult is Result.Success) {
                results.add(scanResult.data)
            }
        }
        Result.success(results)
    }
}
