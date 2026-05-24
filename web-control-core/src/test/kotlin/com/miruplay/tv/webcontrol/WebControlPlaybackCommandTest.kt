package com.miruplay.tv.webcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebControlPlaybackCommandTest {
    @Test
    fun `command kind normalizes WebUI aliases and whitespace`() {
        assertEquals(
            WebControlPlaybackCommandKind.RESUME,
            PlaybackCommandRequest(command = " PLAY ").playbackCommandKind(),
        )
        assertEquals(
            WebControlPlaybackCommandKind.SKIP_BACKWARD,
            PlaybackCommandRequest(command = "SKIP_BACKWARD").playbackCommandKind(),
        )
        assertEquals(
            WebControlPlaybackCommandKind.UNKNOWN,
            PlaybackCommandRequest(command = "restart").playbackCommandKind(),
        )
    }

    @Test
    fun `seek target resolves absolute and relative commands`() {
        assertEquals(
            45_000L,
            PlaybackCommandRequest(command = "seek", positionMs = 45_000L)
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
        assertEquals(
            17_000L,
            PlaybackCommandRequest(command = "seek_relative", deltaMs = 5_000L)
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
        assertEquals(
            0L,
            PlaybackCommandRequest(command = "seek_relative", deltaMs = -20_000L)
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
    }

    @Test
    fun `skip commands share playback seek defaults`() {
        assertEquals(
            42_000L,
            PlaybackCommandRequest(command = "skip_forward")
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
        assertEquals(
            2_000L,
            PlaybackCommandRequest(command = "skip_backward")
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
        assertEquals(
            14_500L,
            PlaybackCommandRequest(command = "skip_forward", deltaMs = 2_500L)
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
        assertEquals(
            9_500L,
            PlaybackCommandRequest(command = "skip_backward", deltaMs = 2_500L)
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
    }

    @Test
    fun `non seek commands have no target position`() {
        assertNull(
            PlaybackCommandRequest(command = "pause")
                .seekTargetPositionMs(currentPositionMs = 12_000L),
        )
    }

    @Test
    fun `speed command defaults to normal playback`() {
        assertEquals(
            1.0f,
            PlaybackCommandRequest(command = "speed").playbackSpeed(),
            0.0f,
        )
        assertEquals(
            1.25f,
            PlaybackCommandRequest(command = "speed", speed = 1.25f).playbackSpeed(),
            0.0f,
        )
    }
}
