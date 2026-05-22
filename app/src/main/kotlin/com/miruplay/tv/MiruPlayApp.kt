package com.miruplay.tv

import android.app.Application
import com.miruplay.tv.repository.WebControlAccessManager
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

    private var webControlPreferenceListener: Closeable? = null

    override fun onCreate() {
        super.onCreate()
        webControlPreferenceListener = webControlPreferences.addEnabledChangeListener {
            syncWebControlServer()
        }
        syncWebControlServer()
        cloudDriveRssScheduler.startIfNeeded()
    }

    override fun onTerminate() {
        webControlPreferenceListener?.close()
        webControlPreferenceListener = null
        cloudDriveRssScheduler.stop()
        webControlServer.stopIfRunning()
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
