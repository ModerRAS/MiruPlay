package com.miruplay.tv.data.logging

import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.repository.LogUploadRepository
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class LogUploadScheduler @Inject constructor(
    private val repository: LogUploadRepository,
    localLogStore: LocalLogStore
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    init {
        MiruLog.setSink(localLogStore)
    }

    fun startIfNeeded() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                repository.uploadPendingLogs()
                delay(UPLOAD_INTERVAL_MS)
            }
        }
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val UPLOAD_INTERVAL_MS = 5 * 60 * 1000L
    }
}
