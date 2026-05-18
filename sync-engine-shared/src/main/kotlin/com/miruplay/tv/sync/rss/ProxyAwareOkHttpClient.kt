package com.miruplay.tv.sync.rss

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

internal class ProxyAwareOkHttpClient(
    initialClient: OkHttpClient,
) {
    private var currentProxy = HttpProxyConfig(enabled = false, host = "", port = 0)

    @Volatile
    private var client: OkHttpClient = initialClient

    @Synchronized
    fun configureProxy(enabled: Boolean, host: String, port: Int) {
        val nextProxy = HttpProxyConfig.normalize(enabled, host, port)
        if (currentProxy == nextProxy) return

        currentProxy = nextProxy
        client = client.newBuilder()
            .proxy(nextProxy.toJavaProxy())
            .build()
    }

    fun newCall(request: Request): Call = client.newCall(request)
}

internal data class HttpProxyConfig(
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
        fun normalize(enabled: Boolean, host: String, port: Int): HttpProxyConfig =
            HttpProxyConfig(
                enabled = enabled,
                host = host.trim(),
                port = port.coerceIn(1, 65535),
            )
    }
}
