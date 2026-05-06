package com.miruplay.tv.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 带重试的挂起函数
 */
suspend fun <T> retrySuspend(
    times: Int = 3,
    initialDelayMillis: Long = 1000,
    maxDelayMillis: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMillis
    repeat(times - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (attempt == times - 2) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
        }
    }
    return block()
}

/**
 * 在 IO 线程执行
 */
suspend fun <T> ioContext(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

/**
 * 带超时的操作
 */
suspend fun <T> withTimeoutOrNull(
    timeoutMillis: Long,
    block: suspend () -> T
): T? = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) { block() }

/**
 * 延迟执行（防抖）
 */
fun CoroutineScope.debounce(
    delayMillis: Long,
    scope: CoroutineScope = this,
    action: () -> Unit
) = scope.launch {
    delay(delayMillis)
    action()
}