package com.miruplay.tv

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.logging.AppCrashDiagnostics
import com.miruplay.tv.data.logging.LogUploadScheduler
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.sync.archive.BangumiArchiveScheduler
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import com.miruplay.tv.webcontrol.WebControlServer
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltAndroidApp
class MiruPlayApp : Application() {

    @Inject lateinit var webControlServer: Lazy<WebControlServer>
    @Inject lateinit var webControlPreferences: Lazy<WebControlAccessManager>
    @Inject lateinit var cloudDriveRssScheduler: Lazy<CloudDriveRssScheduler>
    @Inject lateinit var bangumiArchiveScheduler: Lazy<BangumiArchiveScheduler>
    @Inject lateinit var logUploadScheduler: Lazy<LogUploadScheduler>
    @Inject lateinit var crashDiagnostics: AppCrashDiagnostics

    private var webControlPreferenceListener: Closeable? = null
    private val deferredStartupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val deferredStartupStarted = AtomicBoolean(false)
    private val deferredStartupLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (!deferredStartupStarted.compareAndSet(false, true)) return
            unregisterActivityLifecycleCallbacks(this)
            deferredStartupScope.launch {
                startDeferredAppStartup()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        crashDiagnostics.install()
        registerActivityLifecycleCallbacks(crashDiagnostics.activityLifecycleCallbacks)
        registerActivityLifecycleCallbacks(deferredStartupLifecycleCallbacks)
        crashDiagnostics.markStartupCheckpoint("app_on_create")
        MiruLog.i("MiruPlayApp", "Application started", crashDiagnostics.sessionAttributes())
    }

    override fun onTerminate() {
        webControlPreferenceListener?.close()
        webControlPreferenceListener = null
        MiruLog.i("MiruPlayApp", "Application terminating", crashDiagnostics.sessionAttributes())
        if (deferredStartupStarted.get()) {
            logUploadScheduler.get().close()
            bangumiArchiveScheduler.get().stop()
            cloudDriveRssScheduler.get().stop()
            webControlServer.get().stopIfRunning()
        }
        deferredStartupScope.cancel()
        unregisterActivityLifecycleCallbacks(deferredStartupLifecycleCallbacks)
        crashDiagnostics.markCleanShutdown("application_terminate")
        unregisterActivityLifecycleCallbacks(crashDiagnostics.activityLifecycleCallbacks)
        super.onTerminate()
    }

    private fun startDeferredAppStartup() {
        val preferences = webControlPreferences.get()
        val listener = DeferredAppStartupCoordinator(
            webControlAccessManager = preferences,
            syncWebControlServer = { syncWebControlServer(preferences) },
            startCloudDriveRssScheduler = { cloudDriveRssScheduler.get().startIfNeeded() },
            startBangumiArchiveScheduler = { bangumiArchiveScheduler.get().startIfNeeded() },
            startLogUploadScheduler = { logUploadScheduler.get().startIfNeeded() },
            markStartupCheckpoint = { checkpoint, attributes ->
                crashDiagnostics.markStartupCheckpoint(checkpoint, attributes)
            },
            onWebControlPreferenceChanged = { enabled ->
                MiruLog.i(
                    "MiruPlayApp",
                    "Web control preference changed",
                    mapOf("web_control_enabled" to enabled.toString()),
                )
            },
        ).start()
        synchronized(this) {
            webControlPreferenceListener?.close()
            webControlPreferenceListener = listener
        }
    }

    private fun syncWebControlServer(
        preferences: WebControlAccessManager = webControlPreferences.get(),
    ) {
        val server = webControlServer.get()
        if (preferences.webControlEnabled) {
            server.startIfNeeded()
        } else {
            server.stopIfRunning()
        }
    }
}

internal class DeferredAppStartupCoordinator(
    private val webControlAccessManager: WebControlAccessManager,
    private val syncWebControlServer: () -> Unit,
    private val startCloudDriveRssScheduler: () -> Unit,
    private val startBangumiArchiveScheduler: () -> Unit,
    private val startLogUploadScheduler: () -> Unit,
    private val markStartupCheckpoint: (String, Map<String, String>) -> Unit,
    private val onWebControlPreferenceChanged: (Boolean) -> Unit,
) {
    fun start(): Closeable {
        val listener = webControlAccessManager.addEnabledChangeListener { enabled ->
            onWebControlPreferenceChanged(enabled)
            syncWebControlServer()
        }
        markStartupCheckpoint(
            "web_control_sync",
            mapOf("web_control_enabled" to webControlAccessManager.webControlEnabled.toString()),
        )
        syncWebControlServer()
        markStartupCheckpoint("cloud_drive_scheduler_start", emptyMap())
        startCloudDriveRssScheduler()
        markStartupCheckpoint("bangumi_archive_scheduler_start", emptyMap())
        startBangumiArchiveScheduler()
        markStartupCheckpoint("log_upload_scheduler_start", emptyMap())
        startLogUploadScheduler()
        return listener
    }
}
