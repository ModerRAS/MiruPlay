package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.webcontrol.AnimeDetailDto
import com.miruplay.tv.webcontrol.CloudDriveAutomationDto
import com.miruplay.tv.webcontrol.CloudDriveConfigRequest
import com.miruplay.tv.webcontrol.CloudDriveDirectoryDto
import com.miruplay.tv.webcontrol.CloudDriveLoginRequest
import com.miruplay.tv.webcontrol.CloudDriveRunResponse
import com.miruplay.tv.webcontrol.CloudDriveTokenRequest
import com.miruplay.tv.webcontrol.CloudDriveTokenResponse
import com.miruplay.tv.webcontrol.LibraryDto
import com.miruplay.tv.webcontrol.LocalDirectoryDto
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
import com.miruplay.tv.webcontrol.WebControlLibraryLoader
import com.miruplay.tv.webcontrol.WebControlPlaybackCommandKind
import com.miruplay.tv.webcontrol.absoluteSeekPositionMs
import com.miruplay.tv.webcontrol.addWebControlSource
import com.miruplay.tv.webcontrol.browseWebControlCloudDriveDirectory
import com.miruplay.tv.webcontrol.buildWebControlServerInfo
import com.miruplay.tv.webcontrol.deleteWebControlRssSubscription
import com.miruplay.tv.webcontrol.getWebControlCloudDriveAutomation
import com.miruplay.tv.webcontrol.idleWebControlPlaybackStatus
import com.miruplay.tv.webcontrol.loginWebControlCloudDrive
import com.miruplay.tv.webcontrol.playbackCommandKind
import com.miruplay.tv.webcontrol.relativeSeekDeltaMs
import com.miruplay.tv.webcontrol.removeWebControlSource
import com.miruplay.tv.webcontrol.requireWebControlSuccess
import com.miruplay.tv.webcontrol.runWebControlCloudDriveAutomationNow
import com.miruplay.tv.webcontrol.safeForApi
import com.miruplay.tv.webcontrol.saveWebControlCloudDriveConfig
import com.miruplay.tv.webcontrol.saveWebControlCloudDriveToken
import com.miruplay.tv.webcontrol.saveWebControlRssSubscription
import com.miruplay.tv.webcontrol.skipBackwardDeltaMs
import com.miruplay.tv.webcontrol.skipForwardDeltaMs
import com.miruplay.tv.webcontrol.toMediaSourceInfo
import com.miruplay.tv.webcontrol.toWebControlDirectoryDto
import com.miruplay.tv.webcontrol.toWebControlSourceTestResponse
import com.miruplay.tv.webcontrol.toWebControlSourceScanResponse
import com.miruplay.tv.webcontrol.updateWebControlRssSubscription
import com.miruplay.tv.webcontrol.updateWebControlSource
import com.miruplay.tv.webcontrol.webControlDefaultSourceName
import com.miruplay.tv.webcontrol.webControlMediaSourceIdFromEpisodeId
import com.miruplay.tv.webcontrol.webControlPlaybackStatus
import java.io.File

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
    private val cloudRssActions = CloudDriveRssActionCoordinator(
        repository = repositories.cloudDriveAutomation,
        credentials = repositories.credentials,
        runner = cloudRssEngine,
    )
    private val libraryLoader = WebControlLibraryLoader(
        mediaSources = repositories.mediaSources,
        metadata = repositories.metadata,
        index = repositories.index,
        progress = repositories.progress,
        mergeSameAnimeEnabled = { repositories.scanPreferences.getPreferences().mergeSameAnimeEnabled },
    )

    override suspend fun getServerInfo(port: Int): ServerInfoDto =
        buildWebControlServerInfo(
            deviceName = deviceName,
            port = port,
            startedAt = startedAt,
        )

    override suspend fun listSources(): List<MediaSourceInfo> =
        repositories.mediaSources.getSources().getOrNull().orEmpty().map { it.safeForApi() }

    override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto {
        if (path.isBlank()) {
            return LocalDirectoryBrowser.Listing(
                path = "",
                displayPath = "设备存储",
                parentPath = null,
                entries = File.listRoots()
                    .filter { it.exists() && it.isDirectory && it.canRead() }
                    .map { LocalDirectoryBrowser.Entry(it.absolutePath, it.absolutePath, it.canRead()) },
            ).toWebControlDirectoryDto()
        }
        val listing = LocalDirectoryBrowser.browse(path)
        return listing.toWebControlDirectoryDto()
    }

    override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto {
        return requireWebControlSuccess(
            browseWebControlCloudDriveDirectory(
                client = cloudDriveClient,
                endpointUrl = endpointUrl,
                fallbackEndpointUrl = {
                    repositories.cloudDriveAutomation.getConfig().getOrNull()?.endpointUrl.orEmpty()
                },
                token = repositories.credentials.cloudDriveToken,
                path = path,
            ),
            "读取 CloudDrive 目录失败",
        )
    }

    override suspend fun addSource(request: SourceRequest): MediaSourceInfo {
        return repositories.mediaSources.addWebControlSource(request) { source -> testSource(source) }
    }

    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo {
        return repositories.mediaSources.updateWebControlSource(sourceId, request)
    }

    override suspend fun removeSource(sourceId: Long) {
        repositories.mediaSources.removeWebControlSource(sourceId)
    }

    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse {
        return testSource(request.toMediaSourceInfo())
    }

    private suspend fun testSource(source: MediaSourceInfo): SourceTestResponse {
        val mediaSource = desktopSourceFromInfo(source)
        return try {
            mediaSource.testConnection().toWebControlSourceTestResponse()
        } finally {
            mediaSource.close()
        }
    }

    override suspend fun scanSource(sourceId: Long): SourceScanResponse {
        val source = requireWebControlSuccess(repositories.mediaSources.getSourceById(sourceId), "媒体源不存在")
        val result = requireWebControlSuccess(scanAndIndexDesktopSource(source, repositories.index), "扫描媒体源失败")
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
        return repositories.cloudDriveAutomation.getWebControlCloudDriveAutomation(repositories.credentials)
    }

    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto {
        return cloudRssActions.saveWebControlCloudDriveConfig(
            request = request,
            repository = repositories.cloudDriveAutomation,
            credentials = repositories.credentials,
        )
    }

    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto {
        return cloudRssActions.loginWebControlCloudDrive(
            request = request,
            repository = repositories.cloudDriveAutomation,
            credentials = repositories.credentials,
        )
    }

    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse {
        return cloudRssActions.saveWebControlCloudDriveToken(request)
    }

    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse {
        return cloudRssActions.runWebControlCloudDriveAutomationNow { summary ->
            rescanLinkedCloudDriveSource(summary.completeStatus())
        }
    }

    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): com.miruplay.tv.model.RssSubscriptionInfo {
        return cloudRssActions.saveWebControlRssSubscription(request)
    }

    override suspend fun updateRssSubscription(
        id: Long,
        request: RssSubscriptionRequest,
    ): com.miruplay.tv.model.RssSubscriptionInfo =
        cloudRssActions.updateWebControlRssSubscription(
            id = id,
            request = request,
            repository = repositories.cloudDriveAutomation,
        )

    override suspend fun deleteRssSubscription(id: Long) {
        cloudRssActions.deleteWebControlRssSubscription(id)
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
        return playEpisodeHandler(request, episode)
    }

    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto =
        playbackCommandHandler(request)

    override suspend fun playbackStatus(): PlaybackStatusDto =
        playbackStatusProvider()

    private fun DesktopSourceScanResult.toSourceScanResponse(source: MediaSourceInfo): SourceScanResponse =
        toWebControlSourceScanResponse(
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

}

internal fun desktopWebControlPlaybackStatus(
    player: com.miruplay.tv.player.mpv.MpvProcessPlayer?,
    session: com.miruplay.tv.model.PlaybackProgressSession?,
    mediaPath: String,
    launchStatus: String,
): PlaybackStatusDto {
    if (player == null || session == null) {
        return idlePlaybackStatus()
    }
    return webControlPlaybackStatus(
        state = "Playing",
        uri = mediaPath.takeIf { it.isNotBlank() },
        mediaSourceId = session.episodeId.webControlMediaSourceIdFromEpisodeId(),
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
    val activePlayer = player ?: return idlePlaybackStatus()
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
    return webControlPlaybackStatus(
        state = if (activePlayer.isActive()) "Playing" else "Idle",
        uri = session?.episodeId,
        mediaSourceId = session?.episodeId?.webControlMediaSourceIdFromEpisodeId(),
        positionMs = session?.currentPositionMs() ?: 0L,
        durationMs = 0L,
        isPlaying = activePlayer.isActive(),
    )
}

private fun idlePlaybackStatus(): PlaybackStatusDto =
    idleWebControlPlaybackStatus()
