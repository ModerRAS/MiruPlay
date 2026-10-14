package com.miruplay.tv.player

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackHealthMonitorTest {

    // ── video ───────────────────────────────────────────────────────

    @Test
    fun `video snapshot is null before start`() {
        assertNull(VideoHealthMonitor(clock = FakeClock()).snapshot())
    }

    @Test
    fun `video feeds aggregate into snapshot`() {
        val monitor = VideoHealthMonitor(clock = FakeClock())
        monitor.start()
        monitor.onDecoderInitialized("c2.amlogic.hevc.decoder")
        monitor.onInputFormat("video/hevc", "hev1.2.4.L123", 1920, 1080, 23.976f)
        monitor.onDroppedFrames(3)
        monitor.onDroppedFrames(7)
        monitor.onFrameRendered()
        monitor.onFrameRendered()
        monitor.onAudioUnderrun()

        val snap = monitor.snapshot()!!
        assertEquals("c2.amlogic.hevc.decoder", snap.decoderName)
        assertEquals(1, snap.decoderInitCount)
        assertEquals("video/hevc", snap.sampleMimeType)
        assertEquals(1920, snap.width)
        assertEquals(1080, snap.height)
        assertEquals(10L, snap.droppedFramesTotal)
        assertEquals(2, snap.droppedEvents)
        assertEquals(7, snap.maxDroppedPerEvent)
        assertEquals(2L, snap.renderedFrames)
        assertEquals(1, snap.audioUnderruns)
    }

    @Test
    fun `video start resets previous playback`() {
        val monitor = VideoHealthMonitor(clock = FakeClock())
        monitor.start()
        monitor.onDroppedFrames(5)
        monitor.start()

        assertEquals(0L, monitor.snapshot()!!.droppedFramesTotal)
    }

    @Test
    fun `video stop retains snapshot for post-mortem`() {
        val monitor = VideoHealthMonitor(clock = FakeClock())
        monitor.start()
        monitor.onDroppedFrames(5)
        monitor.stop()
        monitor.onDroppedFrames(99)

        assertEquals(5L, monitor.snapshot()!!.droppedFramesTotal)
    }

    // ── ui jank ─────────────────────────────────────────────────────

    @Test
    fun `ui snapshot is null before start`() {
        assertNull(newJankMonitor().snapshot())
    }

    @Test
    fun `ui counts smooth and jank frames`() {
        val captor = FrameCaptor()
        val monitor = newJankMonitor(captor)
        monitor.start()
        shadowOf(Looper.getMainLooper()).idle()

        var t = 1_000_000_000L
        captor.fire(t)
        t += 16_000_000L
        captor.fire(t)
        t += 16_000_000L
        captor.fire(t)
        t += 50_000_000L // jank
        captor.fire(t)

        val snap = monitor.snapshot()!!
        assertEquals(3L, snap.totalFrames)
        assertEquals(1L, snap.jankFrames)
        assertEquals(50L, snap.maxDeltaMs)
    }

    @Test
    fun `ui stop halts frame accounting`() {
        val captor = FrameCaptor()
        val monitor = newJankMonitor(captor)
        monitor.start()
        shadowOf(Looper.getMainLooper()).idle()
        captor.fire(1_000_000_000L)
        monitor.stop()
        shadowOf(Looper.getMainLooper()).idle()

        // No reschedule after stop: firing the stale callback is a no-op.
        captor.fire(1_100_000_000L)

        val snap = monitor.snapshot()!!
        assertEquals(0L, snap.totalFrames)
        assertTrue(!snap.running)
    }

    private class FakeClock(var nowNs: Long = 0L) : () -> Long {
        override fun invoke(): Long = nowNs
    }

    private class FrameCaptor {
        var callback: Choreographer.FrameCallback? = null
        fun fire(frameTimeNs: Long) {
            callback?.doFrame(frameTimeNs)
        }
    }

    private fun newJankMonitor(captor: FrameCaptor? = null): UiJankMonitor =
        UiJankMonitor(
            mainHandlerProvider = { Handler(Looper.getMainLooper()) },
            postFrameCallback = { callback ->
                if (captor == null) {
                    // No choreographer in plain tests without frames; ignore.
                } else {
                    captor.callback = callback
                }
            },
        )

}
