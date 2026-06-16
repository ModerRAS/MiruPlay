package com.miruplay.tv.data.logging

import android.content.Context
import com.miruplay.tv.repository.DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME
import com.miruplay.tv.repository.OtlpLogUploadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class LogUploadPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("miruplay_log_upload_prefs", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<OtlpLogUploadConfig> = _config.asStateFlow()

    fun getConfig(): OtlpLogUploadConfig = _config.value

    fun save(enabled: Boolean, endpoint: String, streamName: String) {
        val config = _config.value.copy(
            enabled = enabled,
            endpoint = endpoint.trim(),
            streamName = streamName.trim().ifBlank { DEFAULT_STREAM_NAME }
        )
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_ENDPOINT, config.endpoint)
            .putString(KEY_STREAM_NAME, config.streamName)
            .apply()
        _config.value = config
    }

    fun setUploadStatus(timestampMs: Long, status: String) {
        val config = _config.value.copy(
            lastUploadAt = timestampMs,
            lastUploadStatus = status
        )
        prefs.edit()
            .putLong(KEY_LAST_UPLOAD_AT, timestampMs)
            .putString(KEY_LAST_UPLOAD_STATUS, status)
            .apply()
        _config.value = config
    }

    private fun load(): OtlpLogUploadConfig = OtlpLogUploadConfig(
        enabled = readBoolean(KEY_ENABLED, false),
        endpoint = readString(KEY_ENDPOINT).orEmpty(),
        streamName = readString(KEY_STREAM_NAME).orEmpty().ifBlank { DEFAULT_STREAM_NAME },
        lastUploadAt = readLong(KEY_LAST_UPLOAD_AT, 0L),
        lastUploadStatus = readString(KEY_LAST_UPLOAD_STATUS)
    )

    private fun readBoolean(key: String, defaultValue: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, defaultValue) }
            .getOrElse { defaultValue }

    private fun readLong(key: String, defaultValue: Long): Long =
        runCatching { prefs.getLong(key, defaultValue) }
            .getOrElse { defaultValue }

    private fun readString(key: String): String? =
        runCatching { prefs.getString(key, null) }
            .getOrNull()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_STREAM_NAME = "stream_name"
        private const val KEY_LAST_UPLOAD_AT = "last_upload_at"
        private const val KEY_LAST_UPLOAD_STATUS = "last_upload_status"
        private const val DEFAULT_STREAM_NAME = DEFAULT_OTLP_LOG_UPLOAD_STREAM_NAME
    }
}
