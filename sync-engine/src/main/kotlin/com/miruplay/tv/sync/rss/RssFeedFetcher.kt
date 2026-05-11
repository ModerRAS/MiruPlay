package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

@Singleton
class RssFeedFetcher @Inject constructor() {
    private var currentProxyHost: String = ""
    private var currentProxyPort: Int = 0
    private var currentProxyEnabled: Boolean = false

    @Volatile
    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Configure HTTP proxy for RSS feed fetching.
     * Only rebuilds the OkHttpClient when proxy configuration changes.
     * Synchronized to prevent data race between proxy field writes.
     */
    @Synchronized
    fun configureProxy(enabled: Boolean, host: String, port: Int) {
        val normalizedHost = host.trim()
        val normalizedPort = port.coerceIn(1, 65535)
        if (currentProxyEnabled == enabled && currentProxyHost == normalizedHost && currentProxyPort == normalizedPort) {
            return
        }
        currentProxyEnabled = enabled
        currentProxyHost = normalizedHost
        currentProxyPort = normalizedPort

        // Use newBuilder() to share connection pools and dispatchers with previous client
        client = client.newBuilder()
            .proxy(if (enabled && normalizedHost.isNotBlank()) Proxy(Proxy.Type.HTTP, InetSocketAddress(normalizedHost, normalizedPort)) else Proxy.NO_PROXY)
            .build()
    }

    suspend fun fetch(url: String): Result<List<RssFeedItem>> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    AppError.NetworkError.HttpError(response.code, response.message)
                )
            }
            val xml = response.body?.string().orEmpty()
            Result.success(parse(xml))
        } catch (e: Exception) {
            Result.failure(AppError.NetworkError.ServerUnreachable(url))
        }
    }

    private fun parse(xml: String): List<RssFeedItem> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        val itemNodes = doc.getElementsByTagName("item")
        if (itemNodes.length > 0) {
            return (0 until itemNodes.length).mapNotNull { index ->
                val element = itemNodes.item(index) as? Element ?: return@mapNotNull null
                RssFeedItem(
                    title = element.text("title").ifBlank { "未命名条目" },
                    guid = element.text("guid").takeIf { it.isNotBlank() },
                    link = element.text("link").takeIf { it.isNotBlank() },
                    enclosureUrl = element.firstEnclosureUrl()
                )
            }
        }

        val entryNodes = doc.getElementsByTagName("entry")
        return (0 until entryNodes.length).mapNotNull { index ->
            val element = entryNodes.item(index) as? Element ?: return@mapNotNull null
            RssFeedItem(
                title = element.text("title").ifBlank { "未命名条目" },
                guid = element.text("id").takeIf { it.isNotBlank() },
                link = element.firstAtomLink(),
                enclosureUrl = null
            )
        }
    }

    private fun Element.text(tagName: String): String =
        getElementsByTagName(tagName).item(0)?.textContent?.trim().orEmpty()

    private fun Element.firstEnclosureUrl(): String? {
        val nodes = getElementsByTagName("enclosure")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val url = element.getAttribute("url")
            if (url.isNotBlank()) return url
        }
        return null
    }

    private fun Element.firstAtomLink(): String? {
        val nodes = getElementsByTagName("link")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val href = element.getAttribute("href")
            if (href.isNotBlank()) return href
        }
        return text("link").takeIf { it.isNotBlank() }
    }
}
