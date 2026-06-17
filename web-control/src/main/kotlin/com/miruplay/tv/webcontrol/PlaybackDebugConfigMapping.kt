package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.player.LibVlcHardwareAccelerationMode
import com.miruplay.tv.player.LibVlcVoutMode

internal fun playbackRenderBackendFromDebugValue(value: String?): PlaybackRenderBackend? =
    when (value.debugKey()) {
        "standardexo", "standard", "exo", "media3" -> PlaybackRenderBackend.STANDARD_EXO
        "experimentalgl", "gl" -> PlaybackRenderBackend.EXPERIMENTAL_GL
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
