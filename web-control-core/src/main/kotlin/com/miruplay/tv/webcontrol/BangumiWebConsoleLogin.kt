package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.metadataBangumiOAuthFailedMessage
import com.miruplay.tv.model.metadataBangumiOAuthNotConfiguredMessage
import com.miruplay.tv.model.metadataBangumiOAuthSuccessMessage
import com.miruplay.tv.scraper.core.BangumiHttpProxyConfig
import com.miruplay.tv.scraper.core.BangumiOAuthConstants
import com.miruplay.tv.scraper.core.BangumiPasswordLoginApi
import com.miruplay.tv.scraper.core.BangumiPasswordLoginClientFactory
import com.miruplay.tv.scraper.core.BangumiPasswordLoginStep
import com.miruplay.tv.sync.BangumiOAuthCoordinator
import com.miruplay.tv.sync.BangumiOAuthState
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Web-console Bangumi login.
 *
 * The browser only talks to this device. The device loads the captcha and posts
 * the password to Bangumi, then exchanges the loopback code through the same
 * OAuth coordinator the TV uses. The password is not written to preferences.
 */
class BangumiWebConsoleLogin(
    private val oauth: BangumiOAuthCoordinator,
    private val newClient: BangumiPasswordLoginClientFactory,
    private val proxyConfig: suspend () -> BangumiHttpProxyConfig,
    private val tokenConfigured: () -> Boolean,
    private val expiresAtMillis: () -> Long,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var session: BangumiPasswordLoginApi? = null
    private var sessionStartedAtMillis: Long = 0L

    suspend fun start(): BangumiWebLoginDto = mutex.withLock {
        val authorizeUrl = oauth.startAuthorize()
        if (authorizeUrl.isNullOrBlank()) {
            session = null
            return@withLock snapshot(
                status = NOT_CONFIGURED,
                message = metadataBangumiOAuthNotConfiguredMessage().fallback,
            )
        }
        val client = newClient.create()
        client.configureProxy(proxyConfig())
        when (val step = client.startChallenge()) {
            is BangumiPasswordLoginStep.Challenge -> {
                session = client
                sessionStartedAtMillis = clockMillis()
                snapshot(status = CHALLENGE, captcha = step)
            }
            is BangumiPasswordLoginStep.Failed -> {
                oauth.cancel()
                session = null
                snapshot(status = FAILED, message = step.message)
            }
            else -> {
                oauth.cancel()
                session = null
                snapshot(status = FAILED, message = "无法获取 Bangumi 验证码。")
            }
        }
    }

    suspend fun refreshCaptcha(): BangumiWebLoginDto = mutex.withLock {
        val client = liveSession() ?: return@withLock snapshot(status = FAILED, message = "请先获取验证码")
        client.configureProxy(proxyConfig())
        when (val step = client.refreshCaptcha()) {
            is BangumiPasswordLoginStep.Challenge -> snapshot(status = CHALLENGE, captcha = step)
            is BangumiPasswordLoginStep.Failed -> snapshot(status = FAILED, message = step.message)
            else -> snapshot(status = FAILED, message = "无法刷新验证码。")
        }
    }

    suspend fun submit(email: String, password: String, captcha: String): BangumiWebLoginDto = mutex.withLock {
        val trimmedEmail = email.trim()
        val trimmedCaptcha = captcha.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty() || trimmedCaptcha.isEmpty()) {
            throw IllegalArgumentException("请填写邮箱、密码和验证码")
        }
        if (trimmedEmail.length > 320 || password.length > 256 || trimmedCaptcha.length > 64) {
            throw IllegalArgumentException("邮箱、密码或验证码过长")
        }
        val client = liveSession() ?: return@withLock snapshot(status = FAILED, message = "验证码已过期，请重新获取")
        val authorizing = oauth.state.value as? BangumiOAuthState.Authorizing
            ?: return@withLock snapshot(status = FAILED, message = "登录已过期，请重新获取验证码")
        val clientId = BangumiOAuthConstants.queryParameter(authorizing.url, "client_id").orEmpty()
        val state = BangumiOAuthConstants.queryParameter(authorizing.url, "state").orEmpty()
        if (clientId.isBlank() || state.isBlank()) {
            return@withLock snapshot(status = FAILED, message = "登录已过期，请重新获取验证码")
        }
        client.configureProxy(proxyConfig())
        when (
            val step = client.loginAndAuthorize(
                email = trimmedEmail,
                password = password,
                captcha = trimmedCaptcha,
                clientId = clientId,
                redirectUri = BangumiOAuthConstants.CALLBACK_URL,
                state = state,
            )
        ) {
            is BangumiPasswordLoginStep.Authorized -> {
                val accepted = oauth.submitCallbackUrl(step.callbackUrl)
                session = null
                if (!accepted) {
                    snapshot(status = FAILED, message = "授权校验失败，请重新登录")
                } else {
                    when (val oauthState = oauth.state.value) {
                        BangumiOAuthState.Success ->
                            snapshot(status = SUCCESS, message = metadataBangumiOAuthSuccessMessage().fallback)
                        is BangumiOAuthState.Failed ->
                            snapshot(status = FAILED, message = oauthState.message.fallback)
                        else -> snapshot(status = FAILED, message = metadataBangumiOAuthFailedMessage().fallback)
                    }
                }
            }
            is BangumiPasswordLoginStep.Retry ->
                snapshot(
                    status = CHALLENGE,
                    message = step.message,
                    captchaImage = dataUrl(step.image, step.contentType),
                )
            is BangumiPasswordLoginStep.Challenge -> snapshot(status = CHALLENGE, captcha = step)
            is BangumiPasswordLoginStep.Failed -> {
                oauth.cancel()
                session = null
                snapshot(status = FAILED, message = step.message)
            }
        }
    }

    suspend fun cancel(): BangumiWebLoginDto = mutex.withLock {
        session = null
        oauth.cancel()
        snapshot(status = IDLE, message = null)
    }

    suspend fun logout(): BangumiWebLoginDto = mutex.withLock {
        session = null
        oauth.logout()
        snapshot(status = IDLE, message = null)
    }

    private fun liveSession(): BangumiPasswordLoginApi? {
        val current = session ?: return null
        if (clockMillis() - sessionStartedAtMillis > SESSION_TTL_MILLIS) {
            session = null
            oauth.cancel()
            return null
        }
        return current
    }

    private fun snapshot(
        status: String,
        message: String? = null,
        captcha: BangumiPasswordLoginStep.Challenge? = null,
        captchaImage: String? = captcha?.let { dataUrl(it.image, it.contentType) },
    ): BangumiWebLoginDto = BangumiWebLoginDto(
        status = status,
        message = message,
        captchaImage = captchaImage,
        tokenConfigured = tokenConfigured(),
        expiresAtMillis = expiresAtMillis(),
    )

    companion object {
        const val IDLE = "idle"
        const val CHALLENGE = "challenge"
        const val SUCCESS = "success"
        const val FAILED = "failed"
        const val NOT_CONFIGURED = "not_configured"
        const val SESSION_TTL_MILLIS = 10L * 60L * 1000L

        private fun dataUrl(bytes: ByteArray, contentType: String): String {
            val mime = contentType.substringBefore(';').trim().ifBlank { "image/gif" }
            return "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
        }
    }
}
