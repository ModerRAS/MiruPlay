package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.findWebControlLocalIps

fun buildWebControlServerInfo(
    deviceName: String,
    port: Int,
    startedAt: Long,
    appName: String = "MiruPlay",
    localIps: List<String> = findWebControlLocalIps(),
): ServerInfoDto =
    ServerInfoDto(
        appName = appName,
        deviceName = deviceName,
        port = port,
        localIps = localIps.normalizedWebControlLocalIps(),
        startedAt = startedAt,
    )

private fun List<String>.normalizedWebControlLocalIps(): List<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
