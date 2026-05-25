package com.miruplay.tv.sync.rss

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDriveRssScheduler @Inject constructor(
    private val engine: CloudDriveRssAutomationEngine
) {
    private var scope: CoroutineScope? = null

    fun startIfNeeded() {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        newScope.launchCloudDriveRssSchedulerLoop(
            dueRunner = engine,
            checkIntervalMillis = CHECK_INTERVAL_MILLIS,
        ) { _, result ->
            result
                .onSuccess { summary ->
                    if (summary != null) {
                        Log.d(
                            "CloudDriveRssScheduler",
                            "RSS run complete: submitted=${summary.submitted}, organized=${summary.organized}"
                        )
                    }
                }
                .onError { error ->
                    Log.w("CloudDriveRssScheduler", "RSS run failed: $error")
                }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    companion object {
        private const val CHECK_INTERVAL_MILLIS = 5 * 60_000L
    }
}
