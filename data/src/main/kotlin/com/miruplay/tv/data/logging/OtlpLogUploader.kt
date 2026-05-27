package com.miruplay.tv.data.logging

import android.util.Base64
import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.repository.OpenObserveLogConventions
import com.miruplay.tv.repository.OpenObservePayloadContext
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
            .url(OpenObserveLogEndpoint.normalize(endpoint, streamName))
            .addHeader("Authorization", authorizationHeader(token))
            .addHeader("Content-Type", JSON_MEDIA_TYPE)
            .post(
                json.encodeToString(OpenObserveJsonPayloadBuilder.build(records))
                    .toRequestBody(JSON_MEDIA_TYPE.toMediaType())
            )
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
        private const val JSON_MEDIA_TYPE = "application/json"
    }
}

internal object OpenObserveLogEndpoint {
    fun normalize(endpoint: String, streamName: String): String {
        val raw = endpoint.trim().trimEnd('/')
        require(raw.isNotBlank()) { "OpenObserve endpoint is blank" }
        val trimmed = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        val uri = URI(trimmed)
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.endsWith("/_json")) return URI(uri.scheme, uri.authority, path, null, null).toString()

        val safeStream = streamName.trim().ifBlank { DEFAULT_STREAM_NAME }
        val jsonStreamPath = when {
            path.isBlank() -> "/api/default/$safeStream"
            path == "/api" -> "/api/default/$safeStream"
            path.endsWith("/v1/logs") -> path.removeSuffix("/v1/logs").appendStreamIfNeeded(safeStream)
            path.endsWith("/v1/log") -> path.removeSuffix("/v1/log").appendStreamIfNeeded(safeStream)
            path.endsWith("/v1") -> path.removeSuffix("/v1").appendStreamIfNeeded(safeStream)
            path.isOpenObserveStreamPath() -> path
            path.startsWith("/api/") -> "$path/$safeStream"
            else -> "$path/api/default/$safeStream"
        }.trimEnd('/').ifBlank { "/api/default" }
        val normalizedPath = "$jsonStreamPath/_json"
        return URI(uri.scheme, uri.authority, normalizedPath, null, null).toString()
    }

    private fun String.appendStreamIfNeeded(streamName: String): String {
        val normalized = trimEnd('/')
        return when {
            normalized.isBlank() -> "/api/default/$streamName"
            normalized.isOpenObserveStreamPath() -> normalized
            else -> "$normalized/$streamName"
        }
    }

    private fun String.isOpenObserveStreamPath(): Boolean {
        val segments = trim('/').split('/').filter { it.isNotBlank() }
        return segments.size == 3 && segments.firstOrNull() == "api"
    }

    private const val DEFAULT_STREAM_NAME = "miruplay"
}
