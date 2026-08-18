package com.miruplay.tv.player

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.media3.common.Format
import `is`.xyz.mpv.subtitle.NativeAssFont
import `is`.xyz.mpv.subtitle.NativeAssRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibassSubtitleMonitorTest {

    // ── cue bounds parsing ──────────────────────────────────────────

    @Test
    fun `event payload bounds parse absolute cue window`() {
        assertEquals(
            1_000_000L to 3_500_000L,
            dialogueCueBoundsUs("Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,Hello world"),
        )
    }

    @Test
    fun `event payload with trailing newline and commas in text parses`() {
        val bounds = dialogueCueBoundsUs(
            "Dialogue: 4,0:00:07.07,0:00:09.57,Sign,Actor,0000,0000,0000,,{\\pos(300,200)}LV999, 村民\n",
        )
        assertEquals(7_070_000L to 9_570_000L, bounds)
    }

    @Test
    fun `malformed event payload returns null`() {
        assertNull(dialogueCueBoundsUs("Not a dialogue line"))
        assertNull(dialogueCueBoundsUs("Dialogue: 0,broken"))
    }

    @Test
    fun `document events block extracts bounded cue list`() {
        val document = buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            appendLine("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First")
            appendLine("Dialogue: 0,0:00:03.00,0:00:04.50,Default,,0,0,0,,Second")
        }.toByteArray()

        val bounds = assDocumentCueBoundsUs(document)

        assertEquals(
            listOf(1_000_000L to 2_000_000L, 3_000_000L to 4_500_000L),
            bounds,
        )
    }

    @Test
    fun `document with format start-end first resolves columns`() {
        val document = buildString {
            appendLine("[Events]")
            appendLine("Format: Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            appendLine("Dialogue: 0:00:02.00,0:00:03.00,Default,,0,0,0,,No layer column")
        }.toByteArray()

        assertEquals(listOf(2_000_000L to 3_000_000L), assDocumentCueBoundsUs(document))
    }

    @Test
    fun `document without events section yields empty timeline`() {
        assertTrue(assDocumentCueBoundsUs("[Script Info]\nScriptType: v4.00+".toByteArray()).isEmpty())
    }

    // ── submit / clock metrics ──────────────────────────────────────

    @Test
    fun `exo clock lateness is submit wall minus release deadline`() {
        var now = 100_000_000L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()

        now = 110_000_000L
        monitor.onFrameSubmitted(mediaUs = 1_000_000L, anchorWallNs = 100_000_000L, coalesced = false)

        assertEquals(10_000_000L, monitor.snapshot().clockLateness.p95Ns)
    }

    @Test
    fun `ijk clock lateness is submit wall minus expected tick`() {
        var now = 200_000_000L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()

        // First poll: no previous tick, no anchor.
        monitor.onFrameSubmitted(mediaUs = 1_000_000L, anchorWallNs = null, coalesced = false)
        now = 305_000_000L
        // Second poll expected at 300ms (200ms + 100ms interval), actually ran at 305ms -> 5ms late.
        monitor.onFrameSubmitted(mediaUs = 2_000_000L, anchorWallNs = 300_000_000L, coalesced = false)

        assertEquals(5_000_000L, monitor.snapshot().clockLateness.p95Ns)
    }

    @Test
    fun `coalesced submit is counted as frame coalescing not cue skip`() {
        val monitor = monitor()
        monitor.onBeginMedia()
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First")

        monitor.onFrameSubmitted(mediaUs = 500_000L, anchorWallNs = null, coalesced = false)
        monitor.onFrameSubmitted(mediaUs = 1_200_000L, anchorWallNs = null, coalesced = true)

        val snapshot = monitor.snapshot()
        assertEquals(1L, snapshot.coalescedFrameCount)
        assertEquals(0L, snapshot.timelineProvenSkippedCueCount)
    }

    @Test
    fun `cue crossed before its window renders counts as skip`() {
        val monitor = monitor()
        monitor.onBeginMedia()
        // Close the begin reset window with a first UPDATED render.
        monitor.onFrameSubmitted(mediaUs = 100_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 100_000L, result = NativeAssRenderer.RENDER_UPDATED)
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First")

        monitor.onFrameSubmitted(mediaUs = 500_000L, anchorWallNs = null, coalesced = false)
        // Jump straight past the whole window without any render inside it.
        monitor.onFrameSubmitted(mediaUs = 2_500_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 2_500_000L, result = NativeAssRenderer.RENDER_UPDATED)

        assertEquals(1L, monitor.snapshot().timelineProvenSkippedCueCount)
    }

    @Test
    fun `cue rendered inside its window is not counted as skip`() {
        val monitor = monitor()
        monitor.onBeginMedia()
        monitor.onFrameSubmitted(mediaUs = 100_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 100_000L, result = NativeAssRenderer.RENDER_UPDATED)
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First")

        monitor.onFrameSubmitted(mediaUs = 500_000L, anchorWallNs = null, coalesced = false)
        monitor.onFrameSubmitted(mediaUs = 1_200_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 1_200_000L, result = NativeAssRenderer.RENDER_UPDATED)
        monitor.onFrameSubmitted(mediaUs = 2_500_000L, anchorWallNs = null, coalesced = false)

        assertEquals(0L, monitor.snapshot().timelineProvenSkippedCueCount)
    }

    @Test
    fun `cue crossed first and end passed later without updated render counts as skip`() {
        val monitor = monitor()
        monitor.onBeginMedia()
        monitor.onFrameSubmitted(mediaUs = 100_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 100_000L, result = NativeAssRenderer.RENDER_UPDATED)
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First")

        // Crossing submit lands inside the window but never renders (coalesced away).
        monitor.onFrameSubmitted(mediaUs = 1_200_000L, anchorWallNs = null, coalesced = false)
        // A later submit passes the window end; settlement must still count the skip.
        monitor.onFrameSubmitted(mediaUs = 2_500_000L, anchorWallNs = null, coalesced = false)

        assertEquals(1L, monitor.snapshot().timelineProvenSkippedCueCount)
    }

    @Test
    fun `cue crossed first and end passed later with updated render is not skip`() {
        val monitor = monitor()
        monitor.onBeginMedia()
        monitor.onFrameSubmitted(mediaUs = 100_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 100_000L, result = NativeAssRenderer.RENDER_UPDATED)
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First")

        monitor.onFrameSubmitted(mediaUs = 1_200_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 1_200_000L, result = NativeAssRenderer.RENDER_UPDATED)
        monitor.onFrameSubmitted(mediaUs = 2_500_000L, anchorWallNs = null, coalesced = false)

        assertEquals(0L, monitor.snapshot().timelineProvenSkippedCueCount)
    }

    @Test
    fun `unchanged native render counts as no-op not duplicate cue`() {
        val monitor = monitor()
        monitor.onBeginMedia()

        monitor.onFrameSubmitted(mediaUs = 1_000_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 1_000_000L, result = NativeAssRenderer.RENDER_UNCHANGED)

        val snapshot = monitor.snapshot()
        assertEquals(1L, snapshot.nativeNoOpRenderCount)
        assertEquals(0L, snapshot.timelineProvenSkippedCueCount)
        assertEquals(0L, snapshot.updatedRenderCount)
    }

    @Test
    fun `late cue crossing is counted against max budget`() {
        var now = 1_000_000_000L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()
        monitor.onEventPayload("Dialogue: 0,0:00:10.00,0:00:11.00,Default,,0,0,0,,Late cue")
        // Close the reset window.
        monitor.onFrameSubmitted(mediaUs = 9_000_000L, anchorWallNs = now, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 9_000_000L, result = NativeAssRenderer.RENDER_UPDATED)

        // Cue starts at 10s media; mapping at 9s wall 1.0s, so target wall = 1.0s + 1s = 2.0s.
        now = 2_300_000_000L
        monitor.onFrameSubmitted(mediaUs = 10_500_000L, anchorWallNs = now, coalesced = false)

        val snapshot = monitor.snapshot()
        assertEquals(1L, snapshot.lateCueCount)
        assertEquals(300_000_000L, snapshot.cueLateness.maxNs)
    }

    @Test
    fun `on-time cue crossing stays below threshold`() {
        var now = 1_000_000_000L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()
        monitor.onEventPayload("Dialogue: 0,0:00:10.00,0:00:11.00,Default,,0,0,0,,On time")
        monitor.onFrameSubmitted(mediaUs = 9_000_000L, anchorWallNs = now, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 9_000_000L, result = NativeAssRenderer.RENDER_UPDATED)

        now = 2_050_000_000L
        monitor.onFrameSubmitted(mediaUs = 10_100_000L, anchorWallNs = now, coalesced = false)

        val snapshot = monitor.snapshot()
        assertEquals(0L, snapshot.lateCueCount)
        assertEquals(50_000_000L, snapshot.cueLateness.maxNs)
    }

    @Test
    fun `ijk tick deadline stays separate from cue media wall mapping and exports jitter plus end to end`() {
        var now = 100_000_000L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()
        monitor.onFrameSubmitted(mediaUs = 0L, anchorWallNs = null, mappingWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 0L, result = NativeAssRenderer.RENDER_UPDATED)
        monitor.onEventPayload("Dialogue: 0,0:00:02.00,0:00:03.00,Default,,0,0,0,,Mapped cue")

        now = 1_200_000_000L
        monitor.onFrameSubmitted(
            mediaUs = 1_000_000L,
            anchorWallNs = 1_100_000_000L,
            mappingWallNs = null,
            coalesced = false,
        )
        now = 2_250_000_000L
        monitor.onFrameSubmitted(
            mediaUs = 2_050_000L,
            anchorWallNs = 2_100_000_000L,
            mappingWallNs = null,
            coalesced = false,
        )
        monitor.onRenderStarted()
        now = 2_270_000_000L
        monitor.onRenderFinished(mediaUs = 2_050_000L, result = NativeAssRenderer.RENDER_UPDATED)

        val snapshot = monitor.snapshot()
        assertEquals(50_000_000L, snapshot.cueLateness.maxNs)
        assertEquals(150_000_000L, snapshot.clockLateness.maxNs)
        assertEquals(50_000_000L, snapshot.clockJitter.maxNs)
        assertEquals(70_000_000L, snapshot.internalEndToEnd.maxNs)
    }

    @Test
    fun `resume opens a new recovery epoch without discarding cue timeline`() {
        var now = 10_000_000L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Resume cue")
        monitor.onFrameSubmitted(mediaUs = 1_200_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 1_200_000L, result = NativeAssRenderer.RENDER_UPDATED)
        val beforeResume = monitor.snapshot()

        monitor.onResume()
        now = 35_000_000L
        monitor.onFrameSubmitted(mediaUs = 1_200_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        monitor.onRenderFinished(mediaUs = 1_200_000L, result = NativeAssRenderer.RENDER_UNCHANGED)

        val snapshot = monitor.snapshot()
        assertEquals(beforeResume.epoch + 1L, snapshot.epoch)
        assertEquals(1, snapshot.cueTimelineCount)
        assertEquals(LibassSubtitleMonitor.RESET_REASON_RESUME, snapshot.lastResetReason)
        assertFalse(snapshot.resetWindowActive)
        assertEquals(25_000_000L, snapshot.recoveryLatencyNs)
    }

    @Test
    fun `exo video callback forwards release deadline into session monitor`() {
        var now = 100_000_000L
        val fixture = sessionFixture(clock = { now })
        now = 110_000_000L

        fixture.session.onVideoFrameAboutToBeRendered(
            1_000_000L,
            100_000_000L,
            Format.Builder().setWidth(1920).setHeight(1080).build(),
            null,
        )
        fixture.dispatcher.runNext()

        assertEquals(10_000_000L, fixture.session.currentMonitorSnapshot()!!.clockLateness.maxNs)
    }

    @Test
    fun `ijk poller call contract keeps expected tick separate from actual mapping`() {
        var now = 305_000_000L
        val fixture = sessionFixture(clock = { now })

        submitIjkSubtitleClockFrame(
            session = fixture.session,
            positionMs = 1_000L,
            storageWidth = 1920,
            storageHeight = 1080,
            expectedTickWallNs = 300_000_000L,
        )
        fixture.dispatcher.runNext()

        assertEquals(5_000_000L, fixture.session.currentMonitorSnapshot()!!.clockLateness.maxNs)
    }

    // ── render / commit / recovery ─────────────────────────────────

    @Test
    fun `slow render counts as long frame not timeout`() {
        var now = 0L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()

        monitor.onFrameSubmitted(mediaUs = 1_000_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted() // t1 at now
        now = 40_000_000L
        monitor.onRenderFinished(mediaUs = 1_000_000L, result = NativeAssRenderer.RENDER_UPDATED)

        val snapshot = monitor.snapshot()
        assertEquals(1L, snapshot.slowCommitCount)
        assertEquals(0L, snapshot.commitTimeoutCount)
        assertEquals(40_000_000L, snapshot.commitDuration.maxNs)
    }

    @Test
    fun `extreme render counts as commit timeout`() {
        var now = 0L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()

        monitor.onFrameSubmitted(mediaUs = 1_000_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        now = 300_000_000L
        monitor.onRenderFinished(mediaUs = 1_000_000L, result = NativeAssRenderer.RENDER_UPDATED)

        assertEquals(1L, monitor.snapshot().commitTimeoutCount)
    }

    @Test
    fun `recovery latency measured to first updated render after seek reset`() {
        var now = 0L
        val monitor = monitor(clock = { now })
        monitor.onBeginMedia()
        monitor.onSeek() // open reset window
        now = 50_000_000L

        monitor.onFrameSubmitted(mediaUs = 500_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        now = 55_000_000L
        monitor.onRenderFinished(mediaUs = 500_000L, result = NativeAssRenderer.RENDER_UPDATED)

        val snapshot = monitor.snapshot()
        assertFalse(snapshot.resetWindowActive)
        assertEquals(55_000_000L, snapshot.recoveryLatencyNs)
    }

    @Test
    fun `seek reset window suppresses late and skip counting`() {
        val monitor = monitor()
        monitor.onBeginMedia()
        monitor.onSeek()
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Post seek")

        monitor.onFrameSubmitted(mediaUs = 500_000L, anchorWallNs = null, coalesced = false)
        monitor.onFrameSubmitted(mediaUs = 2_500_000L, anchorWallNs = null, coalesced = false)

        val snapshot = monitor.snapshot()
        assertEquals(0L, snapshot.timelineProvenSkippedCueCount)
        assertEquals(0L, snapshot.lateCueCount)
        assertTrue(snapshot.resetWindowActive)
    }

    @Test
    fun `begin media starts a new epoch and resets counters`() {
        val monitor = monitor()
        monitor.onBeginMedia()
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,First")
        monitor.onFrameSubmitted(mediaUs = 500_000L, anchorWallNs = null, coalesced = true)
        assertEquals(1L, monitor.snapshot().coalescedFrameCount)

        monitor.onBeginMedia()
        monitor.onEventPayload("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Second")
        monitor.onFrameSubmitted(mediaUs = 500_000L, anchorWallNs = null, coalesced = false)

        val snapshot = monitor.snapshot()
        assertEquals(2L, snapshot.epoch)
        assertEquals(0L, snapshot.coalescedFrameCount)
        assertEquals(1, snapshot.cueTimelineCount)
    }

    // ── bounded rings / percentiles ────────────────────────────────

    @Test
    fun `ring stays bounded and percentiles reflect injected values`() {
        val ring = BoundedLatencyRing(capacity = 8)
        repeat(20) { index -> ring.add((index + 1) * 1_000_000L) }

        val stats = ring.stats()
        assertEquals(8, stats.count)
        assertEquals(20_000_000L, stats.maxNs)
        assertTrue(stats.p50Ns > 0)
        assertTrue(stats.p95Ns >= stats.p50Ns)
        assertTrue(stats.p99Ns >= stats.p95Ns)
    }

    @Test
    fun `empty ring yields zero stats`() {
        val stats = BoundedLatencyRing().stats()
        assertEquals(0, stats.count)
        assertEquals(0L, stats.maxNs)
    }

    // ── app vsync observer (one-shot, single pending) ──────────────

    @Test
    fun `vsync observer registers at most one pending callback per commit`() {
        var registrations = 0
        var captured: Choreographer.FrameCallback? = null
        val observer = AppVsyncObserver(
            mainHandler = Handler(Looper.getMainLooper()),
            postFrameCallback = { callback ->
                registrations++
                captured = callback
            },
        )
        var observed = 0
        observer.setListener { _, _, _ -> observed++ }

        observer.observe(1_000L, epoch = 1L)
        observer.observe(2_000L, epoch = 1L) // still pending, must not double-register
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, registrations)
        assertNotNull(captured)

        captured!!.doFrame(10_000L)
        assertEquals(1, observed)
    }

    @Test
    fun `closed vsync observer drops further callbacks`() {
        var captured: Choreographer.FrameCallback? = null
        val observer = AppVsyncObserver(
            mainHandler = Handler(Looper.getMainLooper()),
            postFrameCallback = { callback -> captured = callback },
        )
        var observed = 0
        observer.setListener { _, _, _ -> observed++ }

        observer.observe(1_000L, epoch = 1L)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(captured)
        observer.close()
        captured?.doFrame(10_000L)

        assertEquals(0, observed)
    }

    @Test
    fun `epoch mismatch discards stale vsync frame`() {
        var now = 0L
        var captured: Choreographer.FrameCallback? = null
        val observer = AppVsyncObserver(
            mainHandler = Handler(Looper.getMainLooper()),
            postFrameCallback = { callback -> captured = callback },
        )
        val monitor = LibassSubtitleMonitor(clock = { now }, vsync = observer)
        monitor.onBeginMedia()
        monitor.onFrameSubmitted(mediaUs = 1_000_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        now = 10_000_000L
        monitor.onRenderFinished(mediaUs = 1_000_000L, result = NativeAssRenderer.RENDER_UPDATED)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(captured)

        // A new commit in epoch 2 must not retag the still-pending epoch 1 callback.
        monitor.onBeginMedia()
        monitor.onFrameSubmitted(mediaUs = 2_000_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        now = 15_000_000L
        monitor.onRenderFinished(mediaUs = 2_000_000L, result = NativeAssRenderer.RENDER_UPDATED)
        captured!!.doFrame(20_000_000L)

        assertEquals(0L, monitor.snapshot().appVsyncObservedCount)
    }

    @Test
    fun `delayed app vsync callback is counted separately from slow native commit`() {
        var now = 0L
        var captured: Choreographer.FrameCallback? = null
        val observer = AppVsyncObserver(
            mainHandler = Handler(Looper.getMainLooper()),
            postFrameCallback = { callback -> captured = callback },
        )
        val monitor = LibassSubtitleMonitor(clock = { now }, vsync = observer)
        monitor.onBeginMedia()
        monitor.onFrameSubmitted(mediaUs = 1_000_000L, anchorWallNs = null, coalesced = false)
        monitor.onRenderStarted()
        now = 5_000_000L
        monitor.onRenderFinished(mediaUs = 1_000_000L, result = NativeAssRenderer.RENDER_UPDATED)
        shadowOf(Looper.getMainLooper()).idle()
        captured!!.doFrame(45_000_000L)

        val snapshot = monitor.snapshot()
        assertEquals(0L, snapshot.slowCommitCount)
        assertEquals(1L, snapshot.mainThreadLongFrameCount)
        assertEquals(40_000_000L, snapshot.commitToVsync.maxNs)
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun sessionFixture(clock: () -> Long): SessionFixture {
        val monitor = LibassSubtitleMonitor(
            clock = clock,
            vsync = AppVsyncObserver(
                mainHandler = Handler(Looper.getMainLooper()),
                postFrameCallback = { _ -> },
            ),
        )
        val dispatcher = ContractDispatcher()
        val session = LibassSubtitleSession(
            rendererFactory = ContractRendererFactory,
            renderDispatcher = dispatcher,
            monitorProvider = { monitor },
        )
        val generation = session.beginMedia()
        session.startTrack(generation, CONTRACT_HEADER)
        session.bindTarget(ContractTarget)
        return SessionFixture(session, dispatcher)
    }

    private data class SessionFixture(
        val session: LibassSubtitleSession,
        val dispatcher: ContractDispatcher,
    )

    private object ContractRendererFactory : LibassRendererFactory {
        override fun create(document: ByteArray, fonts: List<NativeAssFont>): LibassRendererHandle =
            object : LibassRendererHandle {
                override fun addEvent(dialogueLine: String): Boolean = true
                override fun flushEvents(): Boolean = true
                override fun close() = Unit
            }
    }

    private object ContractTarget : LibassRenderTarget {
        override fun render(renderer: LibassRendererHandle, frame: LibassVideoFrame): Int =
            NativeAssRenderer.RENDER_UPDATED

        override fun clear(renderer: LibassRendererHandle?) = Unit
    }

    private class ContractDispatcher : LibassRenderDispatcher {
        private val pending = ArrayDeque<() -> Unit>()

        override fun dispatch(block: () -> Unit) {
            pending += block
        }

        override fun close() = Unit

        fun runNext() {
            pending.removeFirst().invoke()
        }
    }

    private companion object {
        val CONTRACT_HEADER = (
            "[Script Info]\nScriptType: v4.00+\n" +
                "[Events]\n" +
                "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
            ).toByteArray()
    }

    private fun monitor(
        clock: () -> Long = { 0L },
    ): LibassSubtitleMonitor = LibassSubtitleMonitor(
        clock = clock,
        vsync = AppVsyncObserver(
            mainHandler = Handler(Looper.getMainLooper()),
            postFrameCallback = { _ -> /* no-op in unit tests */ },
        ),
    )
}
