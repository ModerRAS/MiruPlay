@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.util.Log

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource
import `is`.xyz.mpv.subtitle.NativeAssRenderer

internal class NativeAssTextRenderer(
    private val session: LibassSubtitleSession,
    private val nativeAvailable: () -> Boolean = NativeAssRenderer::isAvailable,
) : BaseRenderer(C.TRACK_TYPE_TEXT) {
    private val formatHolder = FormatHolder()
    private val inputBuffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
    private var mediaGeneration = 0L
    private var streamOffsetUs = 0L
    private var inputEnded = false
    private var sampleCount = 0

    override fun getName(): String = "NativeAssTextRenderer"

    override fun supportsFormat(format: Format): Int {
        val mimeType = format.sampleMimeType
        val support = when {
            mimeType == MimeTypes.TEXT_SSA && nativeAvailable() -> {
                if (format.cryptoType == C.CRYPTO_TYPE_NONE) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_DRM
            }
            MimeTypes.isText(mimeType) -> C.FORMAT_UNSUPPORTED_SUBTYPE
            else -> C.FORMAT_UNSUPPORTED_TYPE
        }
        return RendererCapabilities.create(support)
    }

    override fun onStreamChanged(
        formats: Array<out Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        inputEnded = false
        sampleCount = 0
        streamOffsetUs = offsetUs
        mediaGeneration = session.currentGeneration()
        val header = formats.firstOrNull()?.let(::assHeaderFrom)
        session.startTrack(mediaGeneration, header)
        Log.i(TAG, "Activated raw ASS track generation=$mediaGeneration header_bytes=${header?.size ?: 0}")
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        if (inputEnded) return
        while (true) {
            inputBuffer.clear()
            when (readSource(formatHolder, inputBuffer, 0)) {
                C.RESULT_FORMAT_READ -> {
                    formatHolder.format?.let { format ->
                        mediaGeneration = session.currentGeneration()
                        session.startTrack(mediaGeneration, assHeaderFrom(format))
                    }
                }

                C.RESULT_BUFFER_READ -> {
                    if (inputBuffer.isEndOfStream) {
                        inputEnded = true
                        return
                    }
                    inputBuffer.flip()
                    val data = inputBuffer.data ?: continue
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    val payload = decodeLibassPayload(
                        bytes,
                        relativeLibassSampleTimeUs(inputBuffer.timeUs, streamOffsetUs),
                    )
                    if (payload == null) {
                        Log.w(TAG, "Skipped malformed ASS sample time_us=${inputBuffer.timeUs} bytes=${bytes.size}")
                    } else {
                        sampleCount++
                        if (sampleCount == 1) {
                            Log.i(TAG, "Accepted first ASS sample generation=$mediaGeneration time_us=${inputBuffer.timeUs}")
                        }
                    }
                    session.acceptPayload(mediaGeneration, payload)
                }

                C.RESULT_NOTHING_READ -> return
                else -> return
            }
        }
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        inputEnded = false
        session.onSeek(mediaGeneration)
        Log.i(TAG, "Flushed ASS events on seek generation=$mediaGeneration position_us=$positionUs")
    }

    override fun onDisabled() {
        inputEnded = false
        session.deactivate(mediaGeneration)
        Log.i(TAG, "Deactivated raw ASS track generation=$mediaGeneration samples=$sampleCount")
    }

    override fun isEnded(): Boolean = inputEnded

    override fun isReady(): Boolean = true

    private companion object {
        const val TAG = "NativeAssTextRenderer"
    }
}
