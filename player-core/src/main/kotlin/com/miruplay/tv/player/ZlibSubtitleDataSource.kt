package com.miruplay.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Escapes NUL bytes in verified zlib-compressed ASS samples before Media3's Matroska extractor
 * treats them as string terminators. The replacement is equal-length, so container offsets remain
 * unchanged.
 */
internal class ZlibSubtitleProtectingDataSource(
    private val upstream: DataSource,
) : DataSource {
    private val readScratch = ByteArray(READ_BUFFER_SIZE)
    private var escaper = ZlibSubtitleByteEscaper()
    private var pendingOutput = ByteArray(0)
    private var pendingOffset = 0
    private var protectionEnabled = false
    private var endOfInput = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        resetState()
        protectionEnabled = dataSpec.uri.isMatroskaVideo()
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!protectionEnabled || length == 0) return upstream.read(buffer, offset, length)

        while (pendingOffset >= pendingOutput.size) {
            if (endOfInput) return C.RESULT_END_OF_INPUT
            val count = upstream.read(readScratch, 0, readScratch.size)
            if (count == C.RESULT_END_OF_INPUT) {
                pendingOutput = escaper.process(ByteArray(0), endOfInput = true)
                endOfInput = true
            } else {
                pendingOutput = escaper.process(readScratch.copyOf(count))
            }
            pendingOffset = 0
        }

        val count = minOf(length, pendingOutput.size - pendingOffset)
        pendingOutput.copyInto(buffer, offset, pendingOffset, pendingOffset + count)
        pendingOffset += count
        return count
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try {
            upstream.close()
        } finally {
            resetState()
        }
    }

    private fun resetState() {
        escaper = ZlibSubtitleByteEscaper()
        pendingOutput = ByteArray(0)
        pendingOffset = 0
        protectionEnabled = false
        endOfInput = false
    }

    private fun Uri.isMatroskaVideo(): Boolean =
        path?.endsWith(".mkv", ignoreCase = true) == true

    private companion object {
        const val READ_BUFFER_SIZE = 16 * 1024
    }
}

internal class ZlibSubtitleProtectingDataSourceFactory(
    private val upstream: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        ZlibSubtitleProtectingDataSource(upstream.createDataSource())
}
