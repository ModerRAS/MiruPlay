package com.miruplay.tv.player

import `is`.xyz.mpv.subtitle.NativeAssFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibassSubtitleSessionTest {

    @Test
    fun `stale media generation cannot append an event`() {
        val fixture = Fixture()
        val oldGeneration = fixture.session.beginMedia()
        fixture.session.startTrack(oldGeneration, HEADER)
        val newGeneration = fixture.session.beginMedia()
        fixture.session.startTrack(newGeneration, HEADER)

        fixture.session.acceptPayload(oldGeneration, EVENT)
        fixture.session.acceptPayload(newGeneration, EVENT)

        assertEquals(listOf(EVENT.dialogueLine), fixture.factory.renderers.last().events)
    }

    @Test
    fun `late font recreates renderer and replays current events`() {
        val fixture = Fixture()
        val generation = fixture.session.beginMedia()
        fixture.session.startTrack(generation, HEADER)
        fixture.session.acceptPayload(generation, EVENT)

        fixture.session.addFont(
            generation,
            NativeAssFont("signs.otf", byteArrayOf(1, 2, 3)),
        )

        assertEquals(2, fixture.factory.renderers.size)
        assertTrue(fixture.factory.renderers.first().closed)
        assertEquals("signs.otf", fixture.factory.createdFonts.last().single().name)
        assertEquals(listOf(EVENT.dialogueLine), fixture.factory.renderers.last().events)
    }

    @Test
    fun `duplicate font bytes do not recreate renderer`() {
        val fixture = Fixture()
        val generation = fixture.session.beginMedia()
        fixture.session.startTrack(generation, HEADER)
        val font = NativeAssFont("signs.otf", byteArrayOf(1, 2, 3))

        fixture.session.addFont(generation, font)
        fixture.session.addFont(generation, NativeAssFont("SIGNS.OTF", font.data.copyOf()))

        assertEquals(2, fixture.factory.renderers.size)
    }

    @Test
    fun `seek flushes native events and replay buffer`() {
        val fixture = Fixture()
        val generation = fixture.session.beginMedia()
        fixture.session.startTrack(generation, HEADER)
        fixture.session.acceptPayload(generation, EVENT)

        fixture.session.onSeek(generation)

        assertEquals(1, fixture.factory.renderers.last().flushCount)
        fixture.session.addFont(generation, NativeAssFont("late.ttf", byteArrayOf(4)))
        assertTrue(fixture.factory.renderers.last().events.isEmpty())
    }

    @Test
    fun `video timestamps coalesce to latest pending frame`() {
        val fixture = Fixture()
        val generation = fixture.session.beginMedia()
        fixture.session.startTrack(generation, HEADER)
        val target = FakeRenderTarget()
        fixture.session.bindTarget(target)

        fixture.session.submitVideoFrame(1_000L, 1920, 1080)
        fixture.session.submitVideoFrame(2_000L, 1920, 1080)
        fixture.session.submitVideoFrame(3_000L, 1920, 1080)

        assertEquals(1, fixture.dispatcher.pendingCount)
        fixture.dispatcher.runNext()
        assertEquals(listOf(3_000L), target.renderedPresentationTimesUs)
    }

    @Test
    fun `inactive track does not schedule native drawing`() {
        val fixture = Fixture()
        val generation = fixture.session.beginMedia()
        fixture.session.startTrack(generation, HEADER)
        fixture.session.deactivate(generation)
        fixture.session.bindTarget(FakeRenderTarget())

        fixture.session.submitVideoFrame(3_000L, 1920, 1080)

        assertEquals(0, fixture.dispatcher.pendingCount)
    }

    @Test
    fun `close is idempotent`() {
        val fixture = Fixture()
        val generation = fixture.session.beginMedia()
        fixture.session.startTrack(generation, HEADER)

        fixture.session.close()
        fixture.session.close()

        assertTrue(fixture.factory.renderers.single().closed)
        assertEquals(1, fixture.dispatcher.closeCount)
        assertFalse(fixture.session.isActive)
    }

    private class Fixture {
        val factory = FakeRendererFactory()
        val dispatcher = FakeRenderDispatcher()
        val session = LibassSubtitleSession(factory, dispatcher)
    }

    private class FakeRendererFactory : LibassRendererFactory {
        val renderers = mutableListOf<FakeRenderer>()
        val createdFonts = mutableListOf<List<NativeAssFont>>()

        override fun create(document: ByteArray, fonts: List<NativeAssFont>): LibassRendererHandle {
            createdFonts += fonts
            return FakeRenderer().also(renderers::add)
        }
    }

    private class FakeRenderer : LibassRendererHandle {
        val events = mutableListOf<String>()
        var flushCount = 0
        var closed = false

        override fun addEvent(dialogueLine: String): Boolean {
            events += dialogueLine
            return true
        }

        override fun flushEvents(): Boolean {
            flushCount++
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
        var clearCount = 0

        override fun render(renderer: LibassRendererHandle, frame: LibassVideoFrame) {
            renderedPresentationTimesUs += frame.presentationTimeUs
        }

        override fun clear(renderer: LibassRendererHandle?) {
            clearCount++
        }
    }

    private companion object {
        val HEADER = (
            "[Script Info]\nScriptType: v4.00+\n" +
                "[Events]\n" +
                "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
            ).toByteArray()
        val EVENT = LibassPayload.Event(
            "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello",
        )
    }
}
