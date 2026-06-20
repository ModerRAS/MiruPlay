package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.mediasource.testConnection
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModePreferencesRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.OtlpLogUploadConfig
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.SCAN_PREFERENCES_MILLIS_PER_HOUR
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.toScanIntervalMillis
import com.miruplay.tv.repository.toScanIntervalHours
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class SharedWebControlEndpointService(
    private val mediaSourceRepository: MediaSourceRepository,
    metadataRepository: MetadataRepository,
    indexRepository: MediaIndexRepository,
    progressRepository: PlaybackProgressRepository,
    private val scanPreferencesRepository: ScanPreferencesRepository,
    private val appModePreferences: AppModePreferencesRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val credentials: CloudDriveCredentialStore,
    private val securePreferences: AppCredentialStore,
    private val logUploadRepository: LogUploadRepository,
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveActions: CloudDriveRssActionCoordinator,
    private val deviceNameProvider: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : WebControlEndpointService {
    private val startedAt = clock()
    private val cloudDriveRunScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cloudDriveRunLock = Any()

    @Volatile
    private var cloudDriveRunStatus: CloudDriveRunStatusDto = CloudDriveRunStatusDto.idle()

    protected val libraryLoader = WebControlLibraryLoader(
        mediaSources = mediaSourceRepository,
        metadata = metadataRepository,
        index = indexRepository,
        progress = progressRepository,
        mergeSameAnimeEnabled = { scanPreferencesRepository.getPreferences().mergeSameAnimeEnabled },
    )

    protected open suspend fun <T> runOnIo(block: suspend () -> T): T = block()

    protected abstract suspend fun scanSourceResultFor(source: MediaSourceInfo): Result<ScanResult>

    protected open suspend fun afterCloudDriveConfigSaved(config: com.miruplay.tv.model.CloudDriveAutomationConfig) = Unit

    protected open suspend fun afterProxyConfigSaved(config: com.miruplay.tv.model.CloudDriveAutomationConfig) = Unit

    protected open suspend fun beforeCloudDriveAutomationRun() = Unit

    protected open suspend fun afterCloudDriveAutomationRun(summary: CloudDriveRssRunSummary) = Unit

    protected open suspend fun afterCloudDriveAutomationRunFinished() = Unit

    protected open suspend fun afterLogUploadConfigSaved(config: OtlpLogUploadConfig) = Unit

    protected abstract suspend fun playEpisodeResolved(
        request: PlayEpisodeRequest,
        episode: Episode,
    ): PlaybackStatusDto

    protected abstract suspend fun playbackCommandResolved(
        request: PlaybackCommandRequest,
    ): PlaybackStatusDto

    protected abstract suspend fun playbackStatusResolved(): PlaybackStatusDto

    protected suspend fun loadLibrary(): LibraryDto =
        libraryLoader.loadLibrary()

    override suspend fun getServerInfo(port: Int): ServerInfoDto = runOnIo {
        buildWebControlServerInfo(
            deviceName = deviceNameProvider(),
            port = port,
            startedAt = startedAt,
        )
    }

    override suspend fun listSources(): List<MediaSourceInfo> =
        mediaSourceRepository.listWebControlSources()

    override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto = runOnIo {
        LocalDirectoryBrowser.browse(path).toWebControlDirectoryDto()
    }

    override suspend fun browseCloudDriveDirectories(
        endpointUrl: String,
        path: String,
    ): CloudDriveDirectoryDto = runOnIo {
        requireWebControlSuccess(
            browseWebControlCloudDriveDirectory(
                client = cloudDriveClient,
                endpointUrl = endpointUrl,
                fallbackEndpointUrl = { cloudDriveRepository.resolveWebControlCloudDriveEndpoint() },
                token = credentials.cloudDriveToken,
                path = path,
            ),
            "读取 CloudDrive 目录失败",
        )
    }

    override suspend fun addSource(request: SourceRequest): MediaSourceInfo =
        mediaSourceRepository.addWebControlSource(request) { source -> testSource(source) }

    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo =
        mediaSourceRepository.updateWebControlSource(sourceId, request)

    override suspend fun removeSource(sourceId: Long) {
        mediaSourceRepository.removeWebControlSource(sourceId)
    }

    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse =
        testSource(request.toMediaSourceInfo())

    override suspend fun scanSource(sourceId: Long): SourceScanResponse =
        mediaSourceRepository.scanWebControlSourceFromScanResult(sourceId) { source ->
            scanSourceResultFor(source)
        }

    override suspend fun scanAllSources(): List<SourceScanResponse> =
        mediaSourceRepository.scanAllWebControlSourcesFromScanResult { source ->
            scanSourceResultFor(source)
        }

    override suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto =
        cloudDriveRepository.getWebControlCloudDriveAutomation(credentials)

    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto =
        cloudDriveActions.saveWebControlCloudDriveConfig(
            request = request,
            repository = cloudDriveRepository,
            credentials = credentials,
        ).also { dto ->
            afterCloudDriveConfigSaved(dto.config)
        }

    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto =
        cloudDriveActions.loginWebControlCloudDrive(
            request = request,
            repository = cloudDriveRepository,
            credentials = credentials,
        )

    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse =
        cloudDriveActions.saveWebControlCloudDriveToken(request)

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        beforeCloudDriveAutomationRun()
        return try {
            cloudDriveActions.runWebControlCloudDriveAutomationNow(::afterCloudDriveAutomationRun)
        } finally {
            afterCloudDriveAutomationRunFinished()
        }
    }

    override suspend fun startCloudDriveAutomationRun(): CloudDriveRunStatusDto {
        val startedStatus = synchronized(cloudDriveRunLock) {
            if (cloudDriveRunStatus.running) {
                return cloudDriveRunStatus
            }
            CloudDriveRunStatusDto(
                status = CloudDriveRunStatusDto.RUNNING,
                running = true,
                startedAt = clock(),
            ).also { cloudDriveRunStatus = it }
        }

        cloudDriveRunScope.launch {
            val finishedStatus = try {
                val summary = runCloudDriveAutomationNow()
                CloudDriveRunStatusDto(
                    status = CloudDriveRunStatusDto.SUCCEEDED,
                    running = false,
                    startedAt = startedStatus.startedAt,
                    finishedAt = clock(),
                    summary = summary,
                )
            } catch (e: Exception) {
                CloudDriveRunStatusDto(
                    status = CloudDriveRunStatusDto.FAILED,
                    running = false,
                    startedAt = startedStatus.startedAt,
                    finishedAt = clock(),
                    error = e.message ?: "CloudDrive/RSS 执行失败",
                )
            }
            synchronized(cloudDriveRunLock) {
                cloudDriveRunStatus = finishedStatus
            }
        }

        return startedStatus
    }

    override suspend fun getCloudDriveAutomationRunStatus(): CloudDriveRunStatusDto =
        synchronized(cloudDriveRunLock) { cloudDriveRunStatus }

    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): com.miruplay.tv.model.RssSubscriptionInfo =
        cloudDriveActions.saveWebControlRssSubscription(request)

    override suspend fun updateRssSubscription(
        id: Long,
        request: RssSubscriptionRequest,
    ): com.miruplay.tv.model.RssSubscriptionInfo =
        cloudDriveActions.updateWebControlRssSubscription(
            id = id,
            request = request,
            repository = cloudDriveRepository,
        )

    override suspend fun deleteRssSubscription(id: Long) {
        cloudDriveActions.deleteWebControlRssSubscription(id)
    }

    override suspend fun getNetworkProxy(): NetworkProxyDto = runOnIo {
        cloudDriveRepository.getWebControlNetworkProxy()
    }

    override suspend fun saveNetworkProxy(request: NetworkProxyRequest): NetworkProxyDto = runOnIo {
        val (config, dto) = cloudDriveRepository.saveWebControlNetworkProxy(request)
        afterProxyConfigSaved(config)
        dto
    }

    override suspend fun getLogUpload(): LogUploadDto {
        return logUploadRepository.getWebControlLogUpload()
    }

    override suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto {
        return logUploadRepository.saveWebControlLogUploadConfig(request).also { dto ->
            afterLogUploadConfigSaved(
                OtlpLogUploadConfig(
                    enabled = dto.config.enabled,
                    endpoint = dto.config.endpoint,
                    streamName = dto.config.streamName,
                    lastUploadAt = dto.config.lastUploadAt,
                    lastUploadStatus = dto.config.lastUploadStatus,
                ),
            )
        }
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

    override suspend fun getLocalLogs(limit: Int): LocalLogsDto = runOnIo {
        logUploadRepository.getWebControlLocalLogs(limit)
    }

    override suspend fun downloadLocalLogs(sinceTimestampMs: Long?): LocalLogDownload = runOnIo {
        logUploadRepository.downloadWebControlLocalLogs(sinceTimestampMs)
    }

    override suspend fun getMetadataSettings(): MetadataSettingsDto =
        MetadataSettingsDto(
            bangumiTokenConfigured = !securePreferences.bangumiAccessToken.isNullOrBlank(),
            tmdbTokenConfigured = !securePreferences.tmdbAccessToken.isNullOrBlank(),
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

    override suspend fun saveTmdbToken(request: TmdbTokenRequest): MetadataSettingsDto {
        val token = request.token.trim()
        if (token.isBlank()) {
            throw IllegalArgumentException("请填写 TMDB Token")
        }
        securePreferences.tmdbAccessToken = token
        return getMetadataSettings()
    }

    override suspend fun clearTmdbToken(): MetadataSettingsDto {
        securePreferences.clearTmdbToken()
        return getMetadataSettings()
    }

    override suspend fun getScanSettings(): ScanSettingsDto = runOnIo {
        val prefs = scanPreferencesRepository.getPreferences().normalized()
        val appMode = appModePreferences.getSelectionState().currentAppMode
        ScanSettingsDto(
            autoScanEnabled = prefs.autoScanEnabled,
            autoScanIntervalHours = prefs.autoScanIntervalMs.toScanIntervalHours(),
            lastScanAt = prefs.lastScanAt,
            mergeSameAnimeEnabled = prefs.mergeSameAnimeEnabled,
            posterWallArrangement = prefs.posterWallArrangement,
            currentAppMode = appMode?.storageValue,
        )
    }

    override suspend fun saveScanSettings(request: ScanSettingsRequest): ScanSettingsDto = runOnIo {
        request.autoScanEnabled?.let { scanPreferencesRepository.setAutoScanEnabled(it) }
        request.autoScanIntervalHours?.let {
            scanPreferencesRepository.setAutoScanIntervalMs(it.coerceAtLeast(1).toScanIntervalMillis())
        }
        request.mergeSameAnimeEnabled?.let { scanPreferencesRepository.setMergeSameAnimeEnabled(it) }
        request.posterWallArrangement?.let { scanPreferencesRepository.setPosterWallArrangement(it) }
        request.currentAppMode?.let { modeValue ->
            AppMode.fromStorageValue(modeValue)?.let { appModePreferences.setCurrentAppMode(it) }
        }
        getScanSettings()
    }

    override suspend fun searchLibrary(query: String): LibraryDto =
        libraryLoader.searchLibrary(query)

    override suspend fun getAnimeDetail(animeId: String): AnimeDetailDto =
        libraryLoader.loadAnimeDetail(animeId)

    override suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto {
        val episode = libraryLoader.findEpisodeById(request.episodeId)
            ?: throw IllegalArgumentException("剧集不存在")
        return playEpisodeResolved(request, episode)
    }

    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto =
        playbackCommandResolved(request)

    override suspend fun playbackStatus(): PlaybackStatusDto =
        playbackStatusResolved()

    private suspend fun testSource(source: MediaSourceInfo): SourceTestResponse =
        mediaSourceFactory.testConnection(source).toWebControlSourceTestResponse()
}
