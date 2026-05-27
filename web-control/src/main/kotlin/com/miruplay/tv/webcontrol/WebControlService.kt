package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveClient
import android.os.Build
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.model.CloudDriveDirectoryItem
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlService @Inject constructor(
    mediaRepository: MediaSourceRepository,
    metadataRepository: MetadataRepository,
    indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val scanPreferences: ScanPreferencesRepository,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val securePreferences: AppCredentialStore,
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val cloudDriveScheduler: CloudDriveRssScheduler,
    private val scanCoordinator: ScanCoordinator,
    mediaSourceFactory: MediaSourceFactory,
    private val playbackController: PlaybackController,
    private val navigator: WebControlNavigator
) : WebControlEndpointService {
    private val startedAt = System.currentTimeMillis()
    private val cloudDriveActions = CloudDriveRssActionCoordinator(
        repository = cloudDriveRepository,
        credentials = securePreferences,
        runner = cloudDriveEngine,
    )
    private val libraryLoader = WebControlLibraryLoader(
        mediaSources = mediaRepository,
        metadata = metadataRepository,
        index = indexRepository,
        progress = progressRepository,
        mergeSameAnimeEnabled = { scanPreferences.getPreferences().mergeSameAnimeEnabled },
    )

    override suspend fun getServerInfo(port: Int): ServerInfoDto = withContext(Dispatchers.IO) {
        buildWebControlServerInfo(
            deviceName = Build.MODEL ?: "Android TV",
            port = port,
            startedAt = startedAt,
        )
    }

    override suspend fun listSources(): List<MediaSourceInfo> =
        mediaRepository.listWebControlSources()

    override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto = withContext(Dispatchers.IO) {
        val listing = LocalDirectoryBrowser.browse(path)
        listing.toWebControlDirectoryDto()
    }

    override suspend fun addSource(request: SourceRequest): MediaSourceInfo {
        return mediaRepository.addWebControlSource(request) { source -> testSource(source) }
    }

    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo {
        return mediaRepository.updateWebControlSource(sourceId, request)
    }

    override suspend fun removeSource(sourceId: Long) {
        mediaRepository.removeWebControlSource(sourceId)
    }

    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse {
        return testSource(request.toMediaSourceInfo())
    }

    override suspend fun scanSource(sourceId: Long): SourceScanResponse {
        val result = requireWebControlSuccess(scanCoordinator.scanSource(sourceId), "扫描媒体源失败")
        return result.toWebControlSourceScanResponse(sourceId)
    }

    override suspend fun scanAllSources(): List<SourceScanResponse> {
        return mediaRepository.scanAllWebControlSources { source ->
            scanCoordinator.scanSource(source.id)
                .map { it.toWebControlSourceScanResponse(source.id) }
        }
    }

    override suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto {
        return cloudDriveRepository.getWebControlCloudDriveAutomation(securePreferences)
    }

    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto {
        return cloudDriveActions.saveWebControlCloudDriveConfig(
            request = request,
            repository = cloudDriveRepository,
            credentials = securePreferences,
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

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        return cloudDriveActions.runWebControlCloudDriveAutomationNow()
    }

    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo {
        return cloudDriveActions.saveWebControlRssSubscription(request)
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

    override suspend fun deleteRssSubscription(id: Long) {
        cloudDriveActions.deleteWebControlRssSubscription(id)
    }

    suspend fun getLibrary(): LibraryDto {
        return libraryLoader.loadLibrary()
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
        val source = request.toWebControlPlaybackSource(episode, progress)
        navigator.openPlayer(source.toWebPlaybackSource())
        return playbackStatus()
    }

    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto {
        request.executeWebControlPlaybackCommand(AndroidWebControlPlaybackCommandTarget(playbackController))
        return playbackStatus()
    }

    override suspend fun playbackStatus(): PlaybackStatusDto {
        val state = playbackController.state.value
        val currentPosition = runCatching { playbackController.getCurrentPosition() }.getOrDefault(0L)
        val duration = runCatching { playbackController.getDuration() }.getOrDefault(0L)
            .coerceAtLeast(0L)
        return state.toWebControlPlaybackStatus(
            currentPositionMs = currentPosition,
            durationMs = duration,
        )
    }

    private suspend fun testSource(source: MediaSourceInfo): SourceTestResponse {
        return mediaSourceFactory.testConnection(source).toWebControlSourceTestResponse()
    }

    override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto = withContext(Dispatchers.IO) {
        requireWebControlSuccess(
            browseWebControlCloudDriveDirectory(
                client = cloudDriveClient,
                endpointUrl = endpointUrl,
                fallbackEndpointUrl = {
                    requireWebControlSuccess(cloudDriveRepository.getConfig(), "读取 CloudDrive 设置失败").endpointUrl
                },
                token = securePreferences.cloudDriveToken,
                path = path,
            ),
            "读取 CloudDrive 目录失败",
        )
    }

}

private class AndroidWebControlPlaybackCommandTarget(
    private val playbackController: PlaybackController,
) : WebControlPlaybackCommandTarget {
    override suspend fun pause() {
        playbackController.pause()
    }

    override suspend fun resume() {
        playbackController.resume()
    }

    override suspend fun toggle() {
        if (playbackController.isPlaying()) {
            playbackController.pause()
        } else {
            playbackController.resume()
        }
    }

    override suspend fun stop() {
        playbackController.stop()
    }

    override suspend fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
    }

    override suspend fun currentPositionMs(): Long =
        playbackController.getCurrentPosition()

    override suspend fun durationMs(): Long =
        playbackController.getDuration()
}
