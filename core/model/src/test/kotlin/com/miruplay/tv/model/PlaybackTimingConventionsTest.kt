package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimingConventionsTest {
    @Test
    fun `parseSecondsToPositionMs accepts fractional seconds`() {
        assertEquals(12_345L, PlaybackTimingConventions.parseSecondsToPositionMs("12.345"))
        assertEquals(0L, PlaybackTimingConventions.parseSecondsToPositionMs(""))
        assertEquals(0L, PlaybackTimingConventions.parseSecondsToPositionMs("abc"))
        assertEquals(0L, PlaybackTimingConventions.parseSecondsToPositionMs("-3"))
    }

    @Test
    fun `formatMpvStartSeconds emits compact seconds`() {
        assertEquals("90", PlaybackTimingConventions.formatMpvStartSeconds(90_000L))
        assertEquals("90.5", PlaybackTimingConventions.formatMpvStartSeconds(90_500L))
        assertEquals("0", PlaybackTimingConventions.formatMpvStartSeconds(-1L))
    }

    @Test
    fun `secondsToPositionMsFloored matches mpv position polling semantics`() {
        assertEquals(1_234L, PlaybackTimingConventions.secondsToPositionMsFloored(1.2349))
        assertEquals(0L, PlaybackTimingConventions.secondsToPositionMsFloored(-1.0))
    }

    @Test
    fun `secondsToDeltaMs preserves negative seek deltas`() {
        assertEquals(1_500L, PlaybackTimingConventions.secondsToDeltaMs(1.5))
        assertEquals(-2_500L, PlaybackTimingConventions.secondsToDeltaMs(-2.5))
    }

    @Test
    fun `coercePlaybackPositionMs clamps seek targets by known duration`() {
        assertEquals(0L, PlaybackTimingConventions.coercePlaybackPositionMs(-1L))
        assertEquals(90_000L, PlaybackTimingConventions.coercePlaybackPositionMs(90_000L))
        assertEquals(0L, PlaybackTimingConventions.coercePlaybackPositionMs(-1L, durationMs = 120_000L))
        assertEquals(45_000L, PlaybackTimingConventions.coercePlaybackPositionMs(45_000L, durationMs = 120_000L))
        assertEquals(120_000L, PlaybackTimingConventions.coercePlaybackPositionMs(240_000L, durationMs = 120_000L))
    }
}
