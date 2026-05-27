package com.miruplay.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.data.preferences.PlaybackPreferencesManager
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.mediasource.MediaSourceConnectionTestResult
import com.miruplay.tv.mediasource.testConnection
import com.miruplay.tv.mediasource.testConnectionState
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.directoryBrowserRootDisplayName
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.AppUpdateCheck
import com.miruplay.tv.repository.AppUpdateDownloadProgress
import com.miruplay.tv.repository.AppUpdateInfo
import com.miruplay.tv.repository.AppUpdateInstallLaunch
import com.miruplay.tv.repository.AppUpdateRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.sync.BangumiCredentialActionCoordinator
import com.miruplay.tv.sync.BangumiTokenActionResult
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryLoadResult
import com.miruplay.tv.sync.rss.CloudDriveDirectoryOpenResult
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
import com.miruplay.tv.sync.rss.RssSubscriptionActionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val securePrefs: AppCredentialStore,
    private val scanPreferences: ScanPreferencesRepository,
    private val playbackPreferences: PlaybackPreferencesRepository,
    private val webControlPreferences: WebControlAccessManager,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val logUploadRepository: LogUploadRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val cloudDriveScheduler: CloudDriveRssScheduler
) : ViewModel() {
    private val cloudDriveActions = CloudDriveRssActionCoordinator(
        repository = cloudDriveRepository,
        credentials = securePrefs,
        runner = cloudDriveEngine,
    )
    private val bangumiCredentialActions = BangumiCredentialActionCoordinator(securePrefs)
    private val cloudDriveDirectoryActions = CloudDriveDirectoryBrowserCoordinator(cloudDriveClient)
    private val webControlActions = WebControlAccessActionCoordinator(webControlPreferences)
    private val settingsPreferenceActions = SettingsPreferenceActionCoordinator(scanPreferences, playbackPreferences)
    private val mediaSourceActions = MediaSourceActionCoordinator(mediaRepository)

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

    private val _autoScanEnabled = MutableStateFlow(false)
    val autoScanEnabled: StateFlow<Boolean> = _autoScanEnabled.asStateFlow()

    private val _autoScanIntervalHours = MutableStateFlow(ScanPreferenceActionSnapshot().autoScanIntervalHours)
    val autoScanIntervalHours: StateFlow<Int> = _autoScanIntervalHours.asStateFlow()

    private val _lastScanAt = MutableStateFlow(0L)
    val lastScanAt: StateFlow<Long> = _lastScanAt.asStateFlow()

    private val _mergeSameAnimeEnabled = MutableStateFlow(false)
    val mergeSameAnimeEnabled: StateFlow<Boolean> = _mergeSameAnimeEnabled.asStateFlow()

    private val _playbackEndAction = MutableStateFlow(PlaybackEndAction.RETURN_TO_DETAIL)
    val playbackEndAction: StateFlow<PlaybackEndAction> = _playbackEndAction.asStateFlow()

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

    private val _appUpdateState = MutableStateFlow(AppUpdateUiState())
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    init {
        loadSources()
        loadScanPreferences()
        loadPlaybackPreferences()
        refreshWebUiUrls()
        observeCloudDriveAutomation()
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
            _testResult.value = when (
                val result = mediaSourceFactory.testConnection(
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
        return result
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
            when (val result = cloudDriveActions.saveConfig(
                endpointUrl = endpointUrl,
                username = username,
                webDavSourceId = webDavSourceId,
                inboxPath = inboxPath.trim(),
                libraryPath = libraryPath.trim(),
                libraryMode = libraryMode,
                intervalMinutes = intervalMinutes,
                enabled = enabled,
                rssProxyEnabled = rssProxyEnabled,
                rssProxyHost = rssProxyHost.trim(),
                rssProxyPort = rssProxyPort
            )
            cloudDriveRepository.saveConfig(config)
                .onSuccess {
                    cloudDriveScheduler.syncPeriodicWork(config)
                    _cloudDriveActionMessage.value = cloudRssConfigSavedStatus()
                }
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun loginCloudDrive(endpointUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.login(form.endpointUrl, form.username, form.password)
                .onSuccess {
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value = result.status
                }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
                is CloudDriveActionResult.Failed -> {
                    _cloudDriveActionMessage.value = result.status
                }
            }
            _cloudDriveBusy.value = false
        }
    }

    fun saveCloudDriveApiToken(endpointUrl: String, token: String) {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.saveApiToken(form.endpointUrl, form.token)
                .onSuccess { info ->
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value = result.status
                }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
                is CloudDriveActionResult.Failed -> {
                    _cloudDriveActionMessage.value = result.status
                }
            }
            _cloudDriveBusy.value = false
        }
    }

    fun addRssSubscription(name: String, url: String, filterRegex: String, enabled: Boolean) {
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
            cloudDriveEngine.runOnce()
                .onSuccess { summary -> _cloudDriveActionMessage.value = summary.completeStatus() }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            }
            _cloudDriveBusy.value = false
        }
    }

    fun openCloudDriveDirectoryPicker(
        target: CloudDriveDirectoryTarget,
        endpointUrl: String,
        initialPath: String
    ) {
        viewModelScope.launch {
            when (
                val opened = cloudDriveDirectoryActions.open(
                    target = target,
                    endpointUrl = endpointUrl,
                    tokenInput = "",
                    savedToken = securePrefs.cloudDriveToken,
                    initialPath = initialPath,
                )
            ) {
                is CloudDriveDirectoryOpenResult.Ready -> {
                    _cloudDriveDirectoryBrowser.value = opened.state
                    browseCloudDriveDirectory(opened.loadPath)
                }
                is CloudDriveDirectoryOpenResult.Invalid -> {
                    _cloudDriveActionMessage.value = opened.status
                }
                is CloudDriveDirectoryOpenResult.Failed -> {
                    _cloudDriveActionMessage.value = opened.status
                }
            }
        }
    }

    fun browseCloudDriveDirectory(path: String) {
        when (val loading = cloudDriveDirectoryActions.loading(_cloudDriveDirectoryBrowser.value, path)) {
            CloudDriveDirectoryLoadResult.Ignored -> return
            is CloudDriveDirectoryLoadResult.Failed -> {
                _cloudDriveDirectoryBrowser.value = loading.state
                _cloudDriveActionMessage.value = loading.status
            }
            is CloudDriveDirectoryLoadResult.Loading -> {
                _cloudDriveDirectoryBrowser.value = loading.state
                viewModelScope.launch {
                    val loaded = cloudDriveDirectoryActions.load(loading.state)
                    when (
                        val result = cloudDriveDirectoryActions.applyLoadedIfCurrent(
                            currentState = _cloudDriveDirectoryBrowser.value,
                            result = loaded,
                        )
                    ) {
                        CloudDriveDirectoryLoadResult.Ignored -> Unit
                        is CloudDriveDirectoryLoadResult.Loaded -> {
                            _cloudDriveDirectoryBrowser.value = result.state
                        }
                        is CloudDriveDirectoryLoadResult.Failed -> {
                            _cloudDriveDirectoryBrowser.value = result.state
                            _cloudDriveActionMessage.value = result.status
                        }
                        is CloudDriveDirectoryLoadResult.Loading -> Unit
                    }
                }
            }
            is CloudDriveDirectoryLoadResult.Loaded -> {
                _cloudDriveDirectoryBrowser.value = loading.state
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

    fun setPlaybackEndAction(action: PlaybackEndAction) {
        playbackPreferences.endAction = action
        _playbackEndAction.value = action
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

    fun saveLogUploadConfig(endpoint: String, token: String, streamName: String, enabled: Boolean) {
        viewModelScope.launch {
            logUploadRepository.saveConfig(
                enabled = enabled,
                endpoint = endpoint.trim(),
                streamName = streamName.trim()
            )
            val normalizedToken = token.trim()
            if (normalizedToken.isNotBlank()) {
                logUploadRepository.saveToken(normalizedToken)
            }
            MiruLog.i(
                "SettingsViewModel",
                "OpenObserve log upload config saved",
                mapOf("enabled" to enabled.toString(), "stream_name" to streamName.trim().ifBlank { "miruplay" })
            )
            if (enabled) {
                logUploadRepository.uploadPendingLogs()
            }
        }
    }

    fun clearLogUploadToken() {
        viewModelScope.launch {
            logUploadRepository.clearToken()
            MiruLog.i("SettingsViewModel", "OpenObserve log upload token cleared")
        }
    }

    fun uploadLogsNow() {
        viewModelScope.launch {
            MiruLog.i("SettingsViewModel", "Manual OpenObserve log upload requested")
            logUploadRepository.uploadPendingLogs()
        }
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
    }
}

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

sealed class ConnectionTestResult {
    data object Testing : ConnectionTestResult()
    data object Success : ConnectionTestResult()
    data class Failed(val message: String) : ConnectionTestResult()
}
