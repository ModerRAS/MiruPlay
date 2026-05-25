package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.repository.WebControlAccessManager
import com.miruplay.tv.webcontrol.NanoHttpWebControlServer
import com.miruplay.tv.webcontrol.WebControlEndpointService
import com.miruplay.tv.webcontrol.WebControlStaticAssets

internal class DesktopWebControlServer(
    webControlService: WebControlEndpointService,
    webControlAccess: WebControlAccessManager,
    port: Int = WebControlConfig.DEFAULT_PORT,
) : NanoHttpWebControlServer(
    webControlPort = port,
    webControlService = webControlService,
    webControlAccess = webControlAccess,
    staticAssets = WebControlStaticAssets { path ->
        DesktopWebControlServer::class.java.classLoader
            .getResourceAsStream(path)
            ?.use { it.readBytes() }
    },
)
