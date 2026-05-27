package com.miruplay.tv.data.logging

import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.repository.LogUploadAutoScheduler
import com.miruplay.tv.repository.LogUploadRepository
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class LogUploadScheduler @Inject constructor(
    private val repository: LogUploadRepository,
    localLogStore: LocalLogStore,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduler = LogUploadAutoScheduler(
        repository = repository,
        scope = scope,
    )
    private val configObserverJob: Job

    init {
        MiruLog.setSink(localLogStore)
    }

    fun startIfNeeded() {
        if (job?.isActive == true) {
            MiruLog.d("LogUploadScheduler", "Log upload scheduler already running")
            return
        }
        MiruLog.i(
            "LogUploadScheduler",
            "Log upload scheduler started",
            mapOf("upload_interval_ms" to UPLOAD_INTERVAL_MS.toString())
        )
        job = scope.launch {
            while (isActive) {
                repository.uploadPendingLogs()
                delay(UPLOAD_INTERVAL_MS)
            }
        }
    }

    override fun close() {
        MiruLog.i("LogUploadScheduler", "Log upload scheduler stopped")
        job?.cancel()
        job = null
    }

    override fun close() {
        MiruLog.i("LogUploadScheduler", "Log upload scheduler stopped")
        configObserverJob.cancel()
        scheduler.stop()
    }
}
