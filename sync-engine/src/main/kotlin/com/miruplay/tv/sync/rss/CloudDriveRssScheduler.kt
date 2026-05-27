package com.miruplay.tv.sync.rss

import android.content.Context
import android.util.Log
import com.miruplay.tv.core.common.logging.MiruLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class CloudDriveRssScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: CloudDriveAutomationRepository,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    fun syncPeriodicWork(config: CloudDriveAutomationConfig) {
        if (!config.enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val intervalMinutes = config.intervalMinutes
            .coerceAtLeast(MIN_CLOUD_DRIVE_INTERVAL_MINUTES)
            .toLong()
        val request = PeriodicWorkRequestBuilder<CloudDriveRssWorker>(
            intervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_CLOUD_DRIVE_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueRunNow() {
        val request = OneTimeWorkRequestBuilder<CloudDriveRssWorker>()
            .setInputData(workDataOf(CloudDriveRssWorker.KEY_FORCE_RUN to true))
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_CLOUD_DRIVE_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(RUN_NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun stop() {
        // WorkManager owns the persistent schedule. App shutdown should not cancel configured sync.
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private companion object {
        private const val TAG = "CloudDriveRssScheduler"
        private const val PERIODIC_WORK_NAME = "cloud-drive-rss-periodic"
        private const val RUN_NOW_WORK_NAME = "cloud-drive-rss-run-now"
    }
}

class CloudDriveRssWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CloudDriveRssWorkerEntryPoint::class.java,
        )
        val engine = entryPoint.cloudDriveRssAutomationEngine()
        val forceRun = inputData.getBoolean(KEY_FORCE_RUN, false)
        return when (val result = if (forceRun) engine.runOnce() else engine.runIfDue()) {
            is CoreResult.Success -> Result.success()
            is CoreResult.Error -> {
                Log.w("CloudDriveRssWorker", "Cloud/RSS work failed: ${result.error}")
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_FORCE_RUN = "force_run"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudDriveRssWorkerEntryPoint {
    fun cloudDriveRssAutomationEngine(): CloudDriveRssAutomationEngine
}
