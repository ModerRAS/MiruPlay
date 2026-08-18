package com.miruplay.tv.translation

import com.miruplay.tv.scraper.core.BangumiProxyAwareOkHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Bing 网页翻译端点（无 key）。流程（已用 curl 实测通过）：
 * 1. GET https://www.bing.com/ 取 IG；
 * 2. GET https://www.bing.com/translator 解析 params_AbusePreventionHelper = [key, token, expiryMs]；
 * 3. POST https://www.bing.com/ttranslatev3?isVertical=1&&IG=<IG>&IID=translator.5028.1
 *    form: fromLang=auto-detect&to=<code>&text=<text>&token=<token>&key=<key>
 *    需要浏览器 UA + Referer/Origin 头；cookie 非必需。
 * 返回 [{"translations":[{"text":"..."}]}]，取 [0].translations[0].text。
 *
 * 手动验证：curl -x http://<proxy> "https://www.bing.com/translator" 拿 token/key 后按上述拼 POST。
 * token 约 1 小时过期，缓存过期后自动重新获取。
 */
class BingTranslator(
    private val client: BangumiProxyAwareOkHttpClient,
) {
    private var cached: BingCredentials? = null

    suspend fun translate(text: String, targetCode: String): String {
        val credentials = credentials()
        val form = "fromLang=auto-detect&to=$targetCode&text=${URLEncoder.encode(text, Charsets.UTF_8.name())}" +
            "&token=${credentials.token}&key=${credentials.key}"
        val request = Request.Builder()
            .url("https://www.bing.com/ttranslatev3?isVertical=1&&IG=${credentials.ig}&IID=translator.5028.1")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bing.com/translator")
            .header("Origin", "https://www.bing.com")
            .post(form.toRequestBody(FORM_MEDIA_TYPE))
            .build()
        val body = executeForText(client, request)
        val parsed = Json.parseToJsonElement(body).jsonArray
        val statusCode = parsed.firstOrNull()?.jsonObject?.get("statusCode")?.jsonPrimitive?.contentOrNull
        if (statusCode != null && statusCode != "200") {
            throw IOException("Bing 返回 statusCode=$statusCode")
        }
        val result = parsed.firstOrNull()
            ?.jsonObject?.get("translations")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.contentOrNull
        if (result.isNullOrBlank()) throw IOException("Bing 返回空结果")
        return result
    }

    private suspend fun credentials(): BingCredentials {
        cached?.let { if (System.currentTimeMillis() < it.expiresAtMillis) return it }
        val homePage = executeForText(
            client,
            Request.Builder().url("https://www.bing.com/").header("User-Agent", USER_AGENT).build(),
        )
        val ig = HOME_IG.find(homePage)?.groupValues?.get(1)
            ?: throw IOException("无法从 bing.com 获取 IG")
        val translatorPage = executeForText(
            client,
            Request.Builder().url("https://www.bing.com/translator").header("User-Agent", USER_AGENT).build(),
        )
        val match = ABUSE_PREVENTION.find(translatorPage)
            ?: throw IOException("无法从 bing.com/translator 获取 token")
        val key = match.groupValues[1].trim()
        val token = match.groupValues[2].trim().removeSurrounding("\"")
        return BingCredentials(
            ig = ig,
            key = key,
            token = token,
            expiresAtMillis = System.currentTimeMillis() + TOKEN_TTL_MILLIS,
        ).also { cached = it }
    }

    private data class BingCredentials(
        val ig: String,
        val key: String,
        val token: String,
        val expiresAtMillis: Long,
    )

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        val HOME_IG = Regex("""IG="([A-F0-9]+)"""")
        val ABUSE_PREVENTION = Regex("""params_AbusePreventionHelper = \[([0-9]+),\s*"([^"]+)"""")
        // token 页面生成后 1 小时过期，留 10 分钟余量
        val TOKEN_TTL_MILLIS = TimeUnit.MINUTES.toMillis(50)
    }
}
