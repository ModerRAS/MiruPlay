package com.miruplay.tv.scraper.core

import com.miruplay.tv.model.CloudDriveAutomationConfig
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

data class BangumiHttpProxyConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
) {
    fun toJavaProxy(): Proxy =
        if (enabled && host.isNotBlank()) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        } else {
            Proxy.NO_PROXY
        }

    companion object {
        val Disabled: BangumiHttpProxyConfig = BangumiHttpProxyConfig(enabled = false, host = "", port = 0)

        fun normalize(enabled: Boolean, host: String, port: Int): BangumiHttpProxyConfig =
            BangumiHttpProxyConfig(
                enabled = enabled,
                host = host.trim(),
                port = port.coerceIn(1, 65_535),
            )
    }
}

fun CloudDriveAutomationConfig.toBangumiHttpProxyConfig(): BangumiHttpProxyConfig =
    BangumiHttpProxyConfig.normalize(
        enabled = rssProxyEnabled,
        host = rssProxyHost,
        port = rssProxyPort,
    )

internal class BangumiProxyAwareOkHttpClient(
    initialClient: OkHttpClient,
) {
    private var currentProxy = BangumiHttpProxyConfig.Disabled

    @Volatile
    private var client: OkHttpClient = initialClient

    @Synchronized
    fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        val nextProxy = BangumiHttpProxyConfig.normalize(proxyConfig.enabled, proxyConfig.host, proxyConfig.port)
        if (currentProxy == nextProxy) return

        currentProxy = nextProxy
        client = client.newBuilder()
            .proxy(nextProxy.toJavaProxy())
            .build()
    }

    fun newCall(request: Request): Call = client.newCall(request)
}
