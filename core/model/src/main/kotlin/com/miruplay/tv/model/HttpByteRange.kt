package com.miruplay.tv.model

data class HttpByteRangeRequest(
    val start: Long?,
    val endInclusive: Long?,
) {
    init {
        require(start != null || endInclusive != null) { "byte range must include a start or suffix length" }
        require(start == null || start >= 0L) { "start must be non-negative" }
        require(endInclusive == null || endInclusive >= 0L) { "endInclusive must be non-negative" }
    }

    fun resolve(totalLength: Long?): HttpByteRange {
        if (start != null && endInclusive != null && endInclusive < start) {
            return HttpByteRange.Invalid(totalLength?.takeIf { it > 0L })
        }
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
            val equalsIndex = header.indexOf('=').takeIf { it >= 0 } ?: return null
            val unit = header.substring(0, equalsIndex).trim()
            if (!unit.equals(BYTE_RANGE_UNIT, ignoreCase = true)) return null

            val spec = header.substring(equalsIndex + 1).substringBefore(',').trim()
            val match = BYTE_RANGE_SPEC.matchEntire(spec) ?: return null
            val startText = match.groupValues[1]
            val endText = match.groupValues[2]
            val start = startText.takeIf { it.isNotBlank() }?.toLongOrNull() ?: run {
                if (startText.isNotBlank()) return null
                null
            }
            val end = endText.takeIf { it.isNotBlank() }?.toLongOrNull() ?: run {
                if (endText.isNotBlank()) return null
                null
            }
            if (start == null && end == null) return null
            return HttpByteRangeRequest(start = start, endInclusive = end)
        }

        private const val BYTE_RANGE_UNIT = "bytes"
        private val BYTE_RANGE_SPEC = Regex("""(\d*)\s*-\s*(\d*)""")
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

data class HttpStreamResponsePlan(
    val statusCode: Int,
    val contentLength: Long,
    val contentRangeHeader: String? = null,
) {
    companion object {
        fun from(range: HttpByteRange?, totalLength: Long?): HttpStreamResponsePlan =
            when (range) {
                is HttpByteRange.Resolved -> HttpStreamResponsePlan(
                    statusCode = 206,
                    contentLength = range.length,
                    contentRangeHeader = range.contentRangeHeader,
                )
                is HttpByteRange.Invalid -> HttpStreamResponsePlan(
                    statusCode = 416,
                    contentLength = 0L,
                    contentRangeHeader = range.contentRangeHeader,
                )
                else -> HttpStreamResponsePlan(
                    statusCode = 200,
                    contentLength = totalLength?.takeIf { it > 0L } ?: 0L,
                )
            }
    }
}
