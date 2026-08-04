package com.miruplay.tv.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ZlibSubtitleSampleTest {

    @Test
    fun `zlib compressed subtitle sample is inflated`() {
        val ass = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\n".toByteArray()

        assertArrayEquals(ass, inflateSubtitleSampleIfNeeded(deflate(ass)))
    }

    @Test
    fun `matroska dialogue prefix is preserved while zlib payload is inflated`() {
        val prefix = "Dialogue: 0:00:00:00,0:00:02:16,"
        val payload = "Default,,0,0,0,,Hello\n".toByteArray()
        val sample = prefix.toByteArray() + deflate(payload)

        assertEquals(prefix.length, findZlibHeader(sample))
        assertArrayEquals(prefix.toByteArray() + payload, inflateSubtitleSampleIfNeeded(sample))
    }

    @Test
    fun `plain subtitle sample is returned unchanged`() {
        val ass = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\n".toByteArray()

        assertArrayEquals(ass, inflateSubtitleSampleIfNeeded(ass))
    }

    @Test
    fun `invalid compressed-looking sample is returned unchanged`() {
        val bytes = byteArrayOf(0x78.toByte(), 0x9c.toByte(), 0x01, 0x02, 0x03)

        assertArrayEquals(bytes, inflateSubtitleSampleIfNeeded(bytes))
    }

    @Test
    fun `external subtitle parser receives inflated sample`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.TEXT_SSA)
            .build()
        val plain = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Inflated subtitle\n".toByteArray()
        val delegate = RecordingSubtitleParser()
        val compressed = deflate(plain)

        zlibSubtitleParserFactory(RecordingSubtitleParserFactory(delegate))
            .create(format)
            .parse(
                compressed,
                0,
                compressed.size,
                SubtitleParser.OutputOptions.allCues(),
                Consumer { },
            )

        assertArrayEquals(plain, delegate.lastSample)
    }

    @Test
    fun `real lv999 zlib sample with embedded nul is inflated`() {
        val prefix = "Dialogue: 0:00:00:00,0:00:03:08,".toByteArray()
        val compressed = REAL_LV999_SAMPLE.hexBytes()
        val payload = "1,6,Text CN,镜浩二,0,0,0,,只是一般路过的村民".toByteArray()

        assertArrayEquals(prefix + payload, inflateSubtitleSampleIfNeeded(prefix + compressed))
    }

    @Test
    fun `zlib protector preserves a real lv999 sample without nul truncation`() {
        val compressed = REAL_LV999_SAMPLE.hexBytes()
        val protected = protectSubtitleZlibBytes(compressed)

        assertNotEquals(compressed.toList(), protected.toList())
        assertFalse(protected.containsByte(0))
        assertArrayEquals(
            "1,6,Text CN,镜浩二,0,0,0,,只是一般路过的村民".toByteArray(),
            inflateSubtitleSampleIfNeeded(
                "Dialogue: 0:00:00:00,0:00:03:08,".toByteArray() + protected,
            ).copyOfRange(32, 32 + 56),
        )
    }

    @Test
    fun `incomplete zlib stream is not treated as a subtitle`() {
        val compressed = REAL_LV999_SAMPLE.hexBytes().copyOf(26)

        assertArrayEquals(compressed, protectSubtitleZlibBytes(compressed))
        assertArrayEquals(compressed, inflateSubtitleSampleIfNeeded(compressed))
    }

    private class RecordingSubtitleParserFactory(
        private val parser: RecordingSubtitleParser,
    ) : SubtitleParser.Factory {
        override fun supportsFormat(format: Format): Boolean = true

        override fun getCueReplacementBehavior(format: Format): Int = 1

        override fun create(format: Format): SubtitleParser = parser
    }

    private class RecordingSubtitleParser : SubtitleParser {
        var lastSample: ByteArray? = null

        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>,
        ) {
            lastSample = data.copyOfRange(offset, offset + length)
        }

        override fun getCueReplacementBehavior(): Int = 1

        override fun reset() = Unit
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val output = ByteArray(input.size + 64)
        val length = deflater.deflate(output)
        deflater.end()
        return output.copyOf(length)
    }

    private fun ByteArray.containsByte(value: Int): Boolean = any { (it.toInt() and 0xFF) == value }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val REAL_LV999_SAMPLE =
            "78da33d431d30949ad285170f6d3793975ceb3ad2b9fecead13100439da7fdab9ecd58ff6447c38b8e352fb6af7fb1bffdf9ac966773273edb301100e7981e759b820c08a0cea1c8"
    }
}
