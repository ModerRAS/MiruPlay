package com.miruplay.tv.player

import com.miruplay.tv.model.SubtitleFormat
import `is`.xyz.mpv.subtitle.NativeAssFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for IJK external subtitle support:
 *  - external subtitleTracks no longer force an Exo fallback when libass is available;
 *  - SRT/VTT files convert to libass payloads and the session can be selected/closed;
 *  - tracks IJK cannot render (embedded/unknown) cannot fake a successful selection.
 */
class IjkExternalSubtitleTest {

    // --- routing: external subtitles no longer force an Exo fallback ---

    @Test
    fun `external subtitles with libass available do not fall back to exo`() {
        assertNull(ijkExternalSubtitleFallbackReason(hasExternalSubtitles = true, libassAvailable = true))
    }

    @Test
    fun `external subtitles without libass fall back honestly`() {
        assertEquals(
            "IJKPlayer 缺少 libass 渲染库，外挂字幕需使用标准 Exo",
            ijkExternalSubtitleFallbackReason(hasExternalSubtitles = true, libassAvailable = false),
        )
    }

    @Test
    fun `no external subtitles never fall back`() {
        assertNull(ijkExternalSubtitleFallbackReason(hasExternalSubtitles = false, libassAvailable = false))
    }

    // --- selection contract: only renderable tracks can be selected ---

    @Test
    fun `same host reuses overlay while new host replaces it`() {
        val host = Object()
        val differentHost = Object()

        // No overlay yet -> fresh creation on the current host.
        assertEquals(
            IjkSubtitleOverlayAction.CREATE,
            ijkSubtitleOverlayAction(existingOverlayParent = null, host = host),
        )
        // Same host on replay -> keep the view and its live surface.
        assertEquals(
            IjkSubtitleOverlayAction.REBIND,
            ijkSubtitleOverlayAction(existingOverlayParent = host, host = host),
        )
        // Different host -> never move a destroyed SurfaceView across hosts.
        assertEquals(
            IjkSubtitleOverlayAction.REPLACE,
            ijkSubtitleOverlayAction(existingOverlayParent = host, host = differentHost),
        )
    }

    @Test
    fun `embedded or unknown tracks cannot be selected and stay closed`() {
        // Only one renderable (externally loaded) track exists; index 3 cannot be a
        // real renderable track, so selecting it must resolve to null (stays closed)
        // instead of pretending success.
        assertNull(resolveIjkSubtitleTrackSelection(trackIndex = 3, renderableTrackCount = 1))
        assertNull(resolveIjkSubtitleTrackSelection(trackIndex = -1, renderableTrackCount = 1))
    }

    @Test
    fun `closing subtitles resolves to null`() {
        assertNull(resolveIjkSubtitleTrackSelection(trackIndex = null, renderableTrackCount = 2))
    }

    @Test
    fun `renderable track index selects`() {
        assertEquals(1, resolveIjkSubtitleTrackSelection(trackIndex = 1, renderableTrackCount = 2))
    }

    // --- payload parsing: SRT/VTT -> libass document + events, ASS passthrough ---

    @Test
    fun `srt file converts to ass document and dialogue events`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello world

            2
            00:00:04,000 --> 00:00:05,000
            Second line
            still second
        """.trimIndent().toByteArray()

        val payloads = parseExternalSubtitlePayloads(srt, SubtitleFormat.SRT)

        assertEquals(3, payloads.size)
        val document = payloads[0] as LibassPayload.Document
        assertTrue(String(document.bytes).contains("[Script Info]"))
        val first = (payloads[1] as LibassPayload.Event).dialogueLine
        assertTrue(first.startsWith("Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,"))
        assertTrue(first.endsWith("Hello world"))
        val second = (payloads[2] as LibassPayload.Event).dialogueLine
        assertTrue(second.endsWith("Second line\\Nstill second"))
    }

    @Test
    fun `vtt file converts to ass dialogue events`() {
        val vtt = """
            WEBVTT

            00:00:01.500 --> 00:00:04.000
            VTT cue text

            NOTE a comment block

            00:00:05.000 --> 00:00:06.000
            Later cue
        """.trimIndent().toByteArray()

        val payloads = parseExternalSubtitlePayloads(vtt, SubtitleFormat.VTT)

        assertEquals(3, payloads.size)
        val first = (payloads[1] as LibassPayload.Event).dialogueLine
        assertTrue(first.startsWith("Dialogue: 0,0:00:01.50,0:00:04.00,Default,,0,0,0,,"))
        assertTrue(first.endsWith("VTT cue text"))
    }

    @Test
    fun `ass file passes through as libass document`() {
        val ass = "[Script Info]\nScriptType: v4.00+\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,hi".toByteArray()

        val payloads = parseExternalSubtitlePayloads(ass, SubtitleFormat.ASS)

        assertEquals(1, payloads.size)
        assertTrue(payloads.single() is LibassPayload.Document)
    }

    @Test
    fun `empty or cue-less files produce no payloads`() {
        assertTrue(parseExternalSubtitlePayloads(ByteArray(0), SubtitleFormat.SRT).isEmpty())
        assertTrue(parseExternalSubtitlePayloads("no cues here".toByteArray(), SubtitleFormat.SRT).isEmpty())
        assertTrue(parseExternalSubtitlePayloads("WEBVTT\n\njust a header".toByteArray(), SubtitleFormat.VTT).isEmpty())
    }

    // --- session contract: external subtitles selectable and closable ---

    @Test
    fun `external srt payloads feed session and render on the ijk clock`() {
        val srt = "1\n00:00:01,000 --> 00:00:03,000\nClock driven".toByteArray()
        val payloads = parseExternalSubtitlePayloads(srt, SubtitleFormat.SRT)
        val fixture = SessionFixture()
        val generation = fixture.session.beginMedia()
        val target = FakeRenderTarget()
        fixture.session.bindTarget(target)

        fixture.session.startTrack(generation, null)
        payloads.forEach { payload -> fixture.session.acceptPayload(generation, payload) }

        val renderer = fixture.factory.renderers.single()
        assertEquals(1, renderer.events.size)
        assertTrue(renderer.events.single().endsWith("Clock driven"))

        // IJK playback clock drives the session at 1.5s.
        fixture.session.submitVideoFrame(1_500_000L, 1920, 1080)
        fixture.dispatcher.runNext()
        assertEquals(listOf(1_500_000L), target.renderedPresentationTimesUs)
    }

    @Test
    fun `closing ijk subtitle session deactivates and stops rendering`() {
        val srt = "1\n00:00:01,000 --> 00:00:03,000\nClose me".toByteArray()
        val payloads = parseExternalSubtitlePayloads(srt, SubtitleFormat.SRT)
        val fixture = SessionFixture()
        val generation = fixture.session.beginMedia()
        val target = FakeRenderTarget()
        fixture.session.bindTarget(target)

        fixture.session.startTrack(generation, null)
        payloads.forEach { payload -> fixture.session.acceptPayload(generation, payload) }
        fixture.session.submitVideoFrame(1_500_000L, 1920, 1080)
        fixture.dispatcher.runNext()
        assertEquals(1, fixture.factory.renderers.single().events.size)

        fixture.session.deactivate(generation)

        assertTrue(fixture.factory.renderers.single().closed)
        fixture.session.submitVideoFrame(1_600_000L, 1920, 1080)
        assertEquals(0, fixture.dispatcher.pendingCount)
        assertFalse(fixture.session.isActive)
    }

    private class SessionFixture {
        val factory = FakeRendererFactory()
        val dispatcher = FakeRenderDispatcher()
        val session = LibassSubtitleSession(factory, dispatcher)
    }

    private class FakeRendererFactory : LibassRendererFactory {
        val renderers = mutableListOf<FakeRenderer>()

        override fun create(document: ByteArray, fonts: List<NativeAssFont>): LibassRendererHandle {
            val renderer = FakeRenderer().also(renderers::add)
            assertNotNull(renderer)
            return renderer
        }
    }

    private class FakeRenderer : LibassRendererHandle {
        val events = mutableListOf<String>()
        var closed = false

        override fun addEvent(dialogueLine: String): Boolean {
            events += dialogueLine
            return true
        }

        override fun flushEvents(): Boolean {
            events.clear()
            return true
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeRenderDispatcher : LibassRenderDispatcher {
        private val pending = ArrayDeque<() -> Unit>()
        var closeCount = 0

        val pendingCount: Int get() = pending.size

        override fun dispatch(block: () -> Unit) {
            pending += block
        }

        override fun close() {
            closeCount++
        }

        fun runNext() {
            pending.removeFirst().invoke()
        }
    }

    private class FakeRenderTarget : LibassRenderTarget {
        val renderedPresentationTimesUs = mutableListOf<Long>()

        override fun render(renderer: LibassRendererHandle, frame: LibassVideoFrame): Int {
            renderedPresentationTimesUs += frame.presentationTimeUs
            return `is`.xyz.mpv.subtitle.NativeAssRenderer.RENDER_UNCHANGED
        }

        override fun clear(renderer: LibassRendererHandle?) = Unit
    }
}
