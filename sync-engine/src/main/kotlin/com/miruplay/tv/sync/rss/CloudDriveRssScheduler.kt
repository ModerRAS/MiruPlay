package com.miruplay.tv.sync.rss

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.miruplay.tv.core.common.Result as CoreResult
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
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
        scope.launch {
            when (val config = repository.getConfig()) {
                is CoreResult.Success -> syncPeriodicWork(config.data)
                is CoreResult.Error -> Log.w(TAG, "Failed to read Cloud/RSS config: ${config.error}")
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
