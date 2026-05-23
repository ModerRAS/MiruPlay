package com.miruplay.tv.core.common.logging

import kotlinx.serialization.Serializable
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicLong

enum class MiruLogLevel(
    val severityText: String,
    val otlpSeverityNumber: Int
) {
    DEBUG("DEBUG", 5),
    INFO("INFO", 9),
    WARN("WARN", 13),
    ERROR("ERROR", 17),
}

@Serializable
data class MiruLogRecord(
    val id: String,
    val timestampMs: Long,
    val level: MiruLogLevel,
    val tag: String,
    val message: String,
    val throwableClass: String? = null,
    val throwableMessage: String? = null,
    val stackTrace: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

fun interface MiruLogSink {
    fun log(record: MiruLogRecord)
}

object MiruLog {
    private val sequence = AtomicLong()
    private val sinkSuppressionDepth = ThreadLocal.withInitial { 0 }

    @Volatile
    private var sink: MiruLogSink? = null

    fun setSink(sink: MiruLogSink?) {
        this.sink = sink
    }

    fun <T> withoutSinkRecording(block: () -> T): T {
        sinkSuppressionDepth.set(sinkSuppressionDepth.get() + 1)
        return try {
            block()
        } finally {
            val nextDepth = sinkSuppressionDepth.get() - 1
            if (nextDepth <= 0) {
                sinkSuppressionDepth.remove()
            } else {
                sinkSuppressionDepth.set(nextDepth)
            }
        }
    }

    fun d(tag: String, message: String, attributes: Map<String, String> = emptyMap()) {
        log(MiruLogLevel.DEBUG, tag, message, null, attributes)
    }

    fun i(tag: String, message: String, attributes: Map<String, String> = emptyMap()) {
        log(MiruLogLevel.INFO, tag, message, null, attributes)
    }

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        log(MiruLogLevel.WARN, tag, message, throwable, attributes)
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        log(MiruLogLevel.ERROR, tag, message, throwable, attributes)
    }

    fun log(
        level: MiruLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        if (sinkSuppressionDepth.get() > 0) return
        val now = System.currentTimeMillis()
        val record = MiruLogRecord(
            id = "$now-${sequence.incrementAndGet()}",
            timestampMs = now,
            level = level,
            tag = tag.take(MAX_TAG_LENGTH),
            message = message.redactSensitive().take(MAX_MESSAGE_LENGTH),
            throwableClass = throwable?.javaClass?.name,
            throwableMessage = throwable?.message?.redactSensitive()?.take(MAX_MESSAGE_LENGTH),
            stackTrace = throwable?.stackTraceString()?.redactSensitive()?.take(MAX_STACK_TRACE_LENGTH),
            attributes = attributes
                .filterKeys { it.isNotBlank() }
                .mapKeys { it.key.take(MAX_ATTRIBUTE_KEY_LENGTH) }
                .mapValues { it.value.redactSensitive().take(MAX_ATTRIBUTE_VALUE_LENGTH) }
        )
        runCatching { sink?.log(record) }
    }

    private fun Throwable.stackTraceString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun String.redactSensitive(): String {
        var value = this
        sensitiveQueryParamRegexes.forEach { regex ->
            value = regex.replace(value) { match ->
                "${match.groupValues[1]}<redacted>"
            }
        }
        value = authorizationRegex.replace(value) { match ->
            "${match.groupValues[1]}<redacted>"
        }
        return urlUserInfoRegex.replace(value) { match ->
            "${match.groupValues[1]}<redacted>@"
        }
    }

    private const val MAX_TAG_LENGTH = 80
    private const val MAX_MESSAGE_LENGTH = 2_000
    private const val MAX_STACK_TRACE_LENGTH = 12_000
    private const val MAX_ATTRIBUTE_KEY_LENGTH = 80
    private const val MAX_ATTRIBUTE_VALUE_LENGTH = 1_000

    private val sensitiveQueryParamRegexes = listOf(
        Regex("""(?i)([?&](?:access[_-]?token|api[_-]?key|token|password|passwd|secret)=)[^&#\s]+"""),
        Regex("""(?i)(\b(?:access[_-]?token|api[_-]?key|token|password|passwd|secret)\s*[:=]\s*)[^\s,;]+"""),
    )
    private val authorizationRegex = Regex("""(?i)(\bAuthorization\s*[:=]\s*)(?:Basic|Bearer)\s+[A-Za-z0-9._~+/=-]+""")
    private val urlUserInfoRegex = Regex("""(?i)(https?://)[^/@\s]+@""")
}
