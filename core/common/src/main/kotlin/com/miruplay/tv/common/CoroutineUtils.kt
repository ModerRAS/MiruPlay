package com.miruplay.tv.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

suspend fun <T> withTimeoutOrDefault(
    timeoutMs: Long,
    default: T,
    block: suspend () -> T,
): T = try {
    withTimeoutOrNull(timeoutMs) { block() } ?: default
} catch (e: Exception) {
    default
}

suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 8000,
    block: suspend () -> T,
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e
            delay(currentDelay)
            currentDelay = minOf(currentDelay * 2, maxDelayMs)
        }
    }
    throw IllegalStateException("Should not reach here")
}