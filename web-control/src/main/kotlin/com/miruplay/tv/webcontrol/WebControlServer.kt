package com.miruplay.tv.webcontrol

import android.content.Context
import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.repository.WebControlAccessManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlServer @Inject constructor(
    @ApplicationContext private val context: Context,
    webControlService: WebControlEndpointService,
    webControlPreferences: WebControlAccessManager,
) : NanoHttpWebControlServer(
    webControlPort = WebControlConfig.DEFAULT_PORT,
    webControlService = webControlService,
    webControlAccess = webControlPreferences,
    staticAssets = WebControlStaticAssets { path ->
        runCatching {
            context.assets.open(path).use { it.readBytes() }
        }.getOrNull()
    },
) {
    override fun startIfNeeded() {
        val wasRunning = isRunning()
        super.startIfNeeded()
        if (!wasRunning && isRunning()) {
            MiruLog.i(
                "WebControlServer",
                "Web control server started",
                mapOf("port" to WebControlConfig.DEFAULT_PORT.toString()),
            )
        }
    }

    override fun stopIfRunning() {
        val wasRunning = isRunning()
        super.stopIfRunning()
        if (wasRunning && !isRunning()) {
            MiruLog.i("WebControlServer", "Web control server stopped")
        }
    }
}
