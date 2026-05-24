package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.connectionPasswordOrNull
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.model.sortedForPlaybackQueue
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.repository.mediaIndexPosterAnimeId
import com.miruplay.tv.repository.toMediaIndexPosterGroups
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.loadCloudDriveDirectory
import com.miruplay.tv.sync.rss.prepareCloudDriveDirectoryBrowser
import com.miruplay.tv.webcontrol.AnimeDetailDto
import com.miruplay.tv.webcontrol.CloudDriveAutomationDto
import com.miruplay.tv.webcontrol.CloudDriveConfigRequest
import com.miruplay.tv.webcontrol.CloudDriveDirectoryDto
import com.miruplay.tv.webcontrol.CloudDriveDirectoryEntryDto
import com.miruplay.tv.webcontrol.CloudDriveLoginRequest
import com.miruplay.tv.webcontrol.CloudDriveRunResponse
import com.miruplay.tv.webcontrol.CloudDriveTokenRequest
import com.miruplay.tv.webcontrol.CloudDriveTokenResponse
import com.miruplay.tv.webcontrol.ContinueWatchingDto
import com.miruplay.tv.webcontrol.EpisodeWithProgressDto
import com.miruplay.tv.webcontrol.LibraryDto
import com.miruplay.tv.webcontrol.LocalDirectoryDto
import com.miruplay.tv.webcontrol.LocalDirectoryEntryDto
import com.miruplay.tv.webcontrol.PlayEpisodeRequest
import com.miruplay.tv.webcontrol.PlaybackCommandRequest
import com.miruplay.tv.webcontrol.PlaybackStatusDto
import com.miruplay.tv.webcontrol.RssSubscriptionRequest
import com.miruplay.tv.webcontrol.ServerInfoDto
import com.miruplay.tv.webcontrol.SourceRequest
import com.miruplay.tv.webcontrol.SourceScanResponse
import com.miruplay.tv.webcontrol.SourceTestRequest
import com.miruplay.tv.webcontrol.SourceTestResponse
import com.miruplay.tv.webcontrol.WebControlEndpointService
import com.miruplay.tv.webcontrol.WebControlPlaybackCommandKind
import com.miruplay.tv.webcontrol.absoluteSeekPositionMs
import com.miruplay.tv.webcontrol.filteredByQuery
import com.miruplay.tv.webcontrol.playbackCommandKind
import com.miruplay.tv.webcontrol.relativeSeekDeltaMs
import com.miruplay.tv.webcontrol.safeForApi
import com.miruplay.tv.webcontrol.skipBackwardDeltaMs
import com.miruplay.tv.webcontrol.skipForwardDeltaMs
import com.miruplay.tv.webcontrol.toMediaSourceInfo
import com.miruplay.tv.webcontrol.toWebControlLibrary
import com.miruplay.tv.webcontrol.webControlDefaultSourceName
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

internal class DesktopWebControlService(
    private val repositories: DesktopRepositories,
    private val cloudDriveClient: CloudDriveClient = GrpcCloudDriveClient(),
    private val cloudRssEngine: DesktopCloudDriveRssAutomationEngine = DesktopCloudDriveRssAutomationEngine(
        repository = repositories.cloudDriveAutomation,
        credentials = repositories.credentials,
        cloudDriveClient = cloudDriveClient,
    ),
    private val playbackStatusProvider: suspend () -> PlaybackStatusDto = { idlePlaybackStatus() },
    private val playEpisodeHandler: suspend (PlayEpisodeRequest, Episode) -> PlaybackStatusDto = { _, _ ->
        throw UnsupportedOperationException("Windows WebUI 暂未接入远程播放启动")
    },
    private val playbackCommandHandler: suspend (PlaybackCommandRequest) -> PlaybackStatusDto = {
        throw UnsupportedOperationException("Windows WebUI 暂未接入远程播放控制")
    },
    private val clock: () -> Long = System::currentTimeMillis,
    private val deviceName: String = "Windows",
) : WebControlEndpointService {
    private val startedAt = clock()

    override suspend fun getServerInfo(port: Int): ServerInfoDto =
        ServerInfoDto(
            appName = "MiruPlay",
            deviceName = deviceName,
            port = port,
            localIps = findLocalIps(),
            startedAt = startedAt,
        )

    override suspend fun listSources(): List<MediaSourceInfo> =
        repositories.mediaSources.getSources().getOrNull().orEmpty().map { it.safeForApi() }

    override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto {
        if (path.isBlank()) {
            return LocalDirectoryDto(
                path = "",
                displayPath = "设备存储",
                parentPath = null,
                entries = File.listRoots()
                    .filter { it.exists() && it.isDirectory && it.canRead() }
                    .map {
                        LocalDirectoryEntryDto(
                            name = it.absolutePath,
                            path = it.absolutePath,
                            canRead = it.canRead(),
                        )
                    },
            )
        }
        val listing = LocalDirectoryBrowser.browse(path)
        return LocalDirectoryDto(
            path = listing.path,
            displayPath = listing.displayPath,
            parentPath = listing.parentPath,
            entries = listing.entries.map {
                LocalDirectoryEntryDto(
                    name = it.name,
                    path = it.path,
                    canRead = it.canRead,
                )
            },
        )
    }

    override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto {
        val endpoint = endpointUrl.trim().ifBlank {
            repositories.cloudDriveAutomation.getConfig().getOrNull()?.endpointUrl.orEmpty()
        }
        if (endpoint.isBlank()) {
            throw IllegalArgumentException("请先填写 CloudDrive2 地址")
        }
        val token = repositories.credentials.cloudDriveToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("请先登录 CloudDrive2 或保存 API Token")
        val prepared = requireSuccess(
            prepareCloudDriveDirectoryBrowser(
                client = cloudDriveClient,
                target = CloudDriveDirectoryTarget.INBOX,
                endpointUrl = endpoint,
                token = token,
                initialPath = path,
            ),
            "读取 CloudDrive 目录失败",
        )
        val loaded = requireSuccess(
            loadCloudDriveDirectory(
                client = cloudDriveClient,
                state = prepared,
                requestedPath = prepared.path,
            ),
            "读取 CloudDrive 目录失败",
        )
        return CloudDriveDirectoryDto(
            path = loaded.path,
            displayPath = loaded.displayPath,
            parentPath = loaded.parentPath,
            entries = loaded.entries.map {
                CloudDriveDirectoryEntryDto(
                    name = it.name,
                    path = it.path,
                    canRead = true,
                )
            },
        )
    }

    override suspend fun addSource(request: SourceRequest): MediaSourceInfo {
        val source = request.toMediaSourceInfo()
        val sourceId = requireSuccess(repositories.mediaSources.addSource(source), "添加媒体源失败")
        val persisted = source.copy(id = sourceId, isConnected = source.type == MediaSourceType.LOCAL)
        repositories.mediaSources.updateSource(persisted)
        return persisted.safeForApi()
    }

    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo {
        val existing = requireSuccess(repositories.mediaSources.getSourceById(sourceId), "媒体源不存在")
        val source = request.toMediaSourceInfo(
            sourceId = sourceId,
            fallbackPassword = existing.connectionPasswordOrNull(),
            isConnected = existing.isConnected,
            lastScanned = existing.lastScanned,
        )
        requireSuccess(repositories.mediaSources.updateSource(source), "更新媒体源失败")
        return source.safeForApi()
    }

    override suspend fun removeSource(sourceId: Long) {
        requireSuccess(repositories.mediaSources.removeSource(sourceId), "删除媒体源失败")
    }

    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse {
        val source = request.toMediaSourceInfo()
        val mediaSource = desktopSourceFromInfo(source)
        return try {
            when (val tested = mediaSource.testConnection()) {
                is Result.Success -> SourceTestResponse(
                    connected = tested.data,
                    message = if (tested.data) "连接正常" else "无法连接",
                )
                is Result.Error -> SourceTestResponse(
                    connected = false,
                    message = tested.error.toUserMessage(),
                )
            }
        } finally {
            mediaSource.close()
        }
    }

    override suspend fun scanSource(sourceId: Long): SourceScanResponse {
        val source = requireSuccess(repositories.mediaSources.getSourceById(sourceId), "媒体源不存在")
        val result = requireSuccess(scanAndIndexDesktopSource(source, repositories.index), "扫描媒体源失败")
        return result.toSourceScanResponse(source)
    }

    override suspend fun scanAllSources(): List<SourceScanResponse> {
        val sources = repositories.mediaSources.getSources().getOrNull().orEmpty()
        return sources.mapNotNull { source ->
            when (val result = scanAndIndexDesktopSource(source, repositories.index)) {
                is Result.Success -> result.data.toSourceScanResponse(source)
                is Result.Error -> null
            }
        }
    }

    override suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto {
        val config = repositories.cloudDriveAutomation.getConfig().getOrNull() ?: CloudDriveAutomationConfig()
        return CloudDriveAutomationDto(
            config = config,
            subscriptions = repositories.cloudDriveAutomation.observeSubscriptions().first(),
            tokenConfigured = !repositories.credentials.cloudDriveToken.isNullOrBlank(),
        )
    }

    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto {
        val current = repositories.cloudDriveAutomation.getConfig().getOrNull() ?: CloudDriveAutomationConfig()
        val config = CloudDriveAutomationConfig(
            endpointUrl = request.endpointUrl.trim(),
            username = request.username.trim(),
            webDavSourceId = request.webDavSourceId?.takeIf { it > 0L },
            inboxPath = request.inboxPath.trim(),
            libraryPath = request.libraryPath.trim(),
            intervalMinutes = request.intervalMinutes.coerceAtLeast(5),
            enabled = request.enabled,
            lastRunAt = current.lastRunAt,
            rssProxyEnabled = request.rssProxyEnabled,
            rssProxyHost = request.rssProxyHost.trim(),
            rssProxyPort = request.rssProxyPort.coerceIn(1, 65535),
        )
        requireSuccess(repositories.cloudDriveAutomation.saveConfig(config), "保存 CloudDrive 设置失败")
        return getCloudDriveAutomation()
    }

    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto {
        if (request.endpointUrl.isBlank() || request.username.isBlank() || request.password.isBlank()) {
            throw IllegalArgumentException("请填写 CloudDrive2 地址、用户名和密码")
        }
        requireSuccess(
            cloudRssEngine.login(
                endpointUrl = request.endpointUrl.trim(),
                username = request.username.trim(),
                password = request.password,
            ),
            "CloudDrive2 登录失败",
        )
        return getCloudDriveAutomation()
    }

    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse {
        if (request.endpointUrl.isBlank() || request.token.isBlank()) {
            throw IllegalArgumentException("请填写 CloudDrive2 地址和 API Token")
        }
        val tokenInfo = requireSuccess(
            cloudRssEngine.saveApiToken(
                endpointUrl = request.endpointUrl.trim(),
                token = request.token.trim(),
            ),
            "CloudDrive2 API Token 验证失败",
        )
        return CloudDriveTokenResponse(
            rootDir = tokenInfo.rootDir,
            friendlyName = tokenInfo.friendlyName,
            allowList = tokenInfo.allowList,
            allowCreateFolder = tokenInfo.allowCreateFolder,
            allowCreateFile = tokenInfo.allowCreateFile,
            allowWrite = tokenInfo.allowWrite,
            allowMove = tokenInfo.allowMove,
            allowAddOfflineDownload = tokenInfo.allowAddOfflineDownload,
        )
    }

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        val summary = requireSuccess(cloudRssEngine.runOnce(), "CloudDrive/RSS 执行失败")
        rescanLinkedCloudDriveSource(summary.completeStatus())
        return CloudDriveRunResponse(
            submitted = summary.submitted,
            skipped = summary.skipped,
            failed = summary.failed,
            organized = summary.organized,
        )
    }

    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): com.miruplay.tv.model.RssSubscriptionInfo {
        if (request.url.isBlank()) {
            throw IllegalArgumentException("请填写 RSS 地址")
        }
        val subscription = com.miruplay.tv.model.RssSubscriptionInfo(
            id = request.id,
            name = request.name.trim().ifBlank { request.url.trim() },
            url = request.url.trim(),
            filterRegex = request.filterRegex?.trim()?.takeIf { it.isNotBlank() },
            enabled = request.enabled,
        )
        val id = requireSuccess(repositories.cloudDriveAutomation.saveSubscription(subscription), "保存 RSS 订阅失败")
        return subscription.copy(id = if (subscription.id > 0L) subscription.id else id)
    }

    override suspend fun updateRssSubscription(
        id: Long,
        request: RssSubscriptionRequest,
    ): com.miruplay.tv.model.RssSubscriptionInfo =
        saveRssSubscription(request.copy(id = id))

    override suspend fun deleteRssSubscription(id: Long) {
        requireSuccess(repositories.cloudDriveAutomation.deleteSubscription(id), "删除 RSS 订阅失败")
    }

    override suspend fun searchLibrary(query: String): LibraryDto {
        val library = loadLibrary()
        return library.filteredByQuery(query)
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetailDto {
        val group = indexedAnimeGroups().firstOrNull { it.animeId == animeId }
        val cached = repositories.metadata.getCachedMetadata(animeId).getOrNull()
        val anime = cached ?: group?.toAnime()
            ?: throw IllegalArgumentException("番剧不存在")
        val episodes = loadEpisodesForAnime(anime, group)
        return AnimeDetailDto(
            anime = anime,
            episodes = episodes.map { episode ->
                val progress = repositories.progress.getProgress(episode.id).getOrNull()
                EpisodeWithProgressDto(
                    episode = episode,
                    progressMs = progress?.positionMs ?: 0L,
                    lastWatched = progress?.lastWatched ?: 0L,
                    playCount = progress?.playCount ?: 0,
                )
            },
        )
    }

    override suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto {
        val episode = findEpisodeById(request.episodeId)
            ?: throw IllegalArgumentException("剧集不存在")
        return playEpisodeHandler(request, episode)
    }

    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto =
        playbackCommandHandler(request)

    override suspend fun playbackStatus(): PlaybackStatusDto =
        playbackStatusProvider()

    private suspend fun loadLibrary(): LibraryDto {
        val anime = indexedAnimeGroups()
            .map { group -> repositories.metadata.getCachedMetadata(group.animeId).getOrNull() ?: group.toAnime() }
        return anime.toWebControlLibrary(continueWatching = loadContinueWatching())
    }

    private suspend fun loadContinueWatching(): List<ContinueWatchingDto> =
        repositories.progress.getContinueWatching(30).getOrNull().orEmpty().map { record ->
            val episode = findEpisodeById(record.episodeId)
            val anime = episode?.let { repositories.metadata.getCachedMetadata(it.animeId).getOrNull() }
                ?: episode?.let { indexedAnimeGroups().firstOrNull { group -> group.animeId == it.animeId }?.toAnime() }
            ContinueWatchingDto(
                progressEpisodeId = record.episodeId,
                positionMs = record.positionMs,
                lastWatched = record.lastWatched,
                playCount = record.playCount,
                episode = episode,
                anime = anime,
            )
        }

    private suspend fun findEpisodeById(episodeId: String): Episode? {
        repositories.metadata.getCachedEpisode(episodeId).getOrNull()?.let { return it }
        val sourceParts = episodeId.split(":", limit = 2)
        val sourceId = sourceParts.getOrNull(0)?.toLongOrNull() ?: return null
        val path = sourceParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val entry = repositories.index.queryIndex(sourceId, MediaPathConventions.stem(path))
            .getOrNull()
            .orEmpty()
            .firstOrNull { it.path == path }
            ?: return null
        val source = repositories.mediaSources.getSourceById(sourceId).getOrNull()
        val mergeSameAnimeEnabled = repositories.scanPreferences.getPreferences().mergeSameAnimeEnabled
        return entry.toEpisode(source, entry.mediaIndexPosterAnimeId(mergeSameAnimeEnabled))
    }

    private suspend fun loadEpisodesForAnime(
        anime: Anime,
        group: IndexedAnimeGroup?,
    ): List<Episode> {
        val cachedEpisodes = repositories.metadata.getCachedEpisodes(anime.id).getOrNull().orEmpty()
        if (cachedEpisodes.isNotEmpty()) return cachedEpisodes
        val indexedGroup = group ?: return emptyList()
        return indexedGroup.entries
            .map { entry -> entry.toEpisode(indexedGroup.source, indexedGroup.animeId) }
            .sortedForPlaybackQueue()
    }

    private suspend fun indexedAnimeGroups(): List<IndexedAnimeGroup> {
        val sources = repositories.mediaSources.getSources().getOrNull().orEmpty()
        val mergeSameAnimeEnabled = repositories.scanPreferences.getPreferences().mergeSameAnimeEnabled
        return sources.flatMap { source ->
            repositories.index.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .toMediaIndexPosterGroups(mergeSameAnimeEnabled)
                .map { group ->
                    IndexedAnimeGroup(
                        source = source,
                        animeId = group.animeId,
                        title = group.title,
                        entries = group.entries,
                    )
                }
        }
    }

    private fun MediaIndexEntry.toEpisode(source: MediaSourceInfo?, animeId: String): Episode {
        val episodeId = "$sourceId:$path"
        return Episode(
            id = episodeId,
            animeId = animeId,
            seasonNumber = seasonNumber ?: 1,
            episodeNumber = episodeNumber ?: 1,
            title = episodeTitle.orEmpty(),
            filePath = playablePath(source, path),
            fileName = MediaPathConventions.fileName(path),
        )
    }

    private fun IndexedAnimeGroup.toAnime(): Anime {
        val first = entries.first()
        return Anime(
            id = animeId,
            title = title,
            episodeCount = entries.size,
            summary = first.plot.orEmpty(),
        )
    }

    private fun DesktopSourceScanResult.toSourceScanResponse(source: MediaSourceInfo): SourceScanResponse =
        SourceScanResponse(
            sourceId = sourceId,
            animeName = source.name.ifBlank { source.type.webControlDefaultSourceName() },
            episodesFound = videoEntries.size,
            newEpisodes = videoEntries.size,
            updatedEpisodes = 0,
        )

    private suspend fun rescanLinkedCloudDriveSource(reason: String) {
        val config = repositories.cloudDriveAutomation.getConfig().getOrNull() ?: return
        val sourceId = config.webDavSourceId?.takeIf { it > 0L } ?: return
        val source = repositories.mediaSources.getSourceById(sourceId).getOrNull() ?: return
        rescanCloudRssLinkedSource(source, reason, repositories.index)
    }

    private fun playablePath(source: MediaSourceInfo?, path: String): String =
        when {
            path.startsWith("http://") ||
                path.startsWith("https://") ||
                path.startsWith("content://") -> path
            source?.type == MediaSourceType.WEBDAV -> MediaPathConventions.joinRemoteUrl(source.remoteUrl().orEmpty(), path)
            else -> path
        }

    private fun findLocalIps(): List<String> =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .mapNotNull { it.hostAddress?.takeIf(String::isNotBlank) }
            .distinct()

    private fun <T> requireSuccess(result: Result<T>, message: String): T =
        when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw IllegalStateException("$message: ${result.error.toUserMessage()}")
        }

    private data class IndexedAnimeGroup(
        val source: MediaSourceInfo,
        val animeId: String,
        val title: String,
        val entries: List<MediaIndexEntry>,
    )
}

internal fun desktopWebControlPlaybackStatus(
    player: com.miruplay.tv.player.mpv.MpvProcessPlayer?,
    session: com.miruplay.tv.model.PlaybackProgressSession?,
    mediaPath: String,
    launchStatus: String,
): PlaybackStatusDto {
    if (player == null || session == null) {
        return PlaybackStatusDto(state = "Idle")
    }
    return PlaybackStatusDto(
        state = "Playing",
        uri = mediaPath.takeIf { it.isNotBlank() },
        mediaSourceId = session.episodeId.substringBefore(':', "").ifBlank { null },
        positionMs = session.currentPositionMs(),
        durationMs = 0L,
        isPlaying = player.isActive(),
        error = launchStatus.takeIf { it.contains("failed", ignoreCase = true) },
    )
}

internal suspend fun desktopWebControlPlaybackCommand(
    request: PlaybackCommandRequest,
    player: com.miruplay.tv.player.mpv.MpvProcessPlayer?,
    session: com.miruplay.tv.model.PlaybackProgressSession?,
    stopPlayback: suspend () -> Unit,
): PlaybackStatusDto {
    val activePlayer = player ?: return PlaybackStatusDto(state = "Idle")
    when (request.playbackCommandKind()) {
        WebControlPlaybackCommandKind.PAUSE -> {
            activePlayer.setPaused(true)
            session?.setPaused(true)
        }
        WebControlPlaybackCommandKind.RESUME -> {
            activePlayer.setPaused(false)
            session?.setPaused(false)
        }
        WebControlPlaybackCommandKind.TOGGLE -> {
            activePlayer.togglePause()
            session?.togglePaused()
        }
        WebControlPlaybackCommandKind.STOP -> stopPlayback()
        WebControlPlaybackCommandKind.SEEK -> {
            val targetMs = request.absoluteSeekPositionMs()
            val currentMs = session?.currentPositionMs() ?: 0L
            activePlayer.seekBy((targetMs - currentMs) / 1000.0)
            session?.syncPosition(targetMs)
        }
        WebControlPlaybackCommandKind.SEEK_RELATIVE -> {
            val deltaMs = request.relativeSeekDeltaMs()
            activePlayer.seekBy(deltaMs / 1000.0)
            session?.seekBy(deltaMs / 1000.0)
        }
        WebControlPlaybackCommandKind.SKIP_FORWARD -> {
            val deltaMs = request.skipForwardDeltaMs()
            activePlayer.seekBy(deltaMs / 1000.0)
            session?.seekBy(deltaMs / 1000.0)
        }
        WebControlPlaybackCommandKind.SKIP_BACKWARD -> {
            val deltaMs = request.skipBackwardDeltaMs()
            activePlayer.seekBy(-deltaMs / 1000.0)
            session?.seekBy(-deltaMs / 1000.0)
        }
        WebControlPlaybackCommandKind.SPEED -> Unit
        WebControlPlaybackCommandKind.UNKNOWN -> throw IllegalArgumentException("未知播放命令: ${request.command}")
    }
    return PlaybackStatusDto(
        state = if (activePlayer.isActive()) "Playing" else "Idle",
        uri = session?.episodeId,
        mediaSourceId = session?.episodeId?.substringBefore(':', "")?.ifBlank { null },
        positionMs = session?.currentPositionMs() ?: 0L,
        durationMs = 0L,
        isPlaying = activePlayer.isActive(),
    )
}

private suspend fun idlePlaybackStatus(): PlaybackStatusDto =
    PlaybackStatusDto(state = "Idle")
