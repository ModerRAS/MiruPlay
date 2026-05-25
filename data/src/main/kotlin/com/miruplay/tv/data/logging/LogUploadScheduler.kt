package com.miruplay.tv.data.logging

import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.repository.LogUploadAutoScheduler
import com.miruplay.tv.repository.LogUploadRepository
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    init {
        MiruLog.setSink(localLogStore)
    }

    fun startIfNeeded() {
        val started = scheduler.start()
        MiruLog.i(
            "LogUploadScheduler",
            if (started) "Log upload scheduler started" else "Log upload scheduler already running",
            mapOf("upload_interval_ms" to LogUploadAutoScheduler.DEFAULT_INTERVAL_MILLIS.toString()),
        )
    }

    override fun close() {
        MiruLog.i("LogUploadScheduler", "Log upload scheduler stopped")
        scheduler.stop()
    }
}
