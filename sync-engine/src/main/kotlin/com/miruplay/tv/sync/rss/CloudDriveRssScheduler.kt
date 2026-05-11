package com.miruplay.tv.sync.rss

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        newScope.launch {
            while (isActive) {
                engine.runIfDue()
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
                delay(5 * 60_000L)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }
}
