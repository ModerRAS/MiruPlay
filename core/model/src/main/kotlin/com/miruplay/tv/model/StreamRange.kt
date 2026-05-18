package com.miruplay.tv.model

import java.io.InputStream

data class StreamRange(
    val start: Long,
    val endInclusive: Long? = null,
) {
    init {
        require(start >= 0L) { "start must be non-negative" }
        require(endInclusive == null || endInclusive >= start) { "endInclusive must be greater than or equal to start" }
    }

    val length: Long? = endInclusive?.let { it - start + 1L }
}

fun InputStream.applyRange(range: StreamRange): InputStream {
    skipFully(range.start)
    val length = range.length ?: return this
    return RangeLimitedInputStream(this, length)
}

private fun InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped <= 0L) {
            if (read() == -1) break
            remaining--
        } else {
            remaining -= skipped
        }
    }
}

class RangeLimitedInputStream(
    private val delegate: InputStream,
    private var remaining: Long,
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0L) return -1
        val value = delegate.read()
        if (value != -1) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0L) return -1
        val allowed = minOf(length.toLong(), remaining).toInt()
        val read = delegate.read(buffer, offset, allowed)
        if (read > 0) remaining -= read
        return read
    }

    override fun close() {
        delegate.close()
    }
}
