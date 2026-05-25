package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.mediasource.desktop.DesktopMediaSourceFactory
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.webcontrol.PlayEpisodeRequest
import com.miruplay.tv.webcontrol.PlaybackCommandRequest
import com.miruplay.tv.webcontrol.PlaybackStatusDto
import com.miruplay.tv.webcontrol.executeWebControlPlaybackCommand
import com.miruplay.tv.webcontrol.idleWebControlPlaybackStatus
import com.miruplay.tv.webcontrol.WebControlPlaybackCommandTarget
import com.miruplay.tv.webcontrol.SharedWebControlEndpointService
import com.miruplay.tv.webcontrol.webControlMediaSourceIdFromEpisodeId
import com.miruplay.tv.webcontrol.webControlPlaybackStatus
import com.miruplay.tv.webcontrol.webControlPlaybackCommandTarget

internal class DesktopWebControlService(
    private val repositories: DesktopRepositories,
    mediaSourceFactory: MediaSourceFactory = DesktopMediaSourceFactory(),
    cloudDriveClient: CloudDriveClient = GrpcCloudDriveClient(),
    cloudRssEngine: DesktopCloudDriveRssAutomationEngine = DesktopCloudDriveRssAutomationEngine(
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
) : SharedWebControlEndpointService(
    mediaSourceRepository = repositories.mediaSources,
    metadataRepository = repositories.metadata,
    indexRepository = repositories.index,
    progressRepository = repositories.progress,
    scanPreferencesRepository = repositories.scanPreferences,
    mediaSourceFactory = mediaSourceFactory,
    cloudDriveRepository = repositories.cloudDriveAutomation,
    credentials = repositories.credentials,
    securePreferences = repositories.credentials,
    logUploadRepository = repositories.logUpload,
    cloudDriveClient = cloudDriveClient,
    cloudDriveActions = CloudDriveRssActionCoordinator(
        repository = repositories.cloudDriveAutomation,
        credentials = repositories.credentials,
        runner = cloudRssEngine,
    ),
    deviceNameProvider = { deviceName },
    clock = clock,
) {
    override suspend fun scanSourceResultFor(source: MediaSourceInfo): Result<ScanResult> =
        scanAndIndexDesktopSource(source, repositories.index, repositories.metadata)
            .map { result -> result.scanResult }

    override suspend fun afterCloudDriveAutomationRun(summary: CloudDriveRssRunSummary) {
        rescanLinkedCloudDriveSource(summary.completeStatus())
    }

    override suspend fun playEpisodeResolved(
        request: PlayEpisodeRequest,
        episode: Episode,
    ): PlaybackStatusDto {
        return playEpisodeHandler(request, episode)
    }

    override suspend fun playbackCommandResolved(request: PlaybackCommandRequest): PlaybackStatusDto =
        playbackCommandHandler(request)

    override suspend fun playbackStatusResolved(): PlaybackStatusDto =
        playbackStatusProvider()

    private suspend fun rescanLinkedCloudDriveSource(reason: String) {
        val config = repositories.cloudDriveAutomation.getConfig().getOrNull() ?: return
        resolveAndRescanCloudRssLinkedSource(
            sourceId = config.webDavSourceId,
            reason = reason,
            savedSources = emptyList(),
            mediaSources = repositories.mediaSources,
            indexRepository = repositories.index,
            metadataRepository = repositories.metadata,
        )
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
