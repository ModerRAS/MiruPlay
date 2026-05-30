package com.miruplay.tv.sync.archive

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.miruplay.tv.core.common.Result as CoreResult
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.scraper.core.BangumiArchiveStore
import com.miruplay.tv.scraper.core.toBangumiHttpProxyConfig
import com.miruplay.tv.sync.bangumiArchiveForegroundInfo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BangumiArchiveScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun startIfNeeded() {
        val request = PeriodicWorkRequestBuilder<BangumiArchiveWorker>(
            UPDATE_INTERVAL_DAYS,
            TimeUnit.DAYS,
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun stop() {
        // WorkManager owns the persistent weekly update schedule.
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private companion object {
        private const val PERIODIC_WORK_NAME = "bangumi-archive-weekly"
        private const val UPDATE_INTERVAL_DAYS = 7L
    }
}

class BangumiArchiveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        setForeground(
            bangumiArchiveForegroundInfo(
                context = applicationContext,
                text = "正在下载最新离线刮削数据",
            )
        )
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BangumiArchiveWorkerEntryPoint::class.java,
        )
        val store = entryPoint.bangumiArchiveStore()
        when (val config = entryPoint.cloudDriveAutomationRepository().getConfig()) {
            is CoreResult.Success -> store.configureProxy(config.data.toBangumiHttpProxyConfig())
            is CoreResult.Error -> Log.w(TAG, "Failed to read proxy config: ${config.error}")
        }

        return when (val result = store.downloadLatest()) {
            is CoreResult.Success -> Result.success()
            is CoreResult.Error -> {
                Log.w(TAG, "Bangumi Archive weekly update failed: ${result.error}")
                Result.retry()
            }
        }
    }

    private companion object {
        private const val TAG = "BangumiArchiveWorker"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BangumiArchiveWorkerEntryPoint {
    fun bangumiArchiveStore(): BangumiArchiveStore
    fun cloudDriveAutomationRepository(): CloudDriveAutomationRepository
}
