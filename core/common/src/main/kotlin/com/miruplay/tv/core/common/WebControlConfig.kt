package com.miruplay.tv.core.common

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder

object WebControlConfig {
    const val DEFAULT_PORT = 9978
}

fun findWebControlLocalIps(): List<String> =
    runCatching {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            .orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }.getOrDefault(emptyList())

fun buildWebControlAccessUrls(
    accessToken: String,
    port: Int = WebControlConfig.DEFAULT_PORT,
    localIps: List<String> = findWebControlLocalIps(),
): List<String> {
    val token = accessToken.trim()
    if (token.isBlank()) return emptyList()
    val encodedToken = URLEncoder
        .encode(token, Charsets.UTF_8.name())
        .replace("+", "%20")
    return localIps
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .map { ip -> "http://$ip:$port/?token=$encodedToken" }
}
