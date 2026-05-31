package com.miruplay.tv.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressUpdateThrottlerTest {
    @Test
    fun `first progress update is emitted`() {
        var now = 0L
        val throttler = ProgressUpdateThrottler(clock = { now })

        assertTrue(throttler.shouldUpdate(bytesRead = 8L * 1024L, totalBytes = 1024L * 1024L))
    }

    @Test
    fun `small updates before the interval are suppressed`() {
        var now = 0L
        val throttler = ProgressUpdateThrottler(
            minIntervalMs = 1_000L,
            minBytesDelta = 1024L,
            clock = { now },
        )

        assertTrue(throttler.shouldUpdate(bytesRead = 100L, totalBytes = 10_000L))
        now = 500L

        assertFalse(throttler.shouldUpdate(bytesRead = 900L, totalBytes = 10_000L))
    }

    @Test
    fun `updates after interval and byte delta are emitted`() {
        var now = 0L
        val throttler = ProgressUpdateThrottler(
            minIntervalMs = 1_000L,
            minBytesDelta = 1024L,
            clock = { now },
        )

        assertTrue(throttler.shouldUpdate(bytesRead = 100L, totalBytes = 10_000L))
        now = 1_000L

        assertTrue(throttler.shouldUpdate(bytesRead = 1_200L, totalBytes = 10_000L))
    }

    @Test
    fun `completion is emitted without waiting for interval`() {
        var now = 0L
        val throttler = ProgressUpdateThrottler(
            minIntervalMs = 1_000L,
            minBytesDelta = 1024L,
            clock = { now },
        )

        assertTrue(throttler.shouldUpdate(bytesRead = 900L, totalBytes = 1_000L))
        now = 100L

        assertTrue(throttler.shouldUpdate(bytesRead = 1_000L, totalBytes = 1_000L))
    }
}
