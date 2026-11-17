package com.miruplay.tv.scraper.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

fun interface BangumiPasswordLoginClientFactory {
    fun create(): BangumiPasswordLoginApi
}

interface BangumiPasswordLoginApi {
    fun configureProxy(proxyConfig: BangumiHttpProxyConfig)
    suspend fun startChallenge(): BangumiPasswordLoginStep
    suspend fun refreshCaptcha(): BangumiPasswordLoginStep
    suspend fun loginAndAuthorize(
        email: String,
        password: String,
        captcha: String,
        clientId: String,
        redirectUri: String,
        state: String,
    ): BangumiPasswordLoginStep
}

sealed interface BangumiPasswordLoginStep {
    data class Challenge(val image: ByteArray, val contentType: String) : BangumiPasswordLoginStep
    data class Retry(val message: String, val image: ByteArray, val contentType: String) : BangumiPasswordLoginStep
    data class Authorized(val callbackUrl: String) : BangumiPasswordLoginStep
    data class Failed(val message: String) : BangumiPasswordLoginStep
}

/**
 * Server-side Bangumi login used by the web console.
 *
 * The password is written only into the `FollowTheRabbit` form body for that
 * call. It is not stored on this session. Cookies and the login formhash stay
 * so a wrong captcha can be retried without asking Bangumi to start over.
 */
class BangumiPasswordLoginClient private constructor(
    httpClient: OkHttpClient?,
    private val baseUrl: String,
    private val userAgent: String,
    private val clockMillis: () -> Long,
    private val captchaSuffix: () -> Int,
) : BangumiPasswordLoginApi {
    constructor(
        baseUrl: String = DEFAULT_BASE_URL,
        userAgent: String = BangumiOAuthConstants.USER_AGENT,
        clockMillis: () -> Long = System::currentTimeMillis,
        captchaSuffix: () -> Int = { (1..6).random() },
    ) : this(
        httpClient = null,
        baseUrl = baseUrl,
        userAgent = userAgent,
        clockMillis = clockMillis,
        captchaSuffix = captchaSuffix,
    )
    private val cookieJar = MemoryCookieJar()
    private val proxyClient = BangumiProxyAwareOkHttpClient(
        (httpClient ?: defaultClient()).newBuilder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
    )
    private var loginAction: String = "/FollowTheRabbit"
    private var formhash: String = ""

    override fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        proxyClient.configureProxy(proxyConfig)
    }

    override suspend fun startChallenge(): BangumiPasswordLoginStep = guarded { loadLoginForm() }

    override suspend fun refreshCaptcha(): BangumiPasswordLoginStep = guarded {
        if (formhash.isBlank()) {
            BangumiPasswordLoginStep.Failed("请先获取验证码")
        } else {
            fetchCaptcha()
        }
    }

    override suspend fun loginAndAuthorize(
        email: String,
        password: String,
        captcha: String,
        clientId: String,
        redirectUri: String,
        state: String,
    ): BangumiPasswordLoginStep = guarded {
        if (formhash.isBlank()) return@guarded BangumiPasswordLoginStep.Failed("请先获取验证码")
        val posted = execute(
            Request.Builder()
                .url(resolveUrl(loginAction))
                .header("Referer", resolveUrl("/login"))
                .header("Origin", origin())
                .post(
                    FormBody.Builder()
                        .add("formhash", formhash)
                        .add("referer", "")
                        .add("dreferer", "")
                        .add("email", email)
                        .add("password", password)
                        .add("captcha_challenge_field", captcha)
                        .add("loginsubmit", "登录")
                        .build(),
                )
                .build(),
        )
        val landed = followSameSite(posted, redirectUri)
        landed.callbackUrl?.let { return@guarded BangumiPasswordLoginStep.Authorized(withOAuthState(it, state)) }
        if (!cookieJar.has("chii_auth")) {
            val message = parseBangumiNotice(landed.body) ?: "账号、密码或验证码不正确。"
            if (message.contains("不能登录")) return@guarded BangumiPasswordLoginStep.Failed(message)
            return@guarded when (val again = loadLoginForm()) {
                is BangumiPasswordLoginStep.Challenge ->
                    BangumiPasswordLoginStep.Retry(message, again.image, again.contentType)
                is BangumiPasswordLoginStep.Failed -> BangumiPasswordLoginStep.Failed(message)
                else -> BangumiPasswordLoginStep.Failed(message)
            }
        }

        val authorizeUrl = authorizeUrl(clientId, redirectUri, state)
        val authorize = execute(htmlGet(authorizeUrl))
        val authorizeLanded = followSameSite(authorize, redirectUri)
        authorizeLanded.callbackUrl?.let {
            return@guarded BangumiPasswordLoginStep.Authorized(withOAuthState(it, state))
        }
        if (authorizeLanded.body.contains("id=\"loginForm\"") || authorizeLanded.body.contains("id='loginForm'")) {
            return@guarded BangumiPasswordLoginStep.Failed("Bangumi 没有保持登录状态，请重试。")
        }
        val form = parseAuthorizeForm(authorizeLanded.body)
            ?: return@guarded BangumiPasswordLoginStep.Failed(
                parseBangumiNotice(authorizeLanded.body) ?: "Bangumi 授权页没有返回可提交的表单。",
            )
        // Bangumi 的授权表单没有 action 属性：真实浏览器会把表单提交回"当前页面 URL"，
        // 而 client_id / response_type / redirect_uri / state 全在那条 URL 的查询串里。
        // 提交到裸的 /oauth/authorize 会丢掉这些参数，Bangumi 直接回 app_nonexistence。
        val actionUrl = if (form.action.isBlank()) authorizeUrl else resolveAgainst(authorizeUrl, form.action)
        val submitted = execute(
            Request.Builder()
                .url(actionUrl)
                .header("Referer", authorizeUrl)
                .header("Origin", origin())
                .post(
                    FormBody.Builder().apply {
                        form.fields.forEach { (name, value) -> add(name, value) }
                    }.build(),
                )
                .build(),
        )
        val done = followSameSite(submitted, redirectUri)
        done.callbackUrl?.let { return@guarded BangumiPasswordLoginStep.Authorized(withOAuthState(it, state)) }
        BangumiPasswordLoginStep.Failed(parseBangumiNotice(done.body) ?: "Bangumi 授权没有返回验证码。")
    }

    private suspend fun guarded(block: () -> BangumiPasswordLoginStep): BangumiPasswordLoginStep =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                BangumiPasswordLoginStep.Failed("无法连接 Bangumi，请检查电视的出站代理或网络。")
            }
        }

    private fun loadLoginForm(): BangumiPasswordLoginStep {
        val page = execute(htmlGet(resolveUrl("/login")))
        if (page.code !in 200..299) {
            return BangumiPasswordLoginStep.Failed("Bangumi 登录页打开失败（HTTP ${page.code}）。")
        }
        val parsed = parseBangumiLoginForm(page.body)
            ?: return BangumiPasswordLoginStep.Failed("Bangumi 登录页没有返回表单。")
        loginAction = parsed.first
        formhash = parsed.second
        return fetchCaptcha()
    }

    private fun fetchCaptcha(): BangumiPasswordLoginStep {
        val suffix = captchaSuffix().coerceIn(1, 6)
        val page = execute(
            Request.Builder()
                .url(resolveUrl("/signup/captcha?${clockMillis()}$suffix"))
                .header("Referer", resolveUrl("/login"))
                .header("Accept", "image/gif,image/png,image/*;q=0.8")
                .get()
                .build(),
        )
        if (page.code !in 200..299 || page.bytes.isEmpty()) {
            return BangumiPasswordLoginStep.Failed("无法获取 Bangumi 验证码。")
        }
        if (page.bytes.size > MAX_BODY_BYTES) {
            return BangumiPasswordLoginStep.Failed("Bangumi 验证码响应过大。")
        }
        return BangumiPasswordLoginStep.Challenge(page.bytes, imageContentType(page.contentType))
    }

    private fun authorizeUrl(clientId: String, redirectUri: String, state: String): String =
        resolveUrl(
            "/oauth/authorize?client_id=${clientId.urlEncode()}" +
                "&response_type=code" +
                "&redirect_uri=${redirectUri.urlEncode()}" +
                "&state=${state.urlEncode()}",
        )

    private fun htmlGet(url: String): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()

    private fun followSameSite(initial: HttpResult, redirectUri: String): Landed {
        var current = initial
        repeat(MAX_REDIRECTS) {
            val location = current.location ?: return Landed(current.body, null)
            if (isBangumiOAuthCallback(location, redirectUri)) return Landed(current.body, location)
            if (!sameSite(location)) return Landed(current.body, null)
            current = execute(htmlGet(location))
        }
        return Landed(current.body, null)
    }

    private fun execute(request: Request): HttpResult {
        val decorated = request.newBuilder().header("User-Agent", userAgent).build()
        proxyClient.newCall(decorated).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.size > MAX_BODY_BYTES) {
                throw IllegalStateException("Bangumi 响应过大")
            }
            val type = response.header("Content-Type").orEmpty()
            val location = response.header("Location")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { response.request.url.resolve(it)?.toString() ?: it }
            val text = if (type.startsWith("image/")) "" else String(bytes, Charsets.UTF_8)
            return HttpResult(
                code = response.code,
                location = location,
                body = text,
                contentType = type,
                bytes = bytes,
            )
        }
    }

    private fun resolveUrl(reference: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return base.toHttpUrl().resolve(reference)?.toString()
            ?: throw IllegalStateException("无法解析 Bangumi 地址")
    }

    /** Resolves a form action the way a browser does: relative to the page that rendered it. */
    private fun resolveAgainst(pageUrl: String, reference: String): String =
        pageUrl.toHttpUrlOrNull()?.resolve(reference)?.toString() ?: resolveUrl(reference)

    private fun origin(): String = baseUrl.trimEnd('/')

    private fun sameSite(url: String): Boolean {
        val base = origin().toHttpUrl()
        val target = url.toHttpUrlOrNull() ?: return false
        return target.scheme == base.scheme && target.host == base.host && target.port == base.port
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private data class HttpResult(
        val code: Int,
        val location: String?,
        val body: String,
        val contentType: String,
        val bytes: ByteArray,
    )

    private data class Landed(val body: String, val callbackUrl: String?)

    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
            synchronized(cookies) {
                val now = System.currentTimeMillis()
                for (cookie in newCookies) {
                    cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                    if (cookie.expiresAt > now && cookie.value.isNotEmpty()) cookies += cookie
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
            cookies.filter { it.matches(url) }
        }

        fun has(name: String): Boolean = synchronized(cookies) {
            cookies.any { it.name == name && it.value.isNotBlank() }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://bgm.tv"
        private const val MAX_REDIRECTS = 5
        private const val MAX_BODY_BYTES = 2_000_000

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}

internal fun parseBangumiLoginForm(html: String): Pair<String, String>? {
    val form = Jsoup.parse(html).selectFirst("#loginForm")
        ?: Jsoup.parse(html).selectFirst("form[action*=FollowTheRabbit]")
        ?: return null
    val hash = form.selectFirst("input[name=formhash]")?.attr("value").orEmpty()
    if (hash.isBlank()) return null
    val action = form.attr("action").ifBlank { "/FollowTheRabbit" }
    return action to hash
}

internal data class BangumiAuthorizeForm(
    val action: String,
    val fields: List<Pair<String, String>>,
)

internal fun parseAuthorizeForm(html: String): BangumiAuthorizeForm? {
    val form = Jsoup.parse(html).select("form").firstOrNull { element ->
        element.selectFirst("input[name=formhash]") != null &&
            (
                element.attr("action").contains("oauth/authorize") ||
                    element.select("input[type=submit], button").any { control ->
                        control.attr("value").contains("授权") || control.text().contains("授权")
                    }
                )
    } ?: return null
    val fields = mutableListOf<Pair<String, String>>()
    for (input in form.select("input[name]")) {
        val type = input.attr("type").lowercase()
        if (type == "submit" || type == "button" || type == "image") continue
        if ((type == "checkbox" || type == "radio") && !input.hasAttr("checked")) continue
        fields += input.attr("name") to input.attr("value")
    }
    val submit = form.select("input[type=submit], button").firstOrNull { control ->
        control.attr("value").contains("授权") || control.text().contains("授权")
    }
    val submitName = submit?.attr("name")?.ifBlank { "submit" } ?: "submit"
    val submitValue = submit?.attr("value")?.ifBlank { null } ?: submit?.text()?.ifBlank { null } ?: "授权"
    if (fields.none { it.first == submitName }) fields += submitName to submitValue
    if (fields.none { it.first == "formhash" && it.second.isNotBlank() }) return null
    // Keep a blank action blank: the caller must post back to the page URL (query string included).
    return BangumiAuthorizeForm(form.attr("action"), fields)
}

internal fun parseBangumiNotice(html: String): String? {
    if (html.isBlank()) return null
    val document = Jsoup.parse(html)
    val text = document.selectFirst("#colunmNotice .message p.text")?.text()?.trim()
        ?: document.selectFirst(".message p.text")?.text()?.trim()
    return text?.takeIf { it.isNotBlank() }
}

internal fun isBangumiOAuthCallback(location: String, redirectUri: String): Boolean {
    if (location.startsWith(redirectUri)) return true
    val url = location.toHttpUrlOrNull() ?: return false
    val path = url.encodedPath
    return (url.host == "127.0.0.1" || url.host == "localhost") &&
        (path == "/callback" || path == "/callback/")
}

internal fun withOAuthState(callbackUrl: String, state: String): String {
    if (BangumiOAuthConstants.queryParameter(callbackUrl, "state") != null) return callbackUrl
    val separator = if ('?' in callbackUrl) '&' else '?'
    return callbackUrl + separator + "state=" + URLEncoder.encode(state, Charsets.UTF_8.name())
}

internal fun imageContentType(header: String): String {
    val mime = header.substringBefore(';').trim().lowercase()
    return if (mime == "image/gif" || mime == "image/png" || mime == "image/jpeg" || mime == "image/webp") {
        mime
    } else {
        "image/gif"
    }
}
