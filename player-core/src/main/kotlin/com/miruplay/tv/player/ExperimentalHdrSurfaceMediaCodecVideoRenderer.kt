@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import android.util.Log
import androidx.media3.common.ColorInfo
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.miruplay.tv.core.common.logging.MiruLog

internal fun shouldRequestDecoderToneMapToSdr(
    format: Format,
    experimentalVideoPipelineMode: ExperimentalVideoPipelineMode,
    sdkInt: Int = Util.SDK_INT,
): Boolean {
    if (sdkInt < 31 || experimentalVideoPipelineMode != ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE) {
        return false
    }
    // The dedicated GL backend is responsible for consuming HDR output and applying tone mapping.
    // Requesting decoder-side HDR->SDR conversion on the tested Rockchip box produces black frames
    // while also bypassing the format-aware mapping pipeline we actually want to validate.
    return false
}

@UnstableApi
internal class ExperimentalHdrSurfaceMediaCodecVideoRenderer(
    context: Context,
    mediaCodecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    allowedVideoJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    maxDroppedFramesToNotify: Int,
    private val experimentalVideoPipelineMode: ExperimentalVideoPipelineMode,
) : MediaCodecVideoRenderer(
    context,
    mediaCodecAdapterFactory,
    mediaCodecSelector,
    allowedVideoJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify,
) {

    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxValues: CodecMaxValues,
        codecOperatingRate: Float,
        deviceNeedsNoPostProcessWorkaround: Boolean,
        tunnelingAudioSessionId: Int,
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(
            format,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunnelingAudioSessionId,
        )
        val isHdrInput = format.colorInfo?.let(ColorInfo::isTransferHdr) == true ||
            format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        if (shouldRequestDecoderToneMapToSdr(format, experimentalVideoPipelineMode)) {
            mediaFormat.setInteger(
                MediaFormat.KEY_COLOR_TRANSFER_REQUEST,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
            )
            Log.i(
                "ExperimentalHdrSurfaceRenderer",
                "Requesting decoder HDR->SDR output sampleMimeType=${format.sampleMimeType} codecs=${format.codecs.orEmpty()}",
            )
            MiruLog.i(
                "ExperimentalHdrSurfaceRenderer",
                "Requesting decoder HDR->SDR output",
                mapOf(
                    "sample_mime_type" to format.sampleMimeType.orEmpty(),
                    "codecs" to format.codecs.orEmpty(),
                    "width" to format.width.toString(),
                    "height" to format.height.toString(),
                ),
            )
        } else if (isHdrInput && experimentalVideoPipelineMode == ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE) {
            Log.i(
                "ExperimentalHdrSurfaceRenderer",
                "Keeping decoder HDR output for dedicated GL tone mapping sampleMimeType=${format.sampleMimeType} codecs=${format.codecs.orEmpty()}",
            )
            MiruLog.i(
                "ExperimentalHdrSurfaceRenderer",
                "Keeping decoder HDR output for dedicated GL tone mapping",
                mapOf(
                    "sample_mime_type" to format.sampleMimeType.orEmpty(),
                    "codecs" to format.codecs.orEmpty(),
                    "width" to format.width.toString(),
                    "height" to format.height.toString(),
                ),
            )
        }
        return mediaFormat
    }

    override fun onCodecInitialized(
        name: String,
        configuration: MediaCodecAdapter.Configuration,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        super.onCodecInitialized(name, configuration, initializedTimestampMs, initializationDurationMs)
        Log.i(
            "ExperimentalHdrSurfaceRenderer",
            "Initialized codec name=$name durationMs=$initializationDurationMs pipeline=$experimentalVideoPipelineMode",
        )
        MiruLog.i(
            "ExperimentalHdrSurfaceRenderer",
            "Initialized codec",
            mapOf(
                "codec_name" to name,
                "initialization_duration_ms" to initializationDurationMs.toString(),
                "pipeline" to experimentalVideoPipelineMode.name,
            ),
        )
    }

    override fun setVideoEffects(videoEffects: List<Effect>) {
        if (experimentalVideoPipelineMode == ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE) {
            Log.i(
                "ExperimentalHdrSurfaceRenderer",
                "Ignoring Media3 video effects update for dedicated GL surface pipeline size=${videoEffects.size}",
            )
            MiruLog.i(
                "ExperimentalHdrSurfaceRenderer",
                "Ignoring Media3 video effects update for dedicated GL surface pipeline",
                mapOf("effect_count" to videoEffects.size.toString()),
            )
            return
        }
        super.setVideoEffects(videoEffects)
    }

}
