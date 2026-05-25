package com.miruplay.tv.repository

import com.miruplay.tv.core.common.logging.MiruLogRecord
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class OpenObservePayloadContext(
    val serviceName: String,
    val deploymentEnvironment: String,
)

object OpenObserveLogConventions {
    private const val DEFAULT_STREAM_NAME = "miruplay"

    fun normalizeEndpoint(endpoint: String, streamName: String): String {
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
            path.endsWith("/v1/logs") -> appendStreamIfNeeded(path.removeSuffix("/v1/logs"), safeStream)
            path.endsWith("/v1/log") -> appendStreamIfNeeded(path.removeSuffix("/v1/log"), safeStream)
            path.endsWith("/v1") -> appendStreamIfNeeded(path.removeSuffix("/v1"), safeStream)
            path.isOpenObserveStreamPath() -> path
            path.startsWith("/api/") -> "$path/$safeStream"
            else -> "$path/api/default/$safeStream"
        }.trimEnd('/').ifBlank { "/api/default" }
        val normalizedPath = "$jsonStreamPath/_json"
        return URI(uri.scheme, uri.authority, normalizedPath, null, null).toString()
    }

    fun buildJsonPayload(
        records: List<MiruLogRecord>,
        context: OpenObservePayloadContext,
    ): JsonArray = buildJsonArray {
        records.forEach { record ->
            add(
                buildJsonObject {
                    put("_timestamp", record.timestampMs)
                    put("level", record.level.severityText.lowercase())
                    put("severity", record.level.severityText)
                    put("tag", record.tag)
                    put("log", record.message)
                    put("message", record.message)
                    put("job", "miruplay")
                    put("service_name", context.serviceName)
                    put("service_namespace", "miruplay")
                    put("deployment_environment", context.deploymentEnvironment)
                    put("record_id", record.id)
                    record.throwableClass?.let { put("exception_type", it) }
                    record.throwableMessage?.let { put("exception_message", it) }
                    record.stackTrace?.let { put("exception_stacktrace", it) }
                    record.attributes.forEach { (key, value) ->
                        put(safeFieldName(key), value)
                    }
                },
            )
        }
    }

    private fun appendStreamIfNeeded(path: String, streamName: String): String {
        val normalized = path.trimEnd('/')
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

    private fun safeFieldName(key: String): String =
        key.trim()
            .replace(Regex("""[^A-Za-z0-9_]+"""), "_")
            .trim('_')
            .ifBlank { "attribute" }
}
