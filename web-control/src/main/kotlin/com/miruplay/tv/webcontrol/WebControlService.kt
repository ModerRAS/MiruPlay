package com.miruplay.tv.webcontrol

import android.content.Context
import android.os.Build
import com.miruplay.tv.background.BackgroundTaskForegroundController
import com.miruplay.tv.background.BackgroundTaskIds
import com.miruplay.tv.background.BackgroundTaskProgress
import com.miruplay.tv.background.ProgressUpdateThrottler
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.buildWebControlAccessUrls
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.buildToneMappingPreset
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.player.PlaybackDebugOverrides
import com.miruplay.tv.player.EmbeddedMpvDebugConfig
import com.miruplay.tv.player.LibVlcDebugConfig
import com.miruplay.tv.player.effectiveEmbeddedMpvHwdec
import com.miruplay.tv.player.effectiveEmbeddedMpvVideoOutput
import com.miruplay.tv.player.LibVlcHardwareAccelerationMode
import com.miruplay.tv.player.LibVlcVoutMode
import com.miruplay.tv.player.forcedVideoSignalDescriptorFor
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.AppModePreferencesRepository
import com.miruplay.tv.repository.AppUpdateInstallLaunch
import com.miruplay.tv.repository.AppUpdateRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.WebControlAccessManager
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
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlService @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    mediaRepository: MediaSourceRepository,
    metadataRepository: MetadataRepository,
    indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val scanPreferencesRepository: ScanPreferencesRepository,
    appModePreferences: AppModePreferencesRepository,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    logUploadRepository: LogUploadRepository,
    securePreferences: AppCredentialStore,
    cloudDriveClient: CloudDriveClient,
    cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val cloudDriveScheduler: CloudDriveRssScheduler,
    private val scanCoordinator: ScanCoordinator,
    mediaSourceFactory: MediaSourceFactory,
    private val playbackController: PlaybackController,
    private val playbackDebugOverrides: PlaybackDebugOverrides,
    private val navigator: WebControlNavigator,
    private val bangumiArchiveStore: BangumiArchiveStore,
    private val backgroundTasks: BackgroundTaskForegroundController,
    private val scanStatus: LibraryScanStatus,
    private val webControlAccessManager: WebControlAccessManager,
    private val appUpdateRepository: AppUpdateRepository,
) : SharedWebControlEndpointService(
    mediaSourceRepository = mediaRepository,
    metadataRepository = metadataRepository,
    indexRepository = indexRepository,
    progressRepository = progressRepository,
    scanPreferencesRepository = scanPreferencesRepository,
    appModePreferences = appModePreferences,
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

    override suspend fun downloadStartupDiagnostics(name: String): LocalLogDownload = runOnIo {
        val normalized = name.trim().lowercase()
        val fileName = when (normalized) {
            "probe" -> STARTUP_PROBE_FILE_NAME
            "diagnostics" -> STARTUP_DIAGNOSTICS_FILE_NAME
            else -> throw IllegalArgumentException("未知 startup 文件: $name")
        }
        val baseDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val target = java.io.File(java.io.File(baseDir, STARTUP_DIRECTORY_NAME), fileName)
        if (!target.exists()) {
            throw IllegalArgumentException("startup 文件不存在: $fileName")
        }
        LocalLogDownload(
            fileName = fileName,
            contentType = "application/x-ndjson; charset=utf-8",
            content = target.readBytes(),
        )
    }

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
            val progressThrottler = ProgressUpdateThrottler()
            var rawProgressCallbacks = 0L
            var emittedProgressUpdates = 0L
            backgroundTasks.start(
                taskId = BackgroundTaskIds.BANGUMI_ARCHIVE,
                title = "Bangumi Archive 下载",
                text = "正在准备下载 Archive",
                progress = BackgroundTaskProgress.indeterminate(),
            )
            MiruLog.i(
                tag = BANGUMI_ARCHIVE_LOG_TAG,
                message = "Bangumi Archive download started",
                attributes = mapOf(
                    "entrypoint" to "web_control",
                    "proxy_enabled" to (proxyConfig?.enabled == true).toString(),
                    "proxy_host_configured" to (proxyConfig?.host?.isNotBlank() == true).toString(),
                )
            )
            try {
                proxyConfig?.let { bangumiArchiveStore.configureProxy(it) }
                val result = bangumiArchiveStore.downloadLatest { bytesRead, totalBytes ->
                    rawProgressCallbacks += 1
                    val downloadedBytes = bytesRead.coerceAtLeast(0L)
                    val safeTotalBytes = totalBytes.coerceAtLeast(0L)
                    if (progressThrottler.shouldUpdate(downloadedBytes, safeTotalBytes)) {
                        emittedProgressUpdates += 1
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
                }

                when (result) {
                    is Result.Success -> {
                        MiruLog.i(
                            tag = BANGUMI_ARCHIVE_LOG_TAG,
                            message = "Bangumi Archive download finished",
                            attributes = mapOf(
                                "entrypoint" to "web_control",
                                "subject_file_size_bytes" to result.data.subjectFileSizeBytes.toString(),
                                "latest_name" to result.data.latest?.name.orEmpty(),
                                "raw_progress_callbacks" to rawProgressCallbacks.toString(),
                                "emitted_progress_updates" to emittedProgressUpdates.toString(),
                            )
                        )
                        synchronized(bangumiArchiveDownloadLock) {
                            bangumiArchiveDownload = BangumiArchiveDownloadState()
                        }
                    }
                    is Result.Error -> {
                        val message = result.error.toUserMessage()
                        MiruLog.w(
                            tag = BANGUMI_ARCHIVE_LOG_TAG,
                            message = "Bangumi Archive download failed",
                            attributes = mapOf(
                                "entrypoint" to "web_control",
                                "error" to message,
                                "raw_progress_callbacks" to rawProgressCallbacks.toString(),
                                "emitted_progress_updates" to emittedProgressUpdates.toString(),
                            )
                        )
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

    override suspend fun uploadBangumiArchive(
        input: InputStream,
        originalName: String,
        contentLength: Long,
    ): BangumiArchiveDto = runOnIo {
        val result = bangumiArchiveStore.importArchiveStream(
            input = input,
            originalName = originalName,
            contentLength = contentLength,
        )
        val uploadState = when (result) {
            is Result.Success -> BangumiArchiveDownloadState()
            is Result.Error -> BangumiArchiveDownloadState(lastError = result.error.toUserMessage())
        }
        synchronized(bangumiArchiveDownloadLock) {
            bangumiArchiveDownload = uploadState
        }
        bangumiArchiveStore.snapshot().toWebControlBangumiArchive(uploadState)
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

    override suspend fun getPlaybackClockSamples(limit: Int): PlaybackClockSamplesDto =
        PlaybackClockSamplesDto(
            activeBackend = playbackController.activeRenderBackend.value.name,
            requestedBackend = playbackController.requestedRenderBackend.value.name,
            currentSignalKind = playbackController.currentVideoSignalDescriptor.value?.signalKind?.name,
            currentRuleKey = playbackController.currentRenderRuleKey.value.name,
            samples = playbackController.recentPlaybackClockSamples(limit).map { sample ->
                PlaybackClockSampleDto(
                    monotonicTimestampMs = sample.monotonicTimestampMs,
                    positionMs = sample.positionMs,
                    durationMs = sample.durationMs,
                    paused = sample.paused,
                    eofReached = sample.eofReached,
                )
            },
        )

    override suspend fun getPlaybackNativeDiagnostics(logLimit: Int): PlaybackNativeDiagnosticsDto {
        val diagnostics = playbackController.currentMpvNativeDiagnostics(logLimit)
        return if (diagnostics == null) {
            PlaybackNativeDiagnosticsDto(
                activeBackend = playbackController.activeRenderBackend.value.name,
                requestedBackend = playbackController.requestedRenderBackend.value.name,
                available = false,
                notes = listOf("当前没有可用的 embedded mpv native 诊断快照"),
            )
        } else {
            PlaybackNativeDiagnosticsDto(
                activeBackend = playbackController.activeRenderBackend.value.name,
                requestedBackend = playbackController.requestedRenderBackend.value.name,
                available = true,
                collectedAtElapsedRealtimeMs = diagnostics.collectedAtElapsedRealtimeMs,
                surfaceAttached = diagnostics.surfaceAttached,
                pendingStartPositionMs = diagnostics.pendingStartPositionMs,
                properties = diagnostics.properties.map { property ->
                    PlaybackNativePropertyDto(
                        name = property.name,
                        value = property.value,
                    )
                },
                recentLogMessages = diagnostics.recentLogMessages.map { message ->
                    PlaybackNativeLogMessageDto(
                        observedAtElapsedRealtimeMs = message.observedAtElapsedRealtimeMs,
                        prefix = message.prefix,
                        level = message.level,
                        text = message.text,
                    )
                },
                notes = listOf("仅暴露 mpv 当前 scalar 属性和最近 native 日志，不是函数级 flame graph"),
            )
        }
    }

    override suspend fun capturePlaybackProfile(request: PlaybackProfileRequest): PlaybackProfileReportDto = runOnIo {
        if (!profileInProgress.compareAndSet(false, true)) {
            return@runOnIo PlaybackProfileReportDto(notes = listOf("已有采样进行中，请稍后再试"))
        }
        try {
            val durationMs = request.durationMs.coerceIn(500L, 20_000L)
            val intervalMs = request.intervalMs.coerceIn(5L, 250L)
            val maxStacks = request.maxStacks.coerceIn(10, 500)
            val includeNames = request.includeThreadNames.map(String::trim).filter(String::isNotEmpty)
            val excludeNames = request.excludeThreadNames.map(String::trim).filter(String::isNotEmpty)
            val stackCounts = linkedMapOf<String, Int>()
            val threadSamples = linkedMapOf<String, MutableList<PlaybackProfileObservedStack>>()
            val notes = mutableListOf("仅采集 Java 线程栈，native 线程内部不可见")
            val startTime = System.nanoTime()
            var passes = 0
            while (((System.nanoTime() - startTime) / 1_000_000L) < durationMs) {
                val snapshot = Thread.getAllStackTraces()
                snapshot.forEach { (thread, stack) ->
                    val name = thread.name.orEmpty()
                    if (includeNames.isNotEmpty() && includeNames.none { name.contains(it, ignoreCase = true) }) {
                        return@forEach
                    }
                    if (excludeNames.any { name.contains(it, ignoreCase = true) }) {
                        return@forEach
                    }
                    if (stack.isEmpty()) {
                        return@forEach
                    }
                    val collapsed = buildCollapsedStack(name, stack)
                    stackCounts[collapsed] = (stackCounts[collapsed] ?: 0) + 1
                    threadSamples.getOrPut(name) { mutableListOf() }.add(
                        PlaybackProfileObservedStack(
                            stack = stack,
                            threadState = thread.state.name,
                            nativeTopFrame = stack.firstOrNull()?.isNativeMethod == true,
                        )
                    )
                }
                passes += 1
                Thread.sleep(intervalMs)
            }
            val topStacks = stackCounts.entries
                .sortedByDescending { it.value }
                .take(maxStacks)
            val trimmedStackCount = (stackCounts.size - topStacks.size).coerceAtLeast(0)
            if (trimmedStackCount > 0) {
                notes += "trimmed $trimmedStackCount low-frequency stacks"
            }
            val collapsedText = topStacks.joinToString("\n") { "${it.key} ${it.value}" }
            val threadSummaries = threadSamples.entries
                .map { (threadName, stacks) ->
                    val grouped = stacks.groupingBy { sample -> buildCollapsedStack(threadName, sample.stack) }.eachCount()
                    val runnableSamples = stacks.count { it.threadState == java.lang.Thread.State.RUNNABLE.name }
                    val nativeTopFrameSamples = stacks.count { it.nativeTopFrame }
                    PlaybackProfileThreadDto(
                        threadName = threadName,
                        samples = stacks.size,
                        runnableSamples = runnableSamples,
                        nativeTopFrameSamples = nativeTopFrameSamples,
                        topStack = grouped.maxByOrNull { it.value }?.key.orEmpty(),
                    )
                }
                .sortedByDescending { it.samples }
                .take(40)
            if (topStacks.isEmpty()) {
                notes += "no Java stack samples captured"
            }
            PlaybackProfileReportDto(
                durationMs = durationMs,
                intervalMs = intervalMs,
                samplePasses = passes,
                sampledThreadCount = threadSamples.size,
                totalStackSamples = stackCounts.values.sum(),
                trimmedStackCount = trimmedStackCount,
                collapsedStacks = topStacks.map { PlaybackProfileStackDto(stack = it.key, samples = it.value) },
                collapsedText = collapsedText,
                threadSummaries = threadSummaries,
                notes = notes,
            )
        } finally {
            profileInProgress.set(false)
        }
    }

    override suspend fun getPlaybackDebugConfig(): PlaybackDebugConfigDto =
        playbackDebugConfigSnapshot(
            playbackPreferencesRepository = playbackPreferencesRepository,
            playbackController = playbackController,
            playbackDebugOverrides = playbackDebugOverrides,
        )

    override suspend fun savePlaybackDebugConfig(request: PlaybackDebugConfigRequest): PlaybackDebugConfigDto {
        val requestedDefaultBackend = requestedDefaultBackend(request.defaultBackend)
        if (requestedDefaultBackend != null) {
            val current = playbackPreferencesRepository.getFormatAwareToneMappingPreferences().normalized()
            playbackPreferencesRepository.setFormatAwareToneMappingPreferences(
                current.copy(defaultBackend = requestedDefaultBackend)
            )
        }

        if (request.requestedBackend != null) {
            val requestedBackend = if (isDebugClearValue(request.requestedBackend)) {
                null
            } else {
                playbackRenderBackendFromDebugValue(request.requestedBackend)
            }
            playbackController.setRequestedRenderBackend(requestedBackend)
        }

        if (request.forcedSignalKind != null) {
            if (isDebugClearValue(request.forcedSignalKind)) {
                playbackDebugOverrides.forcedVideoSignalDescriptor = null
            } else {
                videoSignalKindFromDebugValue(request.forcedSignalKind)
                    ?.let { kind -> playbackDebugOverrides.forcedVideoSignalDescriptor = forcedVideoSignalDescriptorFor(kind) }
            }
        }

        if (request.clearSessionToneMapping == true) {
            playbackController.clearSessionRuleOverrides()
        } else if (
            request.sessionToneMappingPreset != null ||
            request.sessionPeakDetectionStrategy != null ||
            request.sessionGamutMappingMode != null
        ) {
            val ruleKey = playbackController.currentRenderRuleKey.value
            val currentRule = playbackController.currentToneMappingRuleSet.value
            val preset = toneMappingPresetFromDebugValue(request.sessionToneMappingPreset)
            val peakDetection = peakDetectionStrategyFromDebugValue(request.sessionPeakDetectionStrategy)
            val gamutMappingMode = when {
                request.sessionGamutMappingMode == null -> currentRule.gamutMappingMode
                isDebugClearValue(request.sessionGamutMappingMode) -> null
                else -> gamutMappingModeFromDebugValue(request.sessionGamutMappingMode) ?: currentRule.gamutMappingMode
            }
            val baseRule = preset?.let { buildToneMappingPreset(ruleKey, it) } ?: currentRule
            playbackController.setSessionRuleOverride(
                ruleKey = ruleKey,
                ruleSet = baseRule.copy(
                    peakDetectionStrategy = peakDetection ?: baseRule.peakDetectionStrategy,
                    gamutMappingMode = gamutMappingMode,
                ),
            )
        }

        playbackDebugOverrides.embeddedMpvDebugConfig =
            updatedEmbeddedMpvDebugConfig(
                current = playbackDebugOverrides.embeddedMpvDebugConfig,
                request = request,
            )

        playbackDebugOverrides.libVlcDebugConfig =
            updatedLibVlcDebugConfig(
                current = playbackDebugOverrides.libVlcDebugConfig,
                request = request,
            )

        if (request.glFrameCaptureLabel != null) {
            playbackDebugOverrides.pendingGlFrameCaptureLabel = debugLabelValue(request.glFrameCaptureLabel)
        }
        if (request.libVlcNativeSnapshotLabel != null) {
            val label = debugLabelValue(request.libVlcNativeSnapshotLabel)
            if (label == null) {
                playbackDebugOverrides.pendingLibVlcNativeSnapshotLabel = null
            } else {
                playbackDebugOverrides.requestPendingLibVlcNativeSnapshotLabel(label)
            }
        }

        if (request.skipLibVlcStartupProbe != null) {
            playbackDebugOverrides.skipLibVlcStartupProbe = request.skipLibVlcStartupProbe == true
        }
        if (request.skipLibVlcStartupOptions != null) {
            playbackDebugOverrides.skipLibVlcStartupOptions = request.skipLibVlcStartupOptions == true
        }

        playbackController.refreshActivePlaybackDebugConfig()

        MiruLog.i(
            "WebControlService",
            "Applied playback debug config",
            mapOf(
                "default_backend" to (requestedDefaultBackend?.name ?: "unchanged"),
                "requested_backend" to (request.requestedBackend ?: "unchanged"),
                "forced_signal" to (request.forcedSignalKind ?: "unchanged"),
                "embedded_mpv_vo" to (playbackDebugOverrides.embeddedMpvDebugConfig.vo ?: "default"),
                "embedded_mpv_hwdec" to (playbackDebugOverrides.embeddedMpvDebugConfig.hwdec ?: "default"),
                "libvlc_hw_mode" to playbackDebugOverrides.libVlcDebugConfig.hwMode.name,
                "libvlc_vout_mode" to playbackDebugOverrides.libVlcDebugConfig.voutMode.name,
                "display_chroma_configured" to (playbackDebugOverrides.libVlcDebugConfig.displayChroma != null).toString(),
                "skip_startup_probe" to playbackDebugOverrides.skipLibVlcStartupProbe.toString(),
                "skip_startup_options" to playbackDebugOverrides.skipLibVlcStartupOptions.toString(),
            ),
        )
        return playbackDebugConfigSnapshot(
            playbackPreferencesRepository = playbackPreferencesRepository,
            playbackController = playbackController,
            playbackDebugOverrides = playbackDebugOverrides,
        )
    }

    override suspend fun getServerInfo(port: Int): ServerInfoDto {
        val base = super.getServerInfo(port)
        return base.copy(
            versionName = currentVersionName(),
            versionCode = currentVersionCode(),
            packageName = appContext.packageName,
        )
    }

    override suspend fun getPlaybackSettings(): PlaybackSettingsDto = runOnIo {
        val endAction = playbackPreferencesRepository.getEndAction()
        val toneMapping = playbackPreferencesRepository.getFormatAwareToneMappingPreferences().normalized()
        PlaybackSettingsDto(
            endAction = endAction.storageValue,
            formatAwareToneMapping = toneMapping,
        )
    }

    override suspend fun savePlaybackSettings(request: PlaybackSettingsRequest): PlaybackSettingsDto = runOnIo {
        request.endAction?.let { value ->
            playbackPreferencesRepository.setEndAction(PlaybackEndAction.fromStorageValue(value))
        }
        request.formatAwareToneMapping?.let { prefs ->
            playbackPreferencesRepository.setFormatAwareToneMappingPreferences(prefs.normalized())
        }
        getPlaybackSettings()
    }

    override suspend fun getWebControlAccess(): WebControlAccessDto = runOnIo {
        webControlAccessSnapshot()
    }

    override suspend fun saveWebControlAccess(request: WebControlAccessRequest): WebControlAccessDto = runOnIo {
        request.enabled?.let { webControlAccessManager.webControlEnabled = it }
        webControlAccessSnapshot()
    }

    override suspend fun rotateWebControlAccessToken(): WebControlAccessDto = runOnIo {
        webControlAccessManager.rotateAccessToken()
        webControlAccessSnapshot()
    }

    private fun webControlAccessSnapshot(): WebControlAccessDto {
        val enabled = webControlAccessManager.webControlEnabled
        val token = webControlAccessManager.accessToken
        return WebControlAccessDto(
            enabled = enabled,
            accessToken = token,
            urls = if (enabled) buildWebControlAccessUrls(token) else emptyList(),
        )
    }

    override suspend fun getAppUpdate(): AppUpdateDto = runOnIo {
        lastUpdateCheck ?: baseAppUpdateDto()
    }

    override suspend fun checkAppUpdate(): AppUpdateDto = runOnIo {
        val base = baseAppUpdateDto()
        when (val result = appUpdateRepository.checkLatestUpdate()) {
            is Result.Success -> {
                val check = result.data
                AppUpdateDto(
                    currentVersionName = check.currentVersionName,
                    currentVersionCode = check.currentVersionCode,
                    latest = check.latest.toDto(),
                    updateAvailable = check.updateAvailable,
                    lastCheckedAt = System.currentTimeMillis(),
                    lastError = null,
                    canRequestPackageInstalls = appUpdateRepository.canRequestPackageInstalls(),
                )
            }
            is Result.Error -> base.copy(
                lastCheckedAt = System.currentTimeMillis(),
                lastError = result.error.toUserMessage(),
            )
        }.also { lastUpdateCheck = it }
    }

    override suspend fun downloadAppUpdate(): AppUpdateDownloadResponse = runOnIo {
        val latest = (lastUpdateCheck ?: checkAppUpdate()).latest
            ?: return@runOnIo AppUpdateDownloadResponse(
                installLaunch = AppUpdateInstallLaunch.INSTALL_PERMISSION_REQUIRED.name,
                error = "未获取到可用更新，请先检查更新",
            )
        when (val result = appUpdateRepository.downloadAndLaunchInstaller(latest.toInfo()) { }) {
            is Result.Success -> AppUpdateDownloadResponse(installLaunch = result.data.name)
            is Result.Error -> AppUpdateDownloadResponse(
                installLaunch = AppUpdateInstallLaunch.INSTALL_PERMISSION_REQUIRED.name,
                error = result.error.toUserMessage(),
            )
        }
    }

    override suspend fun openInstallPermissionSettings(): AppUpdateDto = runOnIo {
        appUpdateRepository.openInstallPermissionSettings()
        lastUpdateCheck = (lastUpdateCheck ?: baseAppUpdateDto()).copy(
            canRequestPackageInstalls = appUpdateRepository.canRequestPackageInstalls(),
        )
        lastUpdateCheck!!
    }

    override suspend fun appControl(request: AppControlRequest): AppControlDto = runOnIo {
        when (request.action.trim().lowercase()) {
            "restart" -> {
                val accepted = navigator.requestAppRestart()
                AppControlDto(
                    action = "restart",
                    accepted = accepted,
                    message = if (accepted) "已请求重启应用" else "应用当前无法接收重启请求",
                )
            }
            "exit" -> {
                val accepted = navigator.requestAppExit()
                AppControlDto(
                    action = "exit",
                    accepted = accepted,
                    message = if (accepted) "已请求退出应用" else "应用当前无法接收退出请求",
                )
            }
            else -> AppControlDto(
                action = request.action,
                accepted = false,
                message = "不支持的应用控制动作: ${request.action}",
            )
        }
    }

    @Volatile
    private var lastUpdateCheck: AppUpdateDto? = null

    // ponytail: 采样最多 20s，互斥防并发请求耗尽 NanoHTTPD 线程池。
    private val profileInProgress = AtomicBoolean(false)

    private fun baseAppUpdateDto(): AppUpdateDto =
        AppUpdateDto(
            currentVersionName = currentVersionName(),
            currentVersionCode = currentVersionCode(),
            canRequestPackageInstalls = appUpdateRepository.canRequestPackageInstalls(),
        )

    private fun currentVersionName(): String =
        runCatching { packageInfo().versionName.orEmpty() }.getOrDefault("")

    private fun currentVersionCode(): Long =
        runCatching {
            val info = packageInfo()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        }.getOrDefault(0L)

    private fun packageInfo(): android.content.pm.PackageInfo =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)

    private fun com.miruplay.tv.repository.AppUpdateInfo.toDto(): AppUpdateInfoDto =
        AppUpdateInfoDto(
            versionName = versionName,
            versionCode = versionCode,
            releaseName = releaseName,
            tagName = tagName,
            publishedAt = publishedAt,
            releaseUrl = releaseUrl,
            assetName = assetName,
            assetSizeBytes = assetSizeBytes,
            downloadUrl = downloadUrl,
        )

    private fun AppUpdateInfoDto.toInfo(): com.miruplay.tv.repository.AppUpdateInfo =
        com.miruplay.tv.repository.AppUpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            releaseName = releaseName,
            tagName = tagName,
            publishedAt = publishedAt,
            releaseUrl = releaseUrl,
            assetName = assetName,
            assetSizeBytes = assetSizeBytes,
            downloadUrl = downloadUrl,
        )

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

private suspend fun playbackDebugConfigSnapshot(
    playbackPreferencesRepository: PlaybackPreferencesRepository,
    playbackController: PlaybackController,
    playbackDebugOverrides: PlaybackDebugOverrides,
): PlaybackDebugConfigDto {
    val preferences = playbackPreferencesRepository.getFormatAwareToneMappingPreferences().normalized()
    val debugConfig = playbackDebugOverrides.libVlcDebugConfig
    val embeddedMpvDebugConfig = playbackDebugOverrides.embeddedMpvDebugConfig
    val forcedSignal = playbackDebugOverrides.forcedVideoSignalDescriptor
    val currentSignal = playbackController.currentVideoSignalDescriptor.value
    val currentToneMapping = playbackController.currentToneMappingRuleSet.value
    val effectiveEmbeddedMpvVo = effectiveEmbeddedMpvVideoOutput(
        debugConfig = embeddedMpvDebugConfig,
    )
    val effectiveEmbeddedMpvHwdec = effectiveEmbeddedMpvHwdec(
        debugConfig = embeddedMpvDebugConfig,
    )
    return PlaybackDebugConfigDto(
        defaultBackend = preferences.defaultBackend.name,
        requestedBackend = playbackController.requestedRenderBackend.value.name,
        activeBackend = playbackController.activeRenderBackend.value.name,
        forcedSignalKind = forcedSignal?.signalKind?.name,
        currentSignalKind = currentSignal?.signalKind?.name,
        currentSignalLabel = currentSignal?.displayLabel().orEmpty(),
        currentRuleKey = playbackController.currentRenderRuleKey.value.name,
        currentToneMapping = PlaybackDebugCurrentToneMappingDto(
            enabled = currentToneMapping.enabled,
            curvePreset = currentToneMapping.curvePreset.name,
            peakDetectionStrategy = currentToneMapping.peakDetectionStrategy.name,
            gamutMappingMode = currentToneMapping.gamutMappingMode,
            targetSdrNits = currentToneMapping.targetSdrNits,
            contrastRecovery = currentToneMapping.contrastRecovery,
            saturationRecovery = currentToneMapping.saturationRecovery,
            highlightCompression = currentToneMapping.highlightCompression,
        ),
        fallbackReason = playbackController.fallbackReason.value,
        embeddedMpvVo = embeddedMpvDebugConfig.vo,
        embeddedMpvHwdec = embeddedMpvDebugConfig.hwdec,
        effectiveEmbeddedMpvVo = effectiveEmbeddedMpvVo,
        effectiveEmbeddedMpvHwdec = effectiveEmbeddedMpvHwdec,
        libVlcHardwareMode = debugConfig.hwMode.name,
        libVlcVoutMode = debugConfig.voutMode.name,
        libVlcDisplayChroma = debugConfig.displayChroma,
        pendingGlFrameCaptureLabel = playbackDebugOverrides.peekPendingGlFrameCaptureLabel(),
        pendingLibVlcNativeSnapshotLabel = playbackDebugOverrides.peekPendingLibVlcNativeSnapshotLabel(),
        skipLibVlcStartupProbe = playbackDebugOverrides.skipLibVlcStartupProbe,
        skipLibVlcStartupOptions = playbackDebugOverrides.skipLibVlcStartupOptions,
    )
}

private fun requestedDefaultBackend(value: String?): PlaybackRenderBackend? =
    when {
        value == null -> null
        isDebugClearValue(value) -> PlaybackRenderBackend.STANDARD_EXO
        else -> playbackRenderBackendFromDebugValue(value)
    }

private fun updatedEmbeddedMpvDebugConfig(
    current: EmbeddedMpvDebugConfig,
    request: PlaybackDebugConfigRequest,
): EmbeddedMpvDebugConfig {
    val vo = when {
        request.embeddedMpvVo == null -> current.vo
        isDebugClearValue(request.embeddedMpvVo) -> null
        else -> embeddedMpvVoFromDebugValue(request.embeddedMpvVo) ?: current.vo
    }
    val hwdec = when {
        request.embeddedMpvHwdec == null -> current.hwdec
        isDebugClearValue(request.embeddedMpvHwdec) -> null
        else -> embeddedMpvHwdecFromDebugValue(request.embeddedMpvHwdec) ?: current.hwdec
    }
    return current.copy(
        vo = vo,
        hwdec = hwdec,
    )
}

private fun updatedLibVlcDebugConfig(
    current: LibVlcDebugConfig,
    request: PlaybackDebugConfigRequest,
): LibVlcDebugConfig {
    val hwMode = when {
        request.libVlcHardwareMode == null -> current.hwMode
        isDebugClearValue(request.libVlcHardwareMode) -> LibVlcHardwareAccelerationMode.FULL
        else -> libVlcHardwareModeFromDebugValue(request.libVlcHardwareMode) ?: current.hwMode
    }
    val voutMode = when {
        request.libVlcVoutMode == null -> current.voutMode
        isDebugClearValue(request.libVlcVoutMode) -> LibVlcVoutMode.DEFAULT
        else -> libVlcVoutModeFromDebugValue(request.libVlcVoutMode) ?: current.voutMode
    }
    val displayChroma = when {
        request.libVlcDisplayChroma == null -> current.displayChroma
        isDebugClearValue(request.libVlcDisplayChroma) -> null
        else -> libVlcDisplayChromaFromDebugValue(request.libVlcDisplayChroma) ?: current.displayChroma
    }
    return current.copy(
        hwMode = hwMode,
        voutMode = voutMode,
        displayChroma = displayChroma,
    )
}

private data class PlaybackProfileObservedStack(
    val stack: Array<StackTraceElement>,
    val threadState: String,
    val nativeTopFrame: Boolean,
)

private fun buildCollapsedStack(
    threadName: String,
    stack: Array<StackTraceElement>,
): String =
    (sequenceOf("thread:$threadName") + stack.asSequence().map { frame ->
        "${frame.className}.${frame.methodName}"
    }).joinToString(";")

private const val WEB_CLOUD_DRIVE_TASK_ID = "cloud-drive-rss-web"
private const val BANGUMI_ARCHIVE_LOG_TAG = "BangumiArchiveDownload"
private const val STARTUP_DIRECTORY_NAME = "MiruPlay"
private const val STARTUP_PROBE_FILE_NAME = "miruplay-startup-probe.jsonl"
private const val STARTUP_DIAGNOSTICS_FILE_NAME = "miruplay-startup-diagnostics.jsonl"
