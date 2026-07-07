package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.core.common.logging.PerformanceLog
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.metadata.NfoWriteOptions
import com.miruplay.tv.metadata.XmlNfoWriter
import com.miruplay.tv.metadata.XmlNfoParser
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaRecognitionMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.MediaFileConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.NfoMetadata
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.TvShowNfoMetadata
import com.miruplay.tv.model.UniqueId
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.recognitionMode
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaScrapeStatus
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.scraper.EpisodeMetadata
import com.miruplay.tv.scraper.MetadataImageBackfillScraper
import com.miruplay.tv.scraper.MetadataScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
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
    private val mlipLibraryIndexImporter: MlipLibraryIndexImporter = MlipLibraryIndexImporter(indexRepository, metadataRepository),
    private val metadataScrapers: Set<@JvmSuppressWildcards MetadataScraper> = emptySet(),
    private val cloudDriveRepository: CloudDriveAutomationRepository? = null,
) {
    private val generatedNfoWriter = XmlNfoWriter(NfoWriteOptions(createBackup = false))
    private val imageBackfillScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Full scan of a media source, updates index + metadata in one pass.
     * Cancellable via coroutineContext.
     */
    suspend fun scanSource(
        sourceId: Long,
        filenameOnly: Boolean = false,
        posterCacheDirectory: File? = null,
    ): Result<ScanResult> = PerformanceLog.measureSuspendResult(
        tag = TAG,
        operation = "scan.source",
        attributes = mapOf(
            "source_id" to sourceId.toString(),
            "filename_only" to filenameOnly.toString(),
        ),
    ) {
        withContext(Dispatchers.IO) {
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
        if (sourceInfo.recognitionMode() == MediaRecognitionMode.MLIP) {
            val importStartedAtMs = System.currentTimeMillis()
            MiruLog.i(
                tag = TAG,
                message = "MLIP source import started",
                attributes = mapOf(
                    "scan_phase" to "mlip_import_start",
                    "source_id" to sourceId.toString(),
                    "source_name" to sourceInfo.name,
                    "source_type" to sourceInfo.type.name,
                ),
            )
            val importResult = when (val imported = mlipLibraryIndexImporter.importLibrary(
                source = sourceInfo,
                mediaSource = ms,
                posterCacheDirectory = posterCacheDirectory,
            )) {
                is Result.Success -> imported.data
                is Result.Error -> return@withContext Result.failure(imported.error)
            }
            val completedAtMs = System.currentTimeMillis()
            when (val sourceUpdated = mediaRepository.updateSource(sourceInfo.copy(isConnected = true, lastScanned = completedAtMs))) {
                is Result.Success -> Unit
                is Result.Error -> MiruLog.w(
                    tag = TAG,
                    message = "MLIP source timestamp update failed",
                    attributes = mapOf(
                        "scan_phase" to "mlip_source_timestamp_update_failed",
                        "source_id" to sourceId.toString(),
                        "source_name" to sourceInfo.name,
                        "source_type" to sourceInfo.type.name,
                        "error" to sourceUpdated.error.toUserMessage(),
                    ),
                )
            }
            MiruLog.i(
                tag = TAG,
                message = "MLIP source import completed",
                attributes = mapOf(
                    "scan_phase" to "mlip_import_complete",
                    "source_id" to sourceId.toString(),
                    "source_name" to sourceInfo.name,
                    "source_type" to sourceInfo.type.name,
                    "series_count" to importResult.seriesCount.toString(),
                    "episode_count" to importResult.episodeCount.toString(),
                    "media_file_count" to importResult.mediaFileCount.toString(),
                    "skipped_file_count" to importResult.skippedFileCount.toString(),
                    "artwork_cached_count" to importResult.artworkCachedCount.toString(),
                    "non_integer_episode_count" to importResult.nonIntegerEpisodeCount.toString(),
                    "scan_duration_ms" to (completedAtMs - importStartedAtMs).toString(),
                ),
            )
            return@withContext Result.success(
                ScanResult(
                    animeName = sourceInfo.name,
                    episodesFound = importResult.mediaFileCount,
                    newEpisodes = importResult.mediaFileCount,
                    updatedEpisodes = 0,
                    scraped = importResult.mediaFileCount,
                    noMatch = importResult.skippedFileCount,
                ),
            )
        }
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
        val remoteRootContext = if (isLocalSource) null else remoteRootContextName(rootPath)
        val disableOnlineMetadata = sourceInfo.connectionInfo["disableOnlineMetadata"]?.equals("true", ignoreCase = true) == true
        val isDramaSource = sourceInfo.contentMode == MediaContentMode.DRAMA
        val scanStartedAtMs = System.currentTimeMillis()
        val scanSessionId = "$sourceId-$scanStartedAtMs"

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
        MiruLog.i(
            tag = TAG,
            message = "Scan source started",
            attributes = mapOf(
                "scan_phase" to "source_start",
                "scan_session_id" to scanSessionId,
                "source_id" to sourceId.toString(),
                "source_name" to sourceInfo.name,
                "source_type" to sourceInfo.type.name,
                "content_mode" to sourceInfo.contentMode.name,
                "root_path_tail" to pathTailForLog(rootPath),
                "root_path_hash" to hashForLog(rootPath),
                "scan_start_path_tail" to pathTailForLog(scanStartPath),
                "scan_start_path_hash" to hashForLog(scanStartPath),
                "real_root_path_tail" to realRootPath.orEmpty().let(::pathTailForLog),
                "is_local_source" to isLocalSource.toString(),
                "is_document_tree" to isDocumentTree.toString(),
                "filename_only" to filenameOnly.toString(),
                "disable_online_metadata" to disableOnlineMetadata.toString(),
                "remote_root_context" to normalizeForLog(remoteRootContext.orEmpty(), MAX_LOG_TEXT_LENGTH),
            )
        )

        // Report starting
        progressCallback?.onProgress(sourceInfo.name, 0, 0)

        // Single recursive traversal: build index + parse NFOs
        val detector = DefaultEpisodeDetector()
        val classifier = VideoDirectoryClassifier(detector, filenameMetadataParser, filenameOnly = filenameOnly)
        val indexEntities = mutableListOf<MediaIndexEntry>()
        val titleCandidatesByAnime = mutableMapOf<String, MutableSet<String>>()
        var totalFiles = 0
        var newEpisodes = 0
        var scrapedFiles = 0
        var noMatchFiles = 0
        val traversalDiagnostics = ScanTraversalDiagnostics(scanSessionId = scanSessionId)

        val rootTraversalError = traverseAndProcess(
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
            remoteRootContext = remoteRootContext,
            scanSessionId = scanSessionId,
            diagnostics = traversalDiagnostics,
        )
        if (rootTraversalError != null) {
            flushPendingProgress(traversalDiagnostics)
            return@withContext Result.failure(rootTraversalError)
        }
        flushPendingProgress(traversalDiagnostics)
        MiruLog.i(
            tag = TAG,
            message = "Scan traversal completed",
            attributes = buildTraversalAttributes(
                scanSessionId = scanSessionId,
                sourceId = sourceId,
                sourceName = sourceInfo.name,
                sourceType = sourceInfo.type,
                path = scanStartPath,
                depth = 0,
                diagnostics = traversalDiagnostics,
            ) + mapOf(
                "scan_phase" to "traversal_complete",
                "traversal_duration_ms" to (System.currentTimeMillis() - scanStartedAtMs).toString(),
            )
        )

        // Save index
        if (indexEntities.isNotEmpty()) {
            val updatedIndexEntities = mutableListOf<MediaIndexEntry>()
            updatedIndexEntities += indexEntities.filter { it.isDirectory }

            // Cache episodes for playback/detail flows after the index rebuild completes.
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
                val animeTitleCandidates = titleCandidatesByAnime[animeName].orEmpty()
                val online = if (disableOnlineMetadata) {
                    OnlineMetadata(
                        anime = null,
                        episodes = episodes,
                        scrapeStatus = MediaScrapeStatus.PENDING,
                        scrapeMessage = "Online metadata disabled for source",
                    )
                } else if (isDramaSource) {
                    deferredDramaMetadata(episodes)
                } else {
                    reusableCachedMetadata(
                        animeName = animeName,
                        episodes = episodes,
                        posterCacheDirectory = posterCacheDirectory,
                    ) ?: enrichWithOnlineMetadata(
                        animeName = animeName,
                        episodes = episodes,
                        extraTitleCandidates = animeTitleCandidates,
                        posterCacheDirectory = posterCacheDirectory,
                    )
                }
                MiruLog.i(
                    tag = TAG,
                    message = "Scan recognition summary",
                    attributes = mapOf(
                        "scan_phase" to "recognition_summary",
                        "source_id" to sourceId.toString(),
                        "source_name" to sourceInfo.name,
                        "source_type" to sourceInfo.type.name,
                        "content_mode" to sourceInfo.contentMode.name,
                        "anime_name" to normalizeForLog(animeName, 120),
                        "episode_count" to episodes.size.toString(),
                        "episode_enriched_count" to online.episodes.size.toString(),
                        "candidate_count" to animeTitleCandidates.size.toString(),
                        "candidate_sample" to sampleCandidates(animeTitleCandidates),
                        "scrape_status" to online.scrapeStatus.name,
                        "scrape_message" to normalizeForLog(online.scrapeMessage.orEmpty(), 180),
                        "metadata_source" to (online.match?.source?.name ?: ""),
                        "metadata_id" to (online.match?.animeId ?: ""),
                        "metadata_title" to normalizeForLog(online.match?.displayTitle().orEmpty(), 120),
                        "metadata_confidence" to (online.match?.confidence?.toString() ?: ""),
                    )
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

                var animeForNfo: Anime? = null
                if (isDramaSource) {
                    animeForNfo = createGeneratedLocalMetadata(
                        title = animeName,
                        episodeCount = episodes.size,
                    )
                } else if (online.anime != null) {
                    metadataRepository.cacheMetadata(online.anime)
                    animeForNfo = online.anime
                } else {
                    metadataRepository.getCachedMetadata(animeName).onSuccess { cachedAnime ->
                        if (cachedAnime != null) {
                            val updated = cachedAnime.copy(episodeCount = episodes.size)
                            metadataRepository.cacheMetadata(updated)
                            animeForNfo = updated
                        } else {
                            val minimal = createGeneratedLocalMetadata(
                                title = animeName,
                                episodeCount = episodes.size,
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
                            episodes = online.episodes,
                            entries = sortedEntries
                        )
                    }
                }
            }
            indexRepository.rebuildIndex(sourceId, updatedIndexEntities)
        }

        val scanCompletedAtMs = System.currentTimeMillis()
        when (val sourceUpdated = mediaRepository.updateSource(sourceInfo.copy(isConnected = true, lastScanned = scanCompletedAtMs))) {
            is Result.Success -> Unit
            is Result.Error -> MiruLog.w(
                tag = TAG,
                message = "Scan source timestamp update failed",
                attributes = mapOf(
                    "scan_phase" to "source_timestamp_update_failed",
                    "source_id" to sourceId.toString(),
                    "source_name" to sourceInfo.name,
                    "source_type" to sourceInfo.type.name,
                    "error" to sourceUpdated.error.toUserMessage(),
                )
            )
        }

        // Report done
        Log.d(TAG, "Scan done: ${sourceInfo.name} -> $totalFiles files, $newEpisodes new episodes")
        MiruLog.i(
            tag = TAG,
            message = "Scan source completed",
            attributes = mapOf(
                "scan_phase" to "scan_complete",
                "source_id" to sourceId.toString(),
                "source_name" to sourceInfo.name,
                "source_type" to sourceInfo.type.name,
                "scan_session_id" to scanSessionId,
                "total_video_files" to totalFiles.toString(),
                "new_episode_count" to newEpisodes.toString(),
                "scraped_file_count" to scrapedFiles.toString(),
                "no_match_file_count" to noMatchFiles.toString(),
                "filename_only" to filenameOnly.toString(),
                "scan_duration_ms" to (scanCompletedAtMs - scanStartedAtMs).toString(),
                "directory_count" to traversalDiagnostics.directoriesVisited.toString(),
                "entry_count" to traversalDiagnostics.entriesSeen.toString(),
                "nfo_count" to traversalDiagnostics.nfoFilesSeen.toString(),
                "skipped_directory_count" to traversalDiagnostics.skippedDirectories.toString(),
                "skipped_escaped_path_count" to traversalDiagnostics.skippedEscapedPaths.toString(),
            )
        )

            Result.success(ScanResult(
                animeName = if (isLocalSource) sourceInfo.displayNameOrPath(rootPath) else sourceInfo.name,
                episodesFound = totalFiles,
                newEpisodes = newEpisodes,
                updatedEpisodes = 0,
                scraped = scrapedFiles,
                noMatch = noMatchFiles,
            ))
        }
    }

    private data class OnlineMetadata(
        val anime: Anime?,
        val episodes: List<Episode>,
        val match: ScraperResult? = null,
        val scrapeStatus: MediaScrapeStatus = MediaScrapeStatus.NO_MATCH,
        val scrapeMessage: String? = null,
    )

    private suspend fun reusableCachedMetadata(
        animeName: String,
        episodes: List<Episode>,
        posterCacheDirectory: File?,
    ): OnlineMetadata? {
        val cachedByName = metadataRepository.getCachedMetadata(animeName).getOrNull()
        val cachedAnime = cachedByName?.takeIf { it.hasReusablePosterMetadata() }
            ?: cachedByName?.bangumiId
                ?.let { metadataRepository.getCachedMetadataByBangumiId(it).getOrNull() }
                ?.takeIf { it.hasReusablePosterMetadata() }
            ?: return null
        val cachedEpisodes = metadataRepository.getCachedEpisodes(animeName).getOrNull().orEmpty()
            .ifEmpty {
                if (cachedAnime.id == animeName) {
                    emptyList()
                } else {
                    metadataRepository.getCachedEpisodes(cachedAnime.id).getOrNull().orEmpty()
                }
            }
        val cachedEpisodesByNumber = cachedEpisodes.associateBy { it.episodeNumber }
        val mergedEpisodes = episodes.map { episode ->
            val cached = cachedEpisodesByNumber[episode.episodeNumber] ?: return@map episode
            episode.copy(
                title = cached.title.takeIf { it.isNotBlank() } ?: episode.title,
                duration = episode.duration.takeIf { it > 0 } ?: cached.duration,
                thumbnailPath = cached.thumbnailPath ?: episode.thumbnailPath,
                bangumiEpisodeId = cached.bangumiEpisodeId ?: episode.bangumiEpisodeId,
                bangumiCollectionType = cached.bangumiCollectionType ?: episode.bangumiCollectionType,
            )
        }
        val posterLocalPath = cachedAnime.posterUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { cachePoster(posterCacheDirectory, it) }
            ?: cachedAnime.posterLocalPath
        val anime = cachedAnime.copy(
            id = animeName,
            episodeCount = maxOf(cachedAnime.episodeCount, mergedEpisodes.size),
            posterLocalPath = posterLocalPath ?: cachedAnime.posterLocalPath,
        )
        MiruLog.i(
            tag = TAG,
            message = "Recognition cached metadata reused",
            attributes = mapOf(
                "anime_name" to normalizeForLog(animeName, 120),
                "episode_count" to episodes.size.toString(),
                "cached_episode_count" to cachedEpisodes.size.toString(),
                "poster_cached" to (!posterLocalPath.isNullOrBlank()).toString(),
                "scrape_status" to MediaScrapeStatus.SCRAPED.name,
            )
        )
        return OnlineMetadata(
            anime = anime,
            episodes = mergedEpisodes,
            match = anime.toCachedBangumiMatch(),
            scrapeStatus = MediaScrapeStatus.SCRAPED,
            scrapeMessage = "Cached metadata reused",
        )
    }

    private fun Anime.hasReusablePosterMetadata(): Boolean =
        !posterUrl.isNullOrBlank() || !posterLocalPath.isNullOrBlank()

    private fun Anime.toCachedBangumiMatch(): ScraperResult? {
        val id = bangumiId ?: return null
        return ScraperResult(
            animeId = id.toString(),
            title = title,
            titleCn = titleCn,
            matchedTitle = displayTitle(),
            confidence = 1f,
            source = ScraperSource.BANGUMI,
        )
    }

    private fun deferredDramaMetadata(
        episodes: List<Episode>,
    ): OnlineMetadata = OnlineMetadata(
        anime = null,
        episodes = episodes,
        scrapeStatus = MediaScrapeStatus.PENDING,
        scrapeMessage = "Drama metadata is resolved from TMDB detail flow",
    )

    private fun createGeneratedLocalMetadata(
        title: String,
        episodeCount: Int,
    ): Anime = Anime(
        id = title,
        title = title,
        titleCn = title,
        episodeCount = episodeCount,
    )

    private suspend fun enrichWithOnlineMetadata(
        animeName: String,
        episodes: List<Episode>,
        extraTitleCandidates: Collection<String> = emptyList(),
        posterCacheDirectory: File? = null,
    ): OnlineMetadata = PerformanceLog.measureSuspend(
        tag = TAG,
        operation = "scan.recognition.enrich",
        attributes = mapOf(
            "anime_name_hash" to hashForLog(animeName),
            "episode_count" to episodes.size.toString(),
            "extra_candidate_count" to extraTitleCandidates.size.toString(),
        ),
        resultAttributes = { result ->
            mapOf(
                "scrape_status" to result.scrapeStatus.name,
                "matched" to (result.match != null).toString(),
                "metadata_source" to (result.match?.source?.name ?: ""),
                "episode_enriched_count" to result.episodes.size.toString(),
            )
        },
    ) {
        val candidates = titleCandidates(animeName, extraTitleCandidates)
        val recognitionBaseAttributes = buildRecognitionBaseAttributes(
            animeName = animeName,
            episodes = episodes,
            candidates = candidates,
        )
        val bangumi = metadataScrapers.firstOrNull { it.sourceName.equals("Bangumi", ignoreCase = true) }
            ?: return@measureSuspend OnlineMetadata(
                anime = null,
                episodes = episodes,
                scrapeStatus = MediaScrapeStatus.PENDING,
                scrapeMessage = "Bangumi scraper unavailable",
            ).also {
                MiruLog.w(
                    tag = TAG,
                    message = "Recognition scraper unavailable",
                    attributes = recognitionBaseAttributes + mapOf(
                        "scraper" to "Bangumi",
                        "scrape_status" to it.scrapeStatus.name,
                        "scrape_message" to it.scrapeMessage.orEmpty(),
                    )
                )
            }

        try {
            MiruLog.i(
                tag = TAG,
                message = "Recognition enrichment started",
                attributes = recognitionBaseAttributes + mapOf(
                    "scraper" to bangumi.sourceName,
                    "search_strategy" to "alias_then_fallback",
                    "confidence_threshold" to RECOGNITION_CONFIDENCE_THRESHOLD.toString(),
                )
            )
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
            match ?: return@measureSuspend OnlineMetadata(
                anime = null,
                episodes = episodes,
                scrapeStatus = MediaScrapeStatus.NO_MATCH,
                scrapeMessage = "Bangumi no reliable match",
            )

            val matchedFromArchive = match.fromLocalArchive
            val details = bangumi.getAnimeDetails(match.animeId).getOrNull()
            val episodeMetadata = if (matchedFromArchive) {
                emptyMap()
            } else {
                bangumi.getEpisodes(match.animeId).getOrNull()
                    .orEmpty()
                    .associateBy { it.episodeNumber }
            }

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

            val posterLocalPath = if (matchedFromArchive) {
                null
            } else {
                details?.posterUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { cachePoster(posterCacheDirectory, it) }
            }
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
            MiruLog.i(
                tag = TAG,
                message = "Recognition metadata enriched",
                attributes = recognitionBaseAttributes +
                    buildMatchAttributes(match) +
                    mapOf(
                        "search_strategy" to matchedBy,
                        "details_loaded" to (details != null).toString(),
                        "details_from_archive" to matchedFromArchive.toString(),
                        "remote_episode_count" to episodeMetadata.size.toString(),
                        "poster_cached" to (!posterLocalPath.isNullOrBlank()).toString(),
                        "scrape_status" to MediaScrapeStatus.SCRAPED.name,
                    )
            )

            if (matchedFromArchive) {
                scheduleImageBackfill(
                    scraper = bangumi,
                    anime = anime,
                    animeId = match.animeId,
                    posterCacheDirectory = posterCacheDirectory,
                    logAttributes = recognitionBaseAttributes + buildMatchAttributes(match),
                )
            }

            OnlineMetadata(
                anime = anime,
                episodes = enrichedEpisodes,
                match = match,
                scrapeStatus = MediaScrapeStatus.SCRAPED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Bangumi metadata enrichment failed for $animeName", e)
            MiruLog.e(
                tag = TAG,
                message = "Recognition metadata enrichment failed",
                throwable = e,
                attributes = recognitionBaseAttributes + mapOf(
                    "scraper" to bangumi.sourceName,
                    "scrape_status" to MediaScrapeStatus.FAILED.name,
                )
            )
            OnlineMetadata(
                anime = null,
                episodes = episodes,
                scrapeStatus = MediaScrapeStatus.FAILED,
                scrapeMessage = e.message ?: "Bangumi metadata enrichment failed",
            )
        }
    }

    private fun scheduleImageBackfill(
        scraper: MetadataScraper,
        anime: Anime,
        animeId: String,
        posterCacheDirectory: File?,
        logAttributes: Map<String, String>,
    ) {
        val imageScraper = scraper as? MetadataImageBackfillScraper ?: return
        imageBackfillScope.launch {
            try {
                val details = imageScraper.getImageDetails(animeId).getOrNull() ?: return@launch
                val posterUrl = details.posterUrl?.takeIf { it.isNotBlank() } ?: return@launch
                val posterLocalPath = cachePoster(posterCacheDirectory, posterUrl)
                val currentAnime = metadataRepository.getCachedMetadata(anime.id).getOrNull() ?: anime
                metadataRepository.cacheMetadata(
                    currentAnime.copy(
                        posterUrl = posterUrl,
                        posterLocalPath = posterLocalPath ?: currentAnime.posterLocalPath,
                        fanartUrl = details.fanartUrl ?: currentAnime.fanartUrl,
                    )
                )
                MiruLog.i(
                    tag = TAG,
                    message = "Recognition image backfill completed",
                    attributes = logAttributes + mapOf(
                        "image_backfill" to "completed",
                        "poster_cached" to (!posterLocalPath.isNullOrBlank()).toString(),
                    )
                )
            } catch (error: Exception) {
                MiruLog.w(
                    tag = TAG,
                    message = "Recognition image backfill failed",
                    throwable = error,
                    attributes = logAttributes + mapOf("image_backfill" to "failed"),
                )
            }
        }
    }

    private suspend fun cachePoster(cacheDirectory: File?, url: String): String? {
        val directory = cacheDirectory ?: return null
        val file = File(directory, sha256Hex(url))
        val proxy = currentImageProxy()
        return PerformanceLog.measureSuspend(
            tag = TAG,
            operation = "scan.poster_cache",
            attributes = mapOf(
                "url_hash" to hashForLog(url),
                "cache_hit_before" to (file.exists() && file.length() > 0L).toString(),
            ),
            resultAttributes = { result -> mapOf("poster_cached" to (!result.isNullOrBlank()).toString()) },
        ) {
            runCatching {
                if (file.exists() && file.length() > 0L) return@runCatching file.absolutePath
                directory.mkdirs()
                val temp = File(directory, "${file.name}.tmp")
                URL(url).openConnection(proxy).apply {
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
    }

    private suspend fun currentImageProxy(): Proxy {
        val config = runCatching { cloudDriveRepository?.getConfig()?.getOrNull() }.getOrNull()
            ?: return Proxy.NO_PROXY
        if (!config.rssProxyEnabled || config.rssProxyHost.isBlank()) return Proxy.NO_PROXY
        val address = InetSocketAddress(config.rssProxyHost.trim(), config.rssProxyPort.coerceIn(1, 65_535))
        return Proxy(Proxy.Type.HTTP, address)
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
                val baseForms = listOf(candidate, withoutBracketGroups, withoutSeasonSuffix)
                baseForms + baseForms.mapNotNull(::leadingTokenSuffixCandidate)
            }
            .map(::normalizeTitleCandidate)
            .filter { it.isNotBlank() }
            .distinct()

    private fun leadingTokenSuffixCandidate(candidate: String): String? {
        val tokens = normalizeTitleCandidate(candidate)
            .split(' ')
            .filter { it.isNotBlank() }
        if (tokens.size < 2) return null

        val suffix = tokens.drop(1).joinToString(" ").trim()
        return suffix.takeIf(::isUsefulTitleSuffix)
    }

    private fun normalizeTitleCandidate(candidate: String): String =
        candidate.replace(Regex("[._]+"), " ").replace(whitespaceRegex, " ").trim()

    private fun isUsefulTitleSuffix(candidate: String): Boolean {
        val compact = candidate.replace(whitespaceRegex, "")
        if (compact.isBlank() || seasonOnlyRegex.matches(compact)) return false

        val cjkCount = compact.count { it in '\u4e00'..'\u9fff' }
        val alphaNumericCount = compact.count { it.isLetterOrDigit() }
        return cjkCount >= 2 || alphaNumericCount >= 5
    }

    private fun buildRecognitionBaseAttributes(
        animeName: String,
        episodes: List<Episode>,
        candidates: List<String>,
    ): Map<String, String> = mapOf(
        "anime_name" to normalizeForLog(animeName, 120),
        "episode_count" to episodes.size.toString(),
        "candidate_count" to candidates.size.toString(),
        "candidate_sample" to sampleCandidates(candidates),
    )

    private fun buildMatchAttributes(match: ScraperResult): Map<String, String> = mapOf(
        "metadata_source" to match.source.name,
        "metadata_id" to match.animeId,
        "metadata_title" to normalizeForLog(match.displayTitle(), 120),
        "metadata_confidence" to match.confidence.toString(),
    )

    private fun sampleCandidates(
        candidates: Collection<String>,
        maxItems: Int = MAX_RECOGNITION_CANDIDATES_IN_LOG,
    ): String = candidates.asSequence()
        .map { normalizeForLog(it, MAX_RECOGNITION_CANDIDATE_LENGTH) }
        .filter { it.isNotBlank() }
        .take(maxItems)
        .joinToString(" | ")

    private data class ScanTraversalDiagnostics(
        val scanSessionId: String,
        var directoriesVisited: Int = 0,
        var entriesSeen: Int = 0,
        var videoFilesSeen: Int = 0,
        var nfoFilesSeen: Int = 0,
        var skippedDirectories: Int = 0,
        var skippedEscapedPaths: Int = 0,
        var lastDirectoryTail: String = "",
        var lastEntryTail: String = "",
        var lastVideoTail: String = "",
        var pendingProgressPath: String = "",
        var pendingProgressFiles: Int = 0,
        var pendingProgressNewEpisodes: Int = 0,
    )

    private fun flushPendingProgress(diagnostics: ScanTraversalDiagnostics) {
        if (diagnostics.pendingProgressFiles <= 0) return
        progressCallback?.onProgress(
            diagnostics.pendingProgressPath,
            diagnostics.pendingProgressFiles,
            diagnostics.pendingProgressNewEpisodes,
        )
        diagnostics.pendingProgressPath = ""
        diagnostics.pendingProgressFiles = 0
        diagnostics.pendingProgressNewEpisodes = 0
    }

    private fun shouldLogScanCheckpoint(videoOrdinal: Int): Boolean =
        videoOrdinal <= SCAN_INITIAL_VIDEO_CHECKPOINTS ||
            videoOrdinal % SCAN_VIDEO_CHECKPOINT_INTERVAL == 0 ||
            videoOrdinal in SCAN_DEBUG_FOCUS_VIDEO_RANGE

    private fun buildTraversalAttributes(
        scanSessionId: String,
        sourceId: Long,
        sourceName: String,
        sourceType: MediaSourceType,
        path: String,
        depth: Int,
        diagnostics: ScanTraversalDiagnostics,
    ): Map<String, String> = mapOf(
        "scan_session_id" to scanSessionId,
        "source_id" to sourceId.toString(),
        "source_name" to sourceName,
        "source_type" to sourceType.name,
        "depth" to depth.toString(),
        "path_tail" to pathTailForLog(path),
        "path_hash" to hashForLog(path),
        "directory_count" to diagnostics.directoriesVisited.toString(),
        "entry_count" to diagnostics.entriesSeen.toString(),
        "video_count" to diagnostics.videoFilesSeen.toString(),
        "nfo_count" to diagnostics.nfoFilesSeen.toString(),
        "skipped_directory_count" to diagnostics.skippedDirectories.toString(),
        "skipped_escaped_path_count" to diagnostics.skippedEscapedPaths.toString(),
        "last_directory_tail" to diagnostics.lastDirectoryTail,
        "last_entry_tail" to diagnostics.lastEntryTail,
        "last_video_tail" to diagnostics.lastVideoTail,
    )

    private fun buildScanEntryAttributes(
        scanSessionId: String,
        sourceId: Long,
        sourceName: String,
        sourceType: MediaSourceType,
        file: com.miruplay.tv.model.FileEntry,
        depth: Int,
        diagnostics: ScanTraversalDiagnostics,
    ): Map<String, String> =
        buildTraversalAttributes(
            scanSessionId = scanSessionId,
            sourceId = sourceId,
            sourceName = sourceName,
            sourceType = sourceType,
            path = file.path,
            depth = depth,
            diagnostics = diagnostics,
        ) + mapOf(
            "entry_ordinal" to diagnostics.entriesSeen.toString(),
            "entry_name" to normalizeForLog(file.name, MAX_LOG_TEXT_LENGTH),
            "entry_is_directory" to file.isDirectory.toString(),
            "file_extension" to extensionOf(file.name),
            "file_size_bytes" to file.size.toString(),
            "last_modified_ms" to file.lastModified.toString(),
            "mime_type" to file.mimeType.orEmpty(),
        )

    private fun normalizeForLog(value: String, maxLength: Int): String =
        value.replace(whitespaceRegex, " ").trim().take(maxLength)

    private fun hashForLog(value: String): String =
        value.takeIf { it.isNotBlank() }?.let(::sha256Hex).orEmpty()

    private fun buildVideoClassificationAttributes(
        scanSessionId: String,
        sourceId: Long,
        sourceName: String,
        sourceType: MediaSourceType,
        file: com.miruplay.tv.model.FileEntry,
        classification: VideoClassification,
        filenameOnly: Boolean,
        videoOrdinal: Int,
        entryOrdinal: Int,
        depth: Int,
        classificationDurationMs: Long,
    ): Map<String, String> {
        val diagnostics = classification.diagnostics
        val topEvidence = diagnostics.evidence.maxByOrNull { it.score }
        return mapOf(
            "scan_phase" to "video_classification",
            "scan_session_id" to scanSessionId,
            "source_id" to sourceId.toString(),
            "source_name" to sourceName,
            "source_type" to sourceType.name,
            "filename_only" to filenameOnly.toString(),
            "video_ordinal" to videoOrdinal.toString(),
            "entry_ordinal" to entryOrdinal.toString(),
            "depth" to depth.toString(),
            "classification_duration_ms" to classificationDurationMs.toString(),
            "file_name" to normalizeForLog(file.name, MAX_LOG_TEXT_LENGTH),
            "file_extension" to extensionOf(file.name),
            "file_size_bytes" to file.size.toString(),
            "last_modified_ms" to file.lastModified.toString(),
            "path_tail" to pathTailForLog(file.path),
            "path_hash" to sha256Hex(file.path),
            "parser_enabled" to diagnostics.parserEnabled.toString(),
            "model_path_input_tail" to diagnostics.pathModelText.orEmpty().let { pathTailForLog(it) },
            "model_path_input_hash" to diagnostics.pathModelText.orEmpty().let { if (it.isBlank()) "" else sha256Hex(it) },
            "model_path_input_length" to diagnostics.pathModelText.orEmpty().length.toString(),
            "model_file_input" to normalizeForLog(diagnostics.fileModelText.orEmpty(), MAX_LOG_TEXT_LENGTH),
            "path_parser_title" to normalizeForLog(diagnostics.pathParsed?.title.orEmpty(), MAX_LOG_TEXT_LENGTH),
            "path_parser_season" to diagnostics.pathParsed?.season?.toString().orEmpty(),
            "path_parser_episode" to diagnostics.pathParsed?.episode?.toString().orEmpty(),
            "file_parser_title" to normalizeForLog(diagnostics.fileParsed?.title.orEmpty(), MAX_LOG_TEXT_LENGTH),
            "file_parser_season" to diagnostics.fileParsed?.season?.toString().orEmpty(),
            "file_parser_episode" to diagnostics.fileParsed?.episode?.toString().orEmpty(),
            "folder_parser_count" to diagnostics.folderParsed.size.toString(),
            "folder_parser_sample" to folderParseSample(diagnostics.folderParsed),
            "release_title" to normalizeForLog(diagnostics.release?.title.orEmpty(), MAX_LOG_TEXT_LENGTH),
            "release_season" to diagnostics.release?.seasonNumber?.toString().orEmpty(),
            "release_episode" to diagnostics.release?.episodeNumber?.toString().orEmpty(),
            "detector_title" to normalizeForLog(diagnostics.detector?.title.orEmpty(), MAX_LOG_TEXT_LENGTH),
            "detector_season" to diagnostics.detector?.seasonNumber?.toString().orEmpty(),
            "detector_episode" to diagnostics.detector?.episodeNumber?.toString().orEmpty(),
            "season_folder_number" to diagnostics.seasonFolderNumber?.toString().orEmpty(),
            "show_context_title" to normalizeForLog(diagnostics.showContext?.title.orEmpty(), MAX_LOG_TEXT_LENGTH),
            "show_context_season" to diagnostics.showContext?.seasonNumber?.toString().orEmpty(),
            "show_context_episode" to diagnostics.showContext?.episodeNumber?.toString().orEmpty(),
            "anime_name" to normalizeForLog(classification.animeName, MAX_LOG_TEXT_LENGTH),
            "season_number" to classification.seasonNumber.toString(),
            "episode_number" to classification.episodeNumber?.toString().orEmpty(),
            "episode_detected" to (classification.episodeNumber != null).toString(),
            "title_candidate_count" to classification.titleCandidates.size.toString(),
            "title_candidate_sample" to sampleCandidates(classification.titleCandidates),
            "top_evidence_source" to topEvidence?.source.orEmpty(),
            "top_evidence_score" to topEvidence?.score?.toString().orEmpty(),
            "evidence_summary" to evidenceSummary(diagnostics.evidence),
        )
    }

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
        remoteRootContext: String? = null,
        scanSessionId: String,
        diagnostics: ScanTraversalDiagnostics,
    ): AppError? {
        // Guard: skip hidden directories
        val pathName = MediaPathConventions.fileName(path)
        if (pathName.startsWith(".")) {
            diagnostics.skippedDirectories += 1
            MiruLog.d(
                tag = TAG,
                message = "Scan directory skipped",
                attributes = buildTraversalAttributes(
                    scanSessionId = scanSessionId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    sourceType = sourceType,
                    path = path,
                    depth = depth,
                    diagnostics = diagnostics,
                ) + mapOf(
                    "scan_phase" to "directory_skip",
                    "skip_reason" to "hidden_directory",
                )
            )
            return null
        }

        // Guard: skip Android system media directories
        if (isLocalSource && pathName in skipDirs) {
            diagnostics.skippedDirectories += 1
            MiruLog.d(
                tag = TAG,
                message = "Scan directory skipped",
                attributes = buildTraversalAttributes(
                    scanSessionId = scanSessionId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    sourceType = sourceType,
                    path = path,
                    depth = depth,
                    diagnostics = diagnostics,
                ) + mapOf(
                    "scan_phase" to "directory_skip",
                    "skip_reason" to "system_directory",
                )
            )
            return null
        }

        // Guard: skip /mnt directory entirely
        if (isLocalSource && path.startsWith("/mnt/")) {
            diagnostics.skippedDirectories += 1
            MiruLog.d(
                tag = TAG,
                message = "Scan directory skipped",
                attributes = buildTraversalAttributes(
                    scanSessionId = scanSessionId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    sourceType = sourceType,
                    path = path,
                    depth = depth,
                    diagnostics = diagnostics,
                ) + mapOf(
                    "scan_phase" to "directory_skip",
                    "skip_reason" to "mnt_guard",
                )
            )
            return null
        }

        // Check for cancellation
        if (!currentCoroutineContext().isActive) return null

        try {
            diagnostics.directoriesVisited += 1
            diagnostics.lastDirectoryTail = pathTailForLog(path)
            val directoryOrdinal = diagnostics.directoriesVisited
            val listStartedAtMs = System.currentTimeMillis()
            MiruLog.d(
                tag = TAG,
                message = "Scan directory listing started",
                attributes = buildTraversalAttributes(
                    scanSessionId = scanSessionId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    sourceType = sourceType,
                    path = path,
                    depth = depth,
                    diagnostics = diagnostics,
                ) + mapOf(
                    "scan_phase" to "directory_list_start",
                    "directory_ordinal" to directoryOrdinal.toString(),
                )
            )
            val listResult = ms.listFiles(path)
            val listDurationMs = System.currentTimeMillis() - listStartedAtMs
            val files = when (listResult) {
                is Result.Success -> listResult.data
                is Result.Error -> {
                    MiruLog.w(
                        tag = TAG,
                        message = "Scan directory listing failed",
                        attributes = buildTraversalAttributes(
                            scanSessionId = scanSessionId,
                            sourceId = sourceId,
                            sourceName = sourceName,
                            sourceType = sourceType,
                            path = path,
                            depth = depth,
                            diagnostics = diagnostics,
                        ) + mapOf(
                            "scan_phase" to "directory_list_failed",
                            "directory_ordinal" to directoryOrdinal.toString(),
                            "duration_ms" to listDurationMs.toString(),
                            "error_type" to listResult.error::class.java.simpleName,
                            "error_message" to normalizeForLog(listResult.error.toString(), 240),
                        )
                    )
                    return if (depth == 0) listResult.error else null
                }
            }
            val listAttributes = buildTraversalAttributes(
                scanSessionId = scanSessionId,
                sourceId = sourceId,
                sourceName = sourceName,
                sourceType = sourceType,
                path = path,
                depth = depth,
                diagnostics = diagnostics,
            ) + mapOf(
                "scan_phase" to "directory_list_complete",
                "directory_ordinal" to directoryOrdinal.toString(),
                "directory_entry_count" to files.size.toString(),
                "duration_ms" to listDurationMs.toString(),
            )
            if (listDurationMs >= SLOW_DIRECTORY_LISTING_MS) {
                MiruLog.w(
                    tag = TAG,
                    message = "Scan directory listing slow",
                    attributes = listAttributes,
                )
            } else {
                MiruLog.d(
                    tag = TAG,
                    message = "Scan directory listed",
                    attributes = listAttributes,
                )
            }

            for (file in files) {
                if (!currentCoroutineContext().isActive) return null
                diagnostics.entriesSeen += 1
                diagnostics.lastEntryTail = pathTailForLog(file.path)

                // Belt-and-suspenders: if file.path escapes root boundary, skip it
                if (rootPath != null && !isWithinRoot(file.path, rootPath)) {
                    Log.w("ScanCoordinator", "Path escaped root boundary: ${file.path} (root=$rootPath), skipping")
                    diagnostics.skippedEscapedPaths += 1
                    MiruLog.w(
                        tag = TAG,
                        message = "Scan path escaped root boundary",
                        attributes = buildScanEntryAttributes(
                            scanSessionId = scanSessionId,
                            sourceId = sourceId,
                            sourceName = sourceName,
                            sourceType = sourceType,
                            file = file,
                            depth = depth,
                            diagnostics = diagnostics,
                        ) + mapOf(
                            "scan_phase" to "entry_skip",
                            "skip_reason" to "escaped_root_boundary",
                            "root_path_tail" to pathTailForLog(rootPath),
                            "root_path_hash" to hashForLog(rootPath),
                        )
                    )
                    continue
                }

                if (file.isDirectory) {
                    // Skip trickplay, hidden, and system directories
                    if (file.name.endsWith(".trickplay") || file.name.startsWith(".")) {
                        diagnostics.skippedDirectories += 1
                        MiruLog.d(
                            tag = TAG,
                            message = "Scan child directory skipped",
                            attributes = buildScanEntryAttributes(
                                scanSessionId = scanSessionId,
                                sourceId = sourceId,
                                sourceName = sourceName,
                                sourceType = sourceType,
                                file = file,
                                depth = depth,
                                diagnostics = diagnostics,
                            ) + mapOf(
                                "scan_phase" to "directory_skip",
                                "skip_reason" to "hidden_or_trickplay",
                            )
                        )
                        continue
                    }
                    if (isLocalSource && file.name in skipDirs) {
                        diagnostics.skippedDirectories += 1
                        MiruLog.d(
                            tag = TAG,
                            message = "Scan child directory skipped",
                            attributes = buildScanEntryAttributes(
                                scanSessionId = scanSessionId,
                                sourceId = sourceId,
                                sourceName = sourceName,
                                sourceType = sourceType,
                                file = file,
                                depth = depth,
                                diagnostics = diagnostics,
                            ) + mapOf(
                                "scan_phase" to "directory_skip",
                                "skip_reason" to "system_directory",
                            )
                        )
                        continue
                    }
                    
                    // Recurse into subdirectory
                    val childTraversalError = traverseAndProcess(
                        ms = ms,
                        path = file.path,
                        sourceId = sourceId,
                        sourceName = sourceName,
                        sourceType = sourceType,
                        classifier = classifier,
                        indexEntities = indexEntities,
                        titleCandidatesByAnime = titleCandidatesByAnime,
                        totalFiles = totalFiles,
                        newEpisodes = newEpisodes,
                        depth = depth + 1,
                        rootPath = rootPath,
                        isLocalSource = isLocalSource,
                        filenameOnly = filenameOnly,
                        remoteRootContext = remoteRootContext,
                        scanSessionId = scanSessionId,
                        diagnostics = diagnostics,
                    )
                    if (childTraversalError != null) {
                        return childTraversalError
                    }
                } else {
                    val fileName = file.name
                    if (MediaFileConventions.isVideoName(fileName, videoExtensions)) {
                        totalFiles(1)
                        diagnostics.videoFilesSeen += 1
                        diagnostics.lastVideoTail = pathTailForLog(file.path)
                        val videoOrdinal = diagnostics.videoFilesSeen
                        val classificationPath = classificationPathWithRemoteRoot(file.path, remoteRootContext)
                        if (shouldLogScanCheckpoint(videoOrdinal)) {
                            MiruLog.i(
                                tag = TAG,
                                message = "Scan video processing started",
                                attributes = buildScanEntryAttributes(
                                    scanSessionId = scanSessionId,
                                    sourceId = sourceId,
                                    sourceName = sourceName,
                                    sourceType = sourceType,
                                    file = file,
                                    depth = depth,
                                    diagnostics = diagnostics,
                                ) + mapOf(
                                    "scan_phase" to "video_start",
                                    "video_ordinal" to videoOrdinal.toString(),
                                    "classification_path_tail" to pathTailForLog(classificationPath),
                                    "classification_path_hash" to hashForLog(classificationPath),
                                )
                            )
                        }
                        val classificationStartedAtMs = System.currentTimeMillis()
                        val match = classifier.classifyVideo(
                            path = classificationPath,
                            fileName = fileName,
                            rootContext = remoteRootContext,
                        )
                        val classificationDurationMs = System.currentTimeMillis() - classificationStartedAtMs
                        if (classificationDurationMs >= SLOW_VIDEO_CLASSIFICATION_MS) {
                            MiruLog.w(
                                tag = TAG,
                                message = "Scan video classification slow",
                                attributes = buildScanEntryAttributes(
                                    scanSessionId = scanSessionId,
                                    sourceId = sourceId,
                                    sourceName = sourceName,
                                    sourceType = sourceType,
                                    file = file,
                                    depth = depth,
                                    diagnostics = diagnostics,
                                ) + mapOf(
                                    "scan_phase" to "video_classification_slow",
                                    "video_ordinal" to videoOrdinal.toString(),
                                    "duration_ms" to classificationDurationMs.toString(),
                                    "anime_name" to normalizeForLog(match.animeName, MAX_LOG_TEXT_LENGTH),
                                    "episode_number" to match.episodeNumber?.toString().orEmpty(),
                                )
                            )
                        }
                        MiruLog.i(
                            tag = TAG,
                            message = "Scan video classified",
                            attributes = buildVideoClassificationAttributes(
                                scanSessionId = scanSessionId,
                                sourceId = sourceId,
                                sourceName = sourceName,
                                sourceType = sourceType,
                                file = file,
                                classification = match,
                                filenameOnly = filenameOnly,
                                videoOrdinal = videoOrdinal,
                                entryOrdinal = diagnostics.entriesSeen,
                                depth = depth,
                                classificationDurationMs = classificationDurationMs,
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

                        diagnostics.pendingProgressPath = match.animeName
                        diagnostics.pendingProgressFiles += 1
                        if (match.episodeNumber != null) {
                            diagnostics.pendingProgressNewEpisodes += 1
                        }
                        if (diagnostics.pendingProgressFiles >= SCAN_PROGRESS_INTERVAL_VIDEO_FILES) {
                            flushPendingProgress(diagnostics)
                        }
                    } else if (!filenameOnly) {
                        if (MediaFileConventions.hasExtension(fileName, "nfo")) {
                            diagnostics.nfoFilesSeen += 1
                            val nfoOrdinal = diagnostics.nfoFilesSeen
                            val classificationPath = classificationPathWithRemoteRoot(file.path, remoteRootContext)
                            val nfoAnimeName = classifier.classifyNfo(classificationPath, remoteRootContext).animeName
                            val nfoStartedAtMs = System.currentTimeMillis()
                            MiruLog.d(
                                tag = TAG,
                                message = "Scan NFO parsing started",
                                attributes = buildScanEntryAttributes(
                                    scanSessionId = scanSessionId,
                                    sourceId = sourceId,
                                    sourceName = sourceName,
                                    sourceType = sourceType,
                                    file = file,
                                    depth = depth,
                                    diagnostics = diagnostics,
                                ) + mapOf(
                                    "scan_phase" to "nfo_start",
                                    "nfo_ordinal" to nfoOrdinal.toString(),
                                    "anime_name" to normalizeForLog(nfoAnimeName, MAX_LOG_TEXT_LENGTH),
                                )
                            )
                            parseAndCacheRemoteNfo(
                                ms,
                                file.path,
                                nfoAnimeName,
                                scanSessionId,
                            )
                            val nfoDurationMs = System.currentTimeMillis() - nfoStartedAtMs
                            val nfoAttributes = buildScanEntryAttributes(
                                scanSessionId = scanSessionId,
                                sourceId = sourceId,
                                sourceName = sourceName,
                                sourceType = sourceType,
                                file = file,
                                depth = depth,
                                diagnostics = diagnostics,
                            ) + mapOf(
                                "scan_phase" to "nfo_complete",
                                "nfo_ordinal" to nfoOrdinal.toString(),
                                "duration_ms" to nfoDurationMs.toString(),
                                "anime_name" to normalizeForLog(nfoAnimeName, MAX_LOG_TEXT_LENGTH),
                            )
                            if (nfoDurationMs >= SLOW_NFO_PARSE_MS) {
                                MiruLog.w(
                                    tag = TAG,
                                    message = "Scan NFO parsing slow",
                                    attributes = nfoAttributes,
                                )
                            } else {
                                MiruLog.d(
                                    tag = TAG,
                                    message = "Scan NFO parsed",
                                    attributes = nfoAttributes,
                                )
                            }
                        }
                    }
                }
            }
            return null
        } catch (e: CancellationException) {
            MiruLog.i(
                tag = TAG,
                message = "Scan traversal cancelled",
                attributes = buildTraversalAttributes(
                    scanSessionId = scanSessionId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    sourceType = sourceType,
                    path = path,
                    depth = depth,
                    diagnostics = diagnostics,
                ) + mapOf("scan_phase" to "traversal_cancelled")
            )
            throw e
        } catch (e: Exception) {
            Log.w("ScanCoordinator", "Error traversing path: $path", e)
            MiruLog.e(
                tag = TAG,
                message = "Scan traversal failed",
                throwable = e,
                attributes = buildTraversalAttributes(
                    scanSessionId = scanSessionId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    sourceType = sourceType,
                    path = path,
                    depth = depth,
                    diagnostics = diagnostics,
                ) + mapOf("scan_phase" to "traversal_failed")
            )
            return if (depth == 0) {
                AppError.NetworkError.ServerUnreachable(path.ifBlank { sourceName })
            } else {
                null
            }
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
            MediaSourceType.WEBDAV -> MediaPathConventions.joinRemoteUrl(sourceRoot, path)
            MediaSourceType.SMB -> path
        }

    private fun nameOfPath(path: String): String =
        when {
            path.startsWith("content://") -> {
                val tail = path.substringAfterLast(':', path).substringAfterLast('/')
                MediaPathConventions.decodePath(tail)
            }
            else -> MediaPathConventions.fileName(path)
        }

    private fun fileNameOf(path: String): String = nameOfPath(path).ifEmpty { MediaPathConventions.fileName(path) }

    private fun MediaSourceInfo.displayNameOrPath(path: String): String =
        connectionInfo["displayName"] ?: nameOfPath(path).ifEmpty { path }

    private fun remoteRootContextName(rootPath: String): String? =
        MediaPathConventions.decodePath(MediaPathConventions.fileName(rootPath.substringBefore('?')))
            .trim()
            .takeIf { it.isNotBlank() && !it.isRemoteRootContainerName() }

    private fun classificationPathWithRemoteRoot(path: String, remoteRootContext: String?): String {
        val context = remoteRootContext?.trim('/', '\\')?.takeIf { it.isNotBlank() } ?: return path
        val normalizedPath = path.replace('\\', '/')
        val pathSegments = normalizedPath.trim('/').split('/').filter { it.isNotBlank() }
        if (pathSegments.firstOrNull() == context) return path

        val child = normalizedPath.trimStart('/')
        return if (child.isBlank()) context else "$context/$child"
    }

    private fun String.isRemoteRootContainerName(): Boolean =
        lowercase()
            .replace(Regex("""[._\-\[\]【】()（）]+"""), " ")
            .replace(whitespaceRegex, " ")
            .trim() in remoteRootContainerNames

    /**
     * Download and parse a single NFO file, cache metadata using showDirName as the cache key.
     * This ensures the cache key matches the animeName stored in the index.
     */
    private suspend fun parseAndCacheRemoteNfo(
        ms: MediaSource,
        nfoPath: String,
        showDirName: String,
        scanSessionId: String,
    ) {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ScanCoordinator", "Error parsing NFO: $nfoPath", e)
            MiruLog.e(
                tag = TAG,
                message = "Scan NFO parsing failed",
                throwable = e,
                attributes = mapOf(
                    "scan_phase" to "nfo_failed",
                    "scan_session_id" to scanSessionId,
                    "path_tail" to pathTailForLog(nfoPath),
                    "path_hash" to hashForLog(nfoPath),
                    "anime_name" to normalizeForLog(showDirName, MAX_LOG_TEXT_LENGTH),
                )
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
        private const val SCAN_PROGRESS_INTERVAL_VIDEO_FILES = 5
        private const val SCAN_INITIAL_VIDEO_CHECKPOINTS = 5
        private const val SCAN_VIDEO_CHECKPOINT_INTERVAL = 25
        private const val SLOW_DIRECTORY_LISTING_MS = 5_000L
        private const val SLOW_VIDEO_CLASSIFICATION_MS = 2_000L
        private const val SLOW_NFO_PARSE_MS = 2_000L
        private val SCAN_DEBUG_FOCUS_VIDEO_RANGE = 275..310
        private val whitespaceRegex = Regex("\\s+")
        private val seasonOnlyRegex = Regex("(?i)^(s\\d{1,2}|season\\s*\\d{1,2}|第\\s*\\d+\\s*[季期])$")
        private val videoExtensions = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v")
        private val remoteRootContainerNames = setOf(
            "dav",
            "webdav",
            "root",
            "115open",
            "anime",
            "animation",
            "download",
            "downloads",
            "library",
            "media",
            "movie",
            "movies",
            "video",
            "videos",
            "影视",
            "影音",
            "电视剧",
            "劇集",
            "动漫",
            "動畫",
            "下载",
            "下載",
        )
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
