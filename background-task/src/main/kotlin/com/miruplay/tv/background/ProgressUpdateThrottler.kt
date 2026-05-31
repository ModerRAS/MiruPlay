package com.miruplay.tv.background

class ProgressUpdateThrottler(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val minBytesDelta: Long = DEFAULT_MIN_BYTES_DELTA,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastUpdateAtMs: Long? = null
    private var lastBytesRead: Long = -1L
    private var lastTotalBytes: Long = -1L
    private var lastPercent: Int? = null

    fun shouldUpdate(bytesRead: Long, totalBytes: Long): Boolean {
        val safeBytesRead = bytesRead.coerceAtLeast(0L)
        val safeTotalBytes = totalBytes.coerceAtLeast(0L)
        val percent = progressPercent(safeBytesRead, safeTotalBytes)
        val now = clock()
        val lastUpdateAt = lastUpdateAtMs
        val shouldUpdate = when {
            lastUpdateAt == null -> true
            isComplete(safeBytesRead, safeTotalBytes) && lastPercent != 100 -> true
            safeTotalBytes != lastTotalBytes -> true
            now - lastUpdateAt < minIntervalMs -> false
            safeBytesRead - lastBytesRead >= minBytesDelta -> true
            percent != null && percent != lastPercent -> true
            percent == null && safeBytesRead != lastBytesRead -> true
            else -> false
        }

        if (shouldUpdate) {
            lastUpdateAtMs = now
            lastBytesRead = safeBytesRead
            lastTotalBytes = safeTotalBytes
            lastPercent = percent
        }
        return shouldUpdate
    }

    private fun isComplete(bytesRead: Long, totalBytes: Long): Boolean =
        totalBytes > 0L && bytesRead >= totalBytes

    private fun progressPercent(bytesRead: Long, totalBytes: Long): Int? =
        totalBytes.takeIf { it > 0L }?.let {
            ((bytesRead.coerceAtLeast(0L) * 100L) / it).toInt().coerceIn(0, 100)
        }

    private companion object {
        private const val DEFAULT_MIN_INTERVAL_MS = 1_000L
        private const val DEFAULT_MIN_BYTES_DELTA = 256L * 1024L
    }
}
