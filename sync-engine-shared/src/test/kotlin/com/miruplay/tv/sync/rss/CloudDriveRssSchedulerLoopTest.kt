package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CloudDriveRssSchedulerLoopTest {
    @Test
    fun `shared scheduler loop repeatedly invokes due runner until cancellation`() = runBlocking {
        val checks = AtomicInteger(0)
        val callbacks = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val job = scope.launchCloudDriveRssSchedulerLoop(
                dueRunner = CloudDriveRssDueRunner {
                    checks.incrementAndGet()
                    Result.success(null)
                },
                checkIntervalMillis = 20L,
            ) { checkedAtMillis, result ->
                assertTrue(checkedAtMillis > 0L)
                assertTrue(result is Result.Success)
                callbacks.incrementAndGet()
            }
            withTimeout(500L) {
                while (checks.get() < 3) {
                    delay(10L)
                }
            }
            job.cancel()
            job.join()
        } finally {
            scope.cancel()
        }

        assertTrue(checks.get() >= 3)
        assertEquals(checks.get(), callbacks.get())
    }

    @Test
    fun `shared scheduler loop rejects non-positive interval`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                scope.launchCloudDriveRssSchedulerLoop(
                    dueRunner = CloudDriveRssDueRunner { Result.success(null) },
                    checkIntervalMillis = 0L,
                )
            }
            assertTrue(error.message?.contains("checkIntervalMillis") == true)
        } finally {
            scope.cancel()
        }
    }
}
