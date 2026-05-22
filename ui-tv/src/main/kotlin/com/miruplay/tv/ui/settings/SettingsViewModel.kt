package com.miruplay.tv.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.data.preferences.PlaybackEndAction
import com.miruplay.tv.data.preferences.PlaybackPreferencesManager
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.cloudDriveApiTokenRequiredStatus
import com.miruplay.tv.model.cloudDriveEndpointRequiredStatus
import com.miruplay.tv.model.cloudDriveLoginRequiredStatus
import com.miruplay.tv.model.cloudDriveLoginSucceededStatus
import com.miruplay.tv.model.cloudDriveTokenLoginRequiredStatus
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.cloudDriveTokenVerifiedStatus
import com.miruplay.tv.model.cloudRssConfigSavedStatus
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.LogUploadStatus
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.OtlpLogUploadConfig
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.model.rssSubscriptionDeletedStatus
import com.miruplay.tv.model.rssSubscriptionSavedStatus
import com.miruplay.tv.model.rssUrlRequiredStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val securePrefs: AppCredentialStore,
    private val scanPreferences: ScanPreferencesManager,
    private val playbackPreferences: PlaybackPreferencesManager,
    private val webControlPreferences: WebControlAccessManager,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val logUploadRepository: LogUploadRepository
) : ViewModel() {

    private val _sources = MutableStateFlow<List<MediaSourceInfo>>(emptyList())
    val sources: StateFlow<List<MediaSourceInfo>> = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val testResult: StateFlow<ConnectionTestResult?> = _testResult.asStateFlow()

    private val _bangumiToken = MutableStateFlow(securePrefs.bangumiAccessToken ?: "")
    val bangumiToken: StateFlow<String> = _bangumiToken.asStateFlow()

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

    private val _playbackEndAction = MutableStateFlow(playbackPreferences.endAction)
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

    private val _logUploadConfig = MutableStateFlow(logUploadRepository.getConfig())
    val logUploadConfig: StateFlow<OtlpLogUploadConfig> = _logUploadConfig.asStateFlow()

    private val _logUploadStatus = MutableStateFlow(
        LogUploadStatus(tokenConfigured = logUploadRepository.isTokenConfigured())
    )
    val logUploadStatus: StateFlow<LogUploadStatus> = _logUploadStatus.asStateFlow()

    private val _cloudDriveDirectoryBrowser = MutableStateFlow(CloudDriveDirectoryBrowserState())
    val cloudDriveDirectoryBrowser: StateFlow<CloudDriveDirectoryBrowserState> =
        _cloudDriveDirectoryBrowser.asStateFlow()

    private val _localDirectoryBrowser = MutableStateFlow(LocalDirectoryBrowserState())
    val localDirectoryBrowser: StateFlow<LocalDirectoryBrowserState> =
        _localDirectoryBrowser.asStateFlow()

    init {
        loadSources()
        refreshWebUiUrls()
        observeCloudDriveAutomation()
        observeLogUpload()
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
                MiruLog.i(
                    "SettingsViewModel",
                    "Media source added",
                    mapOf("source_id" to id.toString(), "source_type" to source.type.name)
                )
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
                MiruLog.i(
                    "SettingsViewModel",
                    "Media source updated",
                    mapOf("source_id" to mergedSource.id.toString(), "source_type" to mergedSource.type.name)
                )
                loadSources()
            }
        }
    }

    fun removeSource(sourceId: Long) {
        viewModelScope.launch {
            mediaRepository.removeSource(sourceId).onSuccess {
                MiruLog.i(
                    "SettingsViewModel",
                    "Media source removed",
                    mapOf("source_id" to sourceId.toString())
                )
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
                    MiruLog.i(
                        "SettingsViewModel",
                        "Media source connection test finished",
                        mapOf("source_type" to type.name, "connected" to connected.toString())
                    )
                    _testResult.value = if (connected) ConnectionTestResult.Success else ConnectionTestResult.Failed("无法连接到服务器")
                }.onError { error ->
                    MiruLog.w(
                        "SettingsViewModel",
                        "Media source connection test failed",
                        attributes = mapOf(
                            "source_type" to type.name,
                            "error_type" to error::class.simpleName.orEmpty(),
                            "error_message" to error.toUserMessage()
                        )
                    )
                    _testResult.value = ConnectionTestResult.Failed(error.toUserMessage())
                }
            }.onError { error ->
                MiruLog.w(
                    "SettingsViewModel",
                    "Media source creation failed during connection test",
                    attributes = mapOf(
                        "source_type" to type.name,
                        "error_type" to error::class.simpleName.orEmpty(),
                        "error_message" to error.toUserMessage()
                    )
                )
                _testResult.value = ConnectionTestResult.Failed(error.toUserMessage())
            }
        }
    }

    fun saveBangumiToken(token: String) {
        securePrefs.bangumiAccessToken = token
        MiruLog.i("SettingsViewModel", "Bangumi token saved")
        _bangumiToken.value = token
    }

    fun clearBangumiToken() {
        securePrefs.clearBangumiToken()
        MiruLog.i("SettingsViewModel", "Bangumi token cleared")
        _bangumiToken.value = ""
    }

    fun saveCloudDriveConfig(
        endpointUrl: String,
        username: String,
        webDavSourceId: Long?,
        inboxPath: String,
        libraryPath: String,
        intervalMinutes: Int,
        enabled: Boolean,
        rssProxyEnabled: Boolean = false,
        rssProxyHost: String = "",
        rssProxyPort: Int = 1080
    ) {
        viewModelScope.launch {
            val current = _cloudDriveConfig.value
            val config = current.copy(
                endpointUrl = endpointUrl.trim(),
                username = username.trim(),
                webDavSourceId = webDavSourceId,
                inboxPath = inboxPath.trim(),
                libraryPath = libraryPath.trim(),
                intervalMinutes = intervalMinutes.coerceAtLeast(MIN_CLOUD_DRIVE_INTERVAL_MINUTES),
                enabled = enabled,
                rssProxyEnabled = rssProxyEnabled,
                rssProxyHost = rssProxyHost.trim(),
                rssProxyPort = rssProxyPort.coerceAtLeast(1).coerceAtMost(65535)
            )
            cloudDriveRepository.saveConfig(config)
                .onSuccess {
                    MiruLog.i(
                        "SettingsViewModel",
                        "CloudDrive RSS config saved",
                        mapOf("enabled" to config.enabled.toString())
                    )
                    _cloudDriveActionMessage.value = cloudRssConfigSavedStatus()
                }
                .onError { error ->
                    MiruLog.w(
                        "SettingsViewModel",
                        "CloudDrive RSS config save failed",
                        attributes = mapOf("error_message" to error.toUserMessage())
                    )
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
        }
    }

    fun loginCloudDrive(endpointUrl: String, username: String, password: String) {
        if (endpointUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _cloudDriveActionMessage.value = cloudDriveLoginRequiredStatus()
            return
        }
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.login(endpointUrl.trim(), username.trim(), password)
                .onSuccess {
                    MiruLog.i("SettingsViewModel", "CloudDrive login succeeded")
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value = cloudDriveLoginSucceededStatus()
                }
                .onError { error ->
                    MiruLog.w(
                        "SettingsViewModel",
                        "CloudDrive login failed",
                        attributes = mapOf("error_message" to error.toUserMessage())
                    )
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun saveCloudDriveApiToken(endpointUrl: String, token: String) {
        if (endpointUrl.isBlank()) {
            _cloudDriveActionMessage.value = cloudDriveEndpointRequiredStatus()
            return
        }
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) {
            _cloudDriveActionMessage.value = cloudDriveApiTokenRequiredStatus()
            return
        }
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.saveApiToken(endpointUrl.trim(), normalizedToken)
                .onSuccess { info ->
                    MiruLog.i(
                        "SettingsViewModel",
                        "CloudDrive API token verified",
                        mapOf("root_dir" to info.rootDir)
                    )
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value = cloudDriveTokenVerifiedStatus(
                        friendlyName = info.friendlyName,
                        rootDir = info.rootDir,
                    )
                }
                .onError { error ->
                    MiruLog.w(
                        "SettingsViewModel",
                        "CloudDrive API token verification failed",
                        attributes = mapOf("error_message" to error.toUserMessage())
                    )
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun addRssSubscription(name: String, url: String, filterRegex: String, enabled: Boolean) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            _cloudDriveActionMessage.value = rssUrlRequiredStatus()
            return
        }
        viewModelScope.launch {
            val subscription = RssSubscriptionInfo(
                name = name.trim().ifBlank { normalizedUrl },
                url = normalizedUrl,
                filterRegex = filterRegex.trim().takeIf { it.isNotBlank() },
                enabled = enabled
            )
            cloudDriveRepository.saveSubscription(subscription)
                .onSuccess { _cloudDriveActionMessage.value = rssSubscriptionSavedStatus(subscription.name) }
                .onSuccess {
                    MiruLog.i(
                        "SettingsViewModel",
                        "RSS subscription saved",
                        mapOf("subscription_name" to subscription.name, "enabled" to enabled.toString())
                    )
                }
                .onError { error ->
                    MiruLog.w(
                        "SettingsViewModel",
                        "RSS subscription save failed",
                        attributes = mapOf("error_message" to error.toUserMessage())
                    )
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
        }
    }

    fun setRssSubscriptionEnabled(subscription: RssSubscriptionInfo, enabled: Boolean) {
        viewModelScope.launch {
            cloudDriveRepository.saveSubscription(subscription.copy(enabled = enabled))
                .onSuccess {
                    MiruLog.i(
                        "SettingsViewModel",
                        "RSS subscription enabled state changed",
                        mapOf(
                            "subscription_id" to subscription.id.toString(),
                            "enabled" to enabled.toString()
                        )
                    )
                }
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun deleteRssSubscription(id: Long) {
        viewModelScope.launch {
            cloudDriveRepository.deleteSubscription(id)
                .onSuccess { _cloudDriveActionMessage.value = rssSubscriptionDeletedStatus() }
                .onSuccess {
                    MiruLog.i("SettingsViewModel", "RSS subscription deleted", mapOf("subscription_id" to id.toString()))
                }
                .onError { error ->
                    MiruLog.w(
                        "SettingsViewModel",
                        "RSS subscription delete failed",
                        attributes = mapOf("error_message" to error.toUserMessage())
                    )
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
        }
    }

    fun runCloudDriveNow() {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.runOnce()
                .onSuccess { summary ->
                    MiruLog.i(
                        "SettingsViewModel",
                        "CloudDrive RSS manual run finished",
                        mapOf(
                            "submitted" to summary.submitted.toString(),
                            "organized" to summary.organized.toString(),
                            "skipped" to summary.skipped.toString(),
                            "failed" to summary.failed.toString()
                        )
                    )
                    _cloudDriveActionMessage.value = summary.completeStatus()
                }
                .onError { error ->
                    MiruLog.w(
                        "SettingsViewModel",
                        "CloudDrive RSS manual run failed",
                        attributes = mapOf("error_message" to error.toUserMessage())
                    )
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun openCloudDriveDirectoryPicker(
        target: CloudDriveDirectoryTarget,
        endpointUrl: String,
        initialPath: String
    ) {
        val normalizedEndpoint = endpointUrl.trim()
        if (normalizedEndpoint.isBlank()) {
            _cloudDriveActionMessage.value = cloudDriveEndpointRequiredStatus()
            return
        }
        val token = securePrefs.cloudDriveToken?.takeIf { it.isNotBlank() }
        if (token.isNullOrBlank()) {
            _cloudDriveActionMessage.value = cloudDriveTokenLoginRequiredStatus()
            return
        }

        _cloudDriveDirectoryBrowser.value = CloudDriveDirectoryBrowserState(
            open = true,
            target = target,
            endpointUrl = normalizedEndpoint,
            isLoading = true
        )
        browseCloudDriveDirectory(initialPath.ifBlank { "/" })
    }

    fun browseCloudDriveDirectory(path: String) {
        val state = _cloudDriveDirectoryBrowser.value
        if (!state.open) return
        val token = securePrefs.cloudDriveToken?.takeIf { it.isNotBlank() }
        if (token.isNullOrBlank()) {
            _cloudDriveActionMessage.value = cloudDriveTokenLoginRequiredStatus()
            return
        }

        _cloudDriveDirectoryBrowser.value = state.copy(isLoading = true, message = null)
        viewModelScope.launch {
            loadCloudDriveDirectory(state.endpointUrl, token, path)
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
        MiruLog.i(
            "SettingsViewModel",
            "Auto scan setting changed",
            mapOf("enabled" to enabled.toString())
        )
        _autoScanEnabled.value = enabled
    }

    fun setAutoScanIntervalHours(hours: Int) {
        scanPreferences.autoScanIntervalMs = hours * MILLIS_PER_HOUR
        MiruLog.i(
            "SettingsViewModel",
            "Auto scan interval changed",
            mapOf("hours" to hours.toString())
        )
        _autoScanIntervalHours.value = hours
    }

    fun setMergeSameAnimeEnabled(enabled: Boolean) {
        scanPreferences.mergeSameAnimeEnabled = enabled
        MiruLog.i(
            "SettingsViewModel",
            "Merge same anime setting changed",
            mapOf("enabled" to enabled.toString())
        )
        _mergeSameAnimeEnabled.value = enabled
    }

    fun setPlaybackEndAction(action: PlaybackEndAction) {
        playbackPreferences.endAction = action
        MiruLog.i(
            "SettingsViewModel",
            "Playback end action changed",
            mapOf("action" to action.name)
        )
        _playbackEndAction.value = action
    }

    fun setWebControlEnabled(enabled: Boolean) {
        webControlPreferences.webControlEnabled = enabled
        MiruLog.i(
            "SettingsViewModel",
            "Web control setting changed",
            mapOf("enabled" to enabled.toString())
        )
        _webControlEnabled.value = enabled
        _webControlAccessToken.value = webControlPreferences.accessToken
        refreshWebUiUrls()
    }

    fun rotateWebControlAccessToken() {
        _webControlAccessToken.value = webControlPreferences.rotateAccessToken()
        MiruLog.i("SettingsViewModel", "Web control access token rotated")
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

    fun clearTestResult() {
        _testResult.value = null
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

    private fun observeLogUpload() {
        viewModelScope.launch {
            logUploadRepository.observeConfig().collectLatest { config ->
                _logUploadConfig.value = config
            }
        }
        viewModelScope.launch {
            logUploadRepository.status.collectLatest { status ->
                _logUploadStatus.value = status
            }
        }
    }

    private suspend fun loadCloudDriveDirectory(
        endpointUrl: String,
        token: String,
        path: String
    ) {
        val endpoint = CloudDriveEndpoint(endpointUrl, token)
        val tokenInfo = cloudDriveClient.getApiTokenInfo(endpointUrl, token).getOrNull()
        val rootPath = normalizeCloudDrivePath(tokenInfo?.rootDir ?: "")
        val requestedPath = normalizeCloudDrivePath(path.ifBlank { rootPath })
        val currentPath = when {
            rootPath == "/" -> requestedPath.ifBlank { "/" }
            requestedPath == "/" -> rootPath
            requestedPath == rootPath || requestedPath.startsWith("$rootPath/") -> requestedPath
            else -> rootPath
        }

        when (val result = cloudDriveClient.listFolder(endpoint, currentPath, forceRefresh = false)) {
            is Result.Success -> {
                val state = _cloudDriveDirectoryBrowser.value
                if (!state.open || state.endpointUrl != endpointUrl) return
                _cloudDriveDirectoryBrowser.value = state.copy(
                    isLoading = false,
                    path = currentPath,
                    displayPath = if (currentPath == "/") "CloudDrive 根目录" else currentPath,
                    parentPath = cloudDriveParentPath(currentPath, rootPath),
                    entries = result.data
                        .asSequence()
                        .filter { it.isDirectory }
                        .filter { !it.name.startsWith(".") }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        .map {
                            CloudDriveDirectoryEntry(
                                name = it.name.ifBlank { it.path.substringAfterLast('/') },
                                path = normalizeCloudDrivePath(it.path)
                            )
                        }
                        .toList(),
                    message = null
                )
            }
            is Result.Error -> {
                val state = _cloudDriveDirectoryBrowser.value
                if (!state.open || state.endpointUrl != endpointUrl) return
                _cloudDriveDirectoryBrowser.value = state.copy(
                    isLoading = false,
                    message = result.error.toUserMessage()
                )
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

    private fun normalizeCloudDrivePath(path: String): String {
        val trimmed = path.trim().replace('\\', '/').trimEnd('/')
        return when {
            trimmed.isBlank() -> "/"
            trimmed.startsWith('/') -> trimmed
            else -> "/$trimmed"
        }
    }

    private fun cloudDriveParentPath(path: String, rootPath: String): String? {
        val normalizedPath = normalizeCloudDrivePath(path)
        val normalizedRoot = normalizeCloudDrivePath(rootPath)
        if (normalizedPath == normalizedRoot || normalizedPath == "/") return null
        val parent = normalizedPath.substringBeforeLast('/', "")
        if (parent.isBlank() || parent == normalizedPath) return null
        return when {
            normalizedRoot == "/" -> parent.ifBlank { "/" }
            parent == normalizedRoot || parent.startsWith("$normalizedRoot/") -> parent
            else -> normalizedRoot
        }
    }

    companion object {
        private const val MILLIS_PER_HOUR = 60 * 60 * 1000L
        private const val MIN_CLOUD_DRIVE_INTERVAL_MINUTES = 5
    }
}

data class CloudDriveDirectoryBrowserState(
    val open: Boolean = false,
    val target: CloudDriveDirectoryTarget = CloudDriveDirectoryTarget.INBOX,
    val endpointUrl: String = "",
    val isLoading: Boolean = false,
    val path: String = "",
    val displayPath: String = "CloudDrive 根目录",
    val parentPath: String? = null,
    val entries: List<CloudDriveDirectoryEntry> = emptyList(),
    val message: String? = null
)

data class CloudDriveDirectoryEntry(
    val name: String,
    val path: String
)

data class LocalDirectoryBrowserState(
    val open: Boolean = false,
    val isLoading: Boolean = false,
    val path: String = "",
    val displayPath: String = "设备存储",
    val parentPath: String? = null,
    val entries: List<LocalDirectoryEntry> = emptyList(),
    val message: String? = null
)

data class LocalDirectoryEntry(
    val name: String,
    val path: String,
    val canRead: Boolean
)

enum class CloudDriveDirectoryTarget {
    INBOX,
    LIBRARY
}

sealed class ConnectionTestResult {
    data object Testing : ConnectionTestResult()
    data object Success : ConnectionTestResult()
    data class Failed(val message: String) : ConnectionTestResult()
}

private fun com.miruplay.tv.core.common.AppError.toUserMessage(): String = when (this) {
    is com.miruplay.tv.core.common.AppError.NetworkError.HttpError -> "HTTP ${code}: $message"
    is com.miruplay.tv.core.common.AppError.NetworkError.NoConnectivity -> "网络不可用"
    is com.miruplay.tv.core.common.AppError.NetworkError.ServerUnreachable -> "服务器无法访问: $url"
    is com.miruplay.tv.core.common.AppError.NetworkError.RateLimited -> "请求过于频繁"
    is com.miruplay.tv.core.common.AppError.MediaSourceError.ConnectionLost -> "连接丢失"
    is com.miruplay.tv.core.common.AppError.MediaSourceError.AuthenticationFailed -> "认证失败"
    is com.miruplay.tv.core.common.AppError.MediaSourceError.Timeout -> "连接超时"
    is com.miruplay.tv.core.common.AppError.MediaSourceError.PermissionDenied -> "权限不足"
    else -> "未知错误: ${this::class.simpleName}"
}
