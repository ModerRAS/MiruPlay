package com.miruplay.tv.webcontrol

import android.os.Build
import com.miruplay.tv.background.BackgroundTaskForegroundController
import com.miruplay.tv.background.BackgroundTaskIds
import com.miruplay.tv.background.BackgroundTaskProgress
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
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.scanner.LibraryScanStatus
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.scraper.core.BangumiArchiveSnapshot
import com.miruplay.tv.scraper.core.BangumiArchiveStore
import com.miruplay.tv.scraper.core.toBangumiHttpProxyConfig
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import kotlinx.coroutines.CancellationException
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
    private val scanPreferencesRepository: ScanPreferencesRepository,
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
    private val backgroundTasks: BackgroundTaskForegroundController,
    private val scanStatus: LibraryScanStatus,
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
        scanSourceWithSharedStatus(source)

    override suspend fun afterCloudDriveConfigSaved(config: com.miruplay.tv.model.CloudDriveAutomationConfig) {
        bangumiArchiveStore.configureProxy(config.toBangumiHttpProxyConfig())
        cloudDriveScheduler.syncPeriodicWork(config)
    }

    override suspend fun afterProxyConfigSaved(config: com.miruplay.tv.model.CloudDriveAutomationConfig) {
        bangumiArchiveStore.configureProxy(config.toBangumiHttpProxyConfig())
    }

    override suspend fun beforeCloudDriveAutomationRun() {
        backgroundTasks.start(
            taskId = WEB_CLOUD_DRIVE_TASK_ID,
            title = "CloudDrive/RSS 同步",
            text = "正在下载、整理并扫描订阅内容",
            progress = BackgroundTaskProgress.indeterminate(),
        )
    }

    override suspend fun afterCloudDriveAutomationRunFinished() {
        backgroundTasks.finish(WEB_CLOUD_DRIVE_TASK_ID)
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
            backgroundTasks.start(
                taskId = BackgroundTaskIds.BANGUMI_ARCHIVE,
                title = "Bangumi Archive 下载",
                text = "正在准备下载 Archive",
                progress = BackgroundTaskProgress.indeterminate(),
            )
            try {
                proxyConfig?.let { bangumiArchiveStore.configureProxy(it) }
                val result = bangumiArchiveStore.downloadLatest { bytesRead, totalBytes ->
                    val downloadedBytes = bytesRead.coerceAtLeast(0L)
                    val safeTotalBytes = totalBytes.coerceAtLeast(0L)
                    synchronized(bangumiArchiveDownloadLock) {
                        bangumiArchiveDownload = BangumiArchiveDownloadState(
                            isDownloading = true,
                            downloadedBytes = downloadedBytes,
                            totalBytes = safeTotalBytes,
                        )
                    }
                    backgroundTasks.update(
                        taskId = BackgroundTaskIds.BANGUMI_ARCHIVE,
                        title = "Bangumi Archive 下载",
                        text = downloadProgressText(downloadedBytes, safeTotalBytes),
                        progress = byteProgress(downloadedBytes, safeTotalBytes),
                    )
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
            } finally {
                backgroundTasks.finish(BackgroundTaskIds.BANGUMI_ARCHIVE)
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

    private suspend fun scanSourceWithSharedStatus(
        source: MediaSourceInfo,
    ): Result<ScanResult> {
        if (!scanStatus.tryStart(currentPath = source.name, canCancel = false)) {
            throw IllegalStateException("媒体库正在扫描，请稍后再试")
        }

        backgroundTasks.start(
            taskId = BackgroundTaskIds.LIBRARY_SCAN,
            title = "媒体库扫描",
            text = "正在扫描 ${source.name}",
            progress = BackgroundTaskProgress.indeterminate(),
        )
        scanCoordinator.setProgressCallback(ScanCoordinator.ScanProgressCallback { path, files, newEps ->
            val scanning = scanStatus.reportProgress(path, files, newEps)
            backgroundTasks.update(
                taskId = BackgroundTaskIds.LIBRARY_SCAN,
                title = "媒体库扫描",
                text = scanProgressText(scanning),
                progress = BackgroundTaskProgress.indeterminate(),
            )
        })

        return try {
            when (val result = scanCoordinator.scanSource(source.id)) {
                is Result.Success -> {
                    val scanning = scanStatus.completeSource(result.data)
                    backgroundTasks.update(
                        taskId = BackgroundTaskIds.LIBRARY_SCAN,
                        title = "媒体库扫描",
                        text = scanProgressText(scanning),
                        progress = BackgroundTaskProgress.indeterminate(),
                    )
                    scanPreferencesRepository.setLastScanAt(System.currentTimeMillis())
                    scanStatus.finish(listOf(result.data))
                    result
                }
                is Result.Error -> {
                    scanStatus.fail(result.error.toUserMessage())
                    result
                }
            }
        } catch (e: CancellationException) {
            scanStatus.cancel()
            throw e
        } catch (e: Exception) {
            scanStatus.fail(e.message ?: "扫描媒体源失败")
            throw e
        } finally {
            scanCoordinator.setProgressCallback(null)
            backgroundTasks.finish(BackgroundTaskIds.LIBRARY_SCAN)
        }
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

private fun downloadProgressText(downloadedBytes: Long, totalBytes: Long): String {
    val percent = progressPercent(downloadedBytes, totalBytes)
    return if (percent == null) {
        "已下载 ${formatBytes(downloadedBytes)}"
    } else {
        "已下载 ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)} ($percent%)"
    }
}

private fun byteProgress(downloadedBytes: Long, totalBytes: Long): BackgroundTaskProgress =
    progressPercent(downloadedBytes, totalBytes)?.let {
        BackgroundTaskProgress.determinate(current = it, max = 100)
    } ?: BackgroundTaskProgress.indeterminate()

private fun progressPercent(downloadedBytes: Long, totalBytes: Long): Int? =
    totalBytes.takeIf { it > 0L }?.let {
        ((downloadedBytes.coerceAtLeast(0L) * 100) / it).toInt().coerceIn(0, 100)
    }

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val mib = 1024L * 1024L
    return if (safeBytes >= mib) {
        "${safeBytes / mib} MB"
    } else {
        "${safeBytes / 1024L} KB"
    }
}

private fun scanProgressText(scanState: LibraryScanState.Scanning): String {
    val currentPath = scanState.currentPath.ifBlank { "媒体源" }
    return "正在处理：$currentPath，已发现 ${scanState.filesScanned} 个条目，新剧集 ${scanState.newEpisodes} 个"
}

private const val WEB_CLOUD_DRIVE_TASK_ID = "cloud-drive-rss-web"
