package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveClient
import android.os.Build
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
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
    private val scanPreferences: ScanPreferencesRepository,
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
        val sources = (mediaRepository.getSources() as? Result.Success)?.data ?: emptyList()
        return sources.mapNotNull { source ->
            when (val result = scanCoordinator.scanSource(source.id)) {
                is Result.Success -> result.data.toWebControlSourceScanResponse(source.id)
                is Result.Error -> null
            }
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

    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto {
        return cloudDriveActions.loginWebControlCloudDrive(
            request = request,
            repository = cloudDriveRepository,
            credentials = securePreferences,
        )
    }

    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse {
        return cloudDriveActions.saveWebControlCloudDriveToken(request)
    }

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        return cloudDriveActions.runWebControlCloudDriveAutomationNow()
    }

    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo {
        return cloudDriveActions.saveWebControlRssSubscription(request)
    }

    override suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo =
        cloudDriveActions.updateWebControlRssSubscription(
            id = id,
            request = request,
            repository = cloudDriveRepository,
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
