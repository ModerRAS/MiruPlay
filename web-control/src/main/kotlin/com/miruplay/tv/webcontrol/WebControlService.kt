package com.miruplay.tv.webcontrol

import android.os.Build
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlService @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val metadataRepository: MetadataRepository,
    private val indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val scanPreferencesRepository: ScanPreferencesRepository,
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
) : WebControlEndpointService {
    private val startedAt = System.currentTimeMillis()
    private val libraryLoader = WebControlLibraryLoader(
        mediaSources = mediaRepository,
        metadata = metadataRepository,
        index = indexRepository,
        progress = progressRepository,
        mergeSameAnimeEnabled = { scanPreferencesRepository.getPreferences().mergeSameAnimeEnabled },
    )

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
        com.miruplay.tv.core.common.LocalDirectoryBrowser.browse(path).toWebControlDirectoryDto()
    }

    override suspend fun addSource(request: SourceRequest): MediaSourceInfo {
        val source = request.toMediaSourceInfo()
        val id = requireSuccess(mediaRepository.addSource(source), "添加媒体源失败")
        val connected = testSource(source).connected
        val savedSource = source.copy(id = id, isConnected = connected)
        mediaRepository.updateSource(savedSource)
        return savedSource.safeForApi()
    }

    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo {
        val existing = requireSuccess(mediaRepository.getSourceById(sourceId), "媒体源不存在")
        val currentPassword = existing.connectionInfo["password"]
        val source = request.toMediaSourceInfo(sourceId, currentPassword)
            .copy(isConnected = existing.isConnected, lastScanned = existing.lastScanned)
        requireSuccess(mediaRepository.updateSource(source), "更新媒体源失败")
        return source.safeForApi()
    }

    override suspend fun removeSource(sourceId: Long) {
        requireSuccess(mediaRepository.removeSource(sourceId), "删除媒体源失败")
    }

    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse {
        return testSource(request.toMediaSourceInfo())
    }

    override suspend fun scanSource(sourceId: Long): SourceScanResponse {
        val result = requireSuccess(scanCoordinator.scanSource(sourceId), "扫描媒体源失败")
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
        val config = requireSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败")
        return CloudDriveAutomationDto(
            config = config,
            subscriptions = cloudDriveRepository.observeSubscriptions().first(),
            tokenConfigured = !securePreferences.cloudDriveToken.isNullOrBlank()
        )
    }

    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto {
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

    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto {
        if (request.endpointUrl.isBlank() || request.username.isBlank() || request.password.isBlank()) {
            throw IllegalArgumentException("请填写 CloudDrive2 地址、用户名和密码")
        }
        requireSuccess(
            cloudDriveEngine.login(request.endpointUrl.trim(), request.username.trim(), request.password),
            "CloudDrive2 登录失败"
        )
        return getCloudDriveAutomation()
    }

    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse {
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

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
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

    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo {
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

    override suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo =
        saveRssSubscription(request.copy(id = id))

    override suspend fun deleteRssSubscription(id: Long) {
        requireSuccess(cloudDriveRepository.deleteSubscription(id), "删除 RSS 订阅失败")
    }

    override suspend fun getLogUpload(): LogUploadDto {
        return logUploadRepository.getWebControlLogUpload()
    }

    override suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto {
        return logUploadRepository.saveWebControlLogUploadConfig(request)
    }

    override suspend fun saveLogUploadToken(request: LogUploadTokenRequest): LogUploadDto {
        return logUploadRepository.saveWebControlLogUploadToken(request)
    }

    override suspend fun clearLogUploadToken(): LogUploadDto {
        return logUploadRepository.clearWebControlLogUploadToken()
    }

    override suspend fun uploadPendingLogs(): LogUploadDto {
        return logUploadRepository.runWebControlLogUploadNow()
    }

    override suspend fun getMetadataSettings(): MetadataSettingsDto =
        MetadataSettingsDto(
            bangumiTokenConfigured = !securePreferences.bangumiAccessToken.isNullOrBlank()
        )

    override suspend fun saveBangumiToken(request: BangumiTokenRequest): MetadataSettingsDto {
        val token = request.token.trim()
        if (token.isBlank()) {
            throw IllegalArgumentException("请填写 Bangumi Token")
        }
        securePreferences.bangumiAccessToken = token
        return getMetadataSettings()
    }

    override suspend fun clearBangumiToken(): MetadataSettingsDto {
        securePreferences.clearBangumiToken()
        return getMetadataSettings()
    }

    override suspend fun searchLibrary(query: String): LibraryDto {
        return libraryLoader.searchLibrary(query)
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetailDto {
        return libraryLoader.loadAnimeDetail(animeId)
    }

    override suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto {
        val episode = libraryLoader.findEpisodeById(request.episodeId)
            ?: throw IllegalArgumentException("剧集不存在")
        val progress = progressRepository.getProgress(episode.id).getOrNull()
        navigator.openPlayer(request.toWebControlPlaybackSource(episode, progress).toWebPlaybackSource())
        return playbackStatus()
    }

    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto {
        request.executeWebControlPlaybackCommand(
            webControlPlaybackCommandTarget(
                pause = { playbackController.pause() },
                resume = { playbackController.resume() },
                toggle = {
                    if (playbackController.isPlaying()) playbackController.pause() else playbackController.resume()
                },
                stop = { playbackController.stop() },
                seekTo = { positionMs -> playbackController.seekTo(positionMs) },
                setPlaybackSpeed = { speed -> playbackController.setPlaybackSpeed(speed) },
                currentPositionMs = {
                    runCatching { playbackController.getCurrentPosition() }.getOrDefault(0L)
                },
                durationMs = {
                    runCatching { playbackController.getDuration() }.getOrDefault(0L)
                },
            )
        )
        return playbackStatus()
    }

    override suspend fun playbackStatus(): PlaybackStatusDto {
        val state = playbackController.state.value
        val currentPosition = runCatching { playbackController.getCurrentPosition() }.getOrDefault(0L)
        val duration = runCatching { playbackController.getDuration() }.getOrDefault(0L)
        return state.toWebControlPlaybackStatus(
            currentPositionMs = currentPosition,
            durationMs = duration,
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

    override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto = withContext(Dispatchers.IO) {
        requireSuccess(
            browseWebControlCloudDriveDirectory(
                client = cloudDriveClient,
                endpointUrl = endpointUrl,
                fallbackEndpointUrl = { requireSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败").endpointUrl },
                token = securePreferences.cloudDriveToken,
                path = path,
            ),
            "读取 CloudDrive 目录失败",
        )
    }

    private fun <T> requireSuccess(result: Result<T>, message: String): T {
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw IllegalStateException("$message: ${result.error}")
        }
    }

}
