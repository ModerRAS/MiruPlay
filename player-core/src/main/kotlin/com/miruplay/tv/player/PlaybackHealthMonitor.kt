package com.miruplay.tv.player

import android.media.MediaCodecList
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer

/**
 * Always-cheap video pipeline counters behind the playback debug switch.
 *
 * Fed from the existing Exo AnalyticsListener + per-frame metadata callbacks; every method
 * is a few locked field writes. Expensive work (MediaCodecList lookup for exact
 * hardware-acceleration) happens lazily on the first snapshot, never on the playback path.
 * Reset per playback ([reset] at play); null snapshot before the first start.
 */
internal class VideoHealthMonitor(
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    private val lock = Any()
    private var started = false
    private var everStarted = false
    private var startNs = 0L
    private var decoderName: String? = null
    private var decoderInitCount = 0
    private var hardwareAccelerated: Boolean? = null
    private var sampleMimeType = ""
    private var codecs = ""
    private var width = 0
    private var height = 0
    private var frameRate = 0f
    private var droppedFramesTotal = 0L
    private var droppedEvents = 0
    private var maxDroppedPerEvent = 0
    private var renderedFrames = 0L
    private var audioUnderruns = 0
    private var firstFrameNs = 0L

    fun start() = synchronized(lock) {
        started = true
        everStarted = true
        startNs = clock()
        decoderName = null
        decoderInitCount = 0
        hardwareAccelerated = null
        sampleMimeType = ""
        codecs = ""
        width = 0
        height = 0
        frameRate = 0f
        droppedFramesTotal = 0L
        droppedEvents = 0
        maxDroppedPerEvent = 0
        renderedFrames = 0L
        audioUnderruns = 0
        firstFrameNs = 0L
    }

    fun stop() = synchronized(lock) {
        started = false
    }

    fun onDecoderInitialized(name: String) = synchronized(lock) {
        if (!started) return@synchronized
        decoderName = name
        decoderInitCount += 1
        hardwareAccelerated = null
    }

    fun onInputFormat(mimeType: String?, codecs: String?, width: Int, height: Int, frameRate: Float) =
        synchronized(lock) {
            if (!started) return@synchronized
            sampleMimeType = mimeType.orEmpty()
            this.codecs = codecs.orEmpty()
            this.width = width
            this.height = height
            this.frameRate = frameRate
        }

    fun onDroppedFrames(count: Int) = synchronized(lock) {
        if (!started || count <= 0) return@synchronized
        droppedFramesTotal += count
        droppedEvents += 1
        if (count > maxDroppedPerEvent) maxDroppedPerEvent = count
    }

    fun onFrameRendered() = synchronized(lock) {
        if (!started) return@synchronized
        renderedFrames += 1L
        if (firstFrameNs == 0L) firstFrameNs = clock()
    }

    fun onAudioUnderrun() = synchronized(lock) {
        if (!started) return@synchronized
        audioUnderruns += 1
    }

    fun snapshot(): VideoHealthSnapshot? = synchronized(lock) {
        if (!everStarted) return@synchronized null
        val name = decoderName
        // MediaCodecList enumeration costs ~100ms: never on the main thread (the HUD polls
        // from Main). A background snapshot (WebControl) resolves and caches it instead.
        val onMain = Looper.myLooper() === Looper.getMainLooper()
        if (hardwareAccelerated == null && name != null && !onMain) {
            hardwareAccelerated = runCatching { lookupHardwareAccelerated(name) }.getOrNull()
        }
        val elapsedNs = if (startNs == 0L) 0L else clock() - startNs
        VideoHealthSnapshot(
            decoderName = name.orEmpty(),
            hardwareAccelerated = hardwareAccelerated,
            decoderInitCount = decoderInitCount,
            sampleMimeType = sampleMimeType,
            codecs = codecs,
            width = width,
            height = height,
            frameRate = frameRate,
            droppedFramesTotal = droppedFramesTotal,
            droppedEvents = droppedEvents,
            maxDroppedPerEvent = maxDroppedPerEvent,
            renderedFrames = renderedFrames,
            audioUnderruns = audioUnderruns,
            elapsedMs = elapsedNs / 1_000_000L,
            timeToFirstFrameMs = if (firstFrameNs == 0L || startNs == 0L) -1L
            else (firstFrameNs - startNs) / 1_000_000L,
        )
    }

    private fun lookupHardwareAccelerated(name: String): Boolean? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .firstOrNull { it.name == name }
            ?.isHardwareAccelerated
    }
}

/**
 * Serializable video health for WebControl export / on-TV HUD.
 */
data class VideoHealthSnapshot(
    val decoderName: String = "",
    val hardwareAccelerated: Boolean? = null,
    val decoderInitCount: Int = 0,
    val sampleMimeType: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val droppedFramesTotal: Long = 0L,
    val droppedEvents: Int = 0,
    val maxDroppedPerEvent: Int = 0,
    val renderedFrames: Long = 0L,
    val audioUnderruns: Int = 0,
    val elapsedMs: Long = 0L,
    val timeToFirstFrameMs: Long = -1L,
)

/**
 * Main-thread jank watcher behind the playback debug switch.
 *
 * One Choreographer callback re-posted per vsync; per-frame cost is a timestamp subtract.
 * Jank = frame interval over [JANK_THRESHOLD_NS] (~2 missed 60Hz vsyncs). Start/stop are
 * main-thread posted like [AppVsyncObserver]; snapshots are lock-guarded and cheap.
 */
internal class UiJankMonitor(
    // Provider (not a direct Handler): ExoPlaybackController owns this as a plain field and
    // plain-JVM controller tests have no main looper. Null degrades start/stop to no-ops.
    private val mainHandlerProvider: () -> Handler? = {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    },
    private val postFrameCallback: (Choreographer.FrameCallback) -> Unit = { callback: Choreographer.FrameCallback ->
        Choreographer.getInstance().postFrameCallback(callback)
    },
    private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    private val lock = Any()
    private var running = false
    private var startedOnce = false
    private var startNs = 0L
    private var lastFrameNs = 0L
    private var totalFrames = 0L
    private var jankFrames = 0L
    private var maxDeltaNs = 0L
    private var maxDeltaAtElapsedMs = -1L

    private val frameCallback = Choreographer.FrameCallback(this::onFrame)

    private fun onFrame(frameTimeNs: Long) {
        var logJankMs = -1L
        var logElapsedMs = -1L
        val reschedule = synchronized(lock) {
            if (!running) {
                false
            } else {
                if (lastFrameNs != 0L) {
                    val deltaNs = frameTimeNs - lastFrameNs
                    totalFrames += 1L
                    if (deltaNs > maxDeltaNs) {
                        maxDeltaNs = deltaNs
                        maxDeltaAtElapsedMs = (clock() - startNs) / 1_000_000L
                    }
                    if (deltaNs > JANK_THRESHOLD_NS) {
                        jankFrames += 1L
                        if (deltaNs > LOG_JANK_THRESHOLD_NS) {
                            logJankMs = deltaNs / 1_000_000L
                            logElapsedMs = (clock() - startNs) / 1_000_000L
                        }
                    }
                }
                lastFrameNs = frameTimeNs
                true
            }
        }
        if (logJankMs >= 0) {
            android.util.Log.w(
                "PlaybackHealth",
                "UI jank ${logJankMs}ms at elapsed ${logElapsedMs}ms",
            )
        }
        if (reschedule) postFrameCallback(frameCallback)
    }

    fun start() {
        val handler = mainHandlerProvider()
        if (handler == null) {
            android.util.Log.w("PlaybackHealth", "UiJankMonitor.start without main looper: disabled")
            return
        }
        handler.post {
            synchronized(lock) {
                if (running) return@synchronized
                running = true
                startedOnce = true
                startNs = clock()
                lastFrameNs = 0L
            }
            postFrameCallback(frameCallback)
        }
    }

    fun stop() {
        mainHandlerProvider()?.post {
            synchronized(lock) {
                running = false
                lastFrameNs = 0L
            }
        }
    }

    fun snapshot(): UiJankSnapshot? = synchronized(lock) {
        if (!startedOnce) return@synchronized null
        UiJankSnapshot(
            running = running,
            totalFrames = totalFrames,
            jankFrames = jankFrames,
            maxDeltaMs = maxDeltaNs / 1_000_000L,
            maxDeltaAtElapsedMs = maxDeltaAtElapsedMs,
        )
    }

    private companion object {
        const val JANK_THRESHOLD_NS = 34_000_000L
        const val LOG_JANK_THRESHOLD_NS = 100_000_000L
    }
}

/**
 * Serializable UI jank for WebControl export / on-TV HUD.
 */
data class UiJankSnapshot(
    val running: Boolean = false,
    val totalFrames: Long = 0L,
    val jankFrames: Long = 0L,
    val maxDeltaMs: Long = 0L,
    val maxDeltaAtElapsedMs: Long = -1L,
)

/**
 * Combined playback health snapshot: video pipeline + UI thread + (separately) libass.
 */
data class PlaybackHealthSnapshot(
    val backend: String = "",
    val video: VideoHealthSnapshot = VideoHealthSnapshot(),
    val ui: UiJankSnapshot = UiJankSnapshot(),
) {
    /** Single-line form for the periodic log dump (logcat + persisted MiruLog). */
    fun compactLogLine(): String = buildString {
        val hw = if (video.hardwareAccelerated == true) "HW" else "SW?"
        append("backend=$backend elapsed=${video.elapsedMs}ms")
        append(" decoder=${video.decoderName.ifBlank { "?" }}[$hw] ${video.width}x${video.height}")
        append(" drop=${video.droppedFramesTotal}/${video.renderedFrames}")
        append(" au=${video.audioUnderruns} firstFrame=${video.timeToFirstFrameMs}ms")
        append(" uiJank=${ui.jankFrames}/${ui.totalFrames} uiMax=${ui.maxDeltaMs}ms@${ui.maxDeltaAtElapsedMs}ms")
    }

    fun logAttributes(): Map<String, String> = mapOf(
        "backend" to backend,
        "decoder" to video.decoderName,
        "hardware_accelerated" to video.hardwareAccelerated.toString(),
        "resolution" to "${video.width}x${video.height}",
        "frame_rate" to video.frameRate.toString(),
        "dropped_frames" to video.droppedFramesTotal.toString(),
        "rendered_frames" to video.renderedFrames.toString(),
        "audio_underruns" to video.audioUnderruns.toString(),
        "ui_jank_frames" to ui.jankFrames.toString(),
        "ui_total_frames" to ui.totalFrames.toString(),
        "ui_max_delta_ms" to ui.maxDeltaMs.toString(),
    )
}
