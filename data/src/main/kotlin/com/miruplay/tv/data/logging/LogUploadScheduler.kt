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
        configObserverJob = scope.launch {
            repository.observeConfig()
                .map { it.enabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    val synced = scheduler.syncWithConfig(repository.getConfig())
                    MiruLog.i(
                        "LogUploadScheduler",
                        if (enabled) {
                            if (synced) "Log upload scheduler started from config"
                            else "Log upload scheduler already running from config"
                        } else {
                            "Log upload scheduler stopped from config"
                        },
                        mapOf("log_upload_enabled" to enabled.toString()),
                    )
                }
        }
    }

    fun startIfNeeded() {
        val started = scheduler.syncWithConfig(repository.getConfig())
        MiruLog.i(
            "LogUploadScheduler",
            if (repository.getConfig().enabled) {
                if (started) "Log upload scheduler started"
                else "Log upload scheduler already running"
            } else {
                "Log upload scheduler remains stopped (disabled in config)"
            },
            mapOf("upload_interval_ms" to LogUploadAutoScheduler.DEFAULT_INTERVAL_MILLIS.toString()),
        )
    }

    override fun close() {
        MiruLog.i("LogUploadScheduler", "Log upload scheduler stopped")
        configObserverJob.cancel()
        scheduler.stop()
    }
}
