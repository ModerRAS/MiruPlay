package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.scraper.core.BangumiHttpProxyConfig
import com.miruplay.tv.scraper.core.BangumiOAuthClientApi
import com.miruplay.tv.scraper.core.BangumiOAuthTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCredentials : AppCredentialStore {
    override var cloudDriveToken: String? = null
    override var cloudDrivePassword: String? = null
    override fun clearCloudDriveCredentials() {
        cloudDriveToken = null
        cloudDrivePassword = null
    }

    override var bangumiAccessToken: String? = null
    override var bangumiRefreshToken: String? = null
    override var bangumiTokenExpiresAtMillis: Long = 0L
    override var tmdbAccessToken: String? = null
    override var tmdbApiBaseUrlOverride: String? = null
    override var otlpAccessToken: String? = null
    override fun clearBangumiToken() {
        bangumiAccessToken = null
        bangumiRefreshToken = null
        bangumiTokenExpiresAtMillis = 0L
    }

    override fun clearTmdbToken() {
        tmdbAccessToken = null
        tmdbApiBaseUrlOverride = null
    }

    override fun clearOtlpAccessToken() {
        otlpAccessToken = null
    }
}

private class FakeOAuthClient(
    override val isConfigured: Boolean = true,
    override val clientId: String = "test-id",
    var exchangeResult: Result<BangumiOAuthTokens> =
        Result.success(BangumiOAuthTokens("access-1", "refresh-1", 9_999_999_999L, 7L)),
    var refreshResult: Result<BangumiOAuthTokens> =
        Result.success(BangumiOAuthTokens("access-2", "refresh-2", 9_999_999_999L, 7L)),
) : BangumiOAuthClientApi {
    var lastExchangeCode: String? = null
    var lastRefreshToken: String? = null
    var lastProxyEnabled: Boolean? = null

    override fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        lastProxyEnabled = proxyConfig.enabled
    }

    override suspend fun exchangeCode(code: String, callbackUrl: String): Result<BangumiOAuthTokens> {
        lastExchangeCode = code
        return exchangeResult
    }

    override suspend fun refresh(refreshToken: String, callbackUrl: String): Result<BangumiOAuthTokens> {
        lastRefreshToken = refreshToken
        return refreshResult
    }
}

private class FakeLoopback(val accepted: MutableList<String> = mutableListOf()) :
    BangumiOAuthLoopbackHandle {
    var started = false
    var stopped = false
    override fun start() {
        started = true
    }

    override fun stop() {
        stopped = true
    }
}

class BangumiOAuthCoordinatorTest {
    private fun coordinator(
        credentials: FakeCredentials = FakeCredentials(),
        client: FakeOAuthClient = FakeOAuthClient(),
        loopbacks: MutableList<FakeLoopback> = mutableListOf(),
        clockMillis: () -> Long = { 1_000L },
    ): Triple<BangumiOAuthCoordinator, FakeCredentials, FakeOAuthClient> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = BangumiOAuthCoordinator(
            credentials = credentials,
            client = client,
            loopbackFactory = BangumiOAuthLoopbackFactory { _ ->
                FakeLoopback().also { loopbacks += it }
            },
            scope = scope,
            clockMillis = clockMillis,
        )
        return Triple(coordinator, credentials, client)
    }

    @Test
    fun `unconfigured client reports NotConfigured without url`() = runBlocking {
        val (coordinator, _, _) = coordinator(client = FakeOAuthClient(isConfigured = false))

        assertNull(coordinator.startAuthorize())
        assertTrue(coordinator.state.value is BangumiOAuthState.NotConfigured)
    }

    @Test
    fun `startAuthorize emits url and wrong state callback is rejected`() = runBlocking {
        val (coordinator, _, _) = coordinator()

        val url = coordinator.startAuthorize()
        assertTrue(url!!.contains("https://bgm.tv/oauth/authorize?"))
        assertTrue(coordinator.state.value is BangumiOAuthState.Authorizing)

        assertFalse(coordinator.submitCallbackUrl("http://127.0.0.1:13712/callback?code=x&state=wrong"))
        assertTrue(coordinator.state.value is BangumiOAuthState.Authorizing)
    }

    @Test
    fun `matching callback exchanges code and persists tokens`() = runBlocking {
        val (coordinator, credentials, client) = coordinator()
        val url = coordinator.startAuthorize()!!
        val state = url.substringAfter("state=").substringBefore("&")

        assertTrue(coordinator.submitCallbackUrl("http://127.0.0.1:13712/callback?code=auth-code&state=$state"))
        assertEquals("auth-code", client.lastExchangeCode)
        assertEquals("access-1", credentials.bangumiAccessToken)
        assertEquals("refresh-1", credentials.bangumiRefreshToken)
        assertEquals(9_999_999_999L, credentials.bangumiTokenExpiresAtMillis)
        assertTrue(coordinator.state.value is BangumiOAuthState.Success)
    }

    @Test
    fun `failed exchange surfaces Failed state`() = runBlocking {
        val (coordinator, _, client) = coordinator()
        client.exchangeResult = Result.failure(
            com.miruplay.tv.core.common.AppError.NetworkError.HttpError(400, "bad code")
        )
        val url = coordinator.startAuthorize()!!
        val state = url.substringAfter("state=").substringBefore("&")

        assertTrue(coordinator.submitCallbackUrl("http://127.0.0.1:13712/callback?code=bad&state=$state"))
        assertTrue(coordinator.state.value is BangumiOAuthState.Failed)
    }

    @Test
    fun `ensureValidToken returns fresh token without refresh`() = runBlocking {
        val credentials = FakeCredentials().apply {
            bangumiAccessToken = "still-good"
            bangumiTokenExpiresAtMillis = 9_999_999_999L
        }
        val (coordinator, _, client) = coordinator(credentials = credentials)

        assertEquals("still-good", coordinator.ensureValidToken())
        assertNull(client.lastRefreshToken)
    }

    @Test
    fun `ensureValidToken refreshes near expiry`() = runBlocking {
        val credentials = FakeCredentials().apply {
            bangumiAccessToken = "almost-dead"
            bangumiRefreshToken = "refresh-1"
            bangumiTokenExpiresAtMillis = 2_000L
        }
        val (coordinator, _, client) = coordinator(credentials = credentials, clockMillis = { 1_000L })

        assertEquals("access-2", coordinator.ensureValidToken())
        assertEquals("refresh-1", client.lastRefreshToken)
        assertEquals("access-2", credentials.bangumiAccessToken)
    }

    @Test
    fun `ensureValidToken returns null when logged out`() = runBlocking {
        val (coordinator, _, _) = coordinator()
        assertNull(coordinator.ensureValidToken())
    }

    @Test
    fun `logout clears tokens and resets state`() = runBlocking {
        val (coordinator, credentials, _) = coordinator()
        coordinator.startAuthorize()
        coordinator.logout()

        assertNull(credentials.bangumiAccessToken)
        assertTrue(coordinator.state.value is BangumiOAuthState.Idle)
    }
}
