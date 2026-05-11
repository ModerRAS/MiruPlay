package com.miruplay.tv

import android.app.Application
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import com.miruplay.tv.webcontrol.WebControlServer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MiruPlayApp : Application() {

    @Inject lateinit var webControlServer: WebControlServer
    @Inject lateinit var cloudDriveRssScheduler: CloudDriveRssScheduler

    override fun onCreate() {
        super.onCreate()
        webControlServer.startIfNeeded()
        cloudDriveRssScheduler.startIfNeeded()
    }

    override fun onTerminate() {
        cloudDriveRssScheduler.stop()
        webControlServer.stopIfRunning()
        super.onTerminate()
    }
}
