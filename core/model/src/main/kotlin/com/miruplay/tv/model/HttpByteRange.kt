package com.miruplay.tv.model

data class HttpByteRangeRequest(
    val start: Long?,
    val endInclusive: Long?,
) {
    fun resolve(totalLength: Long?): HttpByteRange {
        val length = totalLength?.takeIf { it > 0L } ?: return HttpByteRange.Unresolved
        val resolvedStart = start ?: (length - (endInclusive ?: 0L)).coerceAtLeast(0L)
        val resolvedEnd = (if (start == null) length - 1 else endInclusive ?: length - 1)
            .coerceAtMost(length - 1)
        if (resolvedStart < 0 || resolvedStart >= length || resolvedStart > resolvedEnd) {
            return HttpByteRange.Invalid(length)
        }
        return HttpByteRange.Resolved(
            start = resolvedStart,
            endInclusive = resolvedEnd,
            totalLength = length,
        )
    }

    companion object {
        fun parse(header: String): HttpByteRangeRequest? {
            if (!header.startsWith("bytes=")) return null
            val spec = header.removePrefix("bytes=").substringBefore(',').trim()
            val start = spec.substringBefore('-', "").trim().takeIf { it.isNotBlank() }?.toLongOrNull()
            val end = spec.substringAfter('-', "").trim().takeIf { it.isNotBlank() }?.toLongOrNull()
            if (start == null && end == null) return null
            return HttpByteRangeRequest(start = start, endInclusive = end)
        }
    }
}

sealed interface HttpByteRange {
    data object Unresolved : HttpByteRange

    data class Invalid(
        val totalLength: Long?,
    ) : HttpByteRange {
        val contentRangeHeader: String = "bytes */${totalLength ?: "*"}"
    }

    data class Resolved(
        val start: Long,
        val endInclusive: Long,
        val totalLength: Long,
    ) : HttpByteRange {
        val length: Long = endInclusive - start + 1
        val contentRangeHeader: String = "bytes $start-$endInclusive/$totalLength"
        fun toStreamRange(): StreamRange = StreamRange(start, endInclusive)
    }
}
