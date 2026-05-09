package com.miruplay.tv

import android.app.Application
import com.miruplay.tv.webcontrol.WebControlServer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MiruPlayApp : Application() {

    @Inject lateinit var webControlServer: WebControlServer

    override fun onCreate() {
        super.onCreate()
        webControlServer.startIfNeeded()
    }

    override fun onTerminate() {
        webControlServer.stopIfRunning()
        super.onTerminate()
    }
}
