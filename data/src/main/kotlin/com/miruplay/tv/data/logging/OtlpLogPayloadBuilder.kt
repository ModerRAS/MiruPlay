package com.miruplay.tv.data.logging

import com.miruplay.tv.core.common.logging.MiruLogRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object OpenObserveJsonPayloadBuilder {
    fun build(records: List<MiruLogRecord>): JsonArray = buildJsonArray {
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
                    put("service_name", "miruplay-android-tv")
                    put("service_namespace", "miruplay")
                    put("deployment_environment", "android-tv")
                    put("record_id", record.id)
                    record.throwableClass?.let { put("exception_type", it) }
                    record.throwableMessage?.let { put("exception_message", it) }
                    record.stackTrace?.let { put("exception_stacktrace", it) }
                    record.attributes.forEach { (key, value) ->
                        put(safeFieldName(key), value)
                    }
                }
            )
        }
    }

    private fun safeFieldName(key: String): String =
        key
            .trim()
            .replace(Regex("""[^A-Za-z0-9_]+"""), "_")
            .trim('_')
            .ifBlank { "attribute" }
}
