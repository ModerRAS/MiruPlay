package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.RendererCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAssTextRendererTest {

    @Test
    fun `native renderer handles only ssa when libass is available`() {
        val renderer = NativeAssTextRenderer(
            session = LibassSubtitleSession(NoopRendererFactory, ImmediateDispatcher),
            nativeAvailable = { true },
        )

        assertEquals(
            C.FORMAT_HANDLED,
            RendererCapabilities.getFormatSupport(renderer.supportsFormat(format(MimeTypes.TEXT_SSA))),
        )
        assertEquals(
            C.FORMAT_UNSUPPORTED_SUBTYPE,
            RendererCapabilities.getFormatSupport(renderer.supportsFormat(format(MimeTypes.TEXT_VTT))),
        )
        assertEquals(
            C.FORMAT_UNSUPPORTED_TYPE,
            RendererCapabilities.getFormatSupport(renderer.supportsFormat(format(MimeTypes.VIDEO_H264))),
        )
    }

    @Test
    fun `ssa falls back when native symbols are unavailable`() {
        val renderer = NativeAssTextRenderer(
            session = LibassSubtitleSession(NoopRendererFactory, ImmediateDispatcher),
            nativeAvailable = { false },
        )

        assertEquals(
            C.FORMAT_UNSUPPORTED_SUBTYPE,
            RendererCapabilities.getFormatSupport(renderer.supportsFormat(format(MimeTypes.TEXT_SSA))),
        )
    }

    @Test
    fun `stock decoder gives ssa exactly one owner and preserves other formats`() {
        val nativeRenderer = NativeAssTextRenderer(
            session = LibassSubtitleSession(NoopRendererFactory, ImmediateDispatcher),
            nativeAvailable = { true },
        )
        val nativeOwner = LibassFallbackSubtitleDecoderFactory(nativeAvailable = { true })
        val stockFallback = LibassFallbackSubtitleDecoderFactory(nativeAvailable = { false })
        val ssa = format(MimeTypes.TEXT_SSA)

        assertEquals(
            1,
            listOf(
                RendererCapabilities.getFormatSupport(nativeRenderer.supportsFormat(ssa)) == C.FORMAT_HANDLED,
                nativeOwner.supportsFormat(ssa),
            ).count { it },
        )
        assertTrue(stockFallback.supportsFormat(ssa))
        assertTrue(nativeOwner.supportsFormat(format(MimeTypes.APPLICATION_SUBRIP)))
        assertTrue(nativeOwner.supportsFormat(format(MimeTypes.TEXT_VTT)))
        assertFalse(nativeOwner.supportsFormat(format(MimeTypes.VIDEO_H264)))
    }

    private fun format(mimeType: String): Format = Format.Builder()
        .setSampleMimeType(mimeType)
        .build()

    private object NoopRendererFactory : LibassRendererFactory {
        override fun create(document: ByteArray, fonts: List<`is`.xyz.mpv.subtitle.NativeAssFont>) = null
    }

    private object ImmediateDispatcher : LibassRenderDispatcher {
        override fun dispatch(block: () -> Unit) = block()
        override fun close() = Unit
    }
}
