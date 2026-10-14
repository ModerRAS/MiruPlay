@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

internal fun playbackDecoderPriority(
    mimeType: String,
    decoderName: String,
    decoderPreference: PlaybackDecoderPreference = PlaybackCodecSelectionState.decoderPreference,
): Int {
    if (mimeType != MimeTypes.VIDEO_H265 && mimeType != MimeTypes.VIDEO_H264) {
        return Int.MAX_VALUE
    }

    val normalizedName = decoderName.lowercase()
    val codecTokens = when (mimeType) {
        MimeTypes.VIDEO_H264 -> listOf("avc", "h264")
        MimeTypes.VIDEO_H265 -> listOf("hevc", "h265")
        else -> return Int.MAX_VALUE
    }
    if (decoderPreference == PlaybackDecoderPreference.PREFER_SOFTWARE_HEVC_FOR_HDR) {
        return when {
            normalizedName.startsWith("c2.android.hevc.decoder") -> 0
            normalizedName.startsWith("omx.google.") -> 1
            normalizedName.startsWith("c2.") &&
                normalizedName.contains("hevc") &&
                !normalizedName.contains("android") -> 2
            normalizedName.startsWith("omx.") &&
                normalizedName.contains("hevc") &&
                !normalizedName.contains("google") &&
                !normalizedName.contains("android") -> 3
            else -> 4
        }
    }
    if (decoderPreference == PlaybackDecoderPreference.PREFER_SOFTWARE_VIDEO_FOR_HDR) {
        return when {
            normalizedName.startsWith("c2.android.avc.decoder") ||
                normalizedName.startsWith("c2.android.h264.decoder") -> 0
            normalizedName.startsWith("omx.google.") &&
                normalizedName.containsAny(codecTokens) -> 1
            normalizedName.startsWith("c2.") &&
                normalizedName.containsAny(codecTokens) &&
                !normalizedName.contains("android") -> 2
            normalizedName.startsWith("omx.") &&
                normalizedName.containsAny(codecTokens) &&
                !normalizedName.contains("google") &&
                !normalizedName.contains("android") -> 3
            else -> 4
        }
    }
    // Default: platform hardware first for both codecs. The AVC branches matter: without
    // them every AVC decoder fell into `else -> 4` and the alphabetical tiebreak put
    // c2.android.avc.decoder ahead of vendor hardware (e.g. c2.rk.avc.decoder), forcing
    // software decode for all H.264 content. ponytail: mirrored HEVC shape, no new knobs.
    return when {
        normalizedName.startsWith("c2.") &&
            normalizedName.contains("hevc") &&
            !normalizedName.contains("android") -> 0
        normalizedName.startsWith("c2.android.hevc.decoder") -> 1
        normalizedName.startsWith("omx.") &&
            normalizedName.contains("hevc") &&
            !normalizedName.contains("google") &&
            !normalizedName.contains("android") -> 2
        normalizedName.startsWith("c2.") &&
            normalizedName.containsAny(codecTokens) &&
            !normalizedName.contains("android") -> 0
        normalizedName.startsWith("c2.android.avc.decoder") ||
            normalizedName.startsWith("c2.android.h264.decoder") -> 1
        normalizedName.startsWith("omx.") &&
            normalizedName.containsAny(codecTokens) &&
            !normalizedName.contains("google") &&
            !normalizedName.contains("android") -> 2
        normalizedName.startsWith("omx.google.") -> 3
            else -> 4
    }
}

private fun String.containsAny(tokens: List<String>): Boolean =
    tokens.any { contains(it) }

object PlaybackMediaCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val decoders = MediaCodecUtil.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
        if (mimeType != MimeTypes.VIDEO_H265 && mimeType != MimeTypes.VIDEO_H264) {
            return decoders
        }
        val preference = PlaybackCodecSelectionState.decoderPreference
        return decoders.sortedWith(
            compareBy<MediaCodecInfo> { playbackDecoderPriority(mimeType, it.name, preference) }
                .thenBy { it.name.lowercase() }
        )
    }
}
