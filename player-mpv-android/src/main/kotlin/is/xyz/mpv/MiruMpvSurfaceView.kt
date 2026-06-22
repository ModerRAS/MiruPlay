package `is`.xyz.mpv

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import com.miruplay.tv.model.MpvNativeDiagnostics
import com.miruplay.tv.model.MpvNativeLogMessage
import com.miruplay.tv.model.MpvNativePropertySample
import java.io.File

class MiruMpvSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BaseMPVView(context, attrs), MPVLib.EventObserver, MPVLib.LogObserver {
    data class SessionOptions(
        val vo: String = "gpu-next",
        val hwdec: String = "mediacodec-copy",
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

    var onStateChanged: ((StateSnapshot) -> Unit)? = null
    var onFileLoaded: (() -> Unit)? = null
    var onPlaybackRestart: (() -> Unit)? = null
    var onLogMessage: ((prefix: String, level: Int, text: String) -> Unit)? = null

    private var initialized = false
    private var sessionOptions = SessionOptions()
    private var appliedSessionOptions: SessionOptions? = null
    private var pendingStartPositionMs: Long? = null
    private var lastState = StateSnapshot()
    private val recentNativeLogMessages = ArrayDeque<MpvNativeLogMessage>()

    fun ensureInitialized() {
        if (initialized) {
            return
        }
        val configDir = File(context.filesDir, "mpv/config").apply { mkdirs() }
        val cacheDir = File(context.cacheDir, "mpv").apply { mkdirs() }
        initialize(configDir.absolutePath, cacheDir.absolutePath)
        MPVLib.addObserver(this)
        MPVLib.addLogObserver(this)
        initialized = true
    }

    fun releaseMpv() {
        if (!initialized) {
            return
        }
        MPVLib.removeObserver(this)
        MPVLib.removeLogObserver(this)
        releasePlayer()
        initialized = false
        appliedSessionOptions = null
        lastState = StateSnapshot()
        synchronized(recentNativeLogMessages) {
            recentNativeLogMessages.clear()
        }
    }

    fun applySessionOptions(options: SessionOptions) {
        sessionOptions = options
        if (!initialized || appliedSessionOptions == options) {
            return
        }
        applyRuntimeOptions(options)
    }

    fun loadMedia(path: String, startPositionMs: Long = 0L) {
        ensureInitialized()
        if (appliedSessionOptions != sessionOptions) {
            applyRuntimeOptions(sessionOptions)
        }
        pendingStartPositionMs = startPositionMs.coerceAtLeast(0L).takeIf(::shouldApplyPendingStartSeek)
        if (shouldLoadMpvFileImmediately(isPlaybackSurfaceAttached())) {
            MPVLib.command(arrayOf("loadfile", path))
        } else {
            playFile(path)
        }
    }

    fun pausePlayback() {
        if (initialized) {
            MPVLib.setPropertyBoolean("pause", true)
        }
    }

    fun resumePlayback() {
        if (initialized) {
            MPVLib.setPropertyBoolean("pause", false)
        }
    }

    fun seekTo(positionMs: Long) {
        if (initialized) {
            MPVLib.setPropertyDouble("time-pos", positionMs.coerceAtLeast(0L) / 1000.0)
        }
    }

    fun stopPlayback() {
        if (initialized) {
            MPVLib.command(arrayOf("stop"))
        }
    }

    fun snapshotNativeDiagnostics(logLimit: Int = 80): MpvNativeDiagnostics {
        val collectedAt = SystemClock.elapsedRealtime()
        val properties = EMBEDDED_MPV_NATIVE_PROPERTY_SPECS.map { spec ->
            MpvNativePropertySample(
                name = spec.name,
                value = readNativePropertyValue(spec),
            )
        }
        val recentLogs = synchronized(recentNativeLogMessages) {
            recentNativeLogMessages.takeLast(logLimit.coerceAtLeast(1))
        }
        return MpvNativeDiagnostics(
            collectedAtElapsedRealtimeMs = collectedAt,
            surfaceAttached = isPlaybackSurfaceAttached(),
            pendingStartPositionMs = pendingStartPositionMs,
            properties = properties,
            recentLogMessages = recentLogs,
        )
    }

    override fun initOptions() {
        MPVLib.setOptionString("profile", sessionOptions.profile)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        MPVLib.setOptionString("hwdec", sessionOptions.hwdec)
        MPVLib.setOptionString("vo", sessionOptions.vo)
        MPVLib.setOptionString("save-position-on-quit", "no")
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
    }

    override fun eventProperty(property: String) = Unit

    override fun eventProperty(property: String, value: Long) = Unit

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> publishState(lastState.copy(paused = value))
            "eof-reached" -> publishState(lastState.copy(eofReached = value))
        }
    }

    override fun eventProperty(property: String, value: String) = Unit

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> publishState(lastState.copy(positionMs = (value * 1000.0).toLong()))
            "duration/full" -> publishState(lastState.copy(durationMs = (value * 1000.0).toLong()))
        }
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                pendingStartPositionMs?.takeIf(::shouldApplyPendingStartSeek)?.let { startPositionMs ->
                    MPVLib.command(arrayOf("seek", (startPositionMs / 1000.0).toString(), "absolute+exact"))
                }
                onFileLoaded?.invoke()
            }
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                pendingStartPositionMs = null
                onPlaybackRestart?.invoke()
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
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
        onLogMessage?.invoke(prefix, level, text)
    }

    private fun publishState(state: StateSnapshot) {
        lastState = state
        onStateChanged?.invoke(state)
    }

    private fun applyRuntimeOptions(options: SessionOptions) {
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

private const val MAX_NATIVE_LOG_MESSAGES = 120
