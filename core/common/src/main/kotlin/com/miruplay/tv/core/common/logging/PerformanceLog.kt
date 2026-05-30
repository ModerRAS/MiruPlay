package com.miruplay.tv.core.common.logging

import com.miruplay.tv.core.common.Result
import java.util.Locale
import kotlinx.coroutines.CancellationException

object PerformanceLog {
    fun <T> measure(
        tag: String,
        operation: String,
        attributes: Map<String, String> = emptyMap(),
        resultAttributes: (T) -> Map<String, String> = { emptyMap() },
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return try {
            val result = block()
            logCompleted(
                tag = tag,
                operation = operation,
                startedAt = startedAt,
                attributes = attributes + resultDataAttributes(result) + resultAttributes(result),
                status = "success",
            )
            result
        } catch (error: CancellationException) {
            logCompleted(tag, operation, startedAt, attributes, status = "cancelled")
            throw error
        } catch (error: Throwable) {
            logFailed(tag, operation, startedAt, attributes, error)
            throw error
        }
    }

    suspend fun <T> measureSuspend(
        tag: String,
        operation: String,
        attributes: Map<String, String> = emptyMap(),
        resultAttributes: (T) -> Map<String, String> = { emptyMap() },
        block: suspend () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return try {
            val result = block()
            logCompleted(
                tag = tag,
                operation = operation,
                startedAt = startedAt,
                attributes = attributes + resultDataAttributes(result) + resultAttributes(result),
                status = "success",
            )
            result
        } catch (error: CancellationException) {
            logCompleted(tag, operation, startedAt, attributes, status = "cancelled")
            throw error
        } catch (error: Throwable) {
            logFailed(tag, operation, startedAt, attributes, error)
            throw error
        }
    }

    fun <T> measureResult(
        tag: String,
        operation: String,
        attributes: Map<String, String> = emptyMap(),
        resultAttributes: (T) -> Map<String, String> = { emptyMap() },
        block: () -> Result<T>,
    ): Result<T> {
        val startedAt = System.nanoTime()
        return try {
            block().also { result ->
                logResult(tag, operation, startedAt, attributes, result, resultAttributes)
            }
        } catch (error: CancellationException) {
            logCompleted(tag, operation, startedAt, attributes, status = "cancelled")
            throw error
        } catch (error: Throwable) {
            logFailed(tag, operation, startedAt, attributes, error)
            throw error
        }
    }

    suspend fun <T> measureSuspendResult(
        tag: String,
        operation: String,
        attributes: Map<String, String> = emptyMap(),
        resultAttributes: (T) -> Map<String, String> = { emptyMap() },
        block: suspend () -> Result<T>,
    ): Result<T> {
        val startedAt = System.nanoTime()
        return try {
            block().also { result ->
                logResult(tag, operation, startedAt, attributes, result, resultAttributes)
            }
        } catch (error: CancellationException) {
            logCompleted(tag, operation, startedAt, attributes, status = "cancelled")
            throw error
        } catch (error: Throwable) {
            logFailed(tag, operation, startedAt, attributes, error)
            throw error
        }
    }

    fun log(
        tag: String,
        operation: String,
        durationMs: Double,
        status: String = "success",
        attributes: Map<String, String> = emptyMap(),
    ) {
        MiruLog.i(
            tag = tag,
            message = "Performance metric",
            attributes = baseAttributes(operation, durationMs, status) + attributes,
        )
    }

    private fun <T> logResult(
        tag: String,
        operation: String,
        startedAt: Long,
        attributes: Map<String, String>,
        result: Result<T>,
        resultAttributes: (T) -> Map<String, String>,
    ) {
        val durationMs = elapsedMs(startedAt)
        when (result) {
            is Result.Success -> log(
                tag = tag,
                operation = operation,
                durationMs = durationMs,
                status = "success",
                attributes = attributes + resultDataAttributes(result.data) + resultAttributes(result.data),
            )
            is Result.Error -> MiruLog.i(
                tag = tag,
                message = "Performance metric",
                attributes = baseAttributes(operation, durationMs, "error") +
                    attributes +
                    mapOf(
                        "error_type" to result.error::class.java.simpleName,
                        "error_message" to result.error.toUserMessage(),
                    ),
            )
        }
    }

    private fun logCompleted(
        tag: String,
        operation: String,
        startedAt: Long,
        attributes: Map<String, String>,
        status: String,
    ) {
        log(
            tag = tag,
            operation = operation,
            durationMs = elapsedMs(startedAt),
            status = status,
            attributes = attributes,
        )
    }

    private fun logFailed(
        tag: String,
        operation: String,
        startedAt: Long,
        attributes: Map<String, String>,
        error: Throwable,
    ) {
        MiruLog.w(
            tag = tag,
            message = "Performance metric failed",
            throwable = error,
            attributes = baseAttributes(operation, elapsedMs(startedAt), "exception") +
                attributes +
                mapOf("exception_type" to error::class.java.simpleName),
        )
    }

    private fun baseAttributes(
        operation: String,
        durationMs: Double,
        status: String,
    ): Map<String, String> =
        mapOf(
            "event" to "performance",
            "operation" to operation,
            "duration_ms" to durationMs.formatDuration(),
            "status" to status,
        )

    private fun resultDataAttributes(data: Any?): Map<String, String> =
        when (data) {
            is Collection<*> -> mapOf("result_count" to data.size.toString())
            is Map<*, *> -> mapOf("result_count" to data.size.toString())
            is Array<*> -> mapOf("result_count" to data.size.toString())
            else -> emptyMap()
        }

    private fun elapsedMs(startedAt: Long): Double =
        (System.nanoTime() - startedAt) / 1_000_000.0

    private fun Double.formatDuration(): String =
        String.format(Locale.US, "%.3f", this)
}
