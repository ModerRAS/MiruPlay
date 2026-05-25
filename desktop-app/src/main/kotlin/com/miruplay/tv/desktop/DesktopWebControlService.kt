package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.mediasource.desktop.DesktopMediaSourceFactory
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.mediasource.testConnection
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
import com.miruplay.tv.webcontrol.addWebControlSource
import com.miruplay.tv.webcontrol.browseWebControlCloudDriveDirectory
import com.miruplay.tv.webcontrol.buildWebControlServerInfo
import com.miruplay.tv.webcontrol.deleteWebControlRssSubscription
import com.miruplay.tv.webcontrol.executeWebControlPlaybackCommand
import com.miruplay.tv.webcontrol.getWebControlCloudDriveAutomation
import com.miruplay.tv.webcontrol.idleWebControlPlaybackStatus
import com.miruplay.tv.webcontrol.loginWebControlCloudDrive
import com.miruplay.tv.webcontrol.listWebControlSources
import com.miruplay.tv.webcontrol.removeWebControlSource
import com.miruplay.tv.webcontrol.requireWebControlSuccess
import com.miruplay.tv.webcontrol.runWebControlCloudDriveAutomationNow
import com.miruplay.tv.webcontrol.saveWebControlCloudDriveConfig
import com.miruplay.tv.webcontrol.saveWebControlCloudDriveToken
import com.miruplay.tv.webcontrol.saveWebControlRssSubscription
import com.miruplay.tv.webcontrol.scanWebControlSource
import com.miruplay.tv.webcontrol.scanAllWebControlSources
import com.miruplay.tv.webcontrol.toMediaSourceInfo
import com.miruplay.tv.webcontrol.toWebControlDirectoryDto
import com.miruplay.tv.webcontrol.toWebControlSourceScanResponse
import com.miruplay.tv.webcontrol.toWebControlSourceTestResponse
import com.miruplay.tv.webcontrol.updateWebControlRssSubscription
import com.miruplay.tv.webcontrol.updateWebControlSource
import com.miruplay.tv.webcontrol.webControlMediaSourceIdFromEpisodeId
import com.miruplay.tv.webcontrol.webControlPlaybackStatus
import com.miruplay.tv.webcontrol.WebControlPlaybackCommandTarget
import com.miruplay.tv.webcontrol.webControlPlaybackCommandTarget
import java.io.File

internal class DesktopWebControlService(
    private val repositories: DesktopRepositories,
    private val mediaSourceFactory: MediaSourceFactory = DesktopMediaSourceFactory(),
    private val cloudDriveClient: CloudDriveClient = GrpcCloudDriveClient(),
    private val cloudRssEngine: DesktopCloudDriveRssAutomationEngine = DesktopCloudDriveRssAutomationEngine(
        repository = repositories.cloudDriveAutomation,
        credentials = repositories.credentials,
        cloudDriveClient = cloudDriveClient,
    ),
    private val playbackStatusProvider: suspend () -> PlaybackStatusDto = { idlePlaybackStatus() },
    private val playEpisodeHandler: suspend (PlayEpisodeRequest, Episode) -> PlaybackStatusDto = { _, _ ->
        idlePlaybackStatus()
    },
    private val playbackCommandHandler: suspend (PlaybackCommandRequest) -> PlaybackStatusDto = {
        idlePlaybackStatus()
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
        repositories.mediaSources.listWebControlSources()

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
        return mediaSourceFactory.testConnection(source).toWebControlSourceTestResponse()
    }

    override suspend fun scanSource(sourceId: Long): SourceScanResponse {
        return repositories.mediaSources.scanWebControlSource(sourceId) { source ->
            scanAndIndexDesktopSource(source, repositories.index, repositories.metadata)
                .map { it.toSourceScanResponse() }
        }
    }

    override suspend fun scanAllSources(): List<SourceScanResponse> {
        return repositories.mediaSources.scanAllWebControlSources { source ->
            scanAndIndexDesktopSource(source, repositories.index, repositories.metadata)
                .map { it.toSourceScanResponse() }
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

    private fun DesktopSourceScanResult.toSourceScanResponse(): SourceScanResponse =
        scanResult.toWebControlSourceScanResponse(sourceId)

    private suspend fun rescanLinkedCloudDriveSource(reason: String) {
        val config = repositories.cloudDriveAutomation.getConfig().getOrNull() ?: return
        val source = when (
            val selected = resolveCloudRssLinkedSource(
                sourceId = config.webDavSourceId,
                savedSources = emptyList(),
                mediaSources = repositories.mediaSources,
            )
        ) {
            is Result.Success -> when (val selection = selected.data) {
                DesktopCloudRssLinkedSourceSelection.MissingLink -> return
                is DesktopCloudRssLinkedSourceSelection.MissingSource -> return
                is DesktopCloudRssLinkedSourceSelection.Ready -> selection.sourceInfo
            }
            is Result.Error -> return
        }
        rescanCloudRssLinkedSource(source, reason, repositories.index, repositories.metadata)
    }

}

internal suspend fun desktopWebControlPlaybackStatus(
    player: com.miruplay.tv.player.mpv.MpvProcessPlayer?,
    session: com.miruplay.tv.model.PlaybackProgressSession?,
    mediaPath: String,
    launchStatus: String,
): PlaybackStatusDto {
    if (player == null || session == null) {
        return idlePlaybackStatus()
    }
    val observedPositionMs = player.queryTimePositionMs().getOrNull()
    if (observedPositionMs != null) {
        session.syncPosition(observedPositionMs)
    }
    val positionMs = observedPositionMs ?: session.currentPositionMs()
    val durationMs = player.queryDurationMs().getOrNull() ?: 0L
    val paused = player.queryPaused().getOrNull() == true
    val isActive = player.isActive()
    return webControlPlaybackStatus(
        state = when {
            paused && isActive -> "Paused"
            isActive -> "Playing"
            else -> "Idle"
        },
        uri = mediaPath.takeIf { it.isNotBlank() },
        mediaSourceId = session.episodeId.webControlMediaSourceIdFromEpisodeId(),
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isActive && !paused,
        error = launchStatus.takeIf { it.contains("failed", ignoreCase = true) },
    )
}

internal suspend fun desktopWebControlPlaybackCommand(
    request: PlaybackCommandRequest,
    player: com.miruplay.tv.player.mpv.MpvProcessPlayer?,
    session: com.miruplay.tv.model.PlaybackProgressSession?,
    stopPlayback: suspend () -> Unit,
    mediaPath: String = "",
    launchStatus: String = "",
): PlaybackStatusDto {
    val activePlayer = player ?: return idlePlaybackStatus()
    request.executeWebControlPlaybackCommand(
        desktopMpvWebControlPlaybackCommandTarget(
            player = activePlayer,
            session = session,
            stopPlayback = stopPlayback,
        )
    )
    return desktopWebControlPlaybackStatus(
        player = activePlayer,
        session = session,
        mediaPath = mediaPath,
        launchStatus = launchStatus,
    )
}

private fun desktopMpvWebControlPlaybackCommandTarget(
    player: com.miruplay.tv.player.mpv.MpvProcessPlayer,
    session: com.miruplay.tv.model.PlaybackProgressSession?,
    stopPlayback: suspend () -> Unit,
): WebControlPlaybackCommandTarget =
    webControlPlaybackCommandTarget(
        pause = {
            player.setPaused(true)
            session?.setPaused(true)
        },
        resume = {
            player.setPaused(false)
            session?.setPaused(false)
        },
        toggle = {
            player.togglePause()
            session?.togglePaused()
        },
        stop = { stopPlayback() },
        seekTo = { positionMs ->
            val observedPositionMs = player.queryTimePositionMs().getOrNull()
            if (observedPositionMs != null) {
                session?.syncPosition(observedPositionMs)
            }
            val currentMs = observedPositionMs ?: session?.currentPositionMs() ?: 0L
            player.seekBy((positionMs - currentMs) / MILLIS_PER_SECOND_DOUBLE)
            session?.syncPosition(positionMs)
        },
        setPlaybackSpeed = { speed -> player.setSpeed(speed.toDouble()) },
        currentPositionMs = {
            val observedPositionMs = player.queryTimePositionMs().getOrNull()
            if (observedPositionMs != null) {
                session?.syncPosition(observedPositionMs)
            }
            observedPositionMs ?: session?.currentPositionMs() ?: 0L
        },
        durationMs = { player.queryDurationMs().getOrNull() ?: 0L },
    )

private fun idlePlaybackStatus(): PlaybackStatusDto =
    idleWebControlPlaybackStatus()

private const val MILLIS_PER_SECOND_DOUBLE = 1_000.0
