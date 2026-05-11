package com.miruplay.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.data.repository.CloudDriveAutomationRepository
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.secure.SecurePreferencesManager
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
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
    private val mediaRepository: MediaRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val securePrefs: SecurePreferencesManager,
    private val scanPreferences: ScanPreferencesManager,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val cloudDriveEngine: CloudDriveRssAutomationEngine
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

    private val _webUiUrls = MutableStateFlow<List<String>>(emptyList())
    val webUiUrls: StateFlow<List<String>> = _webUiUrls.asStateFlow()

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

    init {
        loadSources()
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
                loadSources()
            }
        }
    }

    fun updateSource(source: MediaSourceInfo) {
        viewModelScope.launch {
            val existing = mediaRepository.getSourceById(source.id).getOrNull()
            val mergedSource = if (
                "password" !in source.connectionInfo &&
                existing?.connectionInfo?.containsKey("password") == true
            ) {
                source.copy(
                    connectionInfo = source.connectionInfo + (
                        "password" to existing.connectionInfo.getValue("password")
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
                connectionInfo = buildMap {
                    put("url", url)
                    if (username.isNotBlank()) put("username", username)
                    if (password.isNotBlank()) put("password", password)
                }
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
        securePrefs.bangumiAccessToken = token
        _bangumiToken.value = token
    }

    fun clearBangumiToken() {
        securePrefs.clearBangumiToken()
        _bangumiToken.value = ""
    }

    fun saveCloudDriveConfig(
        endpointUrl: String,
        username: String,
        webDavSourceId: Long?,
        inboxPath: String,
        libraryPath: String,
        intervalMinutes: Int,
        enabled: Boolean
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
                enabled = enabled
            )
            cloudDriveRepository.saveConfig(config)
                .onSuccess { _cloudDriveActionMessage.value = "CloudDrive 设置已保存。" }
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun loginCloudDrive(endpointUrl: String, username: String, password: String) {
        if (endpointUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _cloudDriveActionMessage.value = "请填写 CloudDrive2 地址、账号和密码。"
            return
        }
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.login(endpointUrl.trim(), username.trim(), password)
                .onSuccess {
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value = "CloudDrive2 登录成功，令牌已保存。"
                }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun saveCloudDriveApiToken(endpointUrl: String, token: String) {
        if (endpointUrl.isBlank()) {
            _cloudDriveActionMessage.value = "请先填写 CloudDrive2 地址。"
            return
        }
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) {
            _cloudDriveActionMessage.value = "请填写 CloudDrive2 API Token 或 Key。"
            return
        }
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.saveApiToken(endpointUrl.trim(), normalizedToken)
                .onSuccess { info ->
                    _cloudDriveTokenConfigured.value = true
                    _cloudDriveActionMessage.value =
                        "CloudDrive2 API Token 已验证并保存，根目录 ${info.rootDir.ifBlank { "/" }}。"
                }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun addRssSubscription(name: String, url: String, filterRegex: String, enabled: Boolean) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            _cloudDriveActionMessage.value = "请填写 RSS 地址。"
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
                .onSuccess { _cloudDriveActionMessage.value = "RSS 订阅已保存。" }
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
                .onSuccess { _cloudDriveActionMessage.value = "RSS 订阅已删除。" }
                .onError { error -> _cloudDriveActionMessage.value = error.toUserMessage() }
        }
    }

    fun runCloudDriveNow() {
        viewModelScope.launch {
            _cloudDriveBusy.value = true
            cloudDriveEngine.runOnce()
                .onSuccess { summary ->
                    _cloudDriveActionMessage.value =
                        "本轮完成：提交 ${summary.submitted} 个，跳过 ${summary.skipped} 个，整理 ${summary.organized} 个，失败 ${summary.failed} 个。"
                }
                .onError { error ->
                    _cloudDriveActionMessage.value = error.toUserMessage()
                }
            _cloudDriveBusy.value = false
        }
    }

    fun setAutoScanEnabled(enabled: Boolean) {
        scanPreferences.autoScanEnabled = enabled
        _autoScanEnabled.value = enabled
    }

    fun setAutoScanIntervalHours(hours: Int) {
        scanPreferences.autoScanIntervalMs = hours * MILLIS_PER_HOUR
        _autoScanIntervalHours.value = hours
    }

    fun refreshWebUiUrls() {
        viewModelScope.launch(Dispatchers.IO) {
            _webUiUrls.value = findLocalIps().map { ip -> "http://$ip:${WebControlConfig.DEFAULT_PORT}" }
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
        private const val MIN_CLOUD_DRIVE_INTERVAL_MINUTES = 5
    }
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
