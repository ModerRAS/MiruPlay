package com.miruplay.tv.webcontrol

import kotlinx.coroutines.runBlocking
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
        assertEquals(
            30_000L,
            PlaybackCommandRequest(command = "seek", positionMs = 45_000L)
                .seekTargetPositionMs(currentPositionMs = 12_000L, durationMs = 30_000L),
        )
        assertEquals(
            30_000L,
            PlaybackCommandRequest(command = "seek_relative", deltaMs = 45_000L)
                .seekTargetPositionMs(currentPositionMs = 12_000L, durationMs = 30_000L),
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

    @Test
    fun `execute command dispatches transport actions`() = runBlocking {
        val target = RecordingPlaybackCommandTarget(currentPositionMs = 12_000L)

        PlaybackCommandRequest(command = "pause").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "resume").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "toggle").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "stop").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "speed", speed = 1.5f).executeWebControlPlaybackCommand(target)

        assertEquals(
            listOf("pause", "resume", "toggle", "stop", "speed:1.5"),
            target.actions,
        )
    }

    @Test
    fun `execute seek commands share target current position semantics`() = runBlocking {
        val target = RecordingPlaybackCommandTarget(currentPositionMs = 12_000L)

        PlaybackCommandRequest(command = "seek", positionMs = 45_000L)
            .executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "seek_relative", deltaMs = -20_000L)
            .executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "skip_forward", deltaMs = 2_500L)
            .executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "skip_backward", deltaMs = 2_500L)
            .executeWebControlPlaybackCommand(target)

        assertEquals(
            listOf(45_000L, 0L, 14_500L, 9_500L),
            target.seekPositions,
        )
    }

    @Test
    fun `execute seek commands clamp to target duration`() = runBlocking {
        val target = RecordingPlaybackCommandTarget(
            currentPositionMs = 12_000L,
            durationMs = 15_000L,
        )

        PlaybackCommandRequest(command = "seek", positionMs = 45_000L)
            .executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "skip_forward")
            .executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "seek_relative", deltaMs = -20_000L)
            .executeWebControlPlaybackCommand(target)

        assertEquals(
            listOf(15_000L, 15_000L, 0L),
            target.seekPositions,
        )
    }

    @Test
    fun `lambda command target adapter routes operations`() = runBlocking {
        val actions = mutableListOf<String>()
        val seekPositions = mutableListOf<Long>()
        val target = webControlPlaybackCommandTarget(
            pause = { actions += "pause" },
            resume = { actions += "resume" },
            toggle = { actions += "toggle" },
            stop = { actions += "stop" },
            seekTo = { positionMs -> seekPositions += positionMs },
            setPlaybackSpeed = { speed -> actions += "speed:$speed" },
            currentPositionMs = { 12_000L },
            durationMs = { 15_000L },
        )

        PlaybackCommandRequest(command = "pause").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "resume").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "toggle").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "stop").executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "speed", speed = 1.25f).executeWebControlPlaybackCommand(target)
        PlaybackCommandRequest(command = "seek", positionMs = 45_000L).executeWebControlPlaybackCommand(target)

        assertEquals(listOf("pause", "resume", "toggle", "stop", "speed:1.25"), actions)
        assertEquals(listOf(15_000L), seekPositions)
    }

    private class RecordingPlaybackCommandTarget(
        private val currentPositionMs: Long,
        private val durationMs: Long = 0L,
    ) : WebControlPlaybackCommandTarget {
        val actions = mutableListOf<String>()
        val seekPositions = mutableListOf<Long>()

        override suspend fun pause() {
            actions += "pause"
        }

        override suspend fun resume() {
            actions += "resume"
        }

        override suspend fun toggle() {
            actions += "toggle"
        }

        override suspend fun stop() {
            actions += "stop"
        }

        override suspend fun seekTo(positionMs: Long) {
            seekPositions += positionMs
        }

        override suspend fun setPlaybackSpeed(speed: Float) {
            actions += "speed:$speed"
        }

        override suspend fun currentPositionMs(): Long =
            currentPositionMs

        override suspend fun durationMs(): Long =
            durationMs
    }
}
