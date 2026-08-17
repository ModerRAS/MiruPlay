package `is`.xyz.mpv

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import com.miruplay.tv.model.MpvNativeDiagnostics
import com.miruplay.tv.model.MpvNativeLogMessage
import com.miruplay.tv.model.MpvNativePropertySample
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MiruMpvSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BaseMPVView(context, attrs), MPVLib.EventObserver, MPVLib.LogObserver {
    data class SessionOptions(
        val vo: String = "gpu-next",
        val hwdec: String = "mediacodec,mediacodec-copy",
        val profile: String = "fast",
        val targetPrim: String? = null,
        val targetTrc: String? = null,
        val targetPeak: Int? = null,
        val hdrReferenceWhite: Int? = null,
        val toneMapping: String? = null,
        val toneMappingParam: Float? = null,
        val hdrComputePeak: Boolean? = null,
        val hdrPeakPercentile: Float? = null,
        val hdrPeakDecayRate: Float? = null,
        val hdrSceneThresholdLow: Float? = null,
        val hdrSceneThresholdHigh: Float? = null,
        val hdrContrastRecovery: Float? = null,
        val saturation: Float? = null,
        val gamutMappingMode: String? = null,
        val deband: Boolean = false,
        val shaderPaths: List<String> = emptyList(),
        val extraOptions: Map<String, String> = emptyMap(),
    )

    data class StateSnapshot(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val paused: Boolean = false,
        val eofReached: Boolean = false,
    )

    data class TrackInfo(
        val id: Int,
        val language: String,
        val title: String,
        val codec: String,
        val external: Boolean,
        val selected: Boolean,
    )

    var onStateChanged: ((StateSnapshot) -> Unit)? = null
    var onTracksChanged: ((MiruMpvSurfaceView) -> Unit)? = null
    var onFileLoaded: (() -> Unit)? = null
    var onPlaybackRestart: (() -> Unit)? = null

    private var initialized = false
    private var sessionOptions = SessionOptions()
    private var appliedSessionOptions: SessionOptions? = null
    private var pendingStartPositionMs: Long? = null
    private var pendingExternalSubtitlePaths: List<String> = emptyList()
    private var pendingExternalAudioPaths: List<String> = emptyList()
    private var lastState = StateSnapshot()
    private val recentNativeLogMessages = ArrayDeque<MpvNativeLogMessage>()
    @Volatile private var releaseEventLatch: CountDownLatch? = null
    @Volatile private var lastNativeDiagnostics: MpvNativeDiagnostics? = null

    fun ensureInitialized() {
        lifecycleGate.withNativeAccess {
            if (initialized) return@withNativeAccess
            val configDir = File(context.filesDir, "mpv/config").apply { mkdirs() }
            val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }
            initialize(configDir.absolutePath, cacheDir.absolutePath)
            MPVLib.addObserver(this)
            MPVLib.addLogObserver(this)
            initialized = true
        }
    }

    fun releaseMpv() {
        if (!lifecycleGate.beginReleaseIf { initialized }) return

        onStateChanged = null
        onTracksChanged = null
        onFileLoaded = null
        onPlaybackRestart = null
        val latch = CountDownLatch(1)
        releaseEventLatch = latch
        lifecycleGate.withReleaseNativeAccess {
            MPVLib.command(arrayOf("stop"))
        }
        try {
            if (!latch.await(RELEASE_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                android.util.Log.w(TAG, "Timed out waiting for mpv idle during release")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            android.util.Log.w(TAG, "Interrupted while waiting for mpv idle during release")
        }
        lifecycleGate.withReleaseNativeAccess {
            MPVLib.removeObserver(this)
            MPVLib.removeLogObserver(this)
        }
        releaseEventLatch = null
        releasePlayerAfterBegin()
        initialized = false
        appliedSessionOptions = null
        pendingExternalSubtitlePaths = emptyList()
        pendingExternalAudioPaths = emptyList()
        lastState = StateSnapshot()
        synchronized(recentNativeLogMessages) {
            recentNativeLogMessages.clear()
        }
    }

    fun applySessionOptions(options: SessionOptions): Boolean =
        lifecycleGate.withNativeAccess {
            sessionOptions = options
            if (!initialized || appliedSessionOptions == options) return@withNativeAccess false
            applyRuntimeOptions(options)
            true
        } ?: false

    fun loadMedia(
        path: String,
        startPositionMs: Long = 0L,
        externalSubtitlePaths: List<String> = emptyList(),
        externalAudioPaths: List<String> = emptyList(),
    ) {
        ensureInitialized()
        lifecycleGate.withNativeAccess {
            if (appliedSessionOptions != sessionOptions) {
                applyRuntimeOptions(sessionOptions)
            }
            pendingStartPositionMs = startPositionMs.coerceAtLeast(0L).takeIf(::shouldApplyPendingStartSeek)
            pendingExternalSubtitlePaths = externalSubtitlePaths.filter(String::isNotBlank).distinct()
            pendingExternalAudioPaths = externalAudioPaths.filter(String::isNotBlank).distinct()
            if (shouldLoadMpvFileImmediately(isPlaybackSurfaceAttached())) {
                MPVLib.command(arrayOf("loadfile", path))
            } else {
                playFile(path)
            }
        }
    }

    fun subtitleTracks(): List<TrackInfo> = tracksOfType("sub")

    fun audioTracks(): List<TrackInfo> = tracksOfType("audio")

    private fun tracksOfType(type: String): List<TrackInfo> =
        lifecycleGate.withNativeAccess {
            if (!initialized) return@withNativeAccess emptyList()
            val count = MPVLib.getPropertyInt("track-list/count") ?: return@withNativeAccess emptyList()
            (0 until count).mapNotNull { index ->
                if (MPVLib.getPropertyString("track-list/$index/type") != type) return@mapNotNull null
                val id = MPVLib.getPropertyInt("track-list/$index/id") ?: return@mapNotNull null
                TrackInfo(
                    id = id,
                    language = MPVLib.getPropertyString("track-list/$index/lang") ?: "und",
                    title = MPVLib.getPropertyString("track-list/$index/title") ?: "",
                    codec = MPVLib.getPropertyString("track-list/$index/codec") ?: "",
                    external = MPVLib.getPropertyBoolean("track-list/$index/external") ?: false,
                    selected = MPVLib.getPropertyBoolean("track-list/$index/selected") ?: false,
                )
            }
        } ?: emptyList()

    fun setSubtitleTrack(trackId: Int?) {
        lifecycleGate.withNativeAccess {
            if (initialized) MPVLib.setPropertyString("sid", trackId?.toString() ?: "no")
        }
    }

    fun setAudioTrack(trackId: Int) {
        lifecycleGate.withNativeAccess {
            if (initialized) MPVLib.setPropertyString("aid", trackId.toString())
        }
    }

    fun pausePlayback() {
        lifecycleGate.withNativeAccess {
            if (initialized) MPVLib.setPropertyBoolean("pause", true)
        }
    }

    fun resumePlayback() {
        lifecycleGate.withNativeAccess {
            if (initialized) MPVLib.setPropertyBoolean("pause", false)
        }
    }

    fun seekTo(positionMs: Long) {
        lifecycleGate.withNativeAccess {
            if (initialized) MPVLib.setPropertyDouble("time-pos", positionMs.coerceAtLeast(0L) / 1000.0)
        }
    }

    fun stopPlayback() {
        lifecycleGate.withNativeAccess {
            if (initialized) MPVLib.command(arrayOf("stop"))
        }
    }

    fun snapshotNativeDiagnostics(logLimit: Int = 80): MpvNativeDiagnostics {
        val collectedAt = SystemClock.elapsedRealtime()
        val snapshot = lifecycleGate.withNativeAccess {
            val properties = EMBEDDED_MPV_NATIVE_PROPERTY_SPECS.map { spec ->
                MpvNativePropertySample(
                    name = spec.name,
                    value = readNativePropertyValue(spec),
                )
            }
            val recentLogs = synchronized(recentNativeLogMessages) {
                recentNativeLogMessages.takeLast(logLimit.coerceAtLeast(1))
            }
            MpvNativeDiagnostics(
                collectedAtElapsedRealtimeMs = collectedAt,
                surfaceAttached = isPlaybackSurfaceAttached(),
                pendingStartPositionMs = pendingStartPositionMs,
                properties = properties,
                recentLogMessages = recentLogs,
            )
        }
        if (snapshot != null) {
            lastNativeDiagnostics = snapshot
            return snapshot
        }
        return lastNativeDiagnostics ?: MpvNativeDiagnostics(
            collectedAtElapsedRealtimeMs = collectedAt,
            surfaceAttached = false,
            pendingStartPositionMs = null,
            properties = emptyList(),
            recentLogMessages = emptyList(),
        )
    }

    override fun initOptions() {
        MPVLib.setOptionString("profile", sessionOptions.profile)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        MPVLib.setOptionString("hwdec", sessionOptions.hwdec)
        MPVLib.setOptionString("hwdec-codecs", EMBEDDED_MPV_HWDEC_CODECS)
        setVo(sessionOptions.vo)
        MPVLib.setOptionString("save-position-on-quit", "no")
        // Keep authored ASS styles and positions intact; libass owns subtitle layout.
        mpvSubtitleLayoutNormalisationOptions.forEach { (name, value) ->
            MPVLib.setOptionString(name, value)
        }
        applyColorPipelineProperties(sessionOptions)
        applyShaderProperties(sessionOptions.shaderPaths)
        sessionOptions.extraOptions.forEach { (name, value) ->
            MPVLib.setOptionString(name, value)
        }
        appliedSessionOptions = sessionOptions
    }

    override fun postInitOptions() = Unit

    override fun observeProperties() {
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration/full", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("track-list/count", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("sid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("aid", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    }

    override fun eventProperty(property: String) = Unit

    override fun eventProperty(property: String, value: Long) {
        lifecycleGate.withNativeAccess {
            if (property == "track-list/count") notifyTracksChanged()
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        lifecycleGate.withNativeAccess {
            when (property) {
                "pause" -> publishState(lastState.copy(paused = value))
                "eof-reached" -> publishState(lastState.copy(eofReached = value))
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        lifecycleGate.withNativeAccess {
            if (property == "sid" || property == "aid") notifyTracksChanged()
        }
    }

    private fun notifyTracksChanged() {
        post {
            lifecycleGate.withNativeAccess {
                if (initialized) onTracksChanged?.invoke(this)
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        lifecycleGate.withNativeAccess {
            when (property) {
                "time-pos" -> publishState(lastState.copy(positionMs = (value * 1000.0).toLong()))
                "duration/full" -> publishState(lastState.copy(durationMs = (value * 1000.0).toLong()))
            }
        }
    }

    override fun event(eventId: Int) {
        if (eventId == MPVLib.MpvEvent.MPV_EVENT_END_FILE ||
            eventId == MPVLib.MpvEvent.MPV_EVENT_IDLE ||
            eventId == MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN
        ) {
            releaseEventLatch?.countDown()
        }
        lifecycleGate.withNativeAccess {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    pendingStartPositionMs?.takeIf(::shouldApplyPendingStartSeek)?.let { startPositionMs ->
                        MPVLib.command(arrayOf("seek", (startPositionMs / 1000.0).toString(), "absolute+exact"))
                    }
                    pendingExternalSubtitlePaths.forEachIndexed { index, path ->
                        MPVLib.command(arrayOf("sub-add", path, if (index == 0) "select" else "auto"))
                    }
                    pendingExternalAudioPaths.forEach { path ->
                        MPVLib.command(arrayOf("audio-add", path, "auto"))
                    }
                    onFileLoaded?.invoke()
                }
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                    pendingStartPositionMs = null
                    onPlaybackRestart?.invoke()
                }
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        lifecycleGate.withNativeAccess {
            synchronized(recentNativeLogMessages) {
                recentNativeLogMessages.addLast(
                    MpvNativeLogMessage(
                        observedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                        prefix = prefix,
                        level = level,
                        text = text,
                    )
                )
                while (recentNativeLogMessages.size > MAX_NATIVE_LOG_MESSAGES) {
                    recentNativeLogMessages.removeFirst()
                }
            }
        }
    }

    private fun publishState(state: StateSnapshot) {
        lastState = state
        onStateChanged?.invoke(state)
    }

    private fun applyRuntimeOptions(options: SessionOptions) {
        updateVoInUse(options.vo)
        MPVLib.setPropertyString("vo", options.vo)
        MPVLib.setPropertyString("hwdec", options.hwdec)
        applyColorPipelineProperties(options)
        applyShaderProperties(options.shaderPaths)
        options.extraOptions.forEach { (name, value) ->
            when (name) {
                "speed" -> MPVLib.setPropertyDouble(name, value.toDoubleOrNull() ?: 1.0)
                else -> MPVLib.setPropertyString(name, value)
            }
        }
        appliedSessionOptions = options
    }

    private fun applyColorPipelineProperties(options: SessionOptions) {
        MPVLib.setPropertyString("target-prim", options.targetPrim ?: "auto")
        MPVLib.setPropertyString("target-trc", options.targetTrc ?: "auto")
        MPVLib.setPropertyString("target-peak", options.targetPeak?.toString() ?: "auto")
        MPVLib.setPropertyString("hdr-reference-white", options.hdrReferenceWhite?.toString() ?: "203")
        MPVLib.setPropertyString("tone-mapping", options.toneMapping ?: "auto")
        MPVLib.setPropertyString("tone-mapping-param", options.toneMappingParam?.toString() ?: "default")
        MPVLib.setPropertyString(
            "hdr-compute-peak",
            when (options.hdrComputePeak) {
                true -> "yes"
                false -> "no"
                null -> "auto"
            },
        )
        MPVLib.setPropertyString("hdr-peak-percentile", options.hdrPeakPercentile?.toString() ?: "100")
        MPVLib.setPropertyString("hdr-peak-decay-rate", options.hdrPeakDecayRate?.toString() ?: "20")
        MPVLib.setPropertyString("hdr-scene-threshold-low", options.hdrSceneThresholdLow?.toString() ?: "1")
        MPVLib.setPropertyString("hdr-scene-threshold-high", options.hdrSceneThresholdHigh?.toString() ?: "3")
        MPVLib.setPropertyString("hdr-contrast-recovery", options.hdrContrastRecovery?.toString() ?: "0")
        MPVLib.setPropertyString("saturation", options.saturation?.toString() ?: "0")
        MPVLib.setPropertyString("gamut-mapping-mode", options.gamutMappingMode ?: "auto")
        MPVLib.setPropertyString("deband", if (options.deband) "yes" else "no")
    }

    private fun applyShaderProperties(shaderPaths: List<String>) {
        MPVLib.setPropertyString("glsl-shaders", shaderPaths.joinToString(":") { it.trim() })
    }

    private fun readNativePropertyValue(spec: EmbeddedMpvNativePropertySpec): String? =
        runCatching {
            when (spec.type) {
                EmbeddedMpvNativePropertyType.STRING -> MPVLib.getPropertyString(spec.name)
                EmbeddedMpvNativePropertyType.INT -> MPVLib.getPropertyInt(spec.name)?.toString()
                EmbeddedMpvNativePropertyType.DOUBLE -> MPVLib.getPropertyDouble(spec.name)?.toString()
                EmbeddedMpvNativePropertyType.BOOLEAN -> MPVLib.getPropertyBoolean(spec.name)?.toString()
            }
        }.getOrNull()
}

private enum class EmbeddedMpvNativePropertyType {
    STRING,
    INT,
    DOUBLE,
    BOOLEAN,
}

private data class EmbeddedMpvNativePropertySpec(
    val name: String,
    val type: EmbeddedMpvNativePropertyType,
)

private val EMBEDDED_MPV_NATIVE_PROPERTY_SPECS = listOf(
    EmbeddedMpvNativePropertySpec("vo", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("hwdec-current", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("video-codec", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("audio-codec-name", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("video-params/pixelformat", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("video-params/hw-pixelformat", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("video-params/primaries", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("video-params/gamma", EmbeddedMpvNativePropertyType.STRING),
    EmbeddedMpvNativePropertySpec("video-params/sig-peak", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("container-fps", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("estimated-vf-fps", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("display-sync-active", EmbeddedMpvNativePropertyType.BOOLEAN),
    EmbeddedMpvNativePropertySpec("decoder-frame-drop-count", EmbeddedMpvNativePropertyType.INT),
    EmbeddedMpvNativePropertySpec("frame-drop-count", EmbeddedMpvNativePropertyType.INT),
    EmbeddedMpvNativePropertySpec("mistimed-frame-count", EmbeddedMpvNativePropertyType.INT),
    EmbeddedMpvNativePropertySpec("vo-delayed-frame-count", EmbeddedMpvNativePropertyType.INT),
    EmbeddedMpvNativePropertySpec("speed", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("avsync", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("audio-pts", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("time-pos", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("duration/full", EmbeddedMpvNativePropertyType.DOUBLE),
    EmbeddedMpvNativePropertySpec("pause", EmbeddedMpvNativePropertyType.BOOLEAN),
)

private const val TAG = "MiruMpvSurfaceView"
private const val RELEASE_EVENT_TIMEOUT_MS = 1_000L
private const val MAX_NATIVE_LOG_MESSAGES = 120
private const val EMBEDDED_MPV_HWDEC_CODECS = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1"
