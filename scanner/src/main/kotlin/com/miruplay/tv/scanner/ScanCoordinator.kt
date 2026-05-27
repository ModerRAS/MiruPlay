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
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.NfoMetadata
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.TvShowNfoMetadata
import com.miruplay.tv.model.UniqueId
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexMetadataCache
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaScrapeStatus
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
import java.net.URL
import java.security.MessageDigest
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
    suspend fun scanSource(
        sourceId: Long,
        filenameOnly: Boolean = false,
        posterCacheDirectory: File? = null,
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
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
        val disableOnlineMetadata = sourceInfo.connectionInfo["disableOnlineMetadata"]?.equals("true", ignoreCase = true) == true

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
        val classifier = VideoDirectoryClassifier(detector, filenameMetadataParser, filenameOnly = filenameOnly)
        val indexEntities = mutableListOf<MediaIndexEntry>()
        val titleCandidatesByAnime = mutableMapOf<String, MutableSet<String>>()
        var totalFiles = 0
        var newEpisodes = 0
        var scrapedFiles = 0
        var noMatchFiles = 0

        traverseAndProcess(
            ms = ms,
            path = scanStartPath,
            sourceId = sourceId,
            sourceName = sourceInfo.name,
            sourceType = sourceInfo.type,
            classifier = classifier,
            indexEntities = indexEntities,
            titleCandidatesByAnime = titleCandidatesByAnime,
            totalFiles = { totalFiles += 1 },
            newEpisodes = { newEpisodes += 1 },
            rootPath = realRootPath,
            isLocalSource = isLocalSource,
            filenameOnly = filenameOnly,
        )

        // Save index
        if (indexEntities.isNotEmpty()) {
            val updatedIndexEntities = mutableListOf<MediaIndexEntry>()
            updatedIndexEntities += indexEntities.filter { it.isDirectory }

            // Also cache episodes in the episode table so AnimeDetailViewModel can read them
            val episodesByAnime = indexEntities
                .filter { !it.isDirectory }
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
                val online = enrichWithOnlineMetadata(
                    animeName = animeName,
                    episodes = episodes,
                    extraTitleCandidates = titleCandidatesByAnime[animeName].orEmpty(),
                    posterCacheDirectory = posterCacheDirectory,
                )
                val scrapedAt = if (online.scrapeStatus == MediaScrapeStatus.SCRAPED) {
                    System.currentTimeMillis()
                } else {
                    0L
                }
                when (online.scrapeStatus) {
                    MediaScrapeStatus.SCRAPED -> scrapedFiles += sortedEntries.size
                    MediaScrapeStatus.NO_MATCH -> noMatchFiles += sortedEntries.size
                    else -> Unit
                }
                updatedIndexEntities += sortedEntries.map { entry ->
                    val matchedEntry = online.match?.let { match ->
                        entry.copy(
                            sourceId = sourceId,
                            metadataSource = match.source.name,
                            metadataId = match.animeId,
                            metadataTitle = match.displayTitle(),
                        )
                    } ?: entry
                    matchedEntry.copy(
                        scrapeStatus = online.scrapeStatus,
                        scrapeMessage = online.scrapeMessage,
                        scrapedAt = scrapedAt,
                    )
                }
                metadataRepository.cacheEpisodes(animeName, online.episodes)

            MediaIndexMetadataCache(metadataRepository).cache(
                source = sourceInfo,
                entries = indexEntities,
                episodeTransform = { animeName, episodes ->
                    onlineMetadataFor(animeName, episodes).episodes
                },
                animeTransform = { animeName, episodes ->
                    val online = onlineMetadataFor(animeName, episodes)
                    val anime = online.anime
                        ?: metadataRepository.getCachedMetadata(animeName).getOrNull()?.copy(episodeCount = episodes.size)
                        ?: Anime(
                            id = animeName,
                            title = animeName,
                            titleCn = animeName,
                            episodeCount = episodes.size,
                        )
                    if (isLocalSource && !isDocumentTree) {
                        val sortedEntries = indexEntities
                            .filter { !it.isDirectory && (it.animeName ?: "Unknown") == animeName }
                            .sortedWith(
                                compareBy<MediaIndexEntry>(
                                    { it.seasonNumber ?: 1 },
                                    { it.episodeNumber ?: Int.MAX_VALUE },
                                    { it.path },
                                ),
                            )
                            metadataRepository.cacheMetadata(minimal)
                            animeForNfo = minimal
                        }
                    }
                }

                if (isLocalSource && !isDocumentTree && !filenameOnly) {
                    animeForNfo?.let { anime ->
                        writeGeneratedNfoIfMissing(
                            classifier = classifier,
                            anime = anime,
                            episodes = episodes,
                            entries = sortedEntries,
                        )
                    }
                }
            }
            indexRepository.rebuildIndex(sourceId, updatedIndexEntities)
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
            animeName = sourceInfo.scanResultDisplayName(rootPath),
            episodesFound = totalFiles,
            newEpisodes = newEpisodes,
            updatedEpisodes = 0,
            scraped = scrapedFiles,
            noMatch = noMatchFiles,
        ))
    }

    private data class OnlineMetadata(
        val anime: Anime?,
        val episodes: List<Episode>,
        val match: ScraperResult? = null,
        val scrapeStatus: MediaScrapeStatus = MediaScrapeStatus.NO_MATCH,
        val scrapeMessage: String? = null,
    )

    private suspend fun enrichWithOnlineMetadata(
        animeName: String,
        episodes: List<Episode>,
        extraTitleCandidates: Collection<String> = emptyList(),
        posterCacheDirectory: File? = null,
    ): OnlineMetadata {
        val bangumi = metadataScrapers.firstOrNull { it.sourceName.equals("Bangumi", ignoreCase = true) }
            ?: return OnlineMetadata(
                anime = null,
                episodes = episodes,
                scrapeStatus = MediaScrapeStatus.PENDING,
                scrapeMessage = "Bangumi scraper unavailable",
            )

        return try {
            val candidates = titleCandidates(animeName, extraTitleCandidates)
            var match = bangumi.searchByAlias(animeName, candidates).getOrNull()
            var matchedBy = "alias"
            if (match != null) {
                MiruLog.i(
                    tag = TAG,
                    message = "Recognition alias match found",
                    attributes = recognitionBaseAttributes +
                        buildMatchAttributes(match) +
                        mapOf("search_strategy" to "alias")
                )
            }
            if (match == null) {
                MiruLog.d(
                    tag = TAG,
                    message = "Recognition alias search missed",
                    attributes = recognitionBaseAttributes + mapOf("search_strategy" to "alias")
                )
                var fallbackAttemptCount = 0
                val fallbackAttemptSample = mutableListOf<String>()
                for (candidate in candidates) {
                    fallbackAttemptCount += 1
                    if (fallbackAttemptSample.size < MAX_RECOGNITION_ATTEMPT_SAMPLE_IN_LOG) {
                        fallbackAttemptSample += candidate
                    }
                    match = bangumi.searchAnime(candidate).getOrNull()
                        ?.firstOrNull { it.confidence >= RECOGNITION_CONFIDENCE_THRESHOLD }
                    if (match != null) {
                        matchedBy = "fallback_search"
                        MiruLog.i(
                            tag = TAG,
                            message = "Recognition fallback match found",
                            attributes = recognitionBaseAttributes +
                                buildMatchAttributes(match) +
                                mapOf(
                                    "search_strategy" to matchedBy,
                                    "matched_candidate" to normalizeForLog(candidate, MAX_RECOGNITION_CANDIDATE_LENGTH),
                                    "fallback_attempt_count" to fallbackAttemptCount.toString(),
                                    "fallback_candidate_sample" to sampleCandidates(
                                        fallbackAttemptSample,
                                        MAX_RECOGNITION_ATTEMPT_SAMPLE_IN_LOG
                                    ),
                                )
                        )
                        break
                    }
                }
                if (match == null) {
                    MiruLog.w(
                        tag = TAG,
                        message = "Recognition no reliable match",
                        attributes = recognitionBaseAttributes + mapOf(
                            "search_strategy" to "fallback_search",
                            "fallback_attempt_count" to fallbackAttemptCount.toString(),
                            "fallback_candidate_sample" to sampleCandidates(
                                fallbackAttemptSample,
                                MAX_RECOGNITION_ATTEMPT_SAMPLE_IN_LOG
                            ),
                            "confidence_threshold" to RECOGNITION_CONFIDENCE_THRESHOLD.toString(),
                            "scrape_status" to MediaScrapeStatus.NO_MATCH.name,
                        )
                    )
                }
            }
            match ?: return OnlineMetadata(
                anime = null,
                episodes = episodes,
                scrapeStatus = MediaScrapeStatus.NO_MATCH,
                scrapeMessage = "Bangumi no reliable match",
            )

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

            val posterLocalPath = details?.posterUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { cachePoster(posterCacheDirectory, it) }
            val baseAnime = details ?: Anime(
                id = animeName,
                title = match.title.ifBlank { animeName },
                titleCn = match.titleCn,
                bangumiId = match.animeId.toIntOrNull(),
            )
            val anime = baseAnime.copy(
                id = animeName,
                title = baseAnime.title.ifBlank { animeName },
                titleCn = baseAnime.titleCn ?: match.titleCn,
                episodeCount = maxOf(baseAnime.episodeCount, enrichedEpisodes.size),
                bangumiId = baseAnime.bangumiId ?: match.animeId.toIntOrNull(),
                posterLocalPath = posterLocalPath ?: baseAnime.posterLocalPath,
            )

            OnlineMetadata(
                anime = anime,
                episodes = enrichedEpisodes,
                match = match,
                scrapeStatus = MediaScrapeStatus.SCRAPED,
            )
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

    private fun cachePoster(cacheDirectory: File?, url: String): String? {
        val directory = cacheDirectory ?: return null
        val file = File(directory, sha256Hex(url))
        return runCatching {
            if (file.exists() && file.length() > 0L) return@runCatching file.absolutePath
            directory.mkdirs()
            val temp = File(directory, "${file.name}.tmp")
            URL(url).openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 20_000
            }.getInputStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun titleCandidates(
        animeName: String,
        extraCandidates: Collection<String> = emptyList()
    ): List<String> =
        (listOf(animeName) + extraCandidates)
            .flatMap { candidate ->
                val withoutBracketGroups = candidate
                    .replace(Regex("\\[[^\\]]*]"), " ")
                    .replace(Regex("【[^】]*】"), " ")
                val withoutSeasonSuffix = withoutBracketGroups
                    .replace(Regex("(?i)\\b(s\\d{1,2}|season\\s*\\d{1,2}|第\\s*\\d+\\s*[季期])\\b"), " ")
                listOf(candidate, withoutBracketGroups, withoutSeasonSuffix)
            }
            .map { it.replace(Regex("[._]+"), " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun folderParseSample(contexts: List<VideoClassificationParsedContext>): String =
        contexts.asSequence()
            .take(MAX_CLASSIFICATION_CONTEXT_SAMPLE_IN_LOG)
            .joinToString(" | ") { context ->
                listOfNotNull(
                    "d=${context.distance}",
                    "text=${normalizeForLog(context.text, MAX_LOG_TEXT_LENGTH)}",
                    context.parsed.title?.takeIf { it.isNotBlank() }?.let { "title=${normalizeForLog(it, MAX_LOG_TEXT_LENGTH)}" },
                    context.parsed.season?.let { "s=$it" },
                    context.parsed.episode?.let { "e=$it" },
                ).joinToString(",")
            }

    private fun evidenceSummary(evidence: List<VideoClassificationEvidenceSummary>): String =
        evidence.asSequence()
            .sortedByDescending { it.score }
            .take(MAX_CLASSIFICATION_EVIDENCE_SAMPLE_IN_LOG)
            .joinToString(" | ") { item ->
                listOfNotNull(
                    item.source,
                    "score=${item.score}",
                    item.title?.takeIf { it.isNotBlank() }?.let { "title=${normalizeForLog(it, MAX_LOG_TEXT_LENGTH)}" },
                    item.seasonNumber?.let { "s=$it" },
                    item.episodeNumber?.let { "e=$it" },
                ).joinToString(",")
            }

    private fun pathTailForLog(path: String, maxSegments: Int = MAX_PATH_TAIL_SEGMENTS_IN_LOG): String {
        val normalized = path.replace('\\', '/').substringBefore('?').trim('/')
        if (normalized.isBlank()) return ""
        return normalized
            .split('/')
            .filter { it.isNotBlank() }
            .takeLast(maxSegments)
            .joinToString("/")
            .let { normalizeForLog(it, MAX_PATH_TAIL_LENGTH_IN_LOG) }
    }

    private fun extensionOf(name: String): String =
        name.substringBefore('?')
            .substringAfterLast('/', name)
            .substringAfterLast('\\')
            .substringAfterLast('.', "")
            .lowercase()

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
        sourceName: String,
        sourceType: MediaSourceType,
        classifier: VideoDirectoryClassifier,
        indexEntities: MutableList<MediaIndexEntry>,
        titleCandidatesByAnime: MutableMap<String, MutableSet<String>>,
        totalFiles: (Int) -> Unit,
        newEpisodes: (Int) -> Unit,
        depth: Int = 0,
        rootPath: String?,
        isLocalSource: Boolean,
        filenameOnly: Boolean,
    ) {
        // Guard: skip hidden directories
        val pathName = MediaPathConventions.fileName(path)
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
                    traverseAndProcess(
                        ms = ms,
                        path = file.path,
                        sourceId = sourceId,
                        classifier = classifier,
                        indexEntities = indexEntities,
                        titleCandidatesByAnime = titleCandidatesByAnime,
                        totalFiles = totalFiles,
                        newEpisodes = newEpisodes,
                        depth = depth + 1,
                        rootPath = rootPath,
                        isLocalSource = isLocalSource,
                        filenameOnly = filenameOnly,
                    )
                } else {
                    val fileName = file.name
                    if (MediaFileConventions.isVideoName(fileName, videoExtensions)) {
                        totalFiles(1)
                        val match = classifier.classifyVideo(file.path, fileName)
                        MiruLog.i(
                            tag = TAG,
                            message = "Scan video classified",
                            attributes = buildVideoClassificationAttributes(
                                sourceId = sourceId,
                                sourceName = sourceName,
                                sourceType = sourceType,
                                file = file,
                                classification = match,
                                filenameOnly = filenameOnly,
                            )
                        )
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
                        titleCandidatesByAnime
                            .getOrPut(match.animeName) { linkedSetOf() }
                            .addAll(match.titleCandidates)
                        if (match.episodeNumber != null) newEpisodes(1)

                        // Report progress every 5 video files
                        if (file.path.hashCode() % 5 == 0) {
                            progressCallback?.onProgress(match.animeName, 1, if (match.episodeNumber != null) 1 else 0)
                        }
                    } else if (!filenameOnly) {
                        if (MediaFileConventions.hasExtension(fileName, "nfo")) {
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
        private const val TAG = "ScanCoordinator"
        private const val RECOGNITION_CONFIDENCE_THRESHOLD = 0.62f
        private const val MAX_RECOGNITION_CANDIDATES_IN_LOG = 6
        private const val MAX_RECOGNITION_ATTEMPT_SAMPLE_IN_LOG = 4
        private const val MAX_RECOGNITION_CANDIDATE_LENGTH = 80
        private const val MAX_CLASSIFICATION_CONTEXT_SAMPLE_IN_LOG = 4
        private const val MAX_CLASSIFICATION_EVIDENCE_SAMPLE_IN_LOG = 6
        private const val MAX_LOG_TEXT_LENGTH = 120
        private const val MAX_PATH_TAIL_SEGMENTS_IN_LOG = 4
        private const val MAX_PATH_TAIL_LENGTH_IN_LOG = 240
        private val whitespaceRegex = Regex("\\s+")
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
