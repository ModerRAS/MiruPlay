package com.miruplay.tv.translation

import com.miruplay.tv.scraper.core.BangumiProxyAwareOkHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * DeepSeek Chat API（需要 apiKey）。system prompt 只写翻译指令——
 * DeepSeek 会拒绝包含 "翻译结果不要包含任何解释" 等字样的请求。
 * 手动验证：curl -x http://<proxy> -H "Authorization: Bearer <key>" \
 *   -d '{"model":"deepseek-chat","messages":[{"role":"system","content":"You are a subtitle translator. Translate to 简体中文."},{"role":"user","content":"hello"}],"temperature":1.3}' \
 *   https://api.deepseek.com/chat/completions
 * 解析 choices[0].message.content。
 */
class DeepSeekTranslator(
    private val client: BangumiProxyAwareOkHttpClient,
) {
    suspend fun translate(text: String, apiKey: String, targetInstruction: String): String {
        if (apiKey.isBlank()) throw IOException("DeepSeek API Key 未设置")
        val payload = buildJsonObject {
            put("model", "deepseek-chat")
            put("temperature", 1.3)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", "You are a subtitle translator. Translate to $targetInstruction.")
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", text)
                        },
                    )
                },
            )
        }
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = executeForText(client, request)
        val result = Json.parseToJsonElement(body)
            .jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
        if (result.isNullOrBlank()) throw IOException("DeepSeek 返回空结果")
        return result
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
