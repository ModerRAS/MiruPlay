package com.miruplay.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZlibSubtitleDataSourceTest {
    @Test
    fun `mkv source protects lv999 subtitle zlib across one byte reads`() {
        val containerPrefix = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x42)
        val compressed = REAL_LV999_SAMPLE.hexBytes()
        val sourceBytes = containerPrefix + compressed + byteArrayOf(0x7F)
        val dataSource = ZlibSubtitleProtectingDataSource(
            ChunkedByteArrayDataSource(sourceBytes, maxReadSize = 1),
        )

        dataSource.open(DataSpec(Uri.parse("file:///storage/emulated/0/video.mkv")))
        val output = dataSource.readAll(readBufferSize = 7)

        assertEquals(sourceBytes.size, output.size)
        val protected = output.copyOfRange(containerPrefix.size, containerPrefix.size + compressed.size)
        assertFalse(protected.any { it == 0.toByte() })
        assertArrayEquals(
            DIALOGUE_PREFIX.toByteArray() + EXPECTED_PAYLOAD.toByteArray(),
            inflateSubtitleSampleIfNeeded(DIALOGUE_PREFIX.toByteArray() + protected),
        )
    }

    @Test
    fun `mkv source flushes an incomplete zlib candidate unchanged at eof`() {
        val incomplete = REAL_LV999_SAMPLE.hexBytes().copyOf(26)
        val sourceBytes = byteArrayOf(0x11, 0x22) + incomplete
        val dataSource = ZlibSubtitleProtectingDataSource(
            ChunkedByteArrayDataSource(sourceBytes, maxReadSize = 3),
        )

        dataSource.open(DataSpec(Uri.parse("https://example.test/video.mkv")))

        assertArrayEquals(sourceBytes, dataSource.readAll(readBufferSize = 5))
    }

    @Test
    fun `non mkv source bypasses subtitle byte protection`() {
        val compressed = REAL_LV999_SAMPLE.hexBytes()
        val dataSource = ZlibSubtitleProtectingDataSource(
            ChunkedByteArrayDataSource(compressed, maxReadSize = 2),
        )

        dataSource.open(DataSpec(Uri.parse("https://example.test/video.mp4")))

        assertArrayEquals(compressed, dataSource.readAll(readBufferSize = 9))
    }

    @Test
    fun `ordinary zlib content inside mkv is left unchanged`() {
        val compressed = deflate("ordinary binary content".toByteArray())
        val dataSource = ZlibSubtitleProtectingDataSource(
            ChunkedByteArrayDataSource(compressed, maxReadSize = 2),
        )

        dataSource.open(DataSpec(Uri.parse("https://example.test/video.mkv")))

        assertArrayEquals(compressed, dataSource.readAll(readBufferSize = 9))
    }

    private fun DataSource.readAll(readBufferSize: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(readBufferSize)
        while (true) {
            val count = read(buffer, 0, buffer.size)
            if (count == C.RESULT_END_OF_INPUT) break
            output.write(buffer, 0, count)
        }
        close()
        return output.toByteArray()
    }

    private class ChunkedByteArrayDataSource(
        private val bytes: ByteArray,
        private val maxReadSize: Int,
    ) : DataSource {
        private var position = 0
        private var uri: Uri? = null

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri
            position = dataSpec.position.toInt()
            return (bytes.size - position).toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, maxReadSize, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun getUri(): Uri? = uri

        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

        override fun close() {
            uri = null
        }
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val output = ByteArray(input.size + 64)
        val length = deflater.deflate(output)
        deflater.end()
        return output.copyOf(length)
    }

    private companion object {
        const val DIALOGUE_PREFIX = "Dialogue: 0:00:00:00,0:00:03:08,"
        const val EXPECTED_PAYLOAD = "1,6,Text CN,镜浩二,0,0,0,,只是一般路过的村民"
        const val REAL_LV999_SAMPLE =
            "78da33d431d30949ad285170f6d3793975ceb3ad2b9fecead13100439da7fdab9ecd58ff6447c38b8e352fb6af7fb1bffdf9ac966773273edb301100e7981e759b820c08a0cea1c8"
    }
}
