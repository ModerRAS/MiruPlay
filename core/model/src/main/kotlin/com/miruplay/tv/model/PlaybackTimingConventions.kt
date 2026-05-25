package com.miruplay.tv.model

import java.util.Locale
import kotlin.math.roundToLong

object PlaybackTimingConventions {
    fun parseSecondsToPositionMs(value: String): Long =
        value.trim()
            .takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()
            ?.let(::secondsToPositionMsRounded)
            ?: 0L

    fun secondsToPositionMsRounded(seconds: Double): Long =
        (seconds * MILLIS_PER_SECOND).roundToLong().coerceAtLeast(0L)

    fun secondsToPositionMsFloored(seconds: Double): Long =
        (seconds * MILLIS_PER_SECOND).toLong().coerceAtLeast(0L)

    fun secondsToDeltaMs(seconds: Double): Long =
        (seconds * MILLIS_PER_SECOND).toLong()

    fun coercePlaybackPositionMs(positionMs: Long, durationMs: Long = 0L): Long =
        if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }

    fun playbackProgressFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return coercePlaybackPositionMs(positionMs, durationMs).toFloat() / durationMs.toFloat()
    }

    fun formatMpvStartSeconds(positionMs: Long): String {
        val normalizedPosition = positionMs.coerceAtLeast(0L)
        if (normalizedPosition % 1_000L == 0L) {
            return (normalizedPosition / 1_000L).toString()
        }
        return String.format(Locale.US, "%.3f", normalizedPosition / MILLIS_PER_SECOND)
            .trimEnd('0')
            .trimEnd('.')
    }

    private const val MILLIS_PER_SECOND = 1_000.0
}
