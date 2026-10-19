package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object BangumiOAuthConstants {
    const val AUTHORIZE_URL = "https://bgm.tv/oauth/authorize"
    const val TOKEN_URL = "https://bgm.tv/oauth/access_token"

    const val CALLBACK_PORT = 13712
    const val CALLBACK_PATH = "/callback"
    const val CALLBACK_URL = "http://127.0.0.1:$CALLBACK_PORT$CALLBACK_PATH"

    fun authorizeUrl(
        clientId: String,
        state: String,
        callbackUrl: String = CALLBACK_URL,
    ): String =
        "$AUTHORIZE_URL?client_id=${clientId.urlEncode()}" +
            "&response_type=code" +
            "&state=${state.urlEncode()}" +
            "&redirect_uri=${callbackUrl.urlEncode()}"

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())
}

@Serializable
data class BangumiOAuthTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_in") val expiresInSeconds: Long = 0L,
    @SerialName("token_type") val tokenType: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("user_id") val userId: Long = 0L,
)

data class BangumiOAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch millis at which the access token expires. */
    val expiresAtMillis: Long,
    val userId: Long = 0L,
)

interface BangumiOAuthClientApi {
    val isConfigured: Boolean
    val clientId: String
    fun configureProxy(proxyConfig: BangumiHttpProxyConfig)
    suspend fun exchangeCode(
        code: String,
        callbackUrl: String = BangumiOAuthConstants.CALLBACK_URL,
    ): Result<BangumiOAuthTokens>

    suspend fun refresh(
        refreshToken: String,
        callbackUrl: String = BangumiOAuthConstants.CALLBACK_URL,
    ): Result<BangumiOAuthTokens>
}

/**
 * Direct OAuth2 client for Bangumi (no third-party relay involved).
 *
 * Flow: open [BangumiOAuthConstants.authorizeUrl] in a browser/WebView, Bangumi
 * redirects to the loopback [BangumiOAuthConstants.CALLBACK_URL] with
 * `?code=..&state=..`, then [exchangeCode] swaps the code for tokens.
 * Access tokens live 7 days (`expires_in` 604800); use [refresh] to rotate.
 */
class BangumiOAuthClient(
    override val clientId: String,
    private val clientSecret: String,
    httpClient: OkHttpClient? = null,
    private val tokenUrl: String = BangumiOAuthConstants.TOKEN_URL,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : BangumiOAuthClientApi {
    private val proxyClient = BangumiProxyAwareOkHttpClient(
        httpClient ?: defaultClient(),
    )
    private val json = Json { ignoreUnknownKeys = true }

    override val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    override fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        proxyClient.configureProxy(proxyConfig)
    }

    override suspend fun exchangeCode(
        code: String,
        callbackUrl: String,
    ): Result<BangumiOAuthTokens> = requestToken(
        grantType = "authorization_code",
        callbackUrl = callbackUrl,
    ) {
        add("code", code)
    }

    override suspend fun refresh(
        refreshToken: String,
        callbackUrl: String,
    ): Result<BangumiOAuthTokens> = requestToken(
        grantType = "refresh_token",
        callbackUrl = callbackUrl,
    ) {
        add("refresh_token", refreshToken)
    }

    private suspend fun requestToken(
        grantType: String,
        callbackUrl: String,
        extra: FormBody.Builder.() -> Unit,
    ): Result<BangumiOAuthTokens> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured) {
                return@withContext Result.failure(
                    AppError.ScrapingError.ApiError(SOURCE_NAME, "Bangumi OAuth 未配置 client_id/secret"),
                )
            }
            val body = FormBody.Builder()
                .add("grant_type", grantType)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", callbackUrl)
                .apply(extra)
                .build()
            val request = Request.Builder()
                .url(tokenUrl)
                .post(body)
                .addHeader("User-Agent", DEFAULT_USER_AGENT)
                .addHeader("Accept", "application/json")
                .build()
            proxyClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        AppError.NetworkError.HttpError(response.code, text.ifBlank { response.message }),
                    )
                }
                val parsed = json.decodeFromString<BangumiOAuthTokenResponse>(text)
                if (parsed.accessToken.isBlank()) {
                    return@withContext Result.failure(
                        AppError.ScrapingError.ApiError(SOURCE_NAME, "Bangumi 换 token 响应缺 access_token"),
                    )
                }
                Result.success(
                    BangumiOAuthTokens(
                        accessToken = parsed.accessToken,
                        refreshToken = parsed.refreshToken,
                        expiresAtMillis = clockMillis() + parsed.expiresInSeconds * 1000L,
                        userId = parsed.userId,
                    )
                )
            }
        } catch (error: Exception) {
            Result.failure(AppError.NetworkError.ServerUnreachable(tokenUrl))
        }
    }

    companion object {
        const val SOURCE_NAME = "Bangumi"
        private const val DEFAULT_USER_AGENT = "ModerRAS/MiruPlay/0.1.0 (Android TV) (https://github.com/ModerRAS/MiruPlay)"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
