package com.miruplay.tv.player

import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ZlibSubtitleSampleTest {

    @Test
    fun `zlib compressed subtitle sample is inflated`() {
        val ass = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\n".toByteArray()

        val result = inflateSubtitleSampleIfNeeded(deflate(ass))

        assertArrayEquals(ass, result)
    }

    @Test
    fun `matroska dialogue prefix is preserved while zlib payload is inflated`() {
        val prefix = "Dialogue: 0:00:00:00,0:00:02:16,"
        val payload = "Default,,0,0,0,,Hello\n".toByteArray()
        val sample = prefix.toByteArray() + deflate(payload)

        assertArrayEquals(
            prefix.toByteArray() + payload,
            inflateSubtitleSampleIfNeeded(sample),
        )
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

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val output = ByteArray(input.size + 64)
        val length = deflater.deflate(output)
        deflater.end()
        return output.copyOf(length)
    }
}
