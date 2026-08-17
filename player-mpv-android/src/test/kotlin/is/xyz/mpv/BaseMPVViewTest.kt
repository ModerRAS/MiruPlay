package `is`.xyz.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BaseMPVViewTest {
    @Test
    fun `loadfile waits until surface is attached`() {
        assertFalse(shouldLoadMpvFileImmediately(surfaceAttached = false))
    }

    @Test
    fun `loadfile can run immediately after surface attach`() {
        assertTrue(shouldLoadMpvFileImmediately(surfaceAttached = true))
    }

    @Test
    fun `release gate orders native shutdown and rejects late work`() {
        val gate = MpvReleaseGate()
        val events = mutableListOf<String>()

        assertEquals("snapshot", gate.withNativeAccess {
            events += "native-read"
            "snapshot"
        })
        assertTrue(gate.beginRelease())
        assertFalse(gate.beginRelease())
        assertNull(gate.withNativeAccess { events += "late-callback" })
        assertNull(gate.withNativeAccess { events += "late-surface-reconfigure" })
        assertEquals(Unit, gate.withReleaseNativeAccess { events += "stop" })
        assertTrue(gate.finishRelease {
            assertFalse(gate.beginRelease())
            events += "destroy"
        })
        assertFalse(gate.finishRelease { events += "duplicate-destroy" })
        assertNull(gate.withNativeAccess { events += "read-after-destroy" })

        assertEquals(listOf("native-read", "stop", "destroy"), events)
    }

    @Test
    fun `release waits outside gate so idle event can precede single destroy`() {
        val gate = MpvReleaseGate()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val readEntered = CountDownLatch(1)
        val allowReadExit = CountDownLatch(1)
        val releaseAttempted = CountDownLatch(1)
        val releaseAwaitingEvent = CountDownLatch(1)
        val idleEvent = CountDownLatch(1)
        val releaseCompleted = CountDownLatch(1)
        val destroyCount = AtomicInteger()

        val readThread = Thread {
            gate.withNativeAccess {
                events += "read-enter"
                readEntered.countDown()
                assertTrue(allowReadExit.await(2, TimeUnit.SECONDS))
                events += "read-exit"
            }
        }.apply { start() }
        assertTrue(readEntered.await(2, TimeUnit.SECONDS))

        val releaseThread = Thread {
            releaseAttempted.countDown()
            assertTrue(gate.beginRelease())
            events += "release-began"
            assertEquals(Unit, gate.withReleaseNativeAccess { events += "stop" })
            releaseAwaitingEvent.countDown()
            assertTrue(idleEvent.await(2, TimeUnit.SECONDS))
            events += "idle-observed"
            assertTrue(gate.finishRelease {
                events += "destroy"
                destroyCount.incrementAndGet()
            })
            releaseCompleted.countDown()
        }.apply { start() }
        assertTrue(releaseAttempted.await(2, TimeUnit.SECONDS))
        assertFalse(releaseAwaitingEvent.await(100, TimeUnit.MILLISECONDS))

        allowReadExit.countDown()
        assertTrue(releaseAwaitingEvent.await(2, TimeUnit.SECONDS))
        val eventThread = Thread {
            events += "idle-event"
            idleEvent.countDown()
            assertNull(gate.withNativeAccess { events += "late-callback" })
        }.apply { start() }
        assertTrue(releaseCompleted.await(2, TimeUnit.SECONDS))
        readThread.join(2_000)
        eventThread.join(2_000)
        releaseThread.join(2_000)

        assertFalse(gate.finishRelease { destroyCount.incrementAndGet() })
        assertNull(gate.withNativeAccess { events += "late-read" })
        assertEquals(
            listOf(
                "read-enter",
                "read-exit",
                "release-began",
                "stop",
                "idle-event",
                "idle-observed",
                "destroy",
            ),
            events,
        )
        assertEquals(1, destroyCount.get())
    }

    @Test
    fun `release timeout still destroys exactly once`() {
        val gate = MpvReleaseGate()
        val neverSignalled = CountDownLatch(1)
        val destroyCount = AtomicInteger()

        assertTrue(gate.beginRelease())
        assertEquals(Unit, gate.withReleaseNativeAccess { Unit })
        assertFalse(neverSignalled.await(10, TimeUnit.MILLISECONDS))
        assertTrue(gate.finishRelease { destroyCount.incrementAndGet() })
        assertFalse(gate.finishRelease { destroyCount.incrementAndGet() })

        assertEquals(1, destroyCount.get())
    }

    @Test
    fun `surface destroy policy only detaches and never reconfigures mpv`() {
        assertEquals(listOf(MpvSurfaceDestroyAction.DETACH), surfaceDestroyActions())
    }

    @Test
    fun `resume seek is skipped when start position is zero`() {
        assertFalse(shouldApplyPendingStartSeek(startPositionMs = null))
        assertFalse(shouldApplyPendingStartSeek(startPositionMs = 0L))
    }

    @Test
    fun `resume seek is deferred until file load when start position is positive`() {
        assertTrue(shouldApplyPendingStartSeek(startPositionMs = 1L))
        assertTrue(shouldApplyPendingStartSeek(startPositionMs = 30_000L))
    }
}
