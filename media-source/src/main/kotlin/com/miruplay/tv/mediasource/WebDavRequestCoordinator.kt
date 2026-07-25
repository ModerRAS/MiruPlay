package com.miruplay.tv.mediasource

import java.io.Closeable
import java.io.IOException
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** The caller's intent, used to keep every WebDAV path on the same endpoint consumer. */
enum class WebDavRequestKind {
    PROPFIND,
    GET,
    HEAD,
    RANGE,
    SCANNER,
    LIBRARY_DATABASE,
    ARTWORK,
    ARTWORK_PACK,
    PLAYBACK,
}

data class WebDavRequest(
    val method: String,
    val url: String,
    val kind: WebDavRequestKind,
    val streaming: Boolean = false,
    val deadlineEpochMillis: Long = System.currentTimeMillis() + DEFAULT_DEADLINE_MILLIS,
) {
    internal val endpoint: String = normalizedWebDavEndpoint(url)
        ?: throw IllegalArgumentException("Invalid WebDAV URL: $url")

    internal val coalescingKey: String = "$method $url"

    private companion object {
        private const val DEFAULT_DEADLINE_MILLIS = 60_000L
    }
}

data class WebDavTransportResult<T>(
    val value: T,
    val statusCode: Int,
    val close: () -> Unit = {},
)

class WebDavHttpStatusException(
    val statusCode: Int,
    message: String = "WebDAV HTTP $statusCode",
    cause: Throwable? = null,
) : IOException(message, cause)

class WebDavCircuitOpenException(
    val retryAtEpochMillis: Long,
) : IOException("WebDAV endpoint is temporarily unavailable until $retryAtEpochMillis")

class WebDavLease<T> internal constructor(
    val value: T,
    private val release: () -> Unit,
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}

/**
 * One bounded producer-consumer pipeline per normalized WebDAV authority.
 * Streaming work keeps the consumer blocked until its lease is closed.
 */
object WebDavRequestCoordinator {
    private val endpoints = ConcurrentHashMap<String, WebDavEndpointConsumer>()
    private val registeredEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val coalescedBytes = ConcurrentHashMap<String, CompletableFuture<ByteArray>>()

    fun register(endpointUrl: String) {
        normalizedWebDavEndpoint(endpointUrl)?.let(registeredEndpoints::add)
    }

    fun isRegisteredEndpoint(url: String): Boolean =
        normalizedWebDavEndpoint(url)?.let(registeredEndpoints::contains) == true

    fun <T> execute(
        request: WebDavRequest,
        transport: () -> WebDavTransportResult<T>,
    ): WebDavLease<T> {
        registeredEndpoints += request.endpoint
        return endpoints.computeIfAbsent(request.endpoint) { WebDavEndpointConsumer() }
            .submit(request, transport)
    }

    fun executeBytes(
        request: WebDavRequest,
        transport: () -> WebDavTransportResult<ByteArray>,
    ): ByteArray {
        require(!request.streaming) { "Coalesced byte requests cannot be streaming" }
        val key = "${request.endpoint} ${request.coalescingKey}"
        val created = CompletableFuture<ByteArray>()
        val pending = coalescedBytes.putIfAbsent(key, created)
        if (pending != null) return pending.await(request.deadlineEpochMillis)
        try {
            val bytes = execute(request, transport).use { lease -> lease.value }
            created.complete(bytes)
            return bytes
        } catch (error: Throwable) {
            created.completeExceptionally(error)
            throw error
        } finally {
            coalescedBytes.remove(key, created)
        }
    }

    internal fun resetForTests() {
        endpoints.values.forEach(WebDavEndpointConsumer::close)
        endpoints.clear()
        registeredEndpoints.clear()
        coalescedBytes.clear()
    }
}

internal class WebDavEndpointConsumer(
    private val queueCapacity: Int = 32,
    private val minimumIntervalMillis: Long = 150L,
    private val initialCooldownMillis: Long = 30_000L,
    private val maximumCooldownMillis: Long = 5 * 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Closeable {
    private val queue = ArrayBlockingQueue<QueueWork>(queueCapacity)
    private val closed = AtomicBoolean(false)
    private val stateLock = Any()
    private var openUntil = 0L
    private var cooldownMillis = initialCooldownMillis
    private var lastRequestFinishedAt = 0L
    private val worker = Thread({ consume() }, "miruplay-webdav-consumer").apply {
        isDaemon = true
        start()
    }

    fun <T> submit(
        request: WebDavRequest,
        transport: () -> WebDavTransportResult<T>,
    ): WebDavLease<T> {
        check(!closed.get()) { "WebDAV consumer is closed" }
        circuitFailure(now())?.let { throw it }
        val work = TypedWork(request, transport)
        val remaining = request.deadlineEpochMillis - now()
        if (remaining <= 0L || !queue.offer(work, remaining, TimeUnit.MILLISECONDS)) {
            throw TimeoutException("Timed out enqueuing ${request.kind} request")
        }
        return try {
            work.completion.get(
                (request.deadlineEpochMillis - now()).coerceAtLeast(1L),
                TimeUnit.MILLISECONDS,
            )
        } catch (error: InterruptedException) {
            work.cancel()
            Thread.currentThread().interrupt()
            throw IOException("WebDAV request cancelled", error)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } catch (error: TimeoutException) {
            work.cancel()
            throw error
        }
    }

    private fun consume() {
        while (!closed.get()) {
            val work = try {
                queue.poll(250L, TimeUnit.MILLISECONDS) ?: continue
            } catch (_: InterruptedException) {
                continue
            }
            if (work.cancelled || now() >= work.request.deadlineEpochMillis) {
                work.fail(TimeoutException("WebDAV request deadline exceeded"))
                continue
            }
            val circuitError = circuitFailure(now())
            if (circuitError != null) {
                work.fail(circuitError)
                continue
            }
            pace()
            work.execute(this)
        }
    }

    private fun pace() {
        val waitMillis = minimumIntervalMillis - (now() - lastRequestFinishedAt)
        if (waitMillis > 0L) sleep(waitMillis)
    }

    private fun <T> execute(work: TypedWork<T>) {
        val halfOpen = synchronized(stateLock) { openUntil > 0L && now() >= openUntil }
        try {
            val result = work.transport()
            if (result.statusCode == HTTP_METHOD_NOT_ALLOWED) {
                openCircuit()
                runCatching { result.close() }
                work.fail(WebDavHttpStatusException(HTTP_METHOD_NOT_ALLOWED))
                failQueuedForOpenCircuit()
                return
            }
            if (halfOpen) closeCircuit()
            val released = CountDownLatch(1)
            val lease = WebDavLease(result.value) {
                try {
                    result.close()
                } finally {
                    released.countDown()
                }
            }
            work.complete(lease)
            if (work.request.streaming) {
                released.await()
            } else {
                lease.close()
            }
        } catch (error: Throwable) {
            if (error.findHttpStatus() == HTTP_METHOD_NOT_ALLOWED || halfOpen) {
                openCircuit()
                failQueuedForOpenCircuit()
            }
            work.fail(error)
        } finally {
            lastRequestFinishedAt = now()
        }
    }

    private fun circuitFailure(timestamp: Long): WebDavCircuitOpenException? = synchronized(stateLock) {
        openUntil.takeIf { it > timestamp }?.let(::WebDavCircuitOpenException)
    }

    private fun openCircuit() = synchronized(stateLock) {
        openUntil = now() + cooldownMillis
        cooldownMillis = (cooldownMillis * 2).coerceAtMost(maximumCooldownMillis)
    }

    private fun closeCircuit() = synchronized(stateLock) {
        openUntil = 0L
        cooldownMillis = initialCooldownMillis
    }

    private fun failQueuedForOpenCircuit() {
        val failure = circuitFailure(now()) ?: return
        while (true) (queue.poll() ?: return).fail(failure)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.interrupt()
        while (true) (queue.poll() ?: return).fail(IOException("WebDAV consumer closed"))
    }

    private interface QueueWork {
        val request: WebDavRequest
        val cancelled: Boolean
        fun execute(consumer: WebDavEndpointConsumer)
        fun fail(error: Throwable)
    }

    private class TypedWork<T>(
        override val request: WebDavRequest,
        val transport: () -> WebDavTransportResult<T>,
    ) : QueueWork {
        val completion = CompletableFuture<WebDavLease<T>>()
        override val cancelled: Boolean get() = completion.isCancelled

        override fun execute(consumer: WebDavEndpointConsumer) = consumer.execute(this)
        override fun fail(error: Throwable) {
            completion.completeExceptionally(error)
        }

        fun complete(lease: WebDavLease<T>) {
            if (!completion.complete(lease)) lease.close()
        }

        fun cancel() {
            completion.cancel(false)
        }
    }

    private companion object {
        private const val HTTP_METHOD_NOT_ALLOWED = 405
    }
}

internal fun normalizedWebDavEndpoint(url: String): String? = runCatching {
    val uri = URI(url.trim())
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
        ?: return@runCatching null
    val host = uri.host?.lowercase() ?: return@runCatching null
    val port = when {
        uri.port >= 0 -> uri.port
        scheme == "https" -> 443
        else -> 80
    }
    "$scheme://$host:$port"
}.getOrNull()

private fun Throwable.findHttpStatus(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is WebDavHttpStatusException) return current.statusCode
        current = current.cause
    }
    return null
}

private fun <T> CompletableFuture<T>.await(deadlineEpochMillis: Long): T = try {
    get((deadlineEpochMillis - System.currentTimeMillis()).coerceAtLeast(1L), TimeUnit.MILLISECONDS)
} catch (error: ExecutionException) {
    throw error.cause ?: error
}
