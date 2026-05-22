package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.metadata.NfoWriteOptions
import com.miruplay.tv.metadata.XmlNfoWriter
import com.miruplay.tv.metadata.XmlNfoParser
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.NfoMetadata
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.TvShowNfoMetadata
import com.miruplay.tv.model.UniqueId
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.scraper.EpisodeMetadata
import com.miruplay.tv.scraper.MetadataScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates scanning across MediaSource, Scanner, Index, and Metadata layers.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val indexRepository: MediaIndexRepository,
    private val metadataRepository: MetadataRepository,
    private val filenameMetadataParser: FilenameMetadataParser,
    private val metadataScrapers: Set<@JvmSuppressWildcards MetadataScraper> = emptySet()
) {
    private val generatedNfoWriter = XmlNfoWriter(NfoWriteOptions(createBackup = false))

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
        val isLocalSource = sourceInfo.type == MediaSourceType.LOCAL
        val isDocumentTree = isLocalSource && (
            sourceInfo.connectionInfo["uri"]?.startsWith("content://") == true ||
                sourceInfo.connectionInfo["path"]?.startsWith("content://") == true ||
                sourceInfo.connectionInfo["url"]?.startsWith("content://") == true
            )
        val rootPath = when (sourceInfo.type) {
            MediaSourceType.LOCAL ->
                sourceInfo.connectionInfo["uri"]
                    ?: sourceInfo.connectionInfo["path"]
                    ?: sourceInfo.connectionInfo["url"]
                    ?: "/"
            MediaSourceType.WEBDAV,
            MediaSourceType.SMB ->
                sourceInfo.connectionInfo["url"] ?: sourceInfo.connectionInfo["path"] ?: "/"
        }
        val scanStartPath = if (isLocalSource) rootPath else ""

        // Get the real root path once (resolves symlinks)
        val realRootPath = if (isLocalSource && !isDocumentTree) {
            try {
                File(rootPath).canonicalPath
            } catch (e: Exception) {
                rootPath
            }
        } else if (isDocumentTree) {
            null
        } else {
            null
        }

        // Report starting
        progressCallback?.onProgress(sourceInfo.name, 0, 0)
        MiruLog.i(
            "ScanCoordinator",
            "Scanning source",
            mapOf(
                "source_id" to sourceId.toString(),
                "source_name" to sourceInfo.name,
                "source_type" to sourceInfo.type.name,
                "root_path" to rootPath
            )
        )

        // Single recursive traversal: build index + parse NFOs
        val detector = DefaultEpisodeDetector()
        val classifier = VideoDirectoryClassifier(detector, filenameMetadataParser)
        val indexEntities = mutableListOf<MediaIndexEntry>()
        var totalFiles = 0
        var newEpisodes = 0

        traverseAndProcess(
            ms = ms,
            path = scanStartPath,
            sourceId = sourceId,
            classifier = classifier,
            indexEntities = indexEntities,
            totalFiles = { totalFiles += 1 },
            newEpisodes = { newEpisodes += 1 },
            rootPath = realRootPath,
            isLocalSource = isLocalSource
        )

        // Save index
        if (indexEntities.isNotEmpty()) {
            indexRepository.rebuildIndex(sourceId, indexEntities)

            // Also cache episodes in the episode table so AnimeDetailViewModel can read them
            val episodesByAnime = indexEntities
                .filter { !it.isDirectory && it.episodeNumber != null }
                .groupBy { it.animeName ?: "Unknown" }

            for ((animeName, entries) in episodesByAnime) {
                val sortedEntries = entries.sortedWith(
                    compareBy<MediaIndexEntry>(
                        { it.seasonNumber ?: 1 },
                        { it.episodeNumber ?: Int.MAX_VALUE },
                        { it.path }
                    )
                )
                val episodes = sortedEntries.mapIndexed { idx, entry ->
                    val playablePath = toPlayablePath(entry.path, rootPath, sourceInfo.type)
                        Episode(
                            id = "${sourceId}:${entry.path}",
                            animeId = animeName,
                            seasonNumber = entry.seasonNumber ?: 1,
                            episodeNumber = entry.episodeNumber ?: (idx + 1),
                            title = "",
                            filePath = playablePath,
                            fileName = fileNameOf(entry.path)
                        )
                    }
                val online = enrichWithOnlineMetadata(animeName, episodes)
                metadataRepository.cacheEpisodes(animeName, online.episodes)

                // Update or create anime metadata with episode count
                var animeForNfo: Anime? = null
                if (online.anime != null) {
                    metadataRepository.cacheMetadata(online.anime)
                    animeForNfo = online.anime
                } else {
                    metadataRepository.getCachedMetadata(animeName).onSuccess { cachedAnime ->
                        if (cachedAnime != null) {
                            val updated = cachedAnime.copy(episodeCount = episodes.size)
                            metadataRepository.cacheMetadata(updated)
                            animeForNfo = updated
                        } else {
                            // Create minimal anime metadata if none exists (no NFO was found)
                            val minimal = Anime(
                                id = animeName,
                                title = animeName,
                                titleCn = animeName,
                                episodeCount = episodes.size
                            )
                            metadataRepository.cacheMetadata(minimal)
                            animeForNfo = minimal
                        }
                    }
                }

                if (isLocalSource && !isDocumentTree) {
                    animeForNfo?.let { anime ->
                        writeGeneratedNfoIfMissing(
                            classifier = classifier,
                            anime = anime,
                            episodes = online.episodes,
                            entries = sortedEntries
                        )
                    }
                }
            }
        }

        // Report done
        Log.d("ScanCoordinator", "Scan done: ${sourceInfo.name} -> $totalFiles files, $newEpisodes new episodes")
        MiruLog.i(
            "ScanCoordinator",
            "Source scan done",
            mapOf(
                "source_id" to sourceId.toString(),
                "source_name" to sourceInfo.name,
                "files" to totalFiles.toString(),
                "new_episodes" to newEpisodes.toString()
            )
        )

        Result.success(ScanResult(
            animeName = if (isLocalSource) sourceInfo.displayNameOrPath(rootPath) else sourceInfo.name,
            episodesFound = totalFiles,
            newEpisodes = newEpisodes,
            updatedEpisodes = 0
        ))
    }

    private data class OnlineMetadata(
        val anime: Anime?,
        val episodes: List<Episode>
    )

    private suspend fun enrichWithOnlineMetadata(animeName: String, episodes: List<Episode>): OnlineMetadata {
        val bangumi = metadataScrapers.firstOrNull { it.sourceName.equals("Bangumi", ignoreCase = true) }
            ?: return OnlineMetadata(null, episodes)

        return try {
            val candidates = titleCandidates(animeName)
            var match = bangumi.searchByAlias(animeName, candidates).getOrNull()
            if (match == null) {
                for (candidate in candidates) {
                    match = bangumi.searchAnime(candidate).getOrNull()
                        ?.firstOrNull { it.confidence >= 0.62f }
                    if (match != null) break
                }
            }
            match ?: return OnlineMetadata(null, episodes)

            val details = bangumi.getAnimeDetails(match.animeId).getOrNull()
            val episodeMetadata = bangumi.getEpisodes(match.animeId).getOrNull()
                .orEmpty()
                .associateBy { it.episodeNumber }

            val enrichedEpisodes = episodes.map { episode ->
                val remote = episodeMetadata[episode.episodeNumber]
                if (remote == null) {
                    episode
                } else {
                    episode.copy(
                        title = remote.title ?: episode.title,
                        duration = episode.duration.takeIf { it > 0 } ?: remote.durationMs,
                        bangumiEpisodeId = remote.bangumiEpisodeId,
                        bangumiCollectionType = remote.collectionType
                    )
                }
            }

            val anime = details?.copy(
                id = animeName,
                title = details.title.ifBlank { animeName },
                titleCn = details.titleCn ?: match.titleCn,
                episodeCount = maxOf(details.episodeCount, enrichedEpisodes.size),
                bangumiId = details.bangumiId ?: match.animeId.toIntOrNull()
            )

            OnlineMetadata(anime, enrichedEpisodes)
        } catch (e: Exception) {
            Log.w("ScanCoordinator", "Bangumi metadata enrichment failed for $animeName", e)
            MiruLog.w(
                "ScanCoordinator",
                "Bangumi metadata enrichment failed",
                e,
                mapOf("anime_name" to animeName)
            )
            OnlineMetadata(null, episodes)
        }
    }

    private fun titleCandidates(animeName: String): List<String> {
        val withoutBracketGroups = animeName
            .replace(Regex("\\[[^\\]]*]"), " ")
            .replace(Regex("【[^】]*】"), " ")
        val withoutSeasonSuffix = withoutBracketGroups
            .replace(Regex("(?i)\\b(s\\d{1,2}|season\\s*\\d{1,2}|第\\s*\\d+\\s*[季期])\\b"), " ")
        return listOf(animeName, withoutBracketGroups, withoutSeasonSuffix)
            .map { it.replace(Regex("[._]+"), " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
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
        classifier: VideoDirectoryClassifier,
        indexEntities: MutableList<MediaIndexEntry>,
        totalFiles: (Int) -> Unit,
        newEpisodes: (Int) -> Unit,
        depth: Int = 0,
        rootPath: String?,
        isLocalSource: Boolean
    ) {
        // Guard: skip hidden directories
        val pathName = path.substringAfterLast('/')
        if (pathName.startsWith(".")) return

        // Guard: skip Android system media directories
        if (isLocalSource && pathName in skipDirs) return

        // Guard: skip /mnt directory entirely
        if (isLocalSource && path.startsWith("/mnt/")) return

        // Check for cancellation
        if (!currentCoroutineContext().isActive) return

        try {
            val files = ms.listFiles(path).getOrNull() ?: return

            for (file in files) {
                if (!currentCoroutineContext().isActive) return

                // Belt-and-suspenders: if file.path escapes root boundary, skip it
                if (rootPath != null && !isWithinRoot(file.path, rootPath)) {
                    Log.w("ScanCoordinator", "Path escaped root boundary: ${file.path} (root=$rootPath), skipping")
                    MiruLog.w(
                        "ScanCoordinator",
                        "Path escaped root boundary",
                        attributes = mapOf("path" to file.path, "root_path" to rootPath)
                    )
                    continue
                }

                if (file.isDirectory) {
                    // Skip trickplay, hidden, and system directories
                    if (file.name.endsWith(".trickplay") || file.name.startsWith(".")) continue
                    if (isLocalSource && file.name in skipDirs) continue
                    
                    // Recurse into subdirectory
                    traverseAndProcess(ms, file.path, sourceId, classifier, indexEntities, totalFiles, newEpisodes, depth + 1, rootPath, isLocalSource)
                } else {
                    val fileName = file.name
                    val ext = fileName.substringAfterLast('.', "").lowercase()

                    if (ext in videoExtensions) {
                        totalFiles(1)
                        val match = classifier.classifyVideo(file.path, fileName)
                        indexEntities.add(MediaIndexEntry(
                            sourceId = sourceId,
                            path = file.path,
                            animeName = match.animeName,
                            seasonNumber = match.seasonNumber,
                            episodeNumber = match.episodeNumber,
                            isDirectory = false,
                            fileSize = file.size,
                            lastModified = file.lastModified
                        ))
                        if (match.episodeNumber != null) newEpisodes(1)

                        // Report progress every 5 video files
                        if (file.path.hashCode() % 5 == 0) {
                            progressCallback?.onProgress(match.animeName, 1, if (match.episodeNumber != null) 1 else 0)
                        }
                    } else {
                        if (ext == "nfo") {
                            parseAndCacheRemoteNfo(ms, file.path, classifier.classifyNfo(file.path).animeName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ScanCoordinator", "Error traversing path: $path", e)
            MiruLog.w(
                "ScanCoordinator",
                "Error traversing path",
                e,
                mapOf("path" to path)
            )
        }
    }

    private fun isWithinRoot(path: String, rootPath: String): Boolean {
        val root = normalizeLocalPath(rootPath)
        val normalizedPath = try {
            File(path).canonicalPath
        } catch (e: Exception) {
            path
        }.let(::normalizeLocalPath)
        return normalizedPath == root || normalizedPath.startsWith("$root/")
    }

    private fun normalizeLocalPath(path: String): String =
        path.replace('\\', '/').trimEnd('/')

    private fun toPlayablePath(path: String, sourceRoot: String, sourceType: MediaSourceType): String =
        when (sourceType) {
            MediaSourceType.LOCAL -> path
            MediaSourceType.WEBDAV -> joinRemoteUrl(sourceRoot, path)
            MediaSourceType.SMB -> path
        }

    private fun joinRemoteUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val encodedPath = path
            .trimStart('/')
            .split('/')
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
        return "$base/$encodedPath"
    }

    private fun nameOfPath(path: String): String =
        when {
            path.startsWith("content://") -> {
                val tail = path.substringAfterLast(':', path).substringAfterLast('/')
                java.net.URLDecoder.decode(tail, Charsets.UTF_8.name())
            }
            else -> path.substringAfterLast('/')
        }

    private fun fileNameOf(path: String): String = nameOfPath(path).ifEmpty { path.substringAfterLast('/') }

    private fun MediaSourceInfo.displayNameOrPath(path: String): String =
        connectionInfo["displayName"] ?: nameOfPath(path).ifEmpty { path }

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
            MiruLog.w(
                "ScanCoordinator",
                "Error parsing NFO",
                e,
                mapOf("nfo_path" to nfoPath)
            )
        }
    }

    private suspend fun writeGeneratedNfoIfMissing(
        classifier: VideoDirectoryClassifier,
        anime: Anime,
        episodes: List<Episode>,
        entries: List<MediaIndexEntry>
    ) {
        if (episodes.isEmpty() || entries.isEmpty()) return

        entries.mapNotNull { classifier.showRootForVideo(it.path) }
            .distinct()
            .forEach { showRoot ->
                val showRootFile = File(showRoot)
                if (!showRootFile.exists() || !showRootFile.isDirectory) return@forEach

                val tvshowPath = File(showRootFile, "tvshow.nfo")
                if (!tvshowPath.exists()) {
                    generatedNfoWriter.writeTvShowNfo(tvshowPath.absolutePath, anime.toTvShowNfoMetadata())
                        .onError { error ->
                            Log.w("ScanCoordinator", "Failed to generate tvshow.nfo for ${anime.id}: $error")
                            MiruLog.w(
                                "ScanCoordinator",
                                "Failed to generate tvshow.nfo",
                                attributes = mapOf(
                                    "anime_id" to anime.id,
                                    "error" to error.toUserMessage()
                                )
                            )
                        }
                }
            }

        entries.zip(episodes).forEach { (entry, episode) ->
            val videoFile = File(entry.path)
            val parent = videoFile.parentFile ?: return@forEach
            if (!parent.exists() || !parent.isDirectory) return@forEach

            val nfoPath = File(parent, "${videoFile.nameWithoutExtension}.nfo")
            if (nfoPath.exists()) return@forEach

            generatedNfoWriter.writeEpisodeNfo(
                nfoPath.absolutePath,
                episode.toNfoMetadata(anime)
            ).onError { error ->
                Log.w("ScanCoordinator", "Failed to generate episode NFO for ${episode.id}: $error")
                MiruLog.w(
                    "ScanCoordinator",
                    "Failed to generate episode NFO",
                    attributes = mapOf(
                        "episode_id" to episode.id,
                        "error" to error.toUserMessage()
                    )
                )
            }
        }
    }

    private fun Anime.toTvShowNfoMetadata(): TvShowNfoMetadata =
        TvShowNfoMetadata(
            title = titleCn ?: title,
            originalTitle = title,
            plot = summary,
            genre = genres,
            premiered = airDate,
            studio = studio,
            rating = rating,
            uniqueIds = uniqueIds()
        )

    private fun Episode.toNfoMetadata(anime: Anime): NfoMetadata =
        NfoMetadata(
            title = title.ifBlank { "Episode $episodeNumber" },
            showTitle = anime.titleCn ?: anime.title,
            season = seasonNumber,
            episode = episodeNumber,
            plot = "",
            premiered = anime.airDate,
            rating = 0f,
            playcount = playCount,
            resumePosition = watchedPosition,
            uniqueIds = buildList {
                bangumiEpisodeId?.let { add(UniqueId("bangumi", it.toString(), true)) }
                anime.bangumiId?.let { add(UniqueId("bangumi-subject", it.toString())) }
                anime.anilistId?.let { add(UniqueId("anilist", it.toString())) }
            }
        )

    private fun Anime.uniqueIds(): List<UniqueId> = buildList {
        bangumiId?.let { add(UniqueId("bangumi", it.toString(), true)) }
        anilistId?.let { add(UniqueId("anilist", it.toString(), bangumiId == null)) }
        tmdbId?.let { add(UniqueId("tmdb", it.toString())) }
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
