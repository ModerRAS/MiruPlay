package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext

/**
 * Global error handling utilities for network operations and recovery.
 */
object GlobalErrorHandler {

    /**
     * Retry with exponential backoff
     * @param maxRetries Maximum retry attempts
     * @param baseDelay Base delay in milliseconds
     * @param block Suspend operation to retry
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        baseDelay: Long = 1000L,
        dispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
        block: suspend () -> Result<T>
    ): Result<T> {
        return withContext(dispatcher) {
            var lastError: AppError? = null
            for (attempt in 0..maxRetries) {
                if (attempt > 0) {
                    val delayMs = baseDelay * (1L shl (attempt - 1))
                    delay(delayMs)
                }
                val result = block()
                if (result.isSuccess()) return@withContext result
                lastError = (result as Result.Error).error
                
                // Don't retry certain errors
                if (!shouldRetry(lastError!!)) return@withContext result
            }
            Result.failure(lastError ?: AppError.NetworkError.NoConnectivity)
        }
    }

    /**
     * Determine if an error should be retried
     */
    fun shouldRetry(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError -> true
            is AppError.MediaSourceError.ConnectionLost -> true
            is AppError.MediaSourceError.Timeout -> true
            else -> false
        }
    }

    /**
     * Wrap a Flow with error handling
     */
    fun <T> Flow<T>.withErrorHandling(): Flow<T> {
        return this.catch { e ->
            // Log and re-emit as error
            throw e
        }.retryWhen { cause, attempt ->
            if (attempt < 3 && cause is java.io.IOException) {
                delay(1000L * (1L shl attempt.toInt()))
                true
            } else false
        }
    }

    /**
     * Network-aware operation with recovery
     */
    suspend fun <T> networkOperation(
        block: suspend () -> Result<T>
    ): Result<T> {
        return retryWithBackoff(maxRetries = 3) {
            block()
        }
    }
}
