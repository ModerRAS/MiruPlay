package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopStreamRange
import com.miruplay.tv.model.FileMetadata
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class DesktopPlaybackBridge : AutoCloseable {
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

    fun playableUri(source: DesktopMediaSource, path: String): String {
        val token = UUID.randomUUID().toString()
        routes[token] = Route(source = source, path = path)
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val encodedName = URLEncoder.encode(name.ifBlank { "stream" }, Charsets.UTF_8.name())
            .replace("+", "%20")
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
            ?.let(ByteRangeRequest::parse)
            ?.resolve(metadata)

        if (range == ByteRange.Invalid) {
            exchange.responseHeaders.add("Content-Range", "bytes */${metadata?.size ?: "*"}")
            exchange.sendStatus(416, "Requested range not satisfiable")
            return
        }

        if (exchange.requestMethod == "HEAD") {
            exchange.sendStreamHeaders(range, metadata)
            exchange.responseBody.close()
            return
        }

        val resolvedRange = range as? ByteRange.Resolved
        val streamResult = runBlocking {
            if (resolvedRange != null) {
                route.source.openStream(route.path, DesktopStreamRange(resolvedRange.start, resolvedRange.endInclusive))
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

    private fun HttpExchange.sendStreamHeaders(range: ByteRange?, metadata: FileMetadata?) {
        responseHeaders.add("Content-Type", "application/octet-stream")
        responseHeaders.add("Cache-Control", "no-store")
        responseHeaders.add("Accept-Ranges", "bytes")

        val resolvedRange = range as? ByteRange.Resolved
        if (resolvedRange != null) {
            responseHeaders.add(
                "Content-Range",
                "bytes ${resolvedRange.start}-${resolvedRange.endInclusive}/${resolvedRange.totalLength}"
            )
            sendResponseHeaders(206, resolvedRange.length)
            return
        }

        val size = metadata?.size?.takeIf { it > 0L }
        sendResponseHeaders(200, size ?: 0L)
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

    private data class ByteRangeRequest(
        val start: Long?,
        val endInclusive: Long?,
    ) {
        fun resolve(metadata: FileMetadata?): ByteRange {
            val totalLength = metadata?.size?.takeIf { it > 0L } ?: return ByteRange.Unresolved
            val resolvedStart = start ?: (totalLength - (endInclusive ?: 0L)).coerceAtLeast(0L)
            val resolvedEnd = (if (start == null) totalLength - 1 else endInclusive ?: totalLength - 1)
                .coerceAtMost(totalLength - 1)
            if (resolvedStart < 0 || resolvedStart >= totalLength || resolvedStart > resolvedEnd) {
                return ByteRange.Invalid
            }
            return ByteRange.Resolved(
                start = resolvedStart,
                endInclusive = resolvedEnd,
                totalLength = totalLength,
            )
        }

        companion object {
            fun parse(header: String): ByteRangeRequest? {
                if (!header.startsWith("bytes=")) return null
                val spec = header.removePrefix("bytes=").substringBefore(',').trim()
                val start = spec.substringBefore('-', "").trim().takeIf { it.isNotBlank() }?.toLongOrNull()
                val end = spec.substringAfter('-', "").trim().takeIf { it.isNotBlank() }?.toLongOrNull()
                if (start == null && end == null) return null
                return ByteRangeRequest(start = start, endInclusive = end)
            }
        }
    }

    private sealed interface ByteRange {
        data object Unresolved : ByteRange
        data object Invalid : ByteRange
        data class Resolved(
            val start: Long,
            val endInclusive: Long,
            val totalLength: Long,
        ) : ByteRange {
            val length: Long = endInclusive - start + 1
        }
    }

    private companion object {
        const val STREAM_PREFIX = "/stream/"
    }
}
