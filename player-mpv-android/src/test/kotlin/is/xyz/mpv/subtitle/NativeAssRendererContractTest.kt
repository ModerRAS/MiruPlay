package `is`.xyz.mpv.subtitle

import android.view.Surface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAssRendererContractTest {

    @Test
    fun `create rejects unavailable native symbols`() {
        val calls = FakeNativeAssCalls(available = false)

        assertNull(NativeAssRenderer.create(HEADER, emptyList(), calls))
        assertEquals(0, calls.createCount)
    }

    @Test
    fun `create rejects an empty ass document`() {
        val calls = FakeNativeAssCalls()

        assertNull(NativeAssRenderer.create(byteArrayOf(), emptyList(), calls))
        assertEquals(0, calls.createCount)
    }

    @Test
    fun `renderer forwards every font without rewriting bytes`() {
        val calls = FakeNativeAssCalls()
        val font = NativeAssFont("signs.otf", byteArrayOf(0, 1, 2, -1))

        NativeAssRenderer.create(HEADER, listOf(font), calls)

        assertArrayEquals(HEADER, calls.createdDocument)
        assertEquals("signs.otf", calls.createdFonts.single().name)
        assertArrayEquals(font.data, calls.createdFonts.single().data)
    }

    @Test
    fun `close releases one native handle exactly once`() {
        val calls = FakeNativeAssCalls(createHandle = 42L)
        val renderer = requireNotNull(NativeAssRenderer.create(HEADER, emptyList(), calls))

        renderer.close()
        renderer.close()

        assertEquals(listOf(42L), calls.releasedHandles)
    }

    @Test
    fun `closed renderer rejects events and flushes`() {
        val calls = FakeNativeAssCalls(createHandle = 42L)
        val renderer = requireNotNull(NativeAssRenderer.create(HEADER, emptyList(), calls))
        renderer.close()

        assertFalse(renderer.addEvent("Dialogue: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,Hi"))
        assertFalse(renderer.flushEvents())
        assertTrue(calls.events.isEmpty())
        assertEquals(0, calls.flushCount)
    }

    private class FakeNativeAssCalls(
        private val available: Boolean = true,
        private val createHandle: Long = 7L,
    ) : NativeAssCalls {
        var createCount = 0
        var createdDocument: ByteArray? = null
        var createdFonts: List<NativeAssFont> = emptyList()
        val releasedHandles = mutableListOf<Long>()
        val events = mutableListOf<String>()
        var flushCount = 0

        override fun isAvailable(): Boolean = available

        override fun create(document: ByteArray, fonts: List<NativeAssFont>): Long {
            createCount++
            createdDocument = document.copyOf()
            createdFonts = fonts.map { NativeAssFont(it.name, it.data.copyOf()) }
            return createHandle
        }

        override fun addEvent(handle: Long, dialogueLine: String): Boolean {
            events += dialogueLine
            return true
        }

        override fun flushEvents(handle: Long): Boolean {
            flushCount++
            return true
        }

        override fun render(
            handle: Long,
            surface: Surface,
            timeMs: Long,
            frameWidth: Int,
            frameHeight: Int,
            storageWidth: Int,
            storageHeight: Int,
        ): Int = 0

        override fun clearSurface(surface: Surface, width: Int, height: Int): Boolean = true

        override fun release(handle: Long) {
            releasedHandles += handle
        }
    }

    private companion object {
        val HEADER = "[Script Info]\nScriptType: v4.00+\n[Events]\n".toByteArray()
    }
}
