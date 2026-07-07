package com.miruplay.tv.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LogUploadAutoScheduler(
    private val repository: LogUploadRepository,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) {
    private var job: Job? = null

    fun start(): Boolean {
        if (job?.isActive == true) return false
        job = scope.launch {
            while (isActive) {
                try {
                    repository.uploadPendingLogs()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the settings-scoped auto uploader alive; upload errors are status, not app death.
                }
                delay(intervalMillis)
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun syncWithConfig(config: OtlpLogUploadConfig): Boolean =
        if (config.enabled) start() else {
            stop()
            false
        }

    val running: Boolean
        get() = job?.isActive == true

    companion object {
        const val DEFAULT_INTERVAL_MILLIS: Long = 5 * 60 * 1000L
    }
}
