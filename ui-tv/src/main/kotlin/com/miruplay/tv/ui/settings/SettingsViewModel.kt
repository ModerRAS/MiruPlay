package com.miruplay.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.LocalDirectoryBrowser
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.directoryBrowserRootDisplayName
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.mediaSourceConnectionFailedMessage
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.MediaSourceActionCoordinator
import com.miruplay.tv.repository.MediaSourceAddActionResult
import com.miruplay.tv.repository.MediaSourceAddFailurePhase
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import com.miruplay.tv.repository.ScanPreferenceActionSnapshot
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.SettingsPreferenceActionCoordinator
import com.miruplay.tv.repository.WebControlAccessActionCoordinator
import com.miruplay.tv.repository.WebControlAccessSnapshot
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.sync.BangumiCredentialActionCoordinator
import com.miruplay.tv.sync.BangumiTokenActionResult
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveActionResult
import com.miruplay.tv.sync.rss.CloudDriveConfigActionResult
import com.miruplay.tv.sync.rss.CloudDriveRunActionResult
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserCoordinator
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryLoadResult
import com.miruplay.tv.sync.rss.CloudDriveDirectoryOpenResult
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
import com.miruplay.tv.sync.rss.RssSubscriptionActionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val cloudDriveClient: CloudDriveClient,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine
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

    init {
        loadSources()
        loadScanPreferences()
        loadPlaybackPreferences()
        refreshWebUiUrls()
        observeCloudDriveAutomation()
    }

    private fun loadScanPreferences() {
        viewModelScope.launch {
            applyScanPreferenceSnapshot(settingsPreferenceActions.currentScanPreferences())
        }
    }

    private fun loadPlaybackPreferences() {
        viewModelScope.launch {
            _playbackEndAction.value = settingsPreferenceActions.currentPlaybackEndAction()
        }
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
            when (
                val result = mediaSourceActions.addSource(source) { persisted ->
                    mediaSourceFactory.create(persisted).flatMap { mediaSource ->
                        mediaSource.testConnection()
                    }
                }
            ) {
                is MediaSourceAddActionResult.Saved -> loadSources()
                is MediaSourceAddActionResult.Failed -> {
                    if (result.phase == MediaSourceAddFailurePhase.UpdateConnectionState) {
                        loadSources()
                    }
                }
            }
        }
    }

    fun updateSource(source: MediaSourceInfo) {
        viewModelScope.launch {
            mediaSourceActions.updateSource(source).onSuccess {
                loadSources()
            }
        }
    }

    fun removeSource(sourceId: Long) {
        viewModelScope.launch {
            mediaSourceActions.removeSource(sourceId).onSuccess {
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
            when (val sourceResult = mediaSourceFactory.create(info)) {
                is Result.Success -> {
                    when (val test = sourceResult.data.testConnection()) {
                        is Result.Success -> {
                            _testResult.value = if (test.data) {
                                ConnectionTestResult.Success
                            } else {
                                ConnectionTestResult.Failed(mediaSourceConnectionFailedMessage())
                            }
                        }
                        is Result.Error -> {
                            _testResult.value = ConnectionTestResult.Failed(test.error.toUserMessage())
                        }
                    }
                }
                is Result.Error -> {
                    _testResult.value = ConnectionTestResult.Failed(sourceResult.error.toUserMessage())
                }
            }
        }
    }

    fun saveBangumiToken(token: String): BangumiTokenActionResult.Saved {
        val result = bangumiCredentialActions.saveToken(token)
        _bangumiToken.value = result.token.orEmpty()
        return result
    }

    fun clearBangumiToken(): BangumiTokenActionResult.Cleared {
        val result = bangumiCredentialActions.clearToken()
        _bangumiToken.value = ""
        return result
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
            when (val result = cloudDriveActions.saveConfig(
                endpointUrl = endpointUrl,
                username = username,
                webDavSourceId = webDavSourceId,
                inboxPath = inboxPath,
                libraryPath = libraryPath,
                intervalMinutes = intervalMinutes,
                enabled = enabled,
                rssProxyEnabled = rssProxyEnabled,
                rssProxyHost = rssProxyHost,
                rssProxyPort = rssProxyPort,
            )) {
                is CloudDriveConfigActionResult.Saved -> {
                    _cloudDriveConfig.value = result.config
                    _cloudDriveActionMessage.value = result.status
                }
                is CloudDriveConfigActionResult.Failed -> {
                    _cloudDriveActionMessage.value = result.status
                }
            }
        }
    }

    fun loginCloudDrive(endpointUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            when (val result = cloudDriveActions.loginCloudDrive(endpointUrl, username, password)) {
                is CloudDriveActionResult.Success -> {
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value = result.status
                }
                is CloudDriveActionResult.Invalid -> {
                    _cloudDriveActionMessage.value = result.status
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
            when (val result = cloudDriveActions.verifyCloudDriveApiToken(endpointUrl, token)) {
                is CloudDriveActionResult.Success -> {
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value = result.status
                }
                is CloudDriveActionResult.Invalid -> {
                    _cloudDriveActionMessage.value = result.status
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
            when (
                val result = cloudDriveActions.saveRssSubscription(
                    name = name,
                    url = url,
                    filterRegex = filterRegex,
                    enabled = enabled,
                )
            ) {
                is RssSubscriptionActionResult.Saved -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is RssSubscriptionActionResult.Invalid -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is RssSubscriptionActionResult.Failed -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is RssSubscriptionActionResult.Deleted -> Unit
            }
        }
    }

    fun setRssSubscriptionEnabled(subscription: RssSubscriptionInfo, enabled: Boolean) {
        viewModelScope.launch {
            when (
                val result = cloudDriveActions.saveRssSubscription(
                    name = subscription.name,
                    url = subscription.url,
                    filterRegex = subscription.filterRegex.orEmpty(),
                    enabled = enabled,
                    selectedSubscription = subscription,
                )
            ) {
                is RssSubscriptionActionResult.Failed -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is RssSubscriptionActionResult.Invalid -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is RssSubscriptionActionResult.Saved -> Unit
                is RssSubscriptionActionResult.Deleted -> Unit
            }
        }
    }

    fun deleteRssSubscription(id: Long) {
        viewModelScope.launch {
            when (val result = cloudDriveActions.deleteRssSubscription(id)) {
                is RssSubscriptionActionResult.Deleted -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is RssSubscriptionActionResult.Failed -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is RssSubscriptionActionResult.Invalid -> Unit
                is RssSubscriptionActionResult.Saved -> Unit
            }
        }
    }

    fun runCloudDriveNow() {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            when (val result = cloudDriveActions.runCloudDriveOnce()) {
                is CloudDriveRunActionResult.Completed -> {
                    _cloudDriveActionMessage.value = result.status
                }
                is CloudDriveRunActionResult.Failed -> {
                    _cloudDriveActionMessage.value = result.status
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
        viewModelScope.launch {
            applyScanPreferenceSnapshot(settingsPreferenceActions.setAutoScanEnabled(enabled))
        }
    }

    fun setAutoScanIntervalHours(hours: Int) {
        viewModelScope.launch {
            applyScanPreferenceSnapshot(settingsPreferenceActions.setAutoScanIntervalHours(hours))
        }
    }

    fun setMergeSameAnimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            applyScanPreferenceSnapshot(settingsPreferenceActions.setMergeSameAnimeEnabled(enabled))
        }
    }

    fun setPlaybackEndAction(action: PlaybackEndAction) {
        viewModelScope.launch {
            _playbackEndAction.value = settingsPreferenceActions.setPlaybackEndAction(action)
        }
    }

    fun setWebControlEnabled(enabled: Boolean) {
        updateWebControlSnapshot {
            webControlActions.setEnabled(enabled)
        }
    }

    fun rotateWebControlAccessToken() {
        updateWebControlSnapshot {
            webControlActions.rotateAccessToken()
        }
    }

    fun refreshWebUiUrls() {
        updateWebControlSnapshot {
            webControlActions.refreshUrls()
        }
    }

    private fun updateWebControlSnapshot(snapshotProvider: () -> WebControlAccessSnapshot) {
        viewModelScope.launch(Dispatchers.IO) {
            applyWebControlSnapshot(snapshotProvider())
        }
    }

    private fun applyWebControlSnapshot(snapshot: WebControlAccessSnapshot) {
        _webControlEnabled.value = snapshot.enabled
        _webControlAccessToken.value = snapshot.accessToken
        _webUiUrls.value = snapshot.urls
    }

    private fun applyScanPreferenceSnapshot(snapshot: ScanPreferenceActionSnapshot) {
        _autoScanEnabled.value = snapshot.autoScanEnabled
        _autoScanIntervalHours.value = snapshot.autoScanIntervalHours
        _lastScanAt.value = snapshot.lastScanAt
        _mergeSameAnimeEnabled.value = snapshot.mergeSameAnimeEnabled
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
