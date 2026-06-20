package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
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
    private var lastState = StateSnapshot()

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
        if (startPositionMs > 0L) {
            MPVLib.setPropertyDouble("time-pos", startPositionMs / 1000.0)
        }
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

    override fun initOptions() {
        MPVLib.setOptionString("profile", sessionOptions.profile)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        MPVLib.setOptionString("hwdec", sessionOptions.hwdec)
        MPVLib.setOptionString("vo", sessionOptions.vo)
        MPVLib.setOptionString("save-position-on-quit", "no")
        applyColorPipelineOptions(sessionOptions)
        applyShaderOptions(sessionOptions.shaderPaths)
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
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> onFileLoaded?.invoke()
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> onPlaybackRestart?.invoke()
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        onLogMessage?.invoke(prefix, level, text)
    }

    private fun publishState(state: StateSnapshot) {
        lastState = state
        onStateChanged?.invoke(state)
    }

    private fun applyRuntimeOptions(options: SessionOptions) {
        MPVLib.setOptionString("vo", options.vo)
        MPVLib.setOptionString("hwdec", options.hwdec)
        applyColorPipelineOptions(options)
        applyShaderOptions(options.shaderPaths)
        options.extraOptions.forEach { (name, value) ->
            MPVLib.setOptionString(name, value)
        }
        appliedSessionOptions = options
    }

    private fun applyColorPipelineOptions(options: SessionOptions) {
        MPVLib.setOptionString("target-prim", options.targetPrim ?: "auto")
        MPVLib.setOptionString("target-trc", options.targetTrc ?: "auto")
        MPVLib.setOptionString("target-peak", options.targetPeak?.toString() ?: "auto")
        MPVLib.setOptionString("hdr-reference-white", options.hdrReferenceWhite?.toString() ?: "203")
        MPVLib.setOptionString("tone-mapping", options.toneMapping ?: "auto")
        MPVLib.setOptionString("tone-mapping-param", options.toneMappingParam?.toString() ?: "default")
        MPVLib.setOptionString(
            "hdr-compute-peak",
            when (options.hdrComputePeak) {
                true -> "yes"
                false -> "no"
                null -> "auto"
            },
        )
        MPVLib.setOptionString("hdr-peak-percentile", options.hdrPeakPercentile?.toString() ?: "100")
        MPVLib.setOptionString("hdr-peak-decay-rate", options.hdrPeakDecayRate?.toString() ?: "20")
        MPVLib.setOptionString("hdr-scene-threshold-low", options.hdrSceneThresholdLow?.toString() ?: "1")
        MPVLib.setOptionString("hdr-scene-threshold-high", options.hdrSceneThresholdHigh?.toString() ?: "3")
        MPVLib.setOptionString("hdr-contrast-recovery", options.hdrContrastRecovery?.toString() ?: "0")
        MPVLib.setOptionString("saturation", options.saturation?.toString() ?: "0")
        MPVLib.setOptionString("gamut-mapping-mode", options.gamutMappingMode ?: "auto")
        MPVLib.setOptionString("deband", if (options.deband) "yes" else "no")
    }

    private fun applyShaderOptions(shaderPaths: List<String>) {
        if (shaderPaths.isEmpty()) {
            MPVLib.setOptionString("glsl-shaders", "")
            return
        }
        MPVLib.setOptionString("glsl-shaders", shaderPaths.joinToString(":") { it.trim() })
    }
}
