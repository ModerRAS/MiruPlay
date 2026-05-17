package com.miruplay.tv.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPlaybackSessionTest {
    @Test
    fun `position advances while playback is active`() {
        var now = 1_000L
        val session = DesktopPlaybackSession(
            episodeId = "D:/Anime/Show/01.mkv",
            startPositionMs = 30_000L,
            nowMillis = { now },
        )

        now += 12_500L

        assertEquals(42_500L, session.currentPositionMs())
    }

    @Test
    fun `pause freezes position until resumed`() {
        var now = 0L
        val session = DesktopPlaybackSession(
            episodeId = "D:/Anime/Show/01.mkv",
            startPositionMs = 0L,
            nowMillis = { now },
        )

        now = 10_000L
        session.setPaused(true)
        now = 30_000L

        assertEquals(10_000L, session.currentPositionMs())

        session.setPaused(false)
        now = 35_000L

        assertEquals(15_000L, session.currentPositionMs())
    }

    @Test
    fun `seek adjusts tracked position and clamps below zero`() {
        var now = 0L
        val session = DesktopPlaybackSession(
            episodeId = "D:/Anime/Show/01.mkv",
            startPositionMs = 20_000L,
            nowMillis = { now },
        )

        session.seekBy(30.0)
        assertEquals(50_000L, session.currentPositionMs())

        session.seekBy(-120.0)
        assertEquals(0L, session.currentPositionMs())
    }

    @Test
    fun `syncPosition anchors future estimation to observed player position`() {
        var now = 5_000L
        val session = DesktopPlaybackSession(
            episodeId = "D:/Anime/Show/01.mkv",
            startPositionMs = 10_000L,
            nowMillis = { now },
        )

        now = 8_000L
        session.syncPosition(90_000L)

        assertEquals(90_000L, session.currentPositionMs())

        now = 11_000L

        assertEquals(93_000L, session.currentPositionMs())
    }
}
