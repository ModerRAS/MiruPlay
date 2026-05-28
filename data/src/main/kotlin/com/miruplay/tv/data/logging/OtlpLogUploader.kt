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
            .url(OpenObserveLogConventions.normalizeEndpoint(endpoint, streamName))
            .addHeader("Authorization", authorizationHeader(token))
            .addHeader("Content-Type", JSON_MEDIA_TYPE)
            .post(
                json.encodeToString(
                    OpenObserveLogConventions.buildJsonPayload(
                        records = records,
                        context = ANDROID_PAYLOAD_CONTEXT,
                    )
                ).toRequestBody(JSON_MEDIA_TYPE.toMediaType())
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
        private val ANDROID_PAYLOAD_CONTEXT = OpenObservePayloadContext(
            serviceName = "miruplay-android-tv",
            deploymentEnvironment = "android-tv",
        )
    }
}
