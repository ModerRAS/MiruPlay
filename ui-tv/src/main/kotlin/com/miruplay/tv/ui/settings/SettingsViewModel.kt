package com.miruplay.tv.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.audiomeasure.AudioMeasureController
import com.miruplay.tv.background.BackgroundTaskForegroundController
import com.miruplay.tv.background.BackgroundTaskIds
import com.miruplay.tv.background.BackgroundTaskProgress
import com.miruplay.tv.background.ProgressUpdateThrottler
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.data.preferences.PlaybackPreferencesManager
import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.MusicSrcBypassMode
import com.miruplay.tv.measure.AutoEqFitter
import com.miruplay.tv.measure.MicCalibration
import com.miruplay.tv.measure.MeasuredPresetFactory
import com.miruplay.tv.measure.RoomMeasurer
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.buildToneMappingPreset
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.directoryBrowserRootDisplayName
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.CloudDriveApiTokenFormResult
import com.miruplay.tv.model.CloudDriveDirectoryPickerFormResult
import com.miruplay.tv.model.CloudDriveLoginFormResult
import com.miruplay.tv.model.RssSubscriptionFormResult
import com.miruplay.tv.model.cloudDriveLoginSucceededStatus
import com.miruplay.tv.model.cloudDriveTokenLoginRequiredStatus
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.cloudDriveTokenVerifiedStatus
import com.miruplay.tv.model.cloudRssConfigSavedStatus
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.model.prepareRssSubscriptionForm
import com.miruplay.tv.model.saveBangumiTokenFormResult
import com.miruplay.tv.model.saveTmdbTokenFormResult
import com.miruplay.tv.model.withAutomationFormValues
import com.miruplay.tv.model.validateCloudDriveDirectoryPickerForm
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppUpdateCheck
import com.miruplay.tv.repository.AppUpdateDownloadProgress
import com.miruplay.tv.repository.AppUpdateInfo
import com.miruplay.tv.repository.AppUpdateInstallLaunch
import com.miruplay.tv.repository.AppUpdateRepository
import com.miruplay.tv.repository.AppUpdateChannelStore
import com.miruplay.tv.repository.UpdateChannel
import com.miruplay.tv.repository.AppModePreferencesRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadActionCoordinator
import com.miruplay.tv.repository.LogUploadAutoScheduler
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.OtlpLogUploadActionSnapshot
import com.miruplay.tv.repository.toConfig
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.repository.withRuntimeStatus
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
import com.miruplay.tv.sync.rss.loadCloudDriveDirectory
import com.miruplay.tv.sync.rss.loadingFor
import com.miruplay.tv.sync.rss.prepareCloudDriveDirectoryBrowser
import com.miruplay.tv.sync.BangumiSyncEngine
import com.miruplay.tv.model.rssSubscriptionDeletedStatus
import com.miruplay.tv.model.rssSubscriptionSavedStatus
import com.miruplay.tv.model.settingsAppUpdateCheckingStatus
import com.miruplay.tv.model.settingsAppUpdateDownloadProgressStatus
import com.miruplay.tv.model.settingsAppUpdateIdleStatus
import com.miruplay.tv.model.settingsAppUpdateInstallPermissionGrantedStatus
import com.miruplay.tv.model.settingsAppUpdateInstallPermissionStatus
import com.miruplay.tv.model.settingsAppUpdateInstallerOpenedStatus
import com.miruplay.tv.model.settingsAppUpdateLatestStatus
import com.miruplay.tv.model.settingsAppUpdateReadyStatus
import com.miruplay.tv.model.settingsAndroidTvLogUploadStatusMessage
import com.miruplay.tv.model.settingsLogUploadStatusMessage
import com.miruplay.tv.model.settingsProxySavedStatus
import com.miruplay.tv.model.validateCloudDriveApiTokenForm
import com.miruplay.tv.model.validateCloudDriveLoginForm
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.miruplay.tv.player.AudioDspRuntimeConfig
import com.miruplay.tv.scraper.core.BangumiArchiveSnapshot
import com.miruplay.tv.scraper.core.BangumiArchiveStore
import com.miruplay.tv.scraper.core.toBangumiHttpProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val securePrefs: AppCredentialStore,
    private val appModePreferences: AppModePreferencesRepository,
    private val scanPreferences: ScanPreferencesManager,
    private val playbackPreferences: PlaybackPreferencesManager,
    private val webControlPreferences: WebControlAccessManager,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val logUploadRepository: LogUploadRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val appUpdateChannelStore: AppUpdateChannelStore,
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val cloudDriveScheduler: CloudDriveRssScheduler,
    private val bangumiArchiveStore: BangumiArchiveStore,
    private val backgroundTasks: BackgroundTaskForegroundController,
    private val audioDspRuntimeConfig: AudioDspRuntimeConfig,
    private val bangumiSyncEngine: BangumiSyncEngine,
    @ApplicationContext private val appContext: Context,
    private val audioMeasureController: AudioMeasureController,
    private val toppingController: com.miruplay.tv.topping.ToppingController,
) : ViewModel() {

    private val logUploadActions = LogUploadActionCoordinator(logUploadRepository)
    private val logUploadAutoScheduler = LogUploadAutoScheduler(
        repository = logUploadRepository,
        scope = viewModelScope,
    )
    private var logUploadConfigObserverJob: Job? = null

    private val _sources = MutableStateFlow<List<MediaSourceInfo>>(emptyList())
    val sources: StateFlow<List<MediaSourceInfo>> = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val testResult: StateFlow<ConnectionTestResult?> = _testResult.asStateFlow()

    private val _bangumiToken = MutableStateFlow(securePrefs.bangumiAccessToken ?: "")
    val bangumiToken: StateFlow<String> = _bangumiToken.asStateFlow()

    private val _tmdbToken = MutableStateFlow(securePrefs.tmdbAccessToken ?: "")
    val tmdbToken: StateFlow<String> = _tmdbToken.asStateFlow()

    private val _autoScanEnabled = MutableStateFlow(scanPreferences.autoScanEnabled)
    val autoScanEnabled: StateFlow<Boolean> = _autoScanEnabled.asStateFlow()

    private val _autoScanIntervalHours = MutableStateFlow(
        (scanPreferences.autoScanIntervalMs / MILLIS_PER_HOUR).toInt()
    )
    val autoScanIntervalHours: StateFlow<Int> = _autoScanIntervalHours.asStateFlow()

    private val _lastScanAt = MutableStateFlow(scanPreferences.lastScanAt)
    val lastScanAt: StateFlow<Long> = _lastScanAt.asStateFlow()

    private val _mergeSameAnimeEnabled = MutableStateFlow(scanPreferences.mergeSameAnimeEnabled)
    val mergeSameAnimeEnabled: StateFlow<Boolean> = _mergeSameAnimeEnabled.asStateFlow()

    private val _posterWallArrangement = MutableStateFlow(scanPreferences.posterWallArrangement)
    val posterWallArrangement: StateFlow<PosterWallArrangement> = _posterWallArrangement.asStateFlow()

    private val _currentAppMode = MutableStateFlow(AppMode.ANIME)
    val currentAppMode: StateFlow<AppMode> = _currentAppMode.asStateFlow()

    private val _playbackEndAction = MutableStateFlow(playbackPreferences.endAction)
    val playbackEndAction: StateFlow<PlaybackEndAction> = _playbackEndAction.asStateFlow()
    private val _episodeVersionSelectionPolicy = MutableStateFlow(playbackPreferences.episodeVersionSelectionPolicy)
    val episodeVersionSelectionPolicy: StateFlow<EpisodeVersionSelectionPolicy> =
        _episodeVersionSelectionPolicy.asStateFlow()
    private val _preferredSubtitleLanguage = MutableStateFlow(playbackPreferences.preferredSubtitleLanguage)
    val preferredSubtitleLanguage: StateFlow<SubtitleLanguagePreference> = _preferredSubtitleLanguage.asStateFlow()
    private val _subtitleBackgroundTransparent = MutableStateFlow(playbackPreferences.subtitleBackgroundTransparent)
    val subtitleBackgroundTransparent: StateFlow<Boolean> = _subtitleBackgroundTransparent.asStateFlow()
    private val _formatAwareToneMappingPreferences = MutableStateFlow(
        playbackPreferences.formatAwareToneMappingPreferences.normalized()
    )
    val formatAwareToneMappingPreferences: StateFlow<FormatAwareToneMappingPreferences> =
        _formatAwareToneMappingPreferences.asStateFlow()

    private val _audioDspConfig = MutableStateFlow(
        runCatching { playbackPreferences.audioDspConfig.normalized() }
            .getOrDefault(AudioDspConfig.neutral())
    )
    val audioDspConfig: StateFlow<AudioDspConfig> = _audioDspConfig.asStateFlow()

    data class AudioMeasureResultUi(
        val valid: Boolean,
        val invalidReason: String?,
        val estimatedPpm: Double,
        val bands: List<AudioDspBand>,
        val matchLoHz: Double,
        val matchHiHz: Double,
        val peakAfterDb: Double,
        val nullResidualDb: Double,
        /** Log-freq curve points for the wizard's response chart (same length). */
        val freqs: DoubleArray = DoubleArray(0),
        val smoothedDb: DoubleArray = DoubleArray(0),
        val targetDb: DoubleArray = DoubleArray(0),
    )

    data class AudioMeasureUiState(
        val capabilities: AudioMeasureController.Capabilities? = null,
        /** User-picked input device; persisted across restarts. null = controller default. */
        val selectedMicId: Int? = null,
        val measuring: Boolean = false,
        val progress: String? = null,
        val error: String? = null,
        val result: AudioMeasureResultUi? = null,
        val calibrationName: String? = null,
        val calibrationWarning: String? = null,
        val downloadingCalibration: Boolean = false,
        val calibrationCount: Int = 0,
        val outputDeviceName: String? = null,
        val noisePlaying: Boolean = false,
    )

    private val _audioMeasure = MutableStateFlow(AudioMeasureUiState())
    val audioMeasure: StateFlow<AudioMeasureUiState> = _audioMeasure.asStateFlow()

    data class ToppingUiState(
        val status: com.miruplay.tv.topping.ToppingController.Status? = null,
        val busy: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    private val _topping = MutableStateFlow(ToppingUiState())
    val topping: StateFlow<ToppingUiState> = _topping.asStateFlow()

    init {
        refreshAudioMeasureCalibration()
        refreshTopping()
    }

    fun refreshTopping() {
        viewModelScope.launch {
            _topping.update { it.copy(status = toppingController.status()) }
        }
    }

    private suspend fun toppingCommand(message: String, block: suspend () -> com.miruplay.tv.topping.ToppingController.Status) {
        if (_topping.value.busy) return
        _topping.update { it.copy(busy = true, error = null) }
        try {
            val status = block()
            _topping.update { it.copy(busy = false, status = status, message = message) }
        } catch (e: Exception) {
            _topping.update {
                it.copy(busy = false, error = e.message ?: "操作失败", status = toppingController.status())
            }
        }
    }

    fun setToppingVolume(db: Double, confirmed: Boolean = false) {
        viewModelScope.launch {
            toppingCommand("音量已设为 ${db} dB") { toppingController.setVolume(db, confirmed) }
        }
    }

    fun pushToppingPreset(presetId: String) {
        viewModelScope.launch {
            toppingCommand("预设已推送到 Topping DAC") {
                val config = runCatching { playbackPreferences.audioDspConfig.normalized() }
                    .getOrDefault(AudioDspConfig.neutral())
                val preset = config.presets.firstOrNull { it.id == presetId }
                    ?: throw IllegalStateException("预设不存在：" + presetId)
                toppingController.applyPreset(preset).let { toppingController.status() }
            }
        }
    }

    fun flatTopping() {
        viewModelScope.launch { toppingCommand("已关闭全部 PEQ 频段") { toppingController.flat() } }
    }

    fun setToppingGain(on: Boolean) {
        viewModelScope.launch { toppingCommand(if (on) "增益已开启" else "增益已关闭") { toppingController.setGain(on) } }
    }

    fun setToppingPower(on: Boolean) {
        viewModelScope.launch { toppingCommand(if (on) "设备已唤醒" else "设备已休眠") { toppingController.setPower(on) } }
    }

    fun setToppingPreamp(db: Double) {
        viewModelScope.launch { toppingCommand("Preamp 已设为 ${db} dB") { toppingController.setPreamp(db) } }
    }

    fun requestToppingUsbPermission() {
        viewModelScope.launch {
            toppingCommand("USB 权限已授予") { toppingController.requestUsbPermission(); toppingController.status() }
        }
    }

    private fun refreshAudioMeasureCalibration() {
        viewModelScope.launch {
            val state = runCatching {
                val all = playbackPreferences.getAudioMeasureCalibrations()
                val activeId = playbackPreferences.getAudioMeasureCalibrationActiveId()
                val active = all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
                Triple(active?.name, all.size, active?.let {
                    runCatching { MicCalibration.parse(it.data).calibration.coverageWarning() }.getOrNull()
                })
            }.getOrNull()
            _audioMeasure.update {
                it.copy(
                    calibrationName = state?.first,
                    calibrationCount = state?.second ?: 0,
                    calibrationWarning = state?.third,
                )
            }
        }
    }


    fun downloadCalibration(serial: String, incidence: String) {
        if (_audioMeasure.value.downloadingCalibration) return
        viewModelScope.launch {
            _audioMeasure.update { it.copy(downloadingCalibration = true, error = null) }
            try {
                val inc = if (incidence == "90deg") com.miruplay.tv.measure.MicCalibration.Incidence.NINETY_DEG
                else com.miruplay.tv.measure.MicCalibration.Incidence.ZERO_DEG
                val key = com.miruplay.tv.audiomeasure.UmikCalibrationDownloader.sourceKey(serial, inc)
                val existing = runCatching { playbackPreferences.getAudioMeasureCalibrations() }.getOrDefault(emptyList())
                // Dedupe: same serial+incidence reuses the saved file, no re-download.
                val settings = existing.firstOrNull { it.source == key }
                    ?: audioMeasureController.downloadUmikCalibration(serial, inc)
                val updated = (existing.filterNot { it.id == settings.id } + settings).sortedBy { it.id }
                playbackPreferences.saveAudioMeasureCalibrations(updated, settings.id)
                val warning = runCatching { MicCalibration.parse(settings.data).calibration.coverageWarning() }.getOrNull()
                _audioMeasure.update {
                    it.copy(
                        downloadingCalibration = false,
                        calibrationName = settings.name,
                        calibrationCount = updated.size,
                        calibrationWarning = warning,
                    )
                }
            } catch (e: Exception) {
                _audioMeasure.update { it.copy(downloadingCalibration = false, error = e.message ?: "下载校准失败") }
            }
        }
    }

    fun probeAudioMeasure() {
        viewModelScope.launch {
            val output = runCatching { audioMeasureController.describeDefaultOutput() }.getOrNull()
            _audioMeasure.update {
                it.copy(capabilities = audioMeasureController.probe(), outputDeviceName = output)
            }
        }
    }

    /** Persist the user's mic choice; re-probe so the UI reflects it immediately. */
    fun selectAudioMeasureMic(micId: Int) {
        playbackPreferences.audioMeasureMicId = micId
        _audioMeasure.update { it.copy(selectedMicId = micId) }
        probeAudioMeasure()
    }


    private suspend fun activeCalibrationText(): String? {
        val all = runCatching { playbackPreferences.getAudioMeasureCalibrations() }.getOrDefault(emptyList())
        val activeId = runCatching { playbackPreferences.getAudioMeasureCalibrationActiveId() }.getOrNull()
        return (all.firstOrNull { it.id == activeId } ?: all.firstOrNull())?.data
    }

    fun startSweepMeasurement() {
        if (_audioMeasure.value.measuring) return
        setPinkNoise(false)
        viewModelScope.launch {
            _audioMeasure.update { it.copy(measuring = true, progress = "准备中…", error = null, result = null) }
            try {
                val outcome = audioMeasureController.measureRoom(
                    calibrationText = activeCalibrationText(),
                    preferredMicId = _audioMeasure.value.selectedMicId,
                ) { progress ->
                    _audioMeasure.update { it.copy(progress = progress) }
                }
                _audioMeasure.update {
                    it.copy(
                        measuring = false,
                        progress = null,
                        capabilities = outcome.capabilities,
                        result = outcome.toResultUi(),
                    )
                }
            } catch (e: Exception) {
                _audioMeasure.update { it.copy(measuring = false, progress = null, error = e.message ?: "测量失败") }
            }
        }
    }

    fun importWavMeasurement(uri: Uri) {
        if (_audioMeasure.value.measuring) return
        viewModelScope.launch {
            _audioMeasure.update { it.copy(measuring = true, progress = "读取 WAV…", error = null, result = null) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取所选文件")
                }
                val outcome = audioMeasureController.importWav(bytes, activeCalibrationText()) { progress ->
                    _audioMeasure.update { it.copy(progress = progress) }
                }
                _audioMeasure.update {
                    it.copy(measuring = false, progress = null, result = outcome.toResultUi())
                }
            } catch (e: Exception) {
                _audioMeasure.update { it.copy(measuring = false, progress = null, error = e.message ?: "WAV 导入失败") }
            }
        }
    }

    fun dismissAudioMeasureError() {
        _audioMeasure.update { it.copy(error = null) }
    }

    fun setPinkNoise(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val playing = if (on) audioMeasureController.startPinkNoise() else {
                audioMeasureController.stopPinkNoise()
                false
            }
            _audioMeasure.update { it.copy(noisePlaying = playing) }
        }
    }

    fun clearAudioMeasureResult() {
        _audioMeasure.update { it.copy(result = null) }
    }

    fun applyMeasuredResult(target: AudioDspChannelTarget) {
        val result = _audioMeasure.value.result ?: return
        if (!result.valid) return
        val updated = MeasuredPresetFactory.applyToConfig(
            _audioDspConfig.value,
            MeasuredPresetFactory.MeasuredBands(result.bands, valid = true),
            timestampMs = System.currentTimeMillis(),
            target = target,
        ).normalized()
        playbackPreferences.audioDspConfig = updated
        audioDspRuntimeConfig.update(updated)
        _audioDspConfig.value = updated
        _audioMeasure.update { it.copy(result = null) }
    }

    private fun AudioMeasureController.MeasureOutcome.toResultUi(): AudioMeasureResultUi {
        val m = measurement
        val (peakAfter, nullResidual) = AutoEqFitter.finalMetrics(
            m.smoothedDb, m.targetDb, m.fit.bands, m.freqs, m.fs, m.fit.matchLoHz, m.fit.matchHiHz,
        )
        return AudioMeasureResultUi(
            valid = m.valid,
            invalidReason = m.invalidReason,
            estimatedPpm = m.drift.estimatedPpm,
            bands = m.fit.bands,
            matchLoHz = m.fit.matchLoHz,
            matchHiHz = m.fit.matchHiHz,
            peakAfterDb = peakAfter,
            nullResidualDb = nullResidual,
            freqs = m.freqs,
            smoothedDb = m.smoothedDb,
            targetDb = m.targetDb,
        )
    }

    private val _musicSrcBypassMode = MutableStateFlow(playbackPreferences.musicSrcBypassMode)
    val musicSrcBypassMode: StateFlow<MusicSrcBypassMode> = _musicSrcBypassMode.asStateFlow()

    private val _webUiUrls = MutableStateFlow<List<String>>(emptyList())
    val webUiUrls: StateFlow<List<String>> = _webUiUrls.asStateFlow()

    private val _webControlEnabled = MutableStateFlow(webControlPreferences.webControlEnabled)
    val webControlEnabled: StateFlow<Boolean> = _webControlEnabled.asStateFlow()

    private val _webControlAccessToken = MutableStateFlow(webControlPreferences.accessToken)
    val webControlAccessToken: StateFlow<String> = _webControlAccessToken.asStateFlow()

    private val _cloudDriveConfig = MutableStateFlow(CloudDriveAutomationConfig())
    val cloudDriveConfig: StateFlow<CloudDriveAutomationConfig> = _cloudDriveConfig.asStateFlow()

    private val _rssSubscriptions = MutableStateFlow<List<RssSubscriptionInfo>>(emptyList())
    val rssSubscriptions: StateFlow<List<RssSubscriptionInfo>> = _rssSubscriptions.asStateFlow()

    private val _cloudDriveTokenConfigured = MutableStateFlow(!securePrefs.cloudDriveToken.isNullOrBlank())
    val cloudDriveTokenConfigured: StateFlow<Boolean> = _cloudDriveTokenConfigured.asStateFlow()

    private val _cloudDrivePasswordConfigured = MutableStateFlow(!securePrefs.cloudDrivePassword.isNullOrBlank())
    val cloudDrivePasswordConfigured: StateFlow<Boolean> = _cloudDrivePasswordConfigured.asStateFlow()

    private val _cloudDriveBusy = MutableStateFlow(false)
    val cloudDriveBusy: StateFlow<Boolean> = _cloudDriveBusy.asStateFlow()

    private val _cloudDriveActionMessage = MutableStateFlow<String?>(null)
    val cloudDriveActionMessage: StateFlow<String?> = _cloudDriveActionMessage.asStateFlow()

    private val _cloudDriveDirectoryBrowser = MutableStateFlow(CloudDriveDirectoryBrowserState())
    val cloudDriveDirectoryBrowser: StateFlow<CloudDriveDirectoryBrowserState> =
        _cloudDriveDirectoryBrowser.asStateFlow()

    private val _localDirectoryBrowser = MutableStateFlow(LocalDirectoryBrowserState())
    val localDirectoryBrowser: StateFlow<LocalDirectoryBrowserState> =
        _localDirectoryBrowser.asStateFlow()

    private val _logUploadSnapshot = MutableStateFlow(OtlpLogUploadActionSnapshot())
    val logUploadSnapshot: StateFlow<OtlpLogUploadActionSnapshot> = _logUploadSnapshot.asStateFlow()

    private val _logUploadStatusMessage = MutableStateFlow(settingsAndroidTvLogUploadStatusMessage())
    val logUploadStatusMessage: StateFlow<String> = _logUploadStatusMessage.asStateFlow()

    private val _appUpdateState = MutableStateFlow(AppUpdateUiState(channel = appUpdateChannelStore.updateChannel))
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private val _appUpdateChannel = MutableStateFlow(appUpdateChannelStore.updateChannel)
    val appUpdateChannel: StateFlow<UpdateChannel> = _appUpdateChannel.asStateFlow()

    // 其他表面（WebAPI/WebUI）改渠道时同步 UI
    private val updateChannelChangeListener: java.io.Closeable =
        appUpdateChannelStore.addChannelChangeListener { channel ->
            _appUpdateChannel.value = channel
            _appUpdateState.value = _appUpdateState.value.copy(channel = channel)
        }

    private val _bangumiArchiveState = MutableStateFlow(BangumiArchiveUiState())
    val bangumiArchiveState: StateFlow<BangumiArchiveUiState> = _bangumiArchiveState.asStateFlow()

    private val _bangumiSyncState = MutableStateFlow(BangumiSyncAllUiState())
    val bangumiSyncState: StateFlow<BangumiSyncAllUiState> = _bangumiSyncState.asStateFlow()

    private val _proxyStatusMessage = MutableStateFlow(settingsProxySavedStatus())
    val proxyStatusMessage: StateFlow<String> = _proxyStatusMessage.asStateFlow()

    init {
        loadSources()
        loadAppMode()
        refreshWebUiUrls()
        observeCloudDriveAutomation()
        observeLogUploadAutomation()
        refreshBangumiArchive()
    }

    fun loadSources() {
        viewModelScope.launch {
            _isLoading.value = true
            mediaRepository.getSources().onSuccess { list ->
                _sources.value = list
            }
            _isLoading.value = false
        }
    }

    fun addSource(source: MediaSourceInfo) {
        viewModelScope.launch {
            mediaRepository.addSource(source).onSuccess { id ->
                // Test connection after adding, then update status
                val msResult = mediaSourceFactory.create(source)
                msResult.onSuccess { ms ->
                    ms.testConnection().onSuccess {
                        mediaRepository.updateSource(source.copy(id = id, isConnected = true))
                    }
                }
                loadSources()
            }
        }
    }

    fun updateSource(source: MediaSourceInfo) {
        viewModelScope.launch {
            val existing = mediaRepository.getSourceById(source.id).getOrNull()
            val mergedSource = if (
                MediaSourceInfoConventions.CONNECTION_PASSWORD !in source.connectionInfo &&
                existing?.connectionPassword()?.isNotBlank() == true
            ) {
                source.copy(
                    connectionInfo = source.connectionInfo + (
                        MediaSourceInfoConventions.CONNECTION_PASSWORD to existing.connectionPassword()
                    ),
                    isConnected = existing.isConnected,
                    lastScanned = existing.lastScanned
                )
            } else {
                source.copy(
                    isConnected = existing?.isConnected ?: source.isConnected,
                    lastScanned = existing?.lastScanned ?: source.lastScanned
                )
            }
            mediaRepository.updateSource(mergedSource).onSuccess {
                loadSources()
            }
        }
    }

    fun removeSource(sourceId: Long) {
        viewModelScope.launch {
            mediaRepository.removeSource(sourceId).onSuccess {
                loadSources()
            }
        }
    }

    fun testConnection(type: MediaSourceType, url: String, username: String = "", password: String = "") {
        viewModelScope.launch {
            _testResult.value = ConnectionTestResult.Testing
            val info = MediaSourceInfo(
                name = "test",
                type = type,
                contentMode = MediaContentMode.ANIME,
                connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                    type = type,
                    location = url,
                    username = username,
                    password = password,
                )
            )
            val sourceResult = mediaSourceFactory.create(info)
            sourceResult.onSuccess { ms ->
                val test = ms.testConnection()
                test.onSuccess { connected ->
                    _testResult.value = if (connected) ConnectionTestResult.Success else ConnectionTestResult.Failed("无法连接到服务器")
                }.onError { error ->
                    _testResult.value = ConnectionTestResult.Failed(error.toUserMessage())
                }
            }.onError { error ->
                _testResult.value = ConnectionTestResult.Failed(error.toUserMessage())
            }
        }
    }

    fun saveBangumiToken(token: String) {
        val result = saveBangumiTokenFormResult(
            input = token,
            existingToken = securePrefs.bangumiAccessToken,
        )
        securePrefs.bangumiAccessToken = result.token
        _bangumiToken.value = result.token.orEmpty()
    }

    fun clearBangumiToken() {
        securePrefs.clearBangumiToken()
        _bangumiToken.value = ""
    }

    fun saveTmdbToken(token: String) {
        val result = saveTmdbTokenFormResult(
            input = token,
            existingToken = securePrefs.tmdbAccessToken,
        )
        securePrefs.tmdbAccessToken = result.token
        _tmdbToken.value = result.token.orEmpty()
    }

    fun clearTmdbToken() {
        securePrefs.clearTmdbToken()
        _tmdbToken.value = ""
    }

    fun syncBangumiAll() {
        if (_bangumiSyncState.value.isRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            _bangumiSyncState.value = BangumiSyncAllUiState(isRunning = true)
            try {
                val summary = bangumiSyncEngine.syncAllBangumi()
                _bangumiSyncState.value = BangumiSyncAllUiState(
                    isRunning = false,
                    animeCount = summary.animeCount,
                    syncedCount = summary.synced.size,
                    failedCount = summary.failed.size,
                    totalPushedEpisodes = summary.totalPushedEpisodes,
                    totalPulledEpisodes = summary.totalPulledEpisodes,
                    totalRemoteWatchedEpisodes = summary.totalRemoteWatchedEpisodes,
                    failedAnimeIds = summary.failed.map { it.animeId },
                )
            } catch (e: Exception) {
                _bangumiSyncState.value = BangumiSyncAllUiState(
                    isRunning = false,
                    errorMessage = e.message ?: "同步失败",
                )
            }
        }
    }

    fun saveCloudDriveConfig(
        endpointUrl: String,
        username: String,
        webDavSourceId: Long?,
        inboxPath: String,
        libraryPath: String,
        libraryMode: CloudDriveLibraryMode,
        intervalMinutes: Int,
        enabled: Boolean,
        rssProxyEnabled: Boolean = false,
        rssProxyHost: String = "",
        rssProxyPort: Int = 1080
    ) {
        viewModelScope.launch {
            persistCloudDriveConfig(
                endpointUrl = endpointUrl,
                username = username,
                webDavSourceId = webDavSourceId,
                inboxPath = inboxPath,
                libraryPath = libraryPath,
                libraryMode = libraryMode,
                intervalMinutes = intervalMinutes,
                enabled = enabled,
                rssProxyEnabled = rssProxyEnabled,
                rssProxyHost = rssProxyHost,
                rssProxyPort = rssProxyPort,
            )
                .onSuccess { config ->
                    cloudDriveScheduler.syncPeriodicWork(config)
                    _cloudDriveActionMessage.value = cloudRssConfigSavedStatus()
                }
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun saveProxyConfig(
        enabled: Boolean,
        host: String,
        port: Int,
    ) {
        viewModelScope.launch {
            val current = when (val result = cloudDriveRepository.getConfig()) {
                is Result.Success -> result.data
                is Result.Error -> {
                    _proxyStatusMessage.value = result.error.toUserMessage()
                    return@launch
                }
            }
            val config = current.copy(
                rssProxyEnabled = enabled,
                rssProxyHost = host.trim(),
                rssProxyPort = port,
            )
            cloudDriveRepository.saveConfig(config)
                .onSuccess {
                    bangumiArchiveStore.configureProxy(config.toBangumiHttpProxyConfig())
                    _proxyStatusMessage.value = settingsProxySavedStatus()
                }
                .onError { error -> _proxyStatusMessage.value = error.toUserMessage() }
        }
    }

    fun refreshBangumiArchive() {
        viewModelScope.launch(Dispatchers.IO) {
            _bangumiArchiveState.value = bangumiArchiveStore.snapshot().toBangumiArchiveUiState(
                statusMessage = _bangumiArchiveState.value.statusMessage,
            )
        }
    }

    fun downloadBangumiArchive() {
        if (_bangumiArchiveState.value.isDownloading) return
        viewModelScope.launch(Dispatchers.IO) {
            backgroundTasks.start(
                taskId = BackgroundTaskIds.BANGUMI_ARCHIVE,
                title = "Bangumi Archive 下载",
                text = "正在准备下载 Archive",
                progress = BackgroundTaskProgress.indeterminate(),
            )
            val proxyConfig = _cloudDriveConfig.value.toBangumiHttpProxyConfig()
            bangumiArchiveStore.configureProxy(proxyConfig)
            val progressThrottler = ProgressUpdateThrottler()
            var rawProgressCallbacks = 0L
            var emittedProgressUpdates = 0L
            MiruLog.i(
                tag = BANGUMI_ARCHIVE_LOG_TAG,
                message = "Bangumi Archive download started",
                attributes = mapOf(
                    "entrypoint" to "settings",
                    "proxy_enabled" to proxyConfig.enabled.toString(),
                    "proxy_host_configured" to proxyConfig.host.isNotBlank().toString(),
                )
            )
            _bangumiArchiveState.value = _bangumiArchiveState.value.copy(
                isDownloading = true,
                downloadedBytes = 0L,
                totalBytes = 0L,
                lastError = null,
                statusMessage = "Bangumi Archive 正在下载。",
            )
            try {
                when (
                    val result = bangumiArchiveStore.downloadLatest { bytesRead, totalBytes ->
                        rawProgressCallbacks += 1
                        val downloadedBytes = bytesRead.coerceAtLeast(0L)
                        val safeTotalBytes = totalBytes.coerceAtLeast(0L)
                        if (progressThrottler.shouldUpdate(downloadedBytes, safeTotalBytes)) {
                            emittedProgressUpdates += 1
                            _bangumiArchiveState.value = _bangumiArchiveState.value.copy(
                                isDownloading = true,
                                downloadedBytes = downloadedBytes,
                                totalBytes = safeTotalBytes,
                            )
                            backgroundTasks.update(
                                taskId = BackgroundTaskIds.BANGUMI_ARCHIVE,
                                title = "Bangumi Archive 下载",
                                text = downloadProgressText(downloadedBytes, safeTotalBytes),
                                progress = byteProgress(downloadedBytes, safeTotalBytes),
                            )
                        }
                    }
                ) {
                    is Result.Success -> {
                        MiruLog.i(
                            tag = BANGUMI_ARCHIVE_LOG_TAG,
                            message = "Bangumi Archive download finished",
                            attributes = mapOf(
                                "entrypoint" to "settings",
                                "subject_file_size_bytes" to result.data.subjectFileSizeBytes.toString(),
                                "latest_name" to result.data.latest?.name.orEmpty(),
                                "raw_progress_callbacks" to rawProgressCallbacks.toString(),
                                "emitted_progress_updates" to emittedProgressUpdates.toString(),
                            )
                        )
                        _bangumiArchiveState.value = result.data.toBangumiArchiveUiState(
                            statusMessage = "Bangumi Archive 已更新。",
                        )
                    }
                    is Result.Error -> {
                        val errorMessage = result.error.toUserMessage()
                        MiruLog.w(
                            tag = BANGUMI_ARCHIVE_LOG_TAG,
                            message = "Bangumi Archive download failed",
                            attributes = mapOf(
                                "entrypoint" to "settings",
                                "error" to errorMessage,
                                "raw_progress_callbacks" to rawProgressCallbacks.toString(),
                                "emitted_progress_updates" to emittedProgressUpdates.toString(),
                            )
                        )
                        _bangumiArchiveState.value = bangumiArchiveStore.snapshot().toBangumiArchiveUiState(
                            lastError = errorMessage,
                            statusMessage = "Bangumi Archive 下载失败。",
                        )
                    }
                }
            } finally {
                backgroundTasks.finish(BackgroundTaskIds.BANGUMI_ARCHIVE)
            }
        }
    }

    fun loginCloudDrive(endpointUrl: String, username: String, password: String) {
        val form = when (val result = validateCloudDriveLoginForm(endpointUrl, username, password)) {
            is CloudDriveLoginFormResult.Ready -> result.request
            is CloudDriveLoginFormResult.Invalid -> {
                _cloudDriveActionMessage.value = result.status
                return
            }
        }
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.login(form.endpointUrl, form.username, form.password)
                .onSuccess {
                    refreshCloudDriveCredentialState()
                    _cloudDriveActionMessage.value = cloudDriveLoginSucceededStatus()
                }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun saveCloudDriveApiToken(endpointUrl: String, token: String) {
        val form = when (val result = validateCloudDriveApiTokenForm(endpointUrl, token)) {
            is CloudDriveApiTokenFormResult.Ready -> result.request
            is CloudDriveApiTokenFormResult.Invalid -> {
                _cloudDriveActionMessage.value = result.status
                return
            }
        }
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.saveApiToken(form.endpointUrl, form.token)
                .onSuccess { info ->
                    refreshCloudDriveCredentialState()
                    _cloudDriveActionMessage.value = cloudDriveTokenVerifiedStatus(
                        friendlyName = info.friendlyName,
                        rootDir = info.rootDir,
                    )
                }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun addRssSubscription(name: String, url: String, filterRegex: String, enabled: Boolean) {
        val subscription = when (
            val result = prepareRssSubscriptionForm(
                name = name,
                url = url,
                filterRegex = filterRegex,
                enabled = enabled,
            )
        ) {
            is RssSubscriptionFormResult.Ready -> result.subscription
            is RssSubscriptionFormResult.Invalid -> {
                _cloudDriveActionMessage.value = result.status
                return
            }
        }
        viewModelScope.launch {
            cloudDriveRepository.saveSubscription(subscription)
                .onSuccess { _cloudDriveActionMessage.value = rssSubscriptionSavedStatus(subscription.name) }
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun setRssSubscriptionEnabled(subscription: RssSubscriptionInfo, enabled: Boolean) {
        viewModelScope.launch {
            cloudDriveRepository.saveSubscription(subscription.copy(enabled = enabled))
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun deleteRssSubscription(id: Long) {
        viewModelScope.launch {
            cloudDriveRepository.deleteSubscription(id)
                .onSuccess { _cloudDriveActionMessage.value = rssSubscriptionDeletedStatus() }
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun runCloudDriveNow() {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            backgroundTasks.start(
                taskId = BackgroundTaskIds.CLOUD_DRIVE_RSS,
                title = "CloudDrive/RSS 同步",
                text = "正在下载、整理并扫描订阅内容",
                progress = BackgroundTaskProgress.indeterminate(),
            )
            try {
                cloudDriveEngine.runOnce()
                    .onSuccess { summary ->
                        refreshCloudDriveCredentialState()
                        _cloudDriveActionMessage.value = summary.completeStatus()
                    }
                    .onError { error ->
                        refreshCloudDriveCredentialState()
                        _cloudDriveActionMessage.value = error.toUserMessage()
                    }
            } finally {
                _cloudDriveBusy.value = false
                backgroundTasks.finish(BackgroundTaskIds.CLOUD_DRIVE_RSS)
            }
        }
    }

    fun saveAndRunCloudDriveNow(
        endpointUrl: String,
        username: String,
        password: String,
        webDavSourceId: Long?,
        inboxPath: String,
        libraryPath: String,
        libraryMode: CloudDriveLibraryMode,
        intervalMinutes: Int,
        enabled: Boolean,
        rssProxyEnabled: Boolean = false,
        rssProxyHost: String = "",
        rssProxyPort: Int = 1080
    ) {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            try {
                val config = when (
                    val saved = persistCloudDriveConfig(
                        endpointUrl = endpointUrl,
                        username = username,
                        webDavSourceId = webDavSourceId,
                        inboxPath = inboxPath,
                        libraryPath = libraryPath,
                        libraryMode = libraryMode,
                        intervalMinutes = intervalMinutes,
                        enabled = enabled,
                        rssProxyEnabled = rssProxyEnabled,
                        rssProxyHost = rssProxyHost,
                        rssProxyPort = rssProxyPort,
                    )
                ) {
                    is Result.Success -> saved.data
                    is Result.Error -> {
                        _cloudDriveActionMessage.value = saved.error.toUserMessage()
                        return@launch
                    }
                }
                cloudDriveScheduler.syncPeriodicWork(config)

                if (password.isNotBlank()) {
                    val form = when (val result = validateCloudDriveLoginForm(endpointUrl, username, password)) {
                        is CloudDriveLoginFormResult.Ready -> result.request
                        is CloudDriveLoginFormResult.Invalid -> {
                            _cloudDriveActionMessage.value = result.status
                            return@launch
                        }
                    }
                    when (val login = cloudDriveEngine.login(form.endpointUrl, form.username, form.password)) {
                        is Result.Success -> refreshCloudDriveCredentialState()
                        is Result.Error -> {
                            refreshCloudDriveCredentialState()
                            _cloudDriveActionMessage.value = login.error.toUserMessage()
                            return@launch
                        }
                    }
                }

                if (securePrefs.cloudDriveToken.isNullOrBlank() && securePrefs.cloudDrivePassword.isNullOrBlank()) {
                    _cloudDriveActionMessage.value = cloudDriveTokenLoginRequiredStatus()
                    return@launch
                }

                backgroundTasks.start(
                    taskId = BackgroundTaskIds.CLOUD_DRIVE_RSS,
                    title = "CloudDrive/RSS 同步",
                    text = "正在下载、整理并扫描订阅内容",
                    progress = BackgroundTaskProgress.indeterminate(),
                )
                cloudDriveEngine.runOnce()
                    .onSuccess { summary ->
                        refreshCloudDriveCredentialState()
                        _cloudDriveActionMessage.value = summary.completeStatus()
                    }
                    .onError { error ->
                        refreshCloudDriveCredentialState()
                        _cloudDriveActionMessage.value = error.toUserMessage()
                    }
            } finally {
                _cloudDriveBusy.value = false
                backgroundTasks.finish(BackgroundTaskIds.CLOUD_DRIVE_RSS)
            }
        }
    }

    fun openCloudDriveDirectoryPicker(
        target: CloudDriveDirectoryTarget,
        endpointUrl: String,
        initialPath: String
    ) {
        val form = when (
            val result = validateCloudDriveDirectoryPickerForm(
                endpointUrl = endpointUrl,
                tokenInput = "",
                savedToken = securePrefs.cloudDriveToken,
            )
        ) {
            is CloudDriveDirectoryPickerFormResult.Ready -> result.request
            is CloudDriveDirectoryPickerFormResult.Invalid -> {
                _cloudDriveActionMessage.value = result.status
                return
            }
        }

        viewModelScope.launch {
            when (
                val prepared = prepareCloudDriveDirectoryBrowser(
                    client = cloudDriveClient,
                    target = target,
                    endpointUrl = form.endpointUrl,
                    token = form.token,
                    initialPath = initialPath,
                )
            ) {
                is Result.Success -> {
                    _cloudDriveDirectoryBrowser.value = prepared.data
                    browseCloudDriveDirectory(prepared.data.path)
                }
                is Result.Error -> {
                    _cloudDriveActionMessage.value = prepared.error.toUserMessage()
                }
            }
        }
    }

    fun browseCloudDriveDirectory(path: String) {
        val state = _cloudDriveDirectoryBrowser.value
        if (!state.open) return
        if (state.token.isBlank()) {
            _cloudDriveActionMessage.value = cloudDriveTokenLoginRequiredStatus()
            return
        }

        val loadingState = state.loadingFor(path)
        _cloudDriveDirectoryBrowser.value = loadingState
        viewModelScope.launch {
            when (val result = loadCloudDriveDirectory(cloudDriveClient, loadingState, loadingState.path)) {
                is Result.Success -> {
                    val current = _cloudDriveDirectoryBrowser.value
                    val next = result.data
                    if (!current.open || current.endpointUrl != next.endpointUrl || current.token != next.token) return@launch
                    _cloudDriveDirectoryBrowser.value = next
                }
                is Result.Error -> {
                    val current = _cloudDriveDirectoryBrowser.value
                    if (!current.open || current.endpointUrl != loadingState.endpointUrl || current.token != loadingState.token) return@launch
                    _cloudDriveDirectoryBrowser.value = current.copy(
                        isLoading = false,
                        message = result.error.toUserMessage()
                    )
                }
            }
        }
    }

    fun closeCloudDriveDirectoryPicker() {
        _cloudDriveDirectoryBrowser.value = _cloudDriveDirectoryBrowser.value.copy(
            open = false,
            isLoading = false
        )
    }

    fun openLocalDirectoryPicker(initialPath: String) {
        _localDirectoryBrowser.value = LocalDirectoryBrowserState(
            open = true,
            isLoading = true
        )
        browseLocalDirectory(initialPath.takeIf { it.startsWith("/") }.orEmpty())
    }

    fun browseLocalDirectory(path: String) {
        val state = _localDirectoryBrowser.value
        if (!state.open) return
        _localDirectoryBrowser.value = state.copy(isLoading = true, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { LocalDirectoryBrowser.browse(path) }
            result.onSuccess { listing ->
                val current = _localDirectoryBrowser.value
                if (!current.open) return@onSuccess
                _localDirectoryBrowser.value = current.copy(
                    isLoading = false,
                    path = listing.path,
                    displayPath = listing.displayPath,
                    parentPath = listing.parentPath,
                    entries = listing.entries.map {
                        LocalDirectoryEntry(
                            name = it.name,
                            path = it.path,
                            canRead = it.canRead
                        )
                    },
                    message = null
                )
            }.onFailure { error ->
                val current = _localDirectoryBrowser.value
                if (!current.open) return@onFailure
                _localDirectoryBrowser.value = current.copy(
                    isLoading = false,
                    message = error.message ?: "读取目录失败"
                )
            }
        }
    }

    fun closeLocalDirectoryPicker() {
        _localDirectoryBrowser.value = _localDirectoryBrowser.value.copy(
            open = false,
            isLoading = false
        )
    }

    fun setAutoScanEnabled(enabled: Boolean) {
        scanPreferences.autoScanEnabled = enabled
        _autoScanEnabled.value = enabled
    }

    fun setAutoScanIntervalHours(hours: Int) {
        scanPreferences.autoScanIntervalMs = hours * MILLIS_PER_HOUR
        _autoScanIntervalHours.value = hours
    }

    fun setMergeSameAnimeEnabled(enabled: Boolean) {
        scanPreferences.mergeSameAnimeEnabled = enabled
        _mergeSameAnimeEnabled.value = enabled
    }

    fun setPosterWallArrangement(arrangement: PosterWallArrangement) {
        scanPreferences.posterWallArrangement = arrangement
        _posterWallArrangement.value = arrangement
    }

    fun setCurrentAppMode(mode: AppMode) {
        viewModelScope.launch {
            appModePreferences.setCurrentAppMode(mode)
            _currentAppMode.value = mode
        }
    }

    fun setPlaybackEndAction(action: PlaybackEndAction) {
        playbackPreferences.endAction = action
        _playbackEndAction.value = action
    }

    fun setEpisodeVersionSelectionPolicy(policy: EpisodeVersionSelectionPolicy) {
        playbackPreferences.episodeVersionSelectionPolicy = policy
        _episodeVersionSelectionPolicy.value = policy
    }

    fun setPreferredSubtitleLanguage(preference: SubtitleLanguagePreference) {
        playbackPreferences.preferredSubtitleLanguage = preference
        _preferredSubtitleLanguage.value = preference
    }

    fun setSubtitleBackgroundTransparent(transparent: Boolean) {
        playbackPreferences.subtitleBackgroundTransparent = transparent
        _subtitleBackgroundTransparent.value = transparent
    }

    fun setAudioDspEnabled(enabled: Boolean) {
        val updated = _audioDspConfig.value.normalized().copy(enabled = enabled).normalized()
        playbackPreferences.audioDspConfig = updated
        audioDspRuntimeConfig.update(updated)
        _audioDspConfig.value = updated
    }

    fun setAudioDspPreset(presetId: String) {
        val current = _audioDspConfig.value.normalized()
        if (current.presets.none { it.id == presetId }) return
        val updated = current.copy(selectedPresetId = presetId).normalized()
        playbackPreferences.audioDspConfig = updated
        audioDspRuntimeConfig.update(updated)
        _audioDspConfig.value = updated
    }

    fun setMusicSrcBypassMode(mode: MusicSrcBypassMode) {
        playbackPreferences.musicSrcBypassMode = mode
        audioDspRuntimeConfig.updateMusicMode(mode)
        _musicSrcBypassMode.value = mode
    }

    fun setDefaultPlaybackBackend(backend: PlaybackRenderBackend) {
        val updated = _formatAwareToneMappingPreferences.value.normalized().copy(
            defaultBackend = backend
        )
        playbackPreferences.formatAwareToneMappingPreferences = updated
        _formatAwareToneMappingPreferences.value = updated.normalized()
    }

    fun setToneMappingPreset(ruleKey: VideoRenderRuleKey, preset: ToneMappingProfilePreset) {
        val updated = _formatAwareToneMappingPreferences.value.normalized().copy(
            rules = _formatAwareToneMappingPreferences.value.normalized().rules + (
                ruleKey to buildToneMappingPreset(ruleKey, preset)
            )
        )
        playbackPreferences.formatAwareToneMappingPreferences = updated
        _formatAwareToneMappingPreferences.value = updated.normalized()
    }

    fun setWebControlEnabled(enabled: Boolean) {
        webControlPreferences.webControlEnabled = enabled
        _webControlEnabled.value = enabled
        _webControlAccessToken.value = webControlPreferences.accessToken
        refreshWebUiUrls()
    }

    fun rotateWebControlAccessToken() {
        _webControlAccessToken.value = webControlPreferences.rotateAccessToken()
        refreshWebUiUrls()
    }

    fun refreshWebUiUrls() {
        viewModelScope.launch(Dispatchers.IO) {
            _webUiUrls.value = if (webControlPreferences.webControlEnabled) {
                val token = Uri.encode(webControlPreferences.accessToken)
                findLocalIps().map { ip -> "http://$ip:${WebControlConfig.DEFAULT_PORT}/?token=$token" }
            } else {
                emptyList()
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun setLogUploadEnabled(enabled: Boolean) {
        _logUploadSnapshot.value = _logUploadSnapshot.value.copy(enabled = enabled)
    }

    fun setLogUploadEndpoint(endpoint: String) {
        _logUploadSnapshot.value = _logUploadSnapshot.value.copy(endpoint = endpoint)
    }

    fun setLogUploadStreamName(streamName: String) {
        _logUploadSnapshot.value = _logUploadSnapshot.value.copy(streamName = streamName)
    }

    fun saveLogUploadSettings(token: String) {
        viewModelScope.launch {
            val current = _logUploadSnapshot.value
            val next = logUploadActions.saveSettings(
                enabled = current.enabled,
                endpoint = current.endpoint,
                streamName = current.streamName,
                token = token,
            )
            applyLogUploadSnapshot(next)
            logUploadAutoScheduler.syncWithConfig(next.toConfig())
        }
    }

    fun clearLogUploadToken() {
        viewModelScope.launch {
            applyLogUploadSnapshot(logUploadActions.clearToken())
        }
    }

    fun runLogUploadNow(tokenInput: String) {
        viewModelScope.launch {
            val current = _logUploadSnapshot.value
            val saved = logUploadActions.saveSettings(
                enabled = current.enabled,
                endpoint = current.endpoint,
                streamName = current.streamName,
                token = tokenInput,
            )
            applyLogUploadSnapshot(saved)
            logUploadAutoScheduler.syncWithConfig(saved.toConfig())
            applyLogUploadSnapshot(logUploadActions.runNow())
        }
    }

    fun setAppUpdateChannel(channel: UpdateChannel) {
        if (channel == _appUpdateChannel.value) return
        appUpdateChannelStore.updateChannel = channel
        _appUpdateChannel.value = channel
        // UI 立即反映选择，不等检查结果
        _appUpdateState.value = _appUpdateState.value.copy(channel = channel)
        checkAppUpdate()
    }

    fun checkAppUpdate() {
        if (_appUpdateState.value.isBusy) return
        viewModelScope.launch {
            _appUpdateState.value = _appUpdateState.value.copy(
                isBusy = true,
                progressPercent = null,
                statusMessage = settingsAppUpdateCheckingStatus(),
            )
            when (val result = appUpdateRepository.checkLatestUpdate()) {
                is Result.Success -> {
                    _appUpdateState.value = result.data.toUiState()
                }
                is Result.Error -> {
                    _appUpdateState.value = _appUpdateState.value.copy(
                        isBusy = false,
                        progressPercent = null,
                        statusMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun downloadAndInstallAppUpdate() {
        val latest = _appUpdateState.value.latest ?: return checkAppUpdate()
        if (_appUpdateState.value.isBusy) return
        if (!appUpdateRepository.canRequestPackageInstalls()) {
            openAppUpdateInstallPermissionSettings()
            return
        }

        viewModelScope.launch {
            _appUpdateState.value = _appUpdateState.value.copy(
                isBusy = true,
                progressPercent = 0,
                statusMessage = settingsAppUpdateDownloadProgressStatus(0),
            )
            backgroundTasks.start(
                taskId = BackgroundTaskIds.APP_UPDATE,
                title = "应用更新下载",
                text = settingsAppUpdateDownloadProgressStatus(0),
                progress = BackgroundTaskProgress.determinate(current = 0, max = 100),
            )
            try {
                when (
                    val result = appUpdateRepository.downloadAndLaunchInstaller(
                        update = latest,
                        onProgress = ::updateDownloadProgress,
                    )
                ) {
                    is Result.Success -> {
                        val status = when (result.data) {
                            AppUpdateInstallLaunch.INSTALLER_OPENED -> settingsAppUpdateInstallerOpenedStatus()
                            AppUpdateInstallLaunch.INSTALL_PERMISSION_REQUIRED -> {
                                appUpdateRepository.openInstallPermissionSettings()
                                settingsAppUpdateInstallPermissionStatus()
                            }
                        }
                        _appUpdateState.value = _appUpdateState.value.copy(
                            isBusy = false,
                            progressPercent = null,
                            statusMessage = status,
                        )
                    }
                    is Result.Error -> {
                        _appUpdateState.value = _appUpdateState.value.copy(
                            isBusy = false,
                            progressPercent = null,
                            statusMessage = result.error.toUserMessage(),
                        )
                    }
                }
            } finally {
                backgroundTasks.finish(BackgroundTaskIds.APP_UPDATE)
            }
        }
    }

    fun openAppUpdateInstallPermissionSettings() {
        if (appUpdateRepository.canRequestPackageInstalls()) {
            _appUpdateState.value = _appUpdateState.value.copy(
                isBusy = false,
                statusMessage = settingsAppUpdateInstallPermissionGrantedStatus(),
            )
            return
        }
        when (val result = appUpdateRepository.openInstallPermissionSettings()) {
            is Result.Success -> {
                _appUpdateState.value = _appUpdateState.value.copy(
                    isBusy = false,
                    statusMessage = settingsAppUpdateInstallPermissionStatus(),
                )
            }
            is Result.Error -> {
                _appUpdateState.value = _appUpdateState.value.copy(
                    isBusy = false,
                    statusMessage = result.error.toUserMessage(),
                )
            }
        }
    }

    private suspend fun persistCloudDriveConfig(
        endpointUrl: String,
        username: String,
        webDavSourceId: Long?,
        inboxPath: String,
        libraryPath: String,
        libraryMode: CloudDriveLibraryMode,
        intervalMinutes: Int,
        enabled: Boolean,
        rssProxyEnabled: Boolean = false,
        rssProxyHost: String = "",
        rssProxyPort: Int = 1080
    ): Result<CloudDriveAutomationConfig> {
        val current = _cloudDriveConfig.value
        val config = current.withAutomationFormValues(
            endpointUrl = endpointUrl.trim(),
            username = username.trim(),
            webDavSourceId = webDavSourceId,
            inboxPath = inboxPath.trim(),
            libraryPath = libraryPath.trim(),
            libraryMode = libraryMode,
            intervalMinutes = intervalMinutes,
            enabled = enabled,
            rssProxyEnabled = rssProxyEnabled,
            rssProxyHost = rssProxyHost.trim(),
            rssProxyPort = rssProxyPort,
        )
        return cloudDriveRepository.saveConfig(config).map { config }
    }

    private fun refreshCloudDriveCredentialState() {
        _cloudDriveTokenConfigured.value = !securePrefs.cloudDriveToken.isNullOrBlank()
        _cloudDrivePasswordConfigured.value = !securePrefs.cloudDrivePassword.isNullOrBlank()
    }

    private fun loadAppMode() {
        viewModelScope.launch {
            val selectionState = appModePreferences.getSelectionState()
            _currentAppMode.value = selectionState.currentAppMode ?: AppMode.ANIME
        }
    }

    private fun observeCloudDriveAutomation() {
        viewModelScope.launch {
            cloudDriveRepository.observeConfig().collectLatest { config ->
                _cloudDriveConfig.value = config
            }
        }
        viewModelScope.launch {
            cloudDriveRepository.observeSubscriptions().collectLatest { subscriptions ->
                _rssSubscriptions.value = subscriptions
            }
        }
    }

    private fun observeLogUploadAutomation() {
        viewModelScope.launch {
            applyLogUploadSnapshot(logUploadActions.current())
            logUploadAutoScheduler.syncWithConfig(_logUploadSnapshot.value.toConfig())
        }
        viewModelScope.launch {
            logUploadRepository.status.collectLatest { status ->
                val runtimeSnapshot = _logUploadSnapshot.value.withRuntimeStatus(
                    status = status,
                    tokenConfigured = status.tokenConfigured || logUploadRepository.isTokenConfigured(),
                )
                applyLogUploadSnapshot(runtimeSnapshot)
            }
        }
        logUploadConfigObserverJob?.cancel()
        logUploadConfigObserverJob = viewModelScope.launch {
            logUploadRepository.observeConfig()
                .map { it.enabled }
                .distinctUntilChanged()
                .collect {
                    logUploadAutoScheduler.syncWithConfig(logUploadRepository.getConfig())
                    applyLogUploadSnapshot(logUploadActions.current())
                }
        }
    }

    private fun applyLogUploadSnapshot(snapshot: OtlpLogUploadActionSnapshot) {
        _logUploadSnapshot.value = snapshot
        _logUploadStatusMessage.value = androidTvLogUploadStatusMessage(snapshot)
    }

    private fun updateDownloadProgress(progress: AppUpdateDownloadProgress) {
        val percent = progress.percent
        _appUpdateState.value = _appUpdateState.value.copy(
            progressPercent = percent,
            statusMessage = settingsAppUpdateDownloadProgressStatus(percent),
        )
        backgroundTasks.update(
            taskId = BackgroundTaskIds.APP_UPDATE,
            title = "应用更新下载",
            text = settingsAppUpdateDownloadProgressStatus(percent),
            progress = percent?.let { BackgroundTaskProgress.determinate(it, 100) }
                ?: BackgroundTaskProgress.indeterminate(),
        )
    }

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

    private fun AppUpdateCheck.toUiState(): AppUpdateUiState {
        val latestInfo = latest
        return AppUpdateUiState(
            latest = latestInfo,
            updateAvailable = updateAvailable,
            isBusy = false,
            progressPercent = null,
            statusMessage = if (updateAvailable) {
                settingsAppUpdateReadyStatus(latestInfo.versionName)
            } else {
                settingsAppUpdateLatestStatus(currentVersionName)
            },
            channel = channel,
        )
    }

    private fun findLocalIps(): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MILLIS_PER_HOUR = 60 * 60 * 1000L
        private const val BANGUMI_ARCHIVE_LOG_TAG = "BangumiArchiveDownload"
    }

    override fun onCleared() {
        logUploadConfigObserverJob?.cancel()
        logUploadAutoScheduler.stop()
        updateChannelChangeListener.close()
        super.onCleared()
    }
}

internal fun androidTvLogUploadStatusMessage(snapshot: OtlpLogUploadActionSnapshot): String =
    settingsLogUploadStatusMessage(
        pendingCount = snapshot.pendingCount,
        isUploading = snapshot.isUploading,
        tokenConfigured = snapshot.tokenConfigured,
        lastUploadAt = snapshot.lastUploadAt,
        lastUploadStatus = snapshot.lastUploadStatus,
    )

data class LocalDirectoryBrowserState(
    val open: Boolean = false,
    val isLoading: Boolean = false,
    val path: String = "",
    val displayPath: String = directoryBrowserRootDisplayName(isLocal = true),
    val parentPath: String? = null,
    val entries: List<LocalDirectoryEntry> = emptyList(),
    val message: String? = null
)

data class LocalDirectoryEntry(
    val name: String,
    val path: String,
    val canRead: Boolean
)

data class BangumiArchiveUiState(
    val available: Boolean = true,
    val hasSubjectData: Boolean = false,
    val latestName: String? = null,
    val latestCreatedAt: String? = null,
    val latestUpdatedAt: String? = null,
    val subjectFileSizeBytes: Long = 0L,
    val autoUpdateEnabled: Boolean = true,
    val autoUpdateIntervalDays: Int = 7,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val lastError: String? = null,
    val statusMessage: String = "Bangumi Archive 每周自动更新；也可以手动下载或更新。",
)

private fun BangumiArchiveSnapshot.toBangumiArchiveUiState(
    lastError: String? = null,
    statusMessage: String = "Bangumi Archive 每周自动更新；也可以手动下载或更新。",
): BangumiArchiveUiState =
    BangumiArchiveUiState(
        available = true,
        hasSubjectData = hasSubjectData,
        latestName = latest?.name,
        latestCreatedAt = latest?.createdAt,
        latestUpdatedAt = latest?.updatedAt,
        subjectFileSizeBytes = subjectFileSizeBytes,
        lastError = lastError,
        statusMessage = statusMessage,
    )

data class AppUpdateUiState(
    val latest: AppUpdateInfo? = null,
    val updateAvailable: Boolean = false,
    val isBusy: Boolean = false,
    val progressPercent: Int? = null,
    val statusMessage: String = settingsAppUpdateIdleStatus(),
    val channel: UpdateChannel = UpdateChannel.ALPHA,
)

data class BangumiSyncAllUiState(
    val isRunning: Boolean = false,
    val animeCount: Int = 0,
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val totalPushedEpisodes: Int = 0,
    val totalPulledEpisodes: Int = 0,
    val totalRemoteWatchedEpisodes: Int = 0,
    val failedAnimeIds: List<String> = emptyList(),
    val errorMessage: String? = null,
)

sealed class ConnectionTestResult {
    data object Testing : ConnectionTestResult()
    data object Success : ConnectionTestResult()
    data class Failed(val message: String) : ConnectionTestResult()
}
