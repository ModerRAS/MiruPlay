package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.model.metadataBangumiOAuthFailedMessage
import com.miruplay.tv.model.metadataBangumiOAuthNotConfiguredMessage
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.scraper.core.BangumiHttpProxyConfig
import com.miruplay.tv.scraper.core.BangumiOAuthClientApi
import com.miruplay.tv.scraper.core.BangumiOAuthConstants
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BangumiOAuthState {
    data object Idle : BangumiOAuthState
    data object NotConfigured : BangumiOAuthState
    data class Authorizing(val url: String) : BangumiOAuthState
    data object Exchanging : BangumiOAuthState
    data object Success : BangumiOAuthState
    data class Failed(val message: UiText) : BangumiOAuthState
}

fun interface BangumiOAuthLoopbackFactory {
    fun create(onCallback: (callbackUrl: String) -> Unit): BangumiOAuthLoopbackHandle
}

interface BangumiOAuthLoopbackHandle {
    fun start()
    fun stop()
}

/**
 * App-side driver of the direct Bangumi OAuth flow.
 *
 * TV opens [BangumiOAuthConstants.authorizeUrl], the user approves in a
 * browser/WebView, Bangumi redirects to the loopback callback and
 * [submitCallbackUrl] exchanges the code. Tokens (access 7 days + refresh)
 * persist in [credentials]; [ensureValidToken] rotates them proactively.
 */
class BangumiOAuthCoordinator(
    private val credentials: AppCredentialStore,
    private val client: BangumiOAuthClientApi?,
    private val loopbackFactory: BangumiOAuthLoopbackFactory = BangumiOAuthLoopbackFactory { onCallback ->
        object : BangumiOAuthLoopbackHandle {
            private val server = BangumiOAuthLoopbackServer(
                port = BangumiOAuthConstants.CALLBACK_PORT,
                path = BangumiOAuthConstants.CALLBACK_PATH,
                onCallback = onCallback,
            )

            override fun start() = server.start()
            override fun stop() = server.stop()
        }
    },
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val newState: () -> String = { UUID.randomUUID().toString() },
    private val proxyConfig: suspend () -> BangumiHttpProxyConfig = { BangumiHttpProxyConfig.Disabled },
) {
    private val _state = MutableStateFlow<BangumiOAuthState>(BangumiOAuthState.Idle)
    val state: StateFlow<BangumiOAuthState> = _state.asStateFlow()

    private var pendingState: String? = null
    private var loopback: BangumiOAuthLoopbackHandle? = null
    private var loopbackJob: Job? = null

    val isConfigured: Boolean
        get() = client != null && client.isConfigured

    /** Starts an authorize flow and returns the URL to open. Null when unconfigured. */
    fun startAuthorize(): String? {
        val oauthClient = client?.takeIf { it.isConfigured } ?: run {
            _state.value = BangumiOAuthState.NotConfigured
            return null
        }
        cancelLocked()
        val state = newState()
        pendingState = state
        val url = BangumiOAuthConstants.authorizeUrl(
            clientId = oauthClient.clientId,
            state = state,
        )
        _state.value = BangumiOAuthState.Authorizing(url)
        val handle = loopbackFactory.create { callbackUrl ->
            // Never await the exchange on the HTTP callback thread: stopping the
            // loopback server cancels its own job and would abort the token call.
            scope.launch { submitCallbackUrl(callbackUrl) }
        }
        loopback = handle
        loopbackJob = scope.launch {
            runCatching { handle.start() }
        }
        return url
    }

    /**
     * Accepts a redirected callback URL (`?code=..&state=..`).
     * @return true when the URL belonged to the pending flow.
     */
    suspend fun submitCallbackUrl(callbackUrl: String): Boolean {
        val expected = pendingState ?: return false
        val query = callbackUrl.substringAfter('?', "")
        val params = query.split('&')
            .mapNotNull {
                val key = it.substringBefore('=')
                val value = it.substringAfter('=', "")
                if (key.isNotBlank()) key to value else null
            }.toMap()
        if (params["state"] != expected) return false
        val code = params["code"].orEmpty()
        if (code.isBlank()) {
            _state.value = BangumiOAuthState.Failed(
                metadataBangumiOAuthFailedMessage("缺少授权码")
            )
            stopLoopback()
            return true
        }
        _state.value = BangumiOAuthState.Exchanging
        val oauthClient = client
        if (oauthClient == null) {
            _state.value = BangumiOAuthState.Failed(metadataBangumiOAuthNotConfiguredMessage())
            stopLoopback()
            return true
        }
        oauthClient.configureProxy(proxyConfig())
        when (val result = oauthClient.exchangeCode(code)) {
            is Result.Success -> {
                persist(result.data.accessToken, result.data.refreshToken, result.data.expiresAtMillis)
                pendingState = null
                _state.value = BangumiOAuthState.Success
            }
            is Result.Error -> {
                _state.value = BangumiOAuthState.Failed(metadataBangumiOAuthFailedMessage())
            }
        }
        stopLoopback()
        return true
    }

    fun cancel() {
        stopLoopback()
        pendingState = null
        _state.value = BangumiOAuthState.Idle
    }

    fun logout() {
        stopLoopback()
        pendingState = null
        credentials.clearBangumiToken()
        _state.value = BangumiOAuthState.Idle
    }

    /**
     * Returns a usable access token, refreshing proactively when the stored
     * one expires within [refreshMarginMillis]. Null when logged out or when
     * the refresh fails and nothing valid remains.
     */
    suspend fun ensureValidToken(refreshMarginMillis: Long = REFRESH_MARGIN_MILLIS): String? {
        val current = credentials.bangumiAccessToken?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = credentials.bangumiTokenExpiresAtMillis
        if (expiresAt <= 0L || expiresAt - clockMillis() > refreshMarginMillis) {
            return current
        }
        val refreshToken = credentials.bangumiRefreshToken?.takeIf { it.isNotBlank() }
            ?: return current.takeIf { expiresAt > clockMillis() }
        val oauthClient = client?.takeIf { it.isConfigured } ?: return current
        oauthClient.configureProxy(proxyConfig())
        return when (val result = oauthClient.refresh(refreshToken)) {
            is Result.Success -> {
                persist(result.data.accessToken, result.data.refreshToken, result.data.expiresAtMillis)
                result.data.accessToken
            }
            is Result.Error -> current.takeIf { expiresAt > clockMillis() }
        }
    }

    private fun persist(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        credentials.bangumiAccessToken = accessToken
        credentials.bangumiRefreshToken = refreshToken.ifBlank { credentials.bangumiRefreshToken }
        credentials.bangumiTokenExpiresAtMillis = expiresAtMillis
    }

    private fun stopLoopback() {
        loopbackJob?.cancel()
        loopbackJob = null
        runCatching { loopback?.stop() }
        loopback = null
    }

    private fun cancelLocked() {
        stopLoopback()
        pendingState = null
    }

    companion object {
        const val REFRESH_MARGIN_MILLIS = 24L * 60L * 60L * 1000L
    }
}
