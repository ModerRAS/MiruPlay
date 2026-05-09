package com.miruplay.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.secure.SecurePreferencesManager
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaSourceFactory: MediaSourceFactory,
    private val securePrefs: SecurePreferencesManager
) : ViewModel() {

    private val _sources = MutableStateFlow<List<MediaSourceInfo>>(emptyList())
    val sources: StateFlow<List<MediaSourceInfo>> = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val testResult: StateFlow<ConnectionTestResult?> = _testResult.asStateFlow()

    private val _bangumiToken = MutableStateFlow(securePrefs.bangumiAccessToken ?: "")
    val bangumiToken: StateFlow<String> = _bangumiToken.asStateFlow()

    init {
        loadSources()
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

    fun clearTestResult() {
        _testResult.value = null
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