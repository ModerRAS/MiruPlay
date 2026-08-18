package com.miruplay.tv.translation

import com.miruplay.tv.scraper.core.BangumiProxyAwareOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * Google 免费翻译端点（client=gtx，无 key）。
 * 手动验证：curl -x http://<proxy> "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=Good%20morning"
 * 返回 [[["早安世界",...]]]，取 [0][i][0] 拼接。
 */
class GoogleTranslator(
    private val client: BangumiProxyAwareOkHttpClient,
) {
    suspend fun translate(text: String, targetCode: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetCode&dt=t&q=" +
            URLEncoder.encode(text, Charsets.UTF_8.name())
        val body = executeForText(client, Request.Builder().url(url).build())
        val result = kotlinx.serialization.json.Json.parseToJsonElement(body)
            .jsonArray[0].jsonArray
            .mapNotNull { segment ->
                segment.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull
            }
            .joinToString("")
        if (result.isBlank()) throw IOException("Google 返回空结果")
        return result
    }
}

internal suspend fun executeForText(client: BangumiProxyAwareOkHttpClient, request: Request): String =
    withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(200)}")
            }
            body
        }
    }
