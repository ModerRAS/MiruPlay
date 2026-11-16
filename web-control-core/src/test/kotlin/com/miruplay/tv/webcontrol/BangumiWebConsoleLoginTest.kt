package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.scraper.core.BangumiHttpProxyConfig
import com.miruplay.tv.scraper.core.BangumiOAuthClientApi
import com.miruplay.tv.scraper.core.BangumiOAuthTokens
import com.miruplay.tv.scraper.core.BangumiPasswordLoginApi
import com.miruplay.tv.scraper.core.BangumiPasswordLoginClientFactory
import com.miruplay.tv.scraper.core.BangumiPasswordLoginStep
import com.miruplay.tv.sync.BangumiOAuthCoordinator
import com.miruplay.tv.sync.BangumiOAuthLoopbackFactory
import com.miruplay.tv.sync.BangumiOAuthLoopbackHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiWebConsoleLoginTest {
    @Test
    fun `start returns captcha and submit exchanges the callback without echoing the password`() = runBlocking {
        val password = "s3cret-token-value"
        val harness = harness()
        val started = harness.login.start()
        assertEquals(BangumiWebConsoleLogin.CHALLENGE, started.status)
        assertTrue(started.captchaImage!!.startsWith("data:image/gif;base64,"))
        assertFalse(started.captchaImage!!.contains(password))

        val done = harness.login.submit("user@example.com", password, "abcd")
        assertEquals(BangumiWebConsoleLogin.SUCCESS, done.status)
        assertEquals("Bangumi 登录成功。", done.message)
        assertEquals(true, done.tokenConfigured)
        assertEquals(9_999_999_999L, done.expiresAtMillis)
        assertEquals("access-1", harness.credentials.bangumiAccessToken)
        assertEquals(password, harness.api.lastPassword)
        assertFalse(done.message.orEmpty().contains(password))
        assertNull(done.captchaImage)
    }

    @Test
    fun `wrong captcha keeps the session and a later submit can succeed`() = runBlocking {
        val harness = harness()
        harness.api.mode = LoginMode.RETRY
        harness.login.start()
        val retry = harness.login.submit("user@example.com", "pw", "bad")
        assertEquals(BangumiWebConsoleLogin.CHALLENGE, retry.status)
        assertEquals("验证码错误，请返回重试。", retry.message)
        assertTrue(retry.captchaImage!!.contains("base64,"))
        assertNull(harness.credentials.bangumiAccessToken)

        harness.api.mode = LoginMode.AUTHORIZED
        val done = harness.login.submit("user@example.com", "pw", "ok")
        assertEquals(BangumiWebConsoleLogin.SUCCESS, done.status)
        assertEquals("access-1", harness.credentials.bangumiAccessToken)
    }

    @Test
    fun `blank fields are rejected before any Bangumi call`() = runBlocking {
        val harness = harness()
        harness.login.start()
        val error = runCatching { harness.login.submit("  ", "pw", "code") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("请填写邮箱、密码和验证码", error?.message)
        assertEquals(0, harness.api.authorizeCalls)
    }

    @Test
    fun `missing oauth client reports not configured`() = runBlocking {
        val harness = harness(configured = false)
        val started = harness.login.start()
        assertEquals(BangumiWebConsoleLogin.NOT_CONFIGURED, started.status)
        assertEquals(0, harness.createdClients)
    }

    @Test
    fun `cancel and expiry both require a fresh captcha`() = runBlocking {
        val clock = Clock()
        val harness = harness(clock = clock)
        harness.login.start()
        harness.login.cancel()
        val cancelled = harness.login.submit("user@example.com", "pw", "code")
        assertEquals("验证码已过期，请重新获取", cancelled.message)

        harness.login.start()
        clock.now += BangumiWebConsoleLogin.SESSION_TTL_MILLIS + 1
        val expired = harness.login.submit("user@example.com", "pw", "code")
        assertEquals("验证码已过期，请重新获取", expired.message)
        assertNull(harness.credentials.bangumiAccessToken)
    }

    @Test
    fun `logout clears a saved token`() = runBlocking {
        val harness = harness()
        harness.login.start()
        harness.login.submit("user@example.com", "pw", "code")
        harness.login.logout()
        assertNull(harness.credentials.bangumiAccessToken)
        assertEquals(0L, harness.credentials.bangumiTokenExpiresAtMillis)
    }

    private fun harness(configured: Boolean = true, clock: Clock = Clock()): Harness {
        val credentials = Credentials()
        val oauthClient = OAuthClient(isConfigured = configured)
        var created = 0
        val api = LoginApi()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = BangumiOAuthCoordinator(
            credentials = credentials,
            client = oauthClient,
            loopbackFactory = BangumiOAuthLoopbackFactory {
                object : BangumiOAuthLoopbackHandle {
                    override fun start() = Unit
                    override fun stop() = Unit
                }
            },
            scope = scope,
            clockMillis = { clock.now },
            newState = { "state-1" },
        )
        val login = BangumiWebConsoleLogin(
            oauth = coordinator,
            newClient = BangumiPasswordLoginClientFactory {
                created += 1
                api
            },
            proxyConfig = { BangumiHttpProxyConfig.Disabled },
            tokenConfigured = { !credentials.bangumiAccessToken.isNullOrBlank() },
            expiresAtMillis = { credentials.bangumiTokenExpiresAtMillis },
            clockMillis = { clock.now },
        )
        return Harness(login, credentials, api, createdClients = { created })
    }

    private class Clock(var now: Long = 1_000L)

    private class Harness(
        val login: BangumiWebConsoleLogin,
        val credentials: Credentials,
        val api: LoginApi,
        private val createdClients: () -> Int,
    ) {
        val createdClientsCount: Int get() = createdClients()
    }

    private val Harness.createdClients: Int get() = createdClientsCount

    private enum class LoginMode { AUTHORIZED, RETRY }

    private class LoginApi : BangumiPasswordLoginApi {
        var mode = LoginMode.AUTHORIZED
        var lastPassword: String? = null
        var authorizeCalls = 0

        override fun configureProxy(proxyConfig: BangumiHttpProxyConfig) = Unit

        override suspend fun startChallenge(): BangumiPasswordLoginStep =
            BangumiPasswordLoginStep.Challenge(byteArrayOf(1, 2, 3), "image/gif")

        override suspend fun refreshCaptcha(): BangumiPasswordLoginStep =
            BangumiPasswordLoginStep.Challenge(byteArrayOf(9), "image/gif")

        override suspend fun loginAndAuthorize(
            email: String,
            password: String,
            captcha: String,
            clientId: String,
            redirectUri: String,
            state: String,
        ): BangumiPasswordLoginStep {
            authorizeCalls += 1
            lastPassword = password
            return when (mode) {
                LoginMode.AUTHORIZED -> BangumiPasswordLoginStep.Authorized(
                    "http://127.0.0.1:13712/callback?code=auth-code&state=$state",
                )
                LoginMode.RETRY -> BangumiPasswordLoginStep.Retry(
                    "验证码错误，请返回重试。",
                    byteArrayOf(4),
                    "image/gif",
                )
            }
        }
    }

    private class OAuthClient(override val isConfigured: Boolean) : BangumiOAuthClientApi {
        override val clientId: String = "test-id"
        override fun configureProxy(proxyConfig: BangumiHttpProxyConfig) = Unit
        override suspend fun exchangeCode(code: String, callbackUrl: String): Result<BangumiOAuthTokens> =
            Result.success(BangumiOAuthTokens("access-1", "refresh-1", 9_999_999_999L, 7L))
        override suspend fun refresh(refreshToken: String, callbackUrl: String): Result<BangumiOAuthTokens> =
            Result.success(BangumiOAuthTokens("access-2", "refresh-2", 9_999_999_999L, 7L))
    }

    private class Credentials : AppCredentialStore {
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
}
