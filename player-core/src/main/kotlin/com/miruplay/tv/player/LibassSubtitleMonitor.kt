package com.miruplay.tv.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import `is`.xyz.mpv.subtitle.NativeAssRenderer
import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Low-overhead, bounded monitor for the libass subtitle pipeline.
 *
 * Lives at the shared boundary inside [LibassSubtitleSession]; when the session has no
 * monitor (default) every method here is never reached and the cost is zero. All
 * timestamps use [SystemClock.elapsedRealtimeNanos].
 *
 * Metric families (each a fixed ring with percentile aggregation):
 *  - cue scheduling lateness: first time the media clock crosses a cue's start, the
 *    wall-clock target extrapolated from the previous (media, wall) mapping is compared
 *    with the submit arrival time.
 *  - clock lateness: Exo = submit wall time minus the video frame release deadline
 *    (releaseTimeNs); IJK = submit wall time minus the poller's expected tick.
 *  - selection→render: render start minus the submit that fed the drained frame.
 *  - commit duration: native render+post wall duration (T2 - T1).
 *  - commit→app vsync: one-shot Choreographer callback on the main thread (see
 *    [AppVsyncObserver]); this is the app-side frame callback, NOT SurfaceFlinger
 *    latch / HDMI present.
 *  - clock interval/jitter: submit-to-submit deltas.
 *
 * Independent counters that never imply each other: [coalescedFrameCount] (frame
 * requests dropped by the render coalescing window), [nativeNoOpRenderCount]
 * (RENDER_UNCHANGED), [timelineProvenSkippedCueCount] (a cue window crossed with no
 * UPDATED render inside it), [lateCueCount] (scheduling lateness over the max budget).
 *
 * Reset boundaries: [onBeginMedia] starts a new epoch (per-play window, everything is
 * reset); [onSeek]/[onStartTrack]/[onDeactivate] open a reset window during which
 * late/skip counting is suppressed and the time to the first UPDATED render is
 * measured as recovery latency.
 */
internal class LibassSubtitleMonitor(
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val vsync: AppVsyncObserver = AppVsyncObserver(),
) {
    private val lock = Any()
    private val cueTimeline = ArrayDeque<TrackedCue>()

    // --- per-epoch / reset state ---
    private var epoch = 0L
    private var resetWindowActive = false
    private var lastResetReason = ""
    private var recoveryResetNs = 0L
    private var recoveryLatencyNs = 0L

    // --- media → wall mapping (speed assumed 1.0; ponytail: no speed handling, add when monitoring !=1.0 playback) ---
    private var lastMappingMediaUs = Long.MIN_VALUE
    private var lastMappingWallNs = 0L
    private var lastSubmitWallNs = 0L
    private var lastAnchorWallNs = 0L
    private var pendingSubmitNs = 0L

    // --- counters ---
    private var lateCueCount = 0L
    private var timelineProvenSkippedCueCount = 0L
    private var coalescedFrameCount = 0L
    private var nativeNoOpRenderCount = 0L
    private var renderErrorCount = 0L
    private var updatedRenderCount = 0L
    private var slowCommitCount = 0L
    private var commitTimeoutCount = 0L
    private var mainThreadLongFrameCount = 0L
    private var appVsyncObservedCount = 0L
    private var closed = false

    // --- latency rings ---
    private val cueLateness = BoundedLatencyRing()
    private val selectionToRender = BoundedLatencyRing()
    private val commitDuration = BoundedLatencyRing()
    private val commitToVsync = BoundedLatencyRing()
    private val internalEndToEnd = BoundedLatencyRing()
    private val clockInterval = BoundedLatencyRing()
    private val clockJitter = BoundedLatencyRing()
    private val clockLateness = BoundedLatencyRing()

    init {
        vsync.setListener(::onVsyncFrame)
    }

    // ── lifecycle / reset boundaries ────────────────────────────────

    fun onBeginMedia() = synchronized(lock) {
        epoch += 1L
        resetWindowActive = true
        lastResetReason = RESET_REASON_BEGIN
        recoveryResetNs = clock()
        recoveryLatencyNs = 0L
        cueTimeline.clear()
        lastMappingMediaUs = Long.MIN_VALUE
        lastMappingWallNs = 0L
        lastSubmitWallNs = 0L
        lastAnchorWallNs = 0L
        pendingSubmitNs = 0L
        lateCueCount = 0L
        timelineProvenSkippedCueCount = 0L
        coalescedFrameCount = 0L
        nativeNoOpRenderCount = 0L
        renderErrorCount = 0L
        updatedRenderCount = 0L
        slowCommitCount = 0L
        commitTimeoutCount = 0L
        mainThreadLongFrameCount = 0L
        appVsyncObservedCount = 0L
        cueLateness.reset()
        selectionToRender.reset()
        commitDuration.reset()
        commitToVsync.reset()
        internalEndToEnd.reset()
        clockInterval.reset()
        clockJitter.reset()
        clockLateness.reset()
    }

    fun onSeek(clearTimeline: Boolean = true) = synchronized(lock) {
        if (closed) return@synchronized
        openResetWindow(RESET_REASON_SEEK, clearTimeline)
    }

    fun onStartTrack() = synchronized(lock) {
        if (closed) return@synchronized
        openResetWindow(RESET_REASON_TRACK, clearTimeline = true)
    }

    fun onDeactivate() = synchronized(lock) {
        if (closed) return@synchronized
        openResetWindow(RESET_REASON_DEACTIVATE, clearTimeline = true)
    }

    fun onResume() = synchronized(lock) {
        if (closed) return@synchronized
        openResetWindow(RESET_REASON_RESUME, clearTimeline = false)
    }

    private fun openResetWindow(reason: String, clearTimeline: Boolean) {
        epoch += 1L
        resetWindowActive = true
        lastResetReason = reason
        recoveryResetNs = clock()
        recoveryLatencyNs = 0L
        if (clearTimeline) cueTimeline.clear()
        lastMappingMediaUs = Long.MIN_VALUE
        lastMappingWallNs = 0L
        lastSubmitWallNs = 0L
        lastAnchorWallNs = 0L
        pendingSubmitNs = 0L
    }

    fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        vsync.close()
        cueTimeline.clear()
    }

    // ── cue timeline ────────────────────────────────────────────────

    fun onEventPayload(dialogueLine: String) = synchronized(lock) {
        if (closed) return@synchronized
        val bounds = dialogueCueBoundsUs(dialogueLine) ?: return@synchronized
        addCue(bounds.first, bounds.second)
    }

    fun onDocumentPayload(document: ByteArray) = synchronized(lock) {
        if (closed) return@synchronized
        assDocumentCueBoundsUs(document).forEach { (startUs, endUs) -> addCue(startUs, endUs) }
    }

    private fun addCue(startUs: Long, endUs: Long) {
        if (cueTimeline.size >= MAX_CUE_TIMELINE) cueTimeline.removeFirst()
        cueTimeline.addLast(TrackedCue(startUs, endUs))
    }

    // ── clock entry (submit) ────────────────────────────────────────

    fun onFrameSubmitted(
        mediaUs: Long,
        anchorWallNs: Long?,
        mappingWallNs: Long? = anchorWallNs,
        coalesced: Boolean,
    ) = synchronized(lock) {
        if (closed) return@synchronized
        val t0 = clock()
        if (coalesced) coalescedFrameCount += 1L
        if (lastSubmitWallNs != 0L) {
            val intervalNs = t0 - lastSubmitWallNs
            clockInterval.add(intervalNs)
            if (anchorWallNs != null && lastAnchorWallNs != 0L) {
                val expectedIntervalNs = anchorWallNs - lastAnchorWallNs
                clockJitter.add(abs(intervalNs - expectedIntervalNs))
            }
        }
        if (anchorWallNs != null) clockLateness.add(t0 - anchorWallNs)
        if (lastMappingMediaUs != Long.MIN_VALUE) {
            // Extrapolate each not-yet-crossed cue's wall target from the previous mapping.
            val mappingMediaUs = lastMappingMediaUs
            val mappingWallNs = lastMappingWallNs
            val iterator = cueTimeline.iterator()
            while (iterator.hasNext()) {
                val cue = iterator.next()
                if (cue.settled) {
                    // Keep the bounded timeline compact; each cue settles exactly once.
                    iterator.remove()
                    continue
                }
                if (!cue.crossed && mediaUs >= cue.startUs) {
                    // First crossing of the cue window.
                    cue.crossed = true
                    if (!resetWindowActive) {
                        val targetNs = mappingWallNs + (cue.startUs - mappingMediaUs) * NANOS_PER_MICRO
                        cue.targetWallNs = targetNs
                        val latenessNs = t0 - targetNs
                        cueLateness.add(latenessNs)
                        if (latenessNs > LATE_CUE_THRESHOLD_NS) lateCueCount += 1L
                    }
                }
                if (mediaUs >= cue.endUs) {
                    // Window fully passed (either in this submit or an earlier one):
                    // settle exactly once and evaluate skip only when no UPDATED render
                    // ever fell inside the window.
                    cue.settled = true
                    iterator.remove()
                    if (!resetWindowActive && !cue.rendered) {
                        timelineProvenSkippedCueCount += 1L
                    }
                }
            }
        }
        lastMappingMediaUs = mediaUs
        lastMappingWallNs = mappingWallNs ?: t0
        lastSubmitWallNs = t0
        lastAnchorWallNs = anchorWallNs ?: 0L
        pendingSubmitNs = t0
    }

    // ── render / commit ─────────────────────────────────────────────

    fun onRenderStarted() = synchronized(lock) {
        if (closed) return@synchronized
        val t1 = clock()
        if (pendingSubmitNs != 0L) {
            selectionToRender.add(t1 - pendingSubmitNs)
            pendingSubmitNs = 0L
        }
        renderStartedNs = t1
    }

    fun onRenderFinished(mediaUs: Long, result: Int) = synchronized(lock) {
        if (closed) return@synchronized
        val t2 = clock()
        val durationNs = t2 - renderStartedNs
        commitDuration.add(durationNs)
        if (durationNs > COMMIT_TIMEOUT_NS) {
            commitTimeoutCount += 1L
        } else if (durationNs > LONG_FRAME_NS) {
            slowCommitCount += 1L
        }
        when (result) {
            NativeAssRenderer.RENDER_UPDATED -> updatedRenderCount += 1L
            NativeAssRenderer.RENDER_UNCHANGED -> nativeNoOpRenderCount += 1L
            else -> renderErrorCount += 1L
        }
        if (result == NativeAssRenderer.RENDER_UPDATED) {
            cueTimeline.forEach { cue ->
                if (mediaUs >= cue.startUs && mediaUs < cue.endUs) {
                    cue.rendered = true
                    val targetWallNs = cue.targetWallNs
                    if (!cue.endToEndRecorded && targetWallNs != null) {
                        internalEndToEnd.add(t2 - targetWallNs)
                        cue.endToEndRecorded = true
                    }
                }
            }
        }
        val completesRecovery = result == NativeAssRenderer.RENDER_UPDATED ||
            (lastResetReason == RESET_REASON_RESUME && result == NativeAssRenderer.RENDER_UNCHANGED)
        if (resetWindowActive && completesRecovery) {
            recoveryLatencyNs = t2 - recoveryResetNs
            resetWindowActive = false
        }
        vsync.observe(t2, epoch)
    }

    private fun onVsyncFrame(commitEndNs: Long, frameTimeNs: Long, sampleEpoch: Long) = synchronized(lock) {
        if (closed || sampleEpoch != epoch) return@synchronized
        val delayNs = frameTimeNs - commitEndNs
        commitToVsync.add(delayNs)
        if (delayNs > LONG_FRAME_NS) mainThreadLongFrameCount += 1L
        appVsyncObservedCount += 1L
    }

    // ── snapshot ────────────────────────────────────────────────────

    fun snapshot(): LibassSubtitleMonitorSnapshot = synchronized(lock) {
        LibassSubtitleMonitorSnapshot(
            enabled = !closed,
            epoch = epoch,
            resetWindowActive = resetWindowActive,
            lastResetReason = lastResetReason,
            cueTimelineCount = cueTimeline.size,
            lateCueCount = lateCueCount,
            timelineProvenSkippedCueCount = timelineProvenSkippedCueCount,
            coalescedFrameCount = coalescedFrameCount,
            nativeNoOpRenderCount = nativeNoOpRenderCount,
            renderErrorCount = renderErrorCount,
            updatedRenderCount = updatedRenderCount,
            slowCommitCount = slowCommitCount,
            commitTimeoutCount = commitTimeoutCount,
            mainThreadLongFrameCount = mainThreadLongFrameCount,
            recoveryLatencyNs = recoveryLatencyNs,
            appVsyncObservedCount = appVsyncObservedCount,
            cueLateness = cueLateness.stats(),
            selectionToRender = selectionToRender.stats(),
            commitDuration = commitDuration.stats(),
            commitToVsync = commitToVsync.stats(),
            internalEndToEnd = internalEndToEnd.stats(),
            clockInterval = clockInterval.stats(),
            clockJitter = clockJitter.stats(),
            clockLateness = clockLateness.stats(),
        )
    }

    private var renderStartedNs = 0L

    private data class TrackedCue(
        val startUs: Long,
        val endUs: Long,
        var crossed: Boolean = false,
        var settled: Boolean = false,
        var rendered: Boolean = false,
        var targetWallNs: Long? = null,
        var endToEndRecorded: Boolean = false,
    )

    internal companion object {
        const val RESET_REASON_BEGIN = "begin"
        const val RESET_REASON_SEEK = "seek"
        const val RESET_REASON_TRACK = "track"
        const val RESET_REASON_DEACTIVATE = "deactivate"
        const val RESET_REASON_RESUME = "resume"

        const val MAX_CUE_TIMELINE = 512
        const val RING_CAPACITY = 256
        const val NANOS_PER_MICRO = 1_000L
        // Pre-registered budget: scheduling max <= 250ms.
        const val LATE_CUE_THRESHOLD_NS = 250_000_000L
        // Long frame: more than two 60fps frames.
        const val LONG_FRAME_NS = 33_333_333L
        // Hard commit timeout ceiling.
        const val COMMIT_TIMEOUT_NS = 250_000_000L
    }
}

/**
 * Bounded latency ring with percentile aggregation. Snapshots copy and sort the ring
 * (capacity is fixed at 256) so percentiles are exact for a bounded window.
 */
data class LatencyStats(
    val count: Int = 0,
    val p50Ns: Long = 0L,
    val p95Ns: Long = 0L,
    val p99Ns: Long = 0L,
    val maxNs: Long = 0L,
)

internal class BoundedLatencyRing(private val capacity: Int = LibassSubtitleMonitor.RING_CAPACITY) {
    private val values = LongArray(capacity)
    private var head = 0
    private var size = 0

    fun add(ns: Long) {
        values[head] = ns
        head = (head + 1) % capacity
        if (size < capacity) size += 1
    }

    fun stats(): LatencyStats {
        if (size == 0) return LatencyStats()
        val sorted = LongArray(size)
        for (i in 0 until size) sorted[i] = values[i]
        sorted.sort()
        return LatencyStats(
            count = size,
            p50Ns = sorted[(size - 1) * 50 / 100],
            p95Ns = sorted[(size - 1) * 95 / 100],
            p99Ns = sorted[(size - 1) * 99 / 100],
            maxNs = sorted[size - 1],
        )
    }

    fun reset() {
        head = 0
        size = 0
    }
}

/**
 * Serialized snapshot of the libass subtitle monitor for WebControl export.
 */
data class LibassSubtitleMonitorSnapshot(
    val enabled: Boolean = false,
    val epoch: Long = 0L,
    val activeBackend: String = "",
    val resetWindowActive: Boolean = false,
    val lastResetReason: String = "",
    val cueTimelineCount: Int = 0,
    val lateCueCount: Long = 0L,
    val timelineProvenSkippedCueCount: Long = 0L,
    val coalescedFrameCount: Long = 0L,
    val nativeNoOpRenderCount: Long = 0L,
    val renderErrorCount: Long = 0L,
    val updatedRenderCount: Long = 0L,
    val slowCommitCount: Long = 0L,
    val commitTimeoutCount: Long = 0L,
    val mainThreadLongFrameCount: Long = 0L,
    val recoveryLatencyNs: Long = 0L,
    val appVsyncObservedCount: Long = 0L,
    val cueLateness: LatencyStats = LatencyStats(),
    val selectionToRender: LatencyStats = LatencyStats(),
    val commitDuration: LatencyStats = LatencyStats(),
    val commitToVsync: LatencyStats = LatencyStats(),
    val internalEndToEnd: LatencyStats = LatencyStats(),
    val clockInterval: LatencyStats = LatencyStats(),
    val clockJitter: LatencyStats = LatencyStats(),
    val clockLateness: LatencyStats = LatencyStats(),
)

/**
 * One-shot app-vsync observer. Registration is injected onto the main thread via
 * [Handler] and [Choreographer]; it is never touched from the libass render thread.
 * At most one pending callback exists at a time; [close] drops it. The reported delta
 * is commit-end (native render+post return) to the next app Choreographer frame
 * callback — this is NOT SurfaceFlinger latch or HDMI present time.
 */
internal class AppVsyncObserver(
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val postFrameCallback: (Choreographer.FrameCallback) -> Unit = { callback ->
        Choreographer.getInstance().postFrameCallback(callback)
    },
) {
    private val lock = Any()
    private var listener: ((commitEndNs: Long, frameTimeNs: Long, epoch: Long) -> Unit)? = null
    private var pending = false
    private var closed = false

    fun setListener(listener: ((Long, Long, Long) -> Unit)?) = synchronized(lock) {
        this.listener = listener
        if (listener == null) pending = false
    }

    fun observe(commitEndNs: Long, epoch: Long) = synchronized(lock) {
        if (closed || pending || listener == null) return@synchronized
        pending = true
        val commit = commitEndNs
        mainHandler.post {
            postFrameCallback { frameTimeNs ->
                val callback = synchronized(lock) {
                    pending = false
                    listener
                }
                callback?.invoke(commit, frameTimeNs, epoch)
            }
        }
    }

    fun close() = synchronized(lock) {
        closed = true
        pending = false
        listener = null
        mainHandler.removeCallbacksAndMessages(null)
    }
}

/**
 * Session-construction factory: the debug switch only takes effect for sessions created
 * after it flips (next playback), never by dynamically mounting into a running session.
 */
internal fun libassSubtitleMonitorProvider(
    enabled: () -> Boolean,
): () -> LibassSubtitleMonitor? = {
    if (enabled()) LibassSubtitleMonitor() else null
}
