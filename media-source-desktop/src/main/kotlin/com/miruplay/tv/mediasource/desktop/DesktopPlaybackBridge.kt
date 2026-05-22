package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.HttpByteRange
import com.miruplay.tv.model.HttpByteRangeRequest
import com.miruplay.tv.model.HttpStreamResponsePlan
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class DesktopPlaybackBridge : AutoCloseable, DesktopPlaybackUriBridge {
    private val routes = ConcurrentHashMap<String, Route>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "miruplay-playback-bridge").apply { isDaemon = true }
    }

    val port: Int get() = server.address.port

    init {
        server.createContext(STREAM_PREFIX, ::handleStream)
        server.executor = executor
        server.start()
    }

    override fun playableUri(source: DesktopMediaSource, path: String): String {
        val token = UUID.randomUUID().toString()
        routes[token] = Route(source = source, path = path)
        val name = MediaPathConventions.fileName(path).ifBlank { "stream" }
        val encodedName = MediaPathConventions.encodePathSegment(name)
        return "http://127.0.0.1:$port/stream/$token/$encodedName"
    }

    override fun close() {
        routes.clear()
        server.stop(0)
        executor.shutdownNow()
    }

    private fun handleStream(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
            exchange.sendStatus(405, "Method not allowed")
            return
        }

        val token = exchange.requestURI.path
            .removePrefix(STREAM_PREFIX)
            .trim('/')
            .substringBefore('/')
            .takeIf { it.isNotBlank() }
        val route = token?.let(routes::get)
        if (route == null) {
            exchange.sendStatus(404, "Stream route not found")
            return
        }

        val metadata = runBlocking { route.source.getMetadata(route.path) }.getOrNull()
        val range = exchange.requestHeaders.getFirst("Range")
            ?.let(HttpByteRangeRequest::parse)
            ?.resolve(metadata?.size)

        if (range is HttpByteRange.Invalid) {
            val plan = HttpStreamResponsePlan.from(range, metadata?.size)
            plan.contentRangeHeader?.let { exchange.responseHeaders.add("Content-Range", it) }
            exchange.sendStatus(plan.statusCode, "Requested range not satisfiable")
            return
        }

        if (exchange.requestMethod == "HEAD") {
            exchange.sendStreamHeaders(range, metadata)
            exchange.responseBody.close()
            return
        }

        val resolvedRange = range as? HttpByteRange.Resolved
        val streamResult = runBlocking {
            if (resolvedRange != null) {
                route.source.openStream(route.path, resolvedRange.toStreamRange())
            } else {
                route.source.openStream(route.path)
            }
        }
        when (streamResult) {
            is Result.Success -> streamResult.data.use { input ->
                exchange.sendStreamHeaders(range, metadata)
                exchange.responseBody.use { output ->
                    input.copyTo(output)
                }
            }
            is Result.Error -> exchange.sendStatus(404, streamResult.error.toUserMessage())
        }
    }

    private fun HttpExchange.addStreamHeaders(plan: HttpStreamResponsePlan) {
        responseHeaders.add("Content-Type", "application/octet-stream")
        responseHeaders.add("Cache-Control", "no-store")
        responseHeaders.add("Accept-Ranges", "bytes")
        plan.contentRangeHeader?.let { responseHeaders.add("Content-Range", it) }
    }

    private fun HttpExchange.sendStreamHeaders(range: HttpByteRange?, metadata: FileMetadata?) {
        val plan = HttpStreamResponsePlan.from(range, metadata?.size)
        addStreamHeaders(plan)
        sendResponseHeaders(plan.statusCode, plan.contentLength)
    }

    private fun HttpExchange.sendStatus(status: Int, message: String) {
        val body = message.toByteArray(Charsets.UTF_8)
        responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private data class Route(
        val source: DesktopMediaSource,
        val path: String,
    )

    private companion object {
        const val STREAM_PREFIX = "/stream/"
    }
}

fun playableUriFor(
    source: DesktopMediaSource?,
    bridge: DesktopPlaybackUriBridge,
    mediaPath: String,
): String {
    val path = mediaPath.trim()
    return if (source != null && MediaSourceInfoConventions.shouldBridgeForPlayback(source.info.type, path)) {
        bridge.playableUri(source, path)
    } else {
        path
    }
}

interface DesktopPlaybackUriBridge {
    fun playableUri(source: DesktopMediaSource, path: String): String
}
