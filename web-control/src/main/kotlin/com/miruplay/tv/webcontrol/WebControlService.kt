package com.miruplay.tv.webcontrol

import android.os.Build
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ScanResult
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
import com.miruplay.tv.scraper.core.BangumiArchiveSnapshot
import com.miruplay.tv.scraper.core.BangumiArchiveStore
import com.miruplay.tv.scraper.core.toBangumiHttpProxyConfig
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlService @Inject constructor(
    mediaRepository: MediaSourceRepository,
    metadataRepository: MetadataRepository,
    indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    scanPreferencesRepository: ScanPreferencesRepository,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    logUploadRepository: LogUploadRepository,
    securePreferences: AppCredentialStore,
    cloudDriveClient: CloudDriveClient,
    cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val cloudDriveScheduler: CloudDriveRssScheduler,
    private val scanCoordinator: ScanCoordinator,
    mediaSourceFactory: MediaSourceFactory,
    private val playbackController: PlaybackController,
    private val navigator: WebControlNavigator,
    private val bangumiArchiveStore: BangumiArchiveStore,
) : SharedWebControlEndpointService(
    mediaSourceRepository = mediaRepository,
    metadataRepository = metadataRepository,
    indexRepository = indexRepository,
    progressRepository = progressRepository,
    scanPreferencesRepository = scanPreferencesRepository,
    mediaSourceFactory = mediaSourceFactory,
    cloudDriveRepository = cloudDriveRepository,
    credentials = securePreferences,
    securePreferences = securePreferences,
    logUploadRepository = logUploadRepository,
    cloudDriveClient = cloudDriveClient,
    cloudDriveActions = CloudDriveRssActionCoordinator(
        repository = cloudDriveRepository,
        credentials = securePreferences,
        runner = cloudDriveEngine,
    ),
    deviceNameProvider = { Build.MODEL ?: "Android TV" },
) {
    private val bangumiArchiveDownloadLock = Any()
    private val bangumiArchiveDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var bangumiArchiveDownload: BangumiArchiveDownloadState = BangumiArchiveDownloadState()

    override suspend fun <T> runOnIo(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }

    override suspend fun scanSourceResultFor(source: MediaSourceInfo): Result<ScanResult> =
        scanCoordinator.scanSource(source.id)

    override suspend fun afterCloudDriveConfigSaved(config: com.miruplay.tv.model.CloudDriveAutomationConfig) {
        bangumiArchiveStore.configureProxy(config.toBangumiHttpProxyConfig())
        cloudDriveScheduler.syncPeriodicWork(config)
    }

    override suspend fun getBangumiArchive(): BangumiArchiveDto = runOnIo {
        bangumiArchiveStore.snapshot().toWebControlBangumiArchive(bangumiArchiveDownload)
    }

    override suspend fun downloadBangumiArchive(): BangumiArchiveDto {
        val shouldStart = synchronized(bangumiArchiveDownloadLock) {
            if (bangumiArchiveDownload.isDownloading) {
                false
            } else {
                bangumiArchiveDownload = BangumiArchiveDownloadState(isDownloading = true)
                true
            }
        }
        if (!shouldStart) {
            return getBangumiArchive()
        }

        val proxyConfig = cloudDriveRepository.getConfig().getOrNull()?.toBangumiHttpProxyConfig()
        bangumiArchiveDownloadScope.launch {
            proxyConfig?.let { bangumiArchiveStore.configureProxy(it) }
            val result = bangumiArchiveStore.downloadLatest { bytesRead, totalBytes ->
                synchronized(bangumiArchiveDownloadLock) {
                    bangumiArchiveDownload = BangumiArchiveDownloadState(
                        isDownloading = true,
                        downloadedBytes = bytesRead.coerceAtLeast(0L),
                        totalBytes = totalBytes.coerceAtLeast(0L),
                    )
                }
            }

            when (result) {
                is Result.Success -> {
                    synchronized(bangumiArchiveDownloadLock) {
                        bangumiArchiveDownload = BangumiArchiveDownloadState()
                    }
                }
                is Result.Error -> {
                    val message = result.error.toUserMessage()
                    synchronized(bangumiArchiveDownloadLock) {
                        bangumiArchiveDownload = BangumiArchiveDownloadState(lastError = message)
                    }
                }
            }
        }
        return getBangumiArchive()
    }

    override suspend fun playEpisodeResolved(
        request: PlayEpisodeRequest,
        episode: Episode,
    ): PlaybackStatusDto {
        val progress = progressRepository.getProgress(episode.id).getOrNull()
        navigator.openPlayer(
            request.toWebControlPlaybackSource(episode, progress).toWebPlaybackSource()
        )
        return playbackStatusResolved()
    }

    override suspend fun playbackCommandResolved(request: PlaybackCommandRequest): PlaybackStatusDto {
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
        return playbackStatusResolved()
    }

    override suspend fun playbackStatusResolved(): PlaybackStatusDto {
        val state = playbackController.state.value
        val currentPosition = runCatching { playbackController.getCurrentPosition() }.getOrDefault(0L)
        val duration = runCatching { playbackController.getDuration() }.getOrDefault(0L)
        return state.toWebControlPlaybackStatus(
            currentPositionMs = currentPosition,
            durationMs = duration,
        )
    }
}

private data class BangumiArchiveDownloadState(
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val lastError: String? = null,
)

private fun BangumiArchiveSnapshot.toWebControlBangumiArchive(
    state: BangumiArchiveDownloadState = BangumiArchiveDownloadState(),
): BangumiArchiveDto =
    BangumiArchiveDto(
        available = true,
        hasSubjectData = hasSubjectData,
        latestName = latest?.name,
        latestCreatedAt = latest?.createdAt,
        latestUpdatedAt = latest?.updatedAt,
        subjectFileSizeBytes = subjectFileSizeBytes,
        isDownloading = state.isDownloading,
        downloadedBytes = state.downloadedBytes,
        totalBytes = state.totalBytes,
        lastError = state.lastError,
    )
