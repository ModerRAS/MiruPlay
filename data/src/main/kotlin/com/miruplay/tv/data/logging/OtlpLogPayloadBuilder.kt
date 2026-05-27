package com.miruplay.tv.data.logging

import com.miruplay.tv.core.common.logging.MiruLogRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object OtlpLogPayloadBuilder {
    fun build(records: List<MiruLogRecord>): JsonObject = buildJsonObject {
        put(
            "resourceLogs",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put(
                            "resource",
                            buildJsonObject {
                                put("attributes", attributes(resourceAttributes()))
                            }
                        )
                        put(
                            "scopeLogs",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put(
                                            "scope",
                                            buildJsonObject {
                                                put("name", "com.miruplay.tv")
                                                put("version", "1")
                                            }
                                        )
                                        put("logRecords", logRecords(records))
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    private fun logRecords(records: List<MiruLogRecord>): JsonArray = buildJsonArray {
        records.forEach { record ->
            add(
                buildJsonObject {
                    put("timeUnixNano", record.timestampMs.toString() + "000000")
                    put("observedTimeUnixNano", record.timestampMs.toString() + "000000")
                    put("severityNumber", record.level.otlpSeverityNumber)
                    put("severityText", record.level.severityText)
                    put("body", stringValue(record.message))
                    put("attributes", attributes(recordAttributes(record)))
                }
            )
        }
    }

    private fun resourceAttributes(): Map<String, String> = mapOf(
        "service.name" to "miruplay-android-tv",
        "service.namespace" to "miruplay",
        "deployment.environment" to "android-tv"
    )

    private fun recordAttributes(record: MiruLogRecord): Map<String, String> =
        buildMap {
            put("log.tag", record.tag)
            put("log.record_id", record.id)
            record.throwableClass?.let { put("exception.type", it) }
            record.throwableMessage?.let { put("exception.message", it) }
            record.stackTrace?.let { put("exception.stacktrace", it) }
            record.attributes.forEach { (key, value) -> put(key, value) }
        }

    private fun attributes(values: Map<String, String>): JsonArray = buildJsonArray {
        values.forEach { (key, value) ->
            add(
                buildJsonObject {
                    put("key", key)
                    put("value", stringValue(value))
                }
            )
        }
    }

    private fun stringValue(value: String): JsonObject = buildJsonObject {
        put("stringValue", JsonPrimitive(value))
    }
}
