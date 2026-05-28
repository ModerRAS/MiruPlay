package com.miruplay.tv.data.logging

import android.util.Base64
import com.miruplay.tv.core.common.logging.MiruLogRecord
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OtlpLogUploader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun upload(endpoint: String, token: String, streamName: String, records: List<MiruLogRecord>): UploadResult {
        if (records.isEmpty()) return UploadResult.Success(0)
        val request = Request.Builder()
            .url(OtlpLogEndpoint.normalize(endpoint))
            .addHeader("Authorization", authorizationHeader(token))
            .addHeader("Content-Type", OTLP_JSON_MEDIA_TYPE)
            .addHeader("stream-name", streamName.ifBlank { DEFAULT_STREAM_NAME })
            .post(json.encodeToString(OtlpLogPayloadBuilder.build(records)).toRequestBody(OTLP_JSON_MEDIA_TYPE.toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                UploadResult.Success(records.size)
            } else {
                val body = response.body?.string()?.take(240).orEmpty()
                UploadResult.Failed("HTTP ${response.code}${if (body.isBlank()) "" else ": $body"}")
            }
        }
    }

    private fun authorizationHeader(token: String): String =
        if (token.startsWith("Basic ", ignoreCase = true)) {
            token
        } else if (token.contains(':')) {
            "Basic " + Base64.encodeToString(
                token.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
        } else {
            "Basic $token"
        }

    sealed class UploadResult {
        data class Success(val uploadedCount: Int) : UploadResult()
        data class Failed(val message: String) : UploadResult()
    }

    companion object {
        private const val OTLP_JSON_MEDIA_TYPE = "application/json"
        private const val DEFAULT_STREAM_NAME = "miruplay"
    }
}

internal object OtlpLogEndpoint {
    fun normalize(endpoint: String): String {
        val raw = endpoint.trim().trimEnd('/')
        require(raw.isNotBlank()) { "OTLP endpoint is blank" }
        val trimmed = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        if (trimmed.endsWith("/v1/logs")) return trimmed
        val uri = URI(trimmed)
        val path = uri.path.orEmpty().trimEnd('/')
        val normalizedPath = when {
            path.isBlank() -> "/api/default/v1/logs"
            path == "/api" -> "/api/default/v1/logs"
            path.endsWith("/v1") -> "$path/logs"
            path.startsWith("/api/") -> "$path/v1/logs"
            else -> "$path/api/default/v1/logs"
        }
        return URI(uri.scheme, uri.authority, normalizedPath, null, null).toString()
    }
}
