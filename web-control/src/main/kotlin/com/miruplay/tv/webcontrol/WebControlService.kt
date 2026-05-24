package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveClient
import android.os.Build
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.connectionPasswordOrNull
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
import com.miruplay.tv.sync.rss.loadCloudDriveDirectory
import com.miruplay.tv.sync.rss.prepareCloudDriveDirectoryBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlService @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val securePreferences: AppCredentialStore,
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val scanCoordinator: ScanCoordinator,
    private val mediaSourceFactory: MediaSourceFactory,
    private val playbackController: PlaybackController,
    private val navigator: WebControlNavigator
) : WebControlEndpointService {
    private val startedAt = System.currentTimeMillis()

    override suspend fun getServerInfo(port: Int): ServerInfoDto = withContext(Dispatchers.IO) {
        buildWebControlServerInfo(
            deviceName = Build.MODEL ?: "Android TV",
            port = port,
            startedAt = startedAt,
        )
    }

    override suspend fun listSources(): List<MediaSourceInfo> {
        return (mediaRepository.getSources() as? Result.Success)
            ?.data
            ?.map { it.safeForApi() }
            ?: emptyList()
    }

    override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto = withContext(Dispatchers.IO) {
        val listing = LocalDirectoryBrowser.browse(path)
        listing.toWebControlDirectoryDto()
    }

    override suspend fun addSource(request: SourceRequest): MediaSourceInfo {
        val source = request.toMediaSourceInfo()
        val id = requireWebControlSuccess(mediaRepository.addSource(source), "添加媒体源失败")
        val connected = testSource(source).connected
        val savedSource = source.copy(id = id, isConnected = connected)
        mediaRepository.updateSource(savedSource)
        return savedSource.safeForApi()
    }

    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo {
        val existing = requireWebControlSuccess(mediaRepository.getSourceById(sourceId), "媒体源不存在")
        val source = request.toMediaSourceInfo(
            sourceId = sourceId,
            fallbackPassword = existing.connectionPasswordOrNull(),
            isConnected = existing.isConnected,
            lastScanned = existing.lastScanned,
        )
        requireWebControlSuccess(mediaRepository.updateSource(source), "更新媒体源失败")
        return source.safeForApi()
    }

    override suspend fun removeSource(sourceId: Long) {
        requireWebControlSuccess(mediaRepository.removeSource(sourceId), "删除媒体源失败")
    }

    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse {
        return testSource(request.toMediaSourceInfo())
    }

    override suspend fun scanSource(sourceId: Long): SourceScanResponse {
        val result = requireWebControlSuccess(scanCoordinator.scanSource(sourceId), "扫描媒体源失败")
        return SourceScanResponse(
            sourceId = sourceId,
            animeName = result.animeName,
            episodesFound = result.episodesFound,
            newEpisodes = result.newEpisodes,
            updatedEpisodes = result.updatedEpisodes
        )
    }

    override suspend fun scanAllSources(): List<SourceScanResponse> {
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

    override suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto {
        val config = requireWebControlSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败")
        return config.toWebControlAutomationDto(
            subscriptions = cloudDriveRepository.observeSubscriptions().first(),
            tokenConfigured = !securePreferences.cloudDriveToken.isNullOrBlank(),
        )
    }

    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto {
        val current = requireWebControlSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败")
        val config = request.toAutomationConfig(current)
        requireWebControlSuccess(cloudDriveRepository.saveConfig(config), "保存 CloudDrive 设置失败")
        return getCloudDriveAutomation()
    }

    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto {
        val login = request.validated()
        requireWebControlSuccess(
            cloudDriveEngine.login(login.endpointUrl, login.username, login.password),
            "CloudDrive2 登录失败"
        )
        return getCloudDriveAutomation()
    }

    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse {
        val tokenRequest = request.validated()
        val tokenInfo = requireWebControlSuccess(
            cloudDriveEngine.saveApiToken(tokenRequest.endpointUrl, tokenRequest.token),
            "CloudDrive2 API Token 验证失败"
        )
        return tokenInfo.toWebControlResponse()
    }

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        val summary = requireWebControlSuccess(cloudDriveEngine.runOnce(), "CloudDrive/RSS 执行失败")
        return summary.toWebControlResponse()
    }

    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo {
        val subscription = request.toSubscription() ?: throw IllegalArgumentException("请填写 RSS 地址")
        val id = requireWebControlSuccess(cloudDriveRepository.saveSubscription(subscription), "保存 RSS 订阅失败")
        return subscription.withSavedId(id)
    }

    override suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo =
        saveRssSubscription(request.copy(id = id))

    override suspend fun deleteRssSubscription(id: Long) {
        requireWebControlSuccess(cloudDriveRepository.deleteSubscription(id), "删除 RSS 订阅失败")
    }

    suspend fun getLibrary(): LibraryDto {
        val sources = (mediaRepository.getSources() as? Result.Success)?.data ?: emptyList()
        val anime = loadAllAnime(sources)
        return anime.toWebControlLibrary(continueWatching = loadContinueWatching())
    }

    override suspend fun searchLibrary(query: String): LibraryDto {
        val library = getLibrary()
        return library.filteredByQuery(query)
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetailDto {
        val anime = requireWebControlSuccess(metadataRepository.getCachedMetadata(animeId), "番剧不存在")
            ?: throw IllegalArgumentException("番剧不存在")
        val episodes = requireWebControlSuccess(metadataRepository.getCachedEpisodes(animeId), "读取剧集失败")
        return anime.toWebControlAnimeDetail(episodes) { episode ->
            progressRepository.getProgress(episode.id).getOrNull()
        }
    }

    override suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto {
        val episode = findEpisodeById(request.episodeId)
            ?: throw IllegalArgumentException("剧集不存在")
        val progress = progressRepository.getProgress(episode.id).getOrNull()
        val startPosition = request.startPositionFor(episode, progress)
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

    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto {
        when (request.playbackCommandKind()) {
            WebControlPlaybackCommandKind.PAUSE -> playbackController.pause()
            WebControlPlaybackCommandKind.RESUME -> playbackController.resume()
            WebControlPlaybackCommandKind.TOGGLE -> {
                if (playbackController.isPlaying()) playbackController.pause() else playbackController.resume()
            }
            WebControlPlaybackCommandKind.STOP -> playbackController.stop()
            WebControlPlaybackCommandKind.SEEK,
            WebControlPlaybackCommandKind.SEEK_RELATIVE,
            WebControlPlaybackCommandKind.SKIP_FORWARD,
            WebControlPlaybackCommandKind.SKIP_BACKWARD -> playbackController.seekTo(
                request.seekTargetPositionMs(playbackController.getCurrentPosition()) ?: 0L,
            )
            WebControlPlaybackCommandKind.SPEED -> playbackController.setPlaybackSpeed(request.playbackSpeed())
            WebControlPlaybackCommandKind.UNKNOWN -> throw IllegalArgumentException("未知播放命令: ${request.command}")
        }
        return playbackStatus()
    }

    override suspend fun playbackStatus(): PlaybackStatusDto {
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
        return webControlPlaybackStatus(
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
            is Result.Error -> return result.toWebControlSourceTestResponse()
        }
        return mediaSource.testConnection().toWebControlSourceTestResponse()
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
        return all
    }

    private suspend fun loadContinueWatching(): List<ContinueWatchingDto> {
        return (progressRepository.getContinueWatching(30).getOrNull() ?: emptyList()).mapNotNull { record ->
            val episode = findEpisodeById(record.episodeId)
            val anime = episode?.let { metadataRepository.getCachedMetadata(it.animeId).getOrNull() }
            record.toWebControlContinueWatching(episode, anime)
        }
    }

    override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto = withContext(Dispatchers.IO) {
        val resolvedEndpoint = endpointUrl.trim().takeIf { it.isNotBlank() }
            ?: requireWebControlSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败").endpointUrl
        if (resolvedEndpoint.isBlank()) {
            throw IllegalArgumentException("请先填写 CloudDrive2 地址")
        }

        val token = securePreferences.cloudDriveToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("请先登录 CloudDrive2 或保存 API Token")
        val prepared = requireWebControlSuccess(
            prepareCloudDriveDirectoryBrowser(
                client = cloudDriveClient,
                target = CloudDriveDirectoryTarget.INBOX,
                endpointUrl = resolvedEndpoint,
                token = token,
                initialPath = path,
            ),
            "读取 CloudDrive 目录失败",
        )
        val loaded = requireWebControlSuccess(
            loadCloudDriveDirectory(
                client = cloudDriveClient,
                state = prepared,
                requestedPath = prepared.path,
            ),
            "读取 CloudDrive 目录失败",
        )
        loaded.toWebControlDirectoryDto()
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

}
