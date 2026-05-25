package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import android.os.Build
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.model.CloudDriveDirectoryItem
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.resumePosition
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.cloudDriveDirectoryDisplayPath
import com.miruplay.tv.model.cloudDriveDirectoryItems
import com.miruplay.tv.model.cloudDriveDirectoryParentPath
import com.miruplay.tv.model.normalizeCloudDriveDirectoryPath
import com.miruplay.tv.model.scopedCloudDriveDirectoryPath
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlService @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val logUploadRepository: LogUploadRepository,
    private val securePreferences: AppCredentialStore,
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val cloudDriveScheduler: CloudDriveRssScheduler,
    private val scanCoordinator: ScanCoordinator,
    private val mediaSourceFactory: MediaSourceFactory,
    private val playbackController: PlaybackController,
    private val navigator: WebControlNavigator
) {
    private val startedAt = System.currentTimeMillis()

    suspend fun getServerInfo(port: Int): ServerInfoDto = withContext(Dispatchers.IO) {
        ServerInfoDto(
            appName = "MiruPlay",
            deviceName = Build.MODEL ?: "Android TV",
            port = port,
            localIps = findLocalIps(),
            startedAt = startedAt
        )
    }

    suspend fun listSources(): List<MediaSourceInfo> {
        return (mediaRepository.getSources() as? Result.Success)
            ?.data
            ?.map { it.safeForApi() }
            ?: emptyList()
    }

    suspend fun browseLocalDirectories(path: String): LocalDirectoryDto = withContext(Dispatchers.IO) {
        val listing = LocalDirectoryBrowser.browse(path)
        LocalDirectoryDto(
            path = listing.path,
            displayPath = listing.displayPath,
            parentPath = listing.parentPath,
            entries = listing.entries.map {
                LocalDirectoryEntryDto(
                    name = it.name,
                    path = it.path,
                    canRead = it.canRead
                )
            }
        )
    }

    suspend fun addSource(request: SourceRequest): MediaSourceInfo {
        val source = request.toMediaSourceInfo()
        val id = requireSuccess(mediaRepository.addSource(source), "添加媒体源失败")
        val connected = testSource(source).connected
        val savedSource = source.copy(id = id, isConnected = connected)
        mediaRepository.updateSource(savedSource)
        return savedSource.safeForApi()
    }

    suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo {
        val existing = requireSuccess(mediaRepository.getSourceById(sourceId), "媒体源不存在")
        val currentPassword = existing.connectionInfo["password"]
        val source = request.toMediaSourceInfo(sourceId, currentPassword)
            .copy(isConnected = existing.isConnected, lastScanned = existing.lastScanned)
        requireSuccess(mediaRepository.updateSource(source), "更新媒体源失败")
        return source.safeForApi()
    }

    suspend fun removeSource(sourceId: Long) {
        requireSuccess(mediaRepository.removeSource(sourceId), "删除媒体源失败")
    }

    suspend fun testSource(request: SourceTestRequest): SourceTestResponse {
        return testSource(request.toMediaSourceInfo())
    }

    suspend fun scanSource(sourceId: Long): SourceScanResponse {
        val result = requireSuccess(scanCoordinator.scanSource(sourceId), "扫描媒体源失败")
        return SourceScanResponse(
            sourceId = sourceId,
            animeName = result.animeName,
            episodesFound = result.episodesFound,
            newEpisodes = result.newEpisodes,
            updatedEpisodes = result.updatedEpisodes
        )
    }

    suspend fun scanAllSources(): List<SourceScanResponse> {
        val sources = (mediaRepository.getSources() as? Result.Success)?.data ?: emptyList()
        return sources.mapNotNull { source ->
            when (val result = scanCoordinator.scanSource(source.id)) {
                is Result.Success -> SourceScanResponse(
                    sourceId = source.id,
                    animeName = result.data.animeName,
                    episodesFound = result.data.episodesFound,
                    newEpisodes = result.data.newEpisodes,
                    updatedEpisodes = result.data.updatedEpisodes
                )
                is Result.Error -> null
            }
        }
    }

    suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto {
        val config = requireSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败")
        return CloudDriveAutomationDto(
            config = config,
            subscriptions = cloudDriveRepository.observeSubscriptions().first(),
            tokenConfigured = !securePreferences.cloudDriveToken.isNullOrBlank()
        )
    }

    suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto {
        val current = requireSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败")
        val config = CloudDriveAutomationConfig(
            endpointUrl = request.endpointUrl.trim(),
            username = request.username.trim(),
            webDavSourceId = request.webDavSourceId?.takeIf { it > 0L },
            inboxPath = request.inboxPath.trim(),
            libraryPath = request.libraryPath.trim(),
            libraryMode = request.libraryMode,
            intervalMinutes = request.intervalMinutes.coerceAtLeast(MIN_CLOUD_DRIVE_INTERVAL_MINUTES),
            enabled = request.enabled,
            lastRunAt = current.lastRunAt,
            rssProxyEnabled = request.rssProxyEnabled,
            rssProxyHost = request.rssProxyHost.trim(),
            rssProxyPort = request.rssProxyPort.coerceAtLeast(1).coerceAtMost(65535)
        )
        requireSuccess(cloudDriveRepository.saveConfig(config), "保存 CloudDrive 设置失败")
        cloudDriveScheduler.syncPeriodicWork(config)
        return getCloudDriveAutomation()
    }

    suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto {
        if (request.endpointUrl.isBlank() || request.username.isBlank() || request.password.isBlank()) {
            throw IllegalArgumentException("请填写 CloudDrive2 地址、用户名和密码")
        }
        requireSuccess(
            cloudDriveEngine.login(request.endpointUrl.trim(), request.username.trim(), request.password),
            "CloudDrive2 登录失败"
        )
        return getCloudDriveAutomation()
    }

    suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse {
        if (request.endpointUrl.isBlank() || request.token.isBlank()) {
            throw IllegalArgumentException("请填写 CloudDrive2 地址和 API Token")
        }
        val tokenInfo = requireSuccess(
            cloudDriveEngine.saveApiToken(request.endpointUrl.trim(), request.token.trim()),
            "CloudDrive2 API Token 验证失败"
        )
        return CloudDriveTokenResponse(
            rootDir = tokenInfo.rootDir,
            friendlyName = tokenInfo.friendlyName,
            allowList = tokenInfo.allowList,
            allowCreateFolder = tokenInfo.allowCreateFolder,
            allowCreateFile = tokenInfo.allowCreateFile,
            allowWrite = tokenInfo.allowWrite,
            allowMove = tokenInfo.allowMove,
            allowAddOfflineDownload = tokenInfo.allowAddOfflineDownload
        )
    }

    suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        val summary = requireSuccess(cloudDriveEngine.runOnce(), "CloudDrive/RSS 执行失败")
        return CloudDriveRunResponse(
            submitted = summary.submitted,
            skipped = summary.skipped,
            failed = summary.failed,
            organized = summary.organized,
            indexed = summary.indexed,
            scraped = summary.scraped,
            noMatch = summary.noMatch,
        )
    }

    suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo {
        if (request.url.isBlank()) {
            throw IllegalArgumentException("请填写 RSS 地址")
        }
        val subscription = RssSubscriptionInfo(
            id = request.id,
            name = request.name.trim().ifBlank { request.url.trim() },
            url = request.url.trim(),
            filterRegex = request.filterRegex?.trim()?.takeIf { it.isNotBlank() },
            enabled = request.enabled
        )
        val id = requireSuccess(cloudDriveRepository.saveSubscription(subscription), "保存 RSS 订阅失败")
        return subscription.copy(id = if (subscription.id > 0L) subscription.id else id)
    }

    suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo =
        saveRssSubscription(request.copy(id = id))

    suspend fun deleteRssSubscription(id: Long) {
        requireSuccess(cloudDriveRepository.deleteSubscription(id), "删除 RSS 订阅失败")
    }

    suspend fun getLogUpload(): LogUploadDto {
        val tokenConfigured = logUploadRepository.isTokenConfigured()
        return LogUploadDto(
            config = OtlpLogUploadConfigDto.from(logUploadRepository.getConfig()),
            status = LogUploadStatusDto.from(logUploadRepository.status.first(), tokenConfigured),
            tokenConfigured = tokenConfigured
        )
    }

    suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto {
        if (request.enabled && request.endpoint.isBlank()) {
            throw IllegalArgumentException("请填写 OpenObserve API 地址")
        }
        logUploadRepository.saveConfig(
            enabled = request.enabled,
            endpoint = request.endpoint.trim(),
            streamName = request.streamName.trim().ifBlank { "miruplay" }
        )
        return getLogUpload()
    }

    suspend fun saveLogUploadToken(request: LogUploadTokenRequest): LogUploadDto {
        if (request.token.isBlank()) {
            throw IllegalArgumentException("请填写 OpenObserve Token")
        }
        logUploadRepository.saveToken(request.token.trim())
        return getLogUpload()
    }

    suspend fun clearLogUploadToken(): LogUploadDto {
        logUploadRepository.clearToken()
        return getLogUpload()
    }

    suspend fun uploadPendingLogs(): LogUploadDto {
        logUploadRepository.uploadPendingLogs()
        return getLogUpload()
    }

    fun getMetadataSettings(): MetadataSettingsDto =
        MetadataSettingsDto(
            bangumiTokenConfigured = !securePreferences.bangumiAccessToken.isNullOrBlank()
        )

    fun saveBangumiToken(request: BangumiTokenRequest): MetadataSettingsDto {
        val token = request.token.trim()
        if (token.isBlank()) {
            throw IllegalArgumentException("请填写 Bangumi Token")
        }
        securePreferences.bangumiAccessToken = token
        return getMetadataSettings()
    }

    fun clearBangumiToken(): MetadataSettingsDto {
        securePreferences.clearBangumiToken()
        return getMetadataSettings()
    }

    suspend fun getLibrary(): LibraryDto {
        val sources = (mediaRepository.getSources() as? Result.Success)?.data ?: emptyList()
        val anime = loadAllAnime(sources)
        return LibraryDto(
            continueWatching = loadContinueWatching(),
            recentlyAdded = anime.takeLast(24),
            allAnime = anime
        )
    }

    suspend fun searchLibrary(query: String): LibraryDto {
        val normalized = query.trim()
        val library = getLibrary()
        if (normalized.isBlank()) return library

        val filtered = library.allAnime.filter { item ->
            item.id.contains(normalized, ignoreCase = true) ||
                item.title.contains(normalized, ignoreCase = true) ||
                (item.titleCn?.contains(normalized, ignoreCase = true) == true)
        }
        return library.copy(recentlyAdded = filtered.take(24), allAnime = filtered)
    }

    suspend fun getAnimeDetail(animeId: String): AnimeDetailDto {
        val anime = requireSuccess(metadataRepository.getCachedMetadata(animeId), "番剧不存在")
            ?: throw IllegalArgumentException("番剧不存在")
        val episodes = requireSuccess(metadataRepository.getCachedEpisodes(animeId), "读取剧集失败")
            .map { episode ->
                val progress = progressRepository.getProgress(episode.id).getOrNull()
                EpisodeWithProgressDto(
                    episode = episode,
                    progressMs = progress?.positionMs ?: 0L,
                    lastWatched = progress?.lastWatched ?: 0L,
                    playCount = progress?.playCount ?: 0
                )
            }
        return AnimeDetailDto(anime = anime, episodes = episodes)
    }

    suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto {
        val episode = findEpisodeById(request.episodeId)
            ?: throw IllegalArgumentException("剧集不存在")
        val progress = progressRepository.getProgress(episode.id).getOrNull()
        val startPosition = request.startPositionMs
            ?: episode.resumePosition(progress)
        val source = PlaybackSource(
            uri = episode.filePath,
            mediaSourceId = episode.animeId,
            startPosition = startPosition,
            subtitleTracks = emptyList(),
            episodeId = episode.id
        )
        navigator.openPlayer(
            WebPlaybackSource(
                uri = source.uri,
                mediaSourceId = source.mediaSourceId,
                startPositionMs = source.startPosition,
                episodeId = source.episodeId
            )
        )
        return playbackStatus()
    }

    suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto {
        when (request.command.lowercase()) {
            "pause" -> playbackController.pause()
            "resume", "play" -> playbackController.resume()
            "toggle" -> {
                if (playbackController.isPlaying()) playbackController.pause() else playbackController.resume()
            }
            "stop" -> playbackController.stop()
            "seek" -> playbackController.seekTo((request.positionMs ?: 0L).coerceAtLeast(0L))
            "seek_relative" -> {
                val next = playbackController.getCurrentPosition() + (request.deltaMs ?: 0L)
                playbackController.seekTo(next.coerceAtLeast(0L))
            }
            "skip_forward" -> {
                val next = playbackController.getCurrentPosition() + (request.deltaMs ?: 30_000L)
                playbackController.seekTo(next.coerceAtLeast(0L))
            }
            "skip_backward" -> {
                val next = playbackController.getCurrentPosition() - (request.deltaMs ?: 10_000L)
                playbackController.seekTo(next.coerceAtLeast(0L))
            }
            "speed" -> playbackController.setPlaybackSpeed(request.speed ?: 1.0f)
            else -> throw IllegalArgumentException("未知播放命令: ${request.command}")
        }
        return playbackStatus()
    }

    suspend fun playbackStatus(): PlaybackStatusDto {
        val state = playbackController.state.value
        val currentPosition = runCatching { playbackController.getCurrentPosition() }.getOrDefault(0L)
            .coerceAtLeast(0L)
        val duration = runCatching { playbackController.getDuration() }.getOrDefault(0L)
            .coerceAtLeast(0L)
        val source = when (state) {
            is PlaybackState.Loading -> state.source
            is PlaybackState.Playing -> state.source
            is PlaybackState.Paused -> state.source
            is PlaybackState.Buffering -> state.source
            is PlaybackState.Ended -> state.source
            is PlaybackState.Error -> state.source
            PlaybackState.Idle -> null
        }
        val position = when (state) {
            is PlaybackState.Playing -> state.position
            is PlaybackState.Paused -> state.position
            is PlaybackState.Buffering -> state.position
            else -> currentPosition
        }
        return PlaybackStatusDto(
            state = state::class.simpleName ?: "Idle",
            uri = source?.uri,
            mediaSourceId = source?.mediaSourceId,
            positionMs = position,
            durationMs = duration,
            isPlaying = state is PlaybackState.Playing,
            error = (state as? PlaybackState.Error)?.error
        )
    }

    private suspend fun testSource(source: MediaSourceInfo): SourceTestResponse {
        val mediaSource = when (val result = mediaSourceFactory.create(source)) {
            is Result.Success -> result.data
            is Result.Error -> return SourceTestResponse(false, result.error.toString())
        }
        return when (val result = mediaSource.testConnection()) {
            is Result.Success -> SourceTestResponse(result.data, if (result.data) "连接正常" else "无法连接")
            is Result.Error -> SourceTestResponse(false, result.error.toString())
        }
    }

    private suspend fun loadAllAnime(sources: List<MediaSourceInfo>): List<Anime> {
        val all = mutableListOf<Anime>()
        for (source in sources) {
            val names = indexRepository.getAnimeInIndex(source.id).getOrNull() ?: emptyList()
            for (name in names) {
                val cached = metadataRepository.getCachedMetadata(name).getOrNull()
                if (cached != null) {
                    all += cached
                }
            }
        }
        return all.distinctBy { it.id }.sortedBy { it.title.ifBlank { it.id } }
    }

    private suspend fun loadContinueWatching(): List<ContinueWatchingDto> {
        return (progressRepository.getContinueWatching(30).getOrNull() ?: emptyList()).mapNotNull { record ->
            val episode = findEpisodeById(record.episodeId)
            val anime = episode?.let { metadataRepository.getCachedMetadata(it.animeId).getOrNull() }
            ContinueWatchingDto(
                progressEpisodeId = record.episodeId,
                positionMs = record.positionMs,
                lastWatched = record.lastWatched,
                playCount = record.playCount,
                episode = episode,
                anime = anime
            )
        }
    }

    suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto = withContext(Dispatchers.IO) {
        val resolvedEndpoint = endpointUrl.trim().takeIf { it.isNotBlank() }
            ?: requireSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败").endpointUrl
        if (resolvedEndpoint.isBlank()) {
            throw IllegalArgumentException("请先填写 CloudDrive2 地址")
        }

        val token = securePreferences.cloudDriveToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("请先登录 CloudDrive2 或保存 API Token")
        val endpoint = CloudDriveEndpoint(resolvedEndpoint, token)
        val tokenInfo = cloudDriveClient.getApiTokenInfo(resolvedEndpoint, token).getOrNull()
        val rootPath = normalizeCloudDriveDirectoryPath(tokenInfo?.rootDir ?: "")
        val currentPath = scopedCloudDriveDirectoryPath(path.ifBlank { rootPath }, rootPath)

        val listing = requireSuccess(
            cloudDriveClient.listFolder(endpoint, currentPath, forceRefresh = false),
            "读取 CloudDrive 目录失败"
        )
        val entries = cloudDriveDirectoryItems(
            listing.filter { it.isDirectory }
                .map {
                    CloudDriveDirectoryItem(
                        name = it.name,
                        path = it.path
                    )
                }
        )
            .map {
                CloudDriveDirectoryEntryDto(
                    name = it.name,
                    path = it.path,
                    canRead = true
                )
            }

        CloudDriveDirectoryDto(
            path = currentPath,
            displayPath = cloudDriveDirectoryDisplayPath(currentPath),
            parentPath = cloudDriveDirectoryParentPath(currentPath, rootPath),
            entries = entries
        )
    }

    private suspend fun findEpisodeById(episodeId: String): Episode? {
        findEpisodeFromIndex(episodeId)?.let { return it }

        val sources = mediaRepository.getSources().getOrNull() ?: emptyList()
        val candidateAnimeIds = linkedSetOf<String>()
        for (source in sources) {
            candidateAnimeIds += indexRepository.getAnimeInIndex(source.id).getOrNull() ?: emptyList()
        }
        candidateAnimeIds += loadAllAnime(sources).map { it.id }

        for (animeId in candidateAnimeIds) {
            val episode = metadataRepository.getCachedEpisodes(animeId)
                .getOrNull()
                ?.firstOrNull { it.id == episodeId || it.filePath == episodeId }
            if (episode != null) return episode
        }
        return null
    }

    private suspend fun findEpisodeFromIndex(episodeId: String): Episode? {
        val sourceParts = episodeId.split(":", limit = 2)
        val sourceId = sourceParts.getOrNull(0)?.toLongOrNull() ?: return null
        val path = sourceParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val source = mediaRepository.getSourceById(sourceId).getOrNull()
        val fileName = path.substringAfterLast("/")
        val matches = indexRepository.queryIndex(sourceId, fileName.substringBeforeLast("."))
            .getOrNull()
            .orEmpty()
        val entry = matches.firstOrNull { it.path == path || episodeId.endsWith(it.path) }
            ?: matches.firstOrNull { it.path.substringAfterLast("/") == fileName }
            ?: return null

        return Episode(
            id = episodeId,
            animeId = entry.animeName ?: path.substringBeforeLast("/").substringAfterLast("/"),
            seasonNumber = entry.seasonNumber ?: 1,
            episodeNumber = entry.episodeNumber ?: 1,
            title = "",
            filePath = source?.playableUriFor(entry.path) ?: entry.path,
            fileName = fileName
        )
    }

    private fun MediaSourceInfo.playableUriFor(path: String): String {
        return when (type) {
            MediaSourceType.LOCAL -> path
            MediaSourceType.WEBDAV,
            MediaSourceType.SMB -> {
                if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("smb://")) {
                    path
                } else {
                    val baseUrl = connectionInfo["url"].orEmpty().trimEnd('/')
                    val relativePath = path.trimStart('/')
                    "$baseUrl/${encodePathSegments(relativePath)}"
                }
            }
        }
    }

    private fun encodePathSegments(path: String): String {
        return path.split('/')
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
    }

    private fun SourceRequest.toMediaSourceInfo(
        sourceId: Long = id,
        fallbackPassword: String? = null
    ): MediaSourceInfo {
        val sourceType = parseSourceType(type)
        val trimmedLocation = location.trim()
        return MediaSourceInfo(
            id = sourceId,
            name = name.trim().ifBlank { sourceType.defaultName() },
            type = sourceType,
            connectionInfo = buildMap {
                put("url", trimmedLocation)
                if (sourceType == MediaSourceType.LOCAL) {
                    put("path", trimmedLocation)
                    requestDisplayName()?.let { put("displayName", it) }
                }
                username?.trim()?.takeIf { it.isNotBlank() }?.let { put("username", it) }
                val newPassword = password?.takeIf { it.isNotBlank() }
                when {
                    newPassword != null -> put("password", newPassword)
                    fallbackPassword != null -> put("password", fallbackPassword)
                }
            }
        )
    }

    private fun SourceTestRequest.toMediaSourceInfo(): MediaSourceInfo {
        val sourceType = parseSourceType(type)
        val trimmedLocation = location.trim()
        return MediaSourceInfo(
            name = "test",
            type = sourceType,
            connectionInfo = buildMap {
                put("url", trimmedLocation)
                if (sourceType == MediaSourceType.LOCAL) {
                    put("path", trimmedLocation)
                    requestDisplayName()?.let { put("displayName", it) }
                }
                username?.trim()?.takeIf { it.isNotBlank() }?.let { put("username", it) }
                password?.takeIf { it.isNotBlank() }?.let { put("password", it) }
            }
        )
    }

    private fun parseSourceType(type: String): MediaSourceType {
        return runCatching { MediaSourceType.valueOf(type.uppercase()) }
            .getOrElse { throw IllegalArgumentException("不支持的媒体源类型: $type") }
    }

    private fun MediaSourceType.defaultName(): String = when (this) {
        MediaSourceType.LOCAL -> "本地媒体库"
        MediaSourceType.WEBDAV -> "WebDAV 媒体库"
        MediaSourceType.SMB -> "SMB 共享"
    }

    private fun SourceRequest.requestDisplayName(): String? =
        displayName?.trim()?.takeIf { it.isNotBlank() }

    private fun SourceTestRequest.requestDisplayName(): String? =
        displayName?.trim()?.takeIf { it.isNotBlank() }

    private fun findLocalIps(): List<String> {
        return NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun <T> requireSuccess(result: Result<T>, message: String): T {
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw IllegalStateException("$message: ${result.error}")
        }
    }

}
