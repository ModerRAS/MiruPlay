package com.miruplay.tv.player

import `is`.xyz.mpv.subtitle.NativeAssFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression for the IJK subtitle false-negative: the libass overlay SurfaceView must
 * keep a renderable target across session replacement (play/replay), otherwise the
 * session's submitVideoFrame is gated on a null target and nativeRender never runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibassSubtitleSurfaceViewRebindTest {

    @Test
    fun `rebinding overlay to a replacement session keeps rendering`() {
        val context = RuntimeEnvironment.getApplication()
        val overlay = LibassSubtitleSurfaceView(context)
        val firstSession = newSession()
        val replacementSession = newSession()
        val generation = replacementSession.session.currentGeneration()
        val target = FakeRenderTarget()
        replacementSession.session.bindTarget(target)

        // Initial bind (first play).
        overlay.bind(firstSession.session)
        // Surface lifecycle arrives (async after attach).
        val holder = overlay.holder
        overlay.surfaceCreated(holder)
        overlay.surfaceChanged(holder, 0, 1920, 1080)

        // Replay replaces the session; the controller now rebinds the kept overlay view.
        overlay.bind(replacementSession.session)

        // The kept overlay must still render the replacement session's payloads.
        val payloads = parseExternalSubtitlePayloads(
            "1\n00:00:40,000 --> 00:00:50,000\nEXTERNAL SRT CUE".toByteArray(),
            com.miruplay.tv.model.SubtitleFormat.SRT,
        )
        replacementSession.session.startTrack(generation, null)
        payloads.forEach { payload -> replacementSession.session.acceptPayload(generation, payload) }
        replacementSession.session.submitVideoFrame(43_000_000L, 640, 360)
        replacementSession.dispatcher.runNext()

        assertEquals(listOf(43_000_000L), target.renderedPresentationTimesUs)
        assertTrue(replacementSession.session.isActive)
        assertTrue(replacementSession.factory.renderers.single().events.any { it.endsWith("EXTERNAL SRT CUE") })
    }

    private fun newSession(): SessionFixture = SessionFixture()

    private class SessionFixture {
        val factory = FakeRendererFactory()
        val dispatcher = FakeRenderDispatcher()
        val session = LibassSubtitleSession(factory, dispatcher)
    }

    private class FakeRendererFactory : LibassRendererFactory {
        val renderers = mutableListOf<FakeRenderer>()

        override fun create(document: ByteArray, fonts: List<NativeAssFont>): LibassRendererHandle {
            return FakeRenderer().also(renderers::add)
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

        override fun dispatch(block: () -> Unit) {
            pending += block
        }

        override fun close() = Unit

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
