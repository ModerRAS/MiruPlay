package com.miruplay.tv.sync.rss

import android.util.Log
import com.miruplay.tv.core.common.logging.MiruLog
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
                            MiruLog.i(
                                "CloudDriveRssScheduler",
                                "RSS run complete",
                                mapOf(
                                    "submitted" to summary.submitted.toString(),
                                    "organized" to summary.organized.toString(),
                                    "skipped" to summary.skipped.toString(),
                                    "failed" to summary.failed.toString()
                                )
                            )
                        }
                    }
                    .onError { error ->
                        Log.w("CloudDriveRssScheduler", "RSS run failed: $error")
                        MiruLog.w(
                            "CloudDriveRssScheduler",
                            "RSS run failed",
                            attributes = mapOf(
                                "error_type" to error::class.simpleName.orEmpty(),
                                "error_message" to error.toUserMessage()
                            )
                        )
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
