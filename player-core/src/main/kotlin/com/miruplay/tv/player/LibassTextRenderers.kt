@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.extractor.text.SubtitleDecoder
import `is`.xyz.mpv.subtitle.NativeAssRenderer

internal fun addLibassTextRenderers(
    session: LibassSubtitleSession,
    output: TextOutput,
    outputLooper: Looper,
    out: ArrayList<Renderer>,
) {
    val nativeAvailable = NativeAssRenderer.isAvailable()
    out += NativeAssTextRenderer(session, nativeAvailable = { nativeAvailable })
    out += TextRenderer(
        output,
        outputLooper,
        LibassFallbackSubtitleDecoderFactory { nativeAvailable },
    ).apply {
        experimentalSetLegacyDecodingEnabled(true)
    }
}

internal class LibassFallbackSubtitleDecoderFactory(
    private val nativeAvailable: () -> Boolean,
) : SubtitleDecoderFactory {
    override fun supportsFormat(format: Format): Boolean =
        !(format.sampleMimeType == MimeTypes.TEXT_SSA && nativeAvailable()) &&
            SubtitleDecoderFactory.DEFAULT.supportsFormat(format)

    override fun createDecoder(format: Format): SubtitleDecoder =
        SubtitleDecoderFactory.DEFAULT.createDecoder(format)
}
