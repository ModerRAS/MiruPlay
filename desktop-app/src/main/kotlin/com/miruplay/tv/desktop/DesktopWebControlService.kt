package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.localRootPath
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.repository.mediaFilesOnly
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
import com.miruplay.tv.webcontrol.safeForApi
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

internal class DesktopWebControlService(
    private val repositories: DesktopRepositories,
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
        throw UnsupportedOperationException("Windows WebUI 暂未接入 CloudDrive 目录浏览")
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
            fallbackPassword = existing.connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD],
        ).copy(isConnected = existing.isConnected, lastScanned = existing.lastScanned)
        requireSuccess(repositories.mediaSources.updateSource(source), "更新媒体源失败")
        return source.safeForApi()
    }

    override suspend fun removeSource(sourceId: Long) {
        requireSuccess(repositories.mediaSources.removeSource(sourceId), "删除媒体源失败")
    }

    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse {
        val source = request.toMediaSourceInfo()
        return if (source.type == MediaSourceType.LOCAL && source.localRootPath()?.isNotBlank() == true) {
            SourceTestResponse(connected = true, message = "本地路径格式可用")
        } else {
            SourceTestResponse(connected = false, message = "Windows WebUI 暂未接入远程媒体源连通性测试")
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
        throw UnsupportedOperationException("Windows WebUI 暂未接入 CloudDrive 登录")
    }

    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse {
        if (request.token.isBlank()) {
            throw IllegalArgumentException("请填写 CloudDrive2 API Token")
        }
        repositories.credentials.cloudDriveToken = request.token.trim()
        return CloudDriveTokenResponse(
            rootDir = "",
            friendlyName = "CloudDrive2",
            allowList = false,
            allowCreateFolder = false,
            allowCreateFile = false,
            allowWrite = false,
            allowMove = false,
            allowAddOfflineDownload = false,
        )
    }

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        throw UnsupportedOperationException("Windows WebUI 暂未接入 CloudDrive 手动执行")
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
        val normalized = query.trim()
        if (normalized.isBlank()) return library

        val filtered = library.allAnime.filter { item ->
            item.id.contains(normalized, ignoreCase = true) ||
                item.title.contains(normalized, ignoreCase = true) ||
                item.titleCn?.contains(normalized, ignoreCase = true) == true
        }
        return library.copy(recentlyAdded = filtered.take(24), allAnime = filtered)
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
            .distinctBy { it.id }
            .sortedBy { it.title.ifBlank { it.id } }
        return LibraryDto(
            continueWatching = loadContinueWatching(),
            recentlyAdded = anime.takeLast(24),
            allAnime = anime,
        )
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
        return entry.toEpisode(source)
    }

    private suspend fun loadEpisodesForAnime(
        anime: Anime,
        group: IndexedAnimeGroup?,
    ): List<Episode> {
        val cachedEpisodes = repositories.metadata.getCachedEpisodes(anime.id).getOrNull().orEmpty()
        if (cachedEpisodes.isNotEmpty()) return cachedEpisodes
        return group?.entries.orEmpty()
            .map { entry -> entry.toEpisode(group?.source) }
            .sortedWith(compareBy<Episode>({ it.seasonNumber }, { it.episodeNumber }, { it.filePath }))
    }

    private suspend fun indexedAnimeGroups(): List<IndexedAnimeGroup> {
        val sources = repositories.mediaSources.getSources().getOrNull().orEmpty()
        return sources.flatMap { source ->
            repositories.index.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .mediaFilesOnly()
                .groupBy { it.animeGroupKey() }
                .values
                .mapNotNull { entries ->
                    val first = entries.firstOrNull() ?: return@mapNotNull null
                    IndexedAnimeGroup(source = source, animeId = first.animeGroupKey(), entries = entries)
                }
        }
    }

    private fun MediaIndexEntry.toEpisode(source: MediaSourceInfo?): Episode {
        val episodeId = "$sourceId:$path"
        return Episode(
            id = episodeId,
            animeId = animeGroupKey(),
            seasonNumber = seasonNumber ?: 1,
            episodeNumber = episodeNumber ?: 1,
            title = episodeTitle.orEmpty(),
            filePath = playablePath(source, path),
            fileName = MediaPathConventions.fileName(path),
        )
    }

    private fun IndexedAnimeGroup.toAnime(): Anime {
        val first = entries.first()
        val title = first.metadataTitle?.takeIf { it.isNotBlank() }
            ?: first.animeName?.takeIf { it.isNotBlank() }
            ?: MediaPathConventions.parentName(first.path).takeIf { it.isNotBlank() }
            ?: MediaPathConventions.stem(first.path).ifBlank { animeId }
        return Anime(
            id = animeId,
            title = title,
            episodeCount = entries.size,
            summary = first.plot.orEmpty(),
        )
    }

    private fun MediaIndexEntry.animeGroupKey(): String =
        metadataId?.takeIf { it.isNotBlank() }
            ?: animeName?.takeIf { it.isNotBlank() }
            ?: metadataTitle?.takeIf { it.isNotBlank() }
            ?: MediaPathConventions.parentName(path).takeIf { it.isNotBlank() }
            ?: MediaPathConventions.stem(path).ifBlank { path }

    private fun DesktopSourceScanResult.toSourceScanResponse(source: MediaSourceInfo): SourceScanResponse =
        SourceScanResponse(
            sourceId = sourceId,
            animeName = source.name.ifBlank { source.type.defaultName() },
            episodesFound = videoEntries.size,
            newEpisodes = videoEntries.size,
            updatedEpisodes = 0,
        )

    private fun playablePath(source: MediaSourceInfo?, path: String): String =
        when {
            path.startsWith("http://") ||
                path.startsWith("https://") ||
                path.startsWith("content://") -> path
            source?.type == MediaSourceType.WEBDAV -> MediaPathConventions.joinRemoteUrl(source.remoteUrl().orEmpty(), path)
            else -> path
        }

    private fun SourceRequest.toMediaSourceInfo(
        sourceId: Long = id,
        fallbackPassword: String? = null,
    ): MediaSourceInfo {
        val sourceType = parseSourceType(type)
        val trimmedLocation = location.trim()
        return MediaSourceInfo(
            id = sourceId,
            name = name.trim().ifBlank { sourceType.defaultName() },
            type = sourceType,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = sourceType,
                location = trimmedLocation,
                displayName = displayName.orEmpty(),
                username = username.orEmpty(),
                password = password?.takeIf { it.isNotBlank() } ?: fallbackPassword.orEmpty(),
            ),
            isConnected = sourceType == MediaSourceType.LOCAL,
        )
    }

    private fun SourceTestRequest.toMediaSourceInfo(): MediaSourceInfo {
        val sourceType = parseSourceType(type)
        return MediaSourceInfo(
            name = "test",
            type = sourceType,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = sourceType,
                location = location.trim(),
                displayName = displayName.orEmpty(),
                username = username.orEmpty(),
                password = password.orEmpty(),
            ),
        )
    }

    private fun parseSourceType(type: String): MediaSourceType =
        runCatching { MediaSourceType.valueOf(type.uppercase()) }
            .getOrElse { throw IllegalArgumentException("不支持的媒体源类型: $type") }

    private fun MediaSourceType.defaultName(): String = when (this) {
        MediaSourceType.LOCAL -> "本地媒体库"
        MediaSourceType.WEBDAV -> "WebDAV 媒体库"
        MediaSourceType.SMB -> "SMB 共享"
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
    when (request.command.lowercase()) {
        "pause" -> {
            activePlayer.setPaused(true)
            session?.setPaused(true)
        }
        "resume", "play" -> {
            activePlayer.setPaused(false)
            session?.setPaused(false)
        }
        "toggle" -> {
            activePlayer.togglePause()
            session?.togglePaused()
        }
        "stop" -> stopPlayback()
        "seek" -> {
            val targetMs = (request.positionMs ?: 0L).coerceAtLeast(0L)
            val currentMs = session?.currentPositionMs() ?: 0L
            activePlayer.seekBy((targetMs - currentMs) / 1000.0)
            session?.syncPosition(targetMs)
        }
        "seek_relative" -> {
            val deltaMs = request.deltaMs ?: 0L
            activePlayer.seekBy(deltaMs / 1000.0)
            session?.seekBy(deltaMs / 1000.0)
        }
        "skip_forward" -> {
            val deltaMs = request.deltaMs ?: PLAYBACK_SEEK_FORWARD_SECONDS * 1000L
            activePlayer.seekBy(deltaMs / 1000.0)
            session?.seekBy(deltaMs / 1000.0)
        }
        "skip_backward" -> {
            val deltaMs = request.deltaMs ?: PLAYBACK_SEEK_BACK_SECONDS * 1000L
            activePlayer.seekBy(-deltaMs / 1000.0)
            session?.seekBy(-deltaMs / 1000.0)
        }
        "speed" -> Unit
        else -> throw IllegalArgumentException("未知播放命令: ${request.command}")
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
