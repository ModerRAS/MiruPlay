package com.miruplay.tv.webcontrol

import android.content.Context
import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.repository.WebControlAccessManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlServer @Inject constructor(
    @ApplicationContext context: Context,
    webControlService: WebControlService,
    webControlPreferences: WebControlAccessManager
) : NanoHttpWebControlServer(
    webControlPort = WebControlConfig.DEFAULT_PORT,
    webControlService = webControlService,
    webControlAccess = webControlPreferences,
    staticAssets = WebControlStaticAssets { path ->
        runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()
    },
)
