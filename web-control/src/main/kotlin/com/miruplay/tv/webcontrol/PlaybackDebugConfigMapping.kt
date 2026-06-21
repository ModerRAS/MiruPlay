package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PeakDetectionStrategy
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.player.LibVlcHardwareAccelerationMode
import com.miruplay.tv.player.LibVlcVoutMode

internal fun playbackRenderBackendFromDebugValue(value: String?): PlaybackRenderBackend? =
    when (value.debugKey()) {
        "standardexo", "standard", "exo", "media3" -> PlaybackRenderBackend.STANDARD_EXO
        "experimentalgl", "gl" -> PlaybackRenderBackend.EXPERIMENTAL_GL
        "experimentalmpvandroid", "mpvandroid" -> PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED
        "experimentalmpvembedded", "mpvembedded", "embeddedmpv", "libmpv", "mpv" -> PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED
        "experimentallibvlc", "libvlc", "vlc" -> PlaybackRenderBackend.EXPERIMENTAL_LIBVLC
        else -> null
    }

internal fun videoSignalKindFromDebugValue(value: String?): VideoSignalKind? =
    when (value.videoSignalDebugKey()) {
        "sdr" -> VideoSignalKind.SDR
        "hdr10" -> VideoSignalKind.HDR10
        "hdr10plus", "hdr10p" -> VideoSignalKind.HDR10_PLUS
        "dolbyvision", "dv" -> VideoSignalKind.DOLBY_VISION
        "unknownhdr", "hdr" -> VideoSignalKind.UNKNOWN_HDR
        else -> null
    }

internal fun libVlcHardwareModeFromDebugValue(value: String?): LibVlcHardwareAccelerationMode? =
    when (value.debugKey()) {
        "full", "on" -> LibVlcHardwareAccelerationMode.FULL
        "decodingonly", "decodeonly" -> LibVlcHardwareAccelerationMode.DECODING_ONLY
        "disabled", "disable", "off", "none" -> LibVlcHardwareAccelerationMode.DISABLED
        else -> null
    }

internal fun libVlcVoutModeFromDebugValue(value: String?): LibVlcVoutMode? =
    when (value.debugKey()) {
        "default" -> LibVlcVoutMode.DEFAULT
        "directtexture", "direct", "texture", "gles2" -> LibVlcVoutMode.DIRECT_TEXTURE
        "glsurface", "surface" -> LibVlcVoutMode.GL_SURFACE
        "outputcallbacks", "callbacks" -> LibVlcVoutMode.OUTPUT_CALLBACKS
        "androiddisplay", "display" -> LibVlcVoutMode.ANDROID_DISPLAY
        "vmemstream", "vmem" -> LibVlcVoutMode.VMEM_STREAM
        "vmemprobe", "probe" -> LibVlcVoutMode.VMEM_PROBE
        else -> null
    }

internal fun isDebugClearValue(value: String?): Boolean =
    value.debugKey() in setOf("clear", "reset", "null", "none", "default")

internal fun debugBooleanValue(value: String?): Boolean? =
    when (value.debugKey()) {
        "true", "yes", "1", "on", "enable", "enabled", "skip", "bypass" -> true
        "false", "no", "0", "off", "disable", "disabled" -> false
        else -> null
    }

internal fun toneMappingPresetFromDebugValue(value: String?): ToneMappingProfilePreset? =
    when (value.debugKey()) {
        "bypass", "passthrough", "direct" -> ToneMappingProfilePreset.BYPASS
        "balanced", "default", "mobius" -> ToneMappingProfilePreset.BALANCED
        "punchy" -> ToneMappingProfilePreset.PUNCHY
        "soft" -> ToneMappingProfilePreset.SOFT
        else -> null
    }

internal fun peakDetectionStrategyFromDebugValue(value: String?): PeakDetectionStrategy? =
    when (value.debugKey()) {
        "disabled", "disable", "off", "none" -> PeakDetectionStrategy.DISABLED
        "staticmetadata", "static" -> PeakDetectionStrategy.STATIC_METADATA
        "dynamic", "dyn" -> PeakDetectionStrategy.DYNAMIC
        "dynamicaggressive", "aggressive" -> PeakDetectionStrategy.DYNAMIC_AGGRESSIVE
        else -> null
    }

internal fun gamutMappingModeFromDebugValue(value: String?): String? =
    when (value.debugKey()) {
        "perceptual" -> "perceptual"
        "relative" -> "relative"
        "clip" -> "clip"
        else -> null
    }

internal fun debugLabelValue(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { isDebugClearValue(it) }
        ?.take(80)

internal fun libVlcDisplayChromaFromDebugValue(value: String?): String? =
    value
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.matches(Regex("[A-Z0-9]{4}")) }

private fun String?.debugKey(): String =
    this
        ?.trim()
        ?.lowercase()
        ?.filter { it.isLetterOrDigit() }
        .orEmpty()

private fun String?.videoSignalDebugKey(): String {
    val trimmed = this?.trim()?.lowercase().orEmpty()
    return if ('+' in trimmed) {
        trimmed.replace("+", "plus").filter { it.isLetterOrDigit() }
    } else {
        trimmed.filter { it.isLetterOrDigit() }
    }
}
