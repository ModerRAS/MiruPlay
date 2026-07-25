package com.miruplay.tv.mediasource

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRequestCoordinatorTest {
    @Test
    fun `all typed producers use one transport consumer without overlap`() {
        val consumer = WebDavEndpointConsumer(minimumIntervalMillis = 0L)
        val executor = Executors.newFixedThreadPool(WebDavRequestKind.entries.size)
        val start = CountDownLatch(1)
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        try {
            val futures = WebDavRequestKind.entries.map { kind ->
                executor.submit<Unit> {
                    start.await()
                    consumer.submit(request(kind)) {
                        val current = active.incrementAndGet()
                        maximumActive.updateAndGet { previous -> maxOf(previous, current) }
                        check(current == 1) { "overlapping WebDAV transport" }
                        try {
                            Thread.sleep(5L)
                            WebDavTransportResult(kind, 200)
                        } finally {
                            active.decrementAndGet()
                        }
                    }.close()
                }
            }

            start.countDown()
            futures.forEach { it.get(5L, TimeUnit.SECONDS) }

            assertEquals(1, maximumActive.get())
        } finally {
            executor.shutdownNow()
            consumer.close()
        }
    }

    @Test
    fun `duplicate path byte requests are coalesced`() {
        WebDavRequestCoordinator.resetForTests()
        val executor = Executors.newFixedThreadPool(2)
        val transportStarted = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val releaseTransport = CountDownLatch(1)
        val calls = AtomicInteger()
        val request = request(WebDavRequestKind.PROPFIND)
        try {
            val first = executor.submit<ByteArray> {
                WebDavRequestCoordinator.executeBytes(request) {
                    calls.incrementAndGet()
                    transportStarted.countDown()
                    releaseTransport.await()
                    WebDavTransportResult("listing".toByteArray(), 207)
                }
            }
            assertTrue(transportStarted.await(1L, TimeUnit.SECONDS))
            val second = executor.submit<ByteArray> {
                secondStarted.countDown()
                WebDavRequestCoordinator.executeBytes(request) {
                    calls.incrementAndGet()
                    WebDavTransportResult("unexpected".toByteArray(), 207)
                }
            }

            assertTrue(secondStarted.await(1L, TimeUnit.SECONDS))
            Thread.sleep(20L)
            releaseTransport.countDown()
            assertEquals("listing", first.get(1L, TimeUnit.SECONDS).decodeToString())
            assertEquals("listing", second.get(1L, TimeUnit.SECONDS).decodeToString())
            assertEquals(1, calls.get())
        } finally {
            releaseTransport.countDown()
            executor.shutdownNow()
            WebDavRequestCoordinator.resetForTests()
        }
    }

    @Test
    fun `streaming response holds endpoint lease until closed`() {
        val consumer = WebDavEndpointConsumer(minimumIntervalMillis = 0L)
        val executor = Executors.newSingleThreadExecutor()
        val secondTransportStarted = CountDownLatch(1)
        try {
            val first = consumer.submit(request(WebDavRequestKind.PLAYBACK, streaming = true)) {
                WebDavTransportResult("stream", 200)
            }
            val second = executor.submit<WebDavLease<String>> {
                consumer.submit(request(WebDavRequestKind.RANGE, streaming = true)) {
                    secondTransportStarted.countDown()
                    WebDavTransportResult("range", 206)
                }
            }

            assertTrue(!secondTransportStarted.await(100L, TimeUnit.MILLISECONDS))
            first.close()
            assertTrue(secondTransportStarted.await(1L, TimeUnit.SECONDS))
            second.get(1L, TimeUnit.SECONDS).close()
        } finally {
            executor.shutdownNow()
            consumer.close()
        }
    }

    @Test
    fun `405 opens endpoint circuit and failed half open probe extends cooldown`() {
        val clock = AtomicLong(1_000L)
        val calls = AtomicInteger()
        val consumer = WebDavEndpointConsumer(
            minimumIntervalMillis = 0L,
            initialCooldownMillis = 100L,
            maximumCooldownMillis = 400L,
            now = clock::get,
            sleep = {},
        )
        try {
            val first = runCatching {
                consumer.submit(request(WebDavRequestKind.PROPFIND)) {
                    calls.incrementAndGet()
                    WebDavTransportResult(Unit, 405)
                }
            }.exceptionOrNull()
            assertTrue(first is WebDavHttpStatusException)

            val blocked = runCatching {
                consumer.submit(request(WebDavRequestKind.LIBRARY_DATABASE)) {
                    calls.incrementAndGet()
                    WebDavTransportResult(Unit, 200)
                }
            }.exceptionOrNull()
            assertTrue(blocked is WebDavCircuitOpenException)
            assertEquals(1, calls.get())

            clock.addAndGet(100L)
            val halfOpen = runCatching {
                consumer.submit(request(WebDavRequestKind.HEAD)) {
                    calls.incrementAndGet()
                    WebDavTransportResult(Unit, 405)
                }
            }.exceptionOrNull()
            assertTrue("half-open result was $halfOpen", halfOpen is WebDavHttpStatusException)

            clock.addAndGet(100L)
            val stillBlocked = runCatching {
                consumer.submit(request(WebDavRequestKind.GET)) {
                    calls.incrementAndGet()
                    WebDavTransportResult(Unit, 200)
                }
            }.exceptionOrNull()
            assertTrue(stillBlocked is WebDavCircuitOpenException)
            assertEquals(2, calls.get())

            clock.addAndGet(100L)
            consumer.submit(request(WebDavRequestKind.GET)) {
                calls.incrementAndGet()
                WebDavTransportResult(Unit, 200)
            }.close()
            assertEquals(3, calls.get())
        } finally {
            consumer.close()
        }
    }

    private fun request(kind: WebDavRequestKind, streaming: Boolean = false): WebDavRequest =
        WebDavRequest(
            method = if (kind == WebDavRequestKind.PROPFIND) "PROPFIND" else "GET",
            url = "https://dav.example.test/library/${kind.name.lowercase()}",
            kind = kind,
            streaming = streaming,
            deadlineEpochMillis = System.currentTimeMillis() + 5_000L,
        )
}
