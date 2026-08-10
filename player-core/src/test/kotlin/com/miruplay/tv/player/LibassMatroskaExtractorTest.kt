package com.miruplay.tv.player

import `is`.xyz.mpv.subtitle.NativeAssFont
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibassMatroskaExtractorTest {
    @Test
    fun `factory uses raw Matroska only when native ASS is available`() {
        val session = LibassSubtitleSession()
        val delegate = mockk<DefaultExtractorsFactory>()
        every { delegate.createExtractors() } answers { arrayOf(MatroskaExtractor()) }
        try {
            val enabled = ZlibSubtitleExtractorsFactory(
                session = session,
                nativeAvailable = { true },
                delegate = delegate,
            ).createExtractors()
            val fallback = ZlibSubtitleExtractorsFactory(
                session = session,
                nativeAvailable = { false },
                delegate = delegate,
            ).createExtractors()

            assertTrue(enabled.any { it is LibassMatroskaExtractor })
            assertFalse(fallback.any { it is LibassMatroskaExtractor })
            assertTrue(fallback.any { it is MatroskaExtractor })
        } finally {
            session.close()
        }
    }

    @Test
    fun `font mime publishes exact attachment bytes even without font extension`() {
        val fonts = mutableListOf<NativeAssFont>()
        val collector = LibassAttachmentCollector(fonts::add)
        val bytes = byteArrayOf(0, 1, 2, -1)

        collector.startFile()
        collector.setData(bytes)
        collector.setName("subtitle-font.bin")
        collector.setMimeType("font/otf")
        collector.endFile()

        assertEquals("subtitle-font.bin", fonts.single().name)
        assertArrayEquals(bytes, fonts.single().data)
    }

    @Test
    fun `font extension works when mime is absent and data arrives first`() {
        val fonts = mutableListOf<NativeAssFont>()
        val collector = LibassAttachmentCollector(fonts::add)

        collector.startFile()
        collector.setData(byteArrayOf(7, 8))
        collector.setName("signs.TTC")
        collector.endFile()

        assertEquals("signs.TTC", fonts.single().name)
    }

    @Test
    fun `non font and oversized attachments are ignored`() {
        val fonts = mutableListOf<NativeAssFont>()
        val collector = LibassAttachmentCollector(fonts::add)

        collector.startFile()
        collector.setName("poster.jpg")
        collector.setMimeType("image/jpeg")
        collector.setData(byteArrayOf(1))
        collector.endFile()

        collector.startFile()
        assertFalse(collector.canReadData(32 * 1024 * 1024 + 1))
        collector.rejectData()
        collector.setName("huge.ttf")
        collector.endFile()

        assertTrue(fonts.isEmpty())
    }

    @Test
    fun `font count limit bounds malformed containers`() {
        val fonts = mutableListOf<NativeAssFont>()
        val collector = LibassAttachmentCollector(fonts::add)

        repeat(65) { index ->
            collector.startFile()
            collector.setName("font-$index.ttf")
            collector.setData(byteArrayOf(index.toByte()))
            collector.endFile()
        }

        assertEquals(64, fonts.size)
    }
}
