package com.miruplay.tv

import android.app.Application
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.logging.AppCrashDiagnostics
import com.miruplay.tv.data.logging.LogUploadScheduler
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.sync.archive.BangumiArchiveScheduler
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import com.miruplay.tv.webcontrol.WebControlServer
import dagger.hilt.android.HiltAndroidApp
import java.io.Closeable
import javax.inject.Inject

@HiltAndroidApp
class MiruPlayApp : Application() {

    @Inject lateinit var webControlServer: WebControlServer
    @Inject lateinit var webControlPreferences: WebControlAccessManager
    @Inject lateinit var cloudDriveRssScheduler: CloudDriveRssScheduler
    @Inject lateinit var bangumiArchiveScheduler: BangumiArchiveScheduler
    @Inject lateinit var logUploadScheduler: LogUploadScheduler
    @Inject lateinit var crashDiagnostics: AppCrashDiagnostics

    private var webControlPreferenceListener: Closeable? = null

    override fun onCreate() {
        super.onCreate()
        crashDiagnostics.install()
        registerActivityLifecycleCallbacks(crashDiagnostics.activityLifecycleCallbacks)
        crashDiagnostics.markStartupCheckpoint("app_on_create")
        webControlPreferenceListener = webControlPreferences.addEnabledChangeListener {
            MiruLog.i(
                "MiruPlayApp",
                "Web control preference changed",
                mapOf("web_control_enabled" to webControlPreferences.webControlEnabled.toString())
            )
            syncWebControlServer()
        }
        crashDiagnostics.markStartupCheckpoint(
            "web_control_sync",
            mapOf("web_control_enabled" to webControlPreferences.webControlEnabled.toString())
        )
        syncWebControlServer()
        crashDiagnostics.markStartupCheckpoint("cloud_drive_scheduler_start")
        cloudDriveRssScheduler.startIfNeeded()
        crashDiagnostics.markStartupCheckpoint("bangumi_archive_scheduler_start")
        bangumiArchiveScheduler.startIfNeeded()
        crashDiagnostics.markStartupCheckpoint("log_upload_scheduler_start")
        logUploadScheduler.startIfNeeded()
        MiruLog.i("MiruPlayApp", "Application started", crashDiagnostics.sessionAttributes())
    }

    override fun onTerminate() {
        webControlPreferenceListener?.close()
        webControlPreferenceListener = null
        MiruLog.i("MiruPlayApp", "Application terminating", crashDiagnostics.sessionAttributes())
        logUploadScheduler.close()
        bangumiArchiveScheduler.stop()
        cloudDriveRssScheduler.stop()
        webControlServer.stopIfRunning()
        crashDiagnostics.markCleanShutdown("application_terminate")
        unregisterActivityLifecycleCallbacks(crashDiagnostics.activityLifecycleCallbacks)
        super.onTerminate()
    }

    private fun syncWebControlServer() {
        if (webControlPreferences.webControlEnabled) {
            webControlServer.startIfNeeded()
        } else {
            webControlServer.stopIfRunning()
        }
    }
}
