package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPlaybackProgressSyncTest {
    @Test
    fun `syncPlaybackProgressFromMpv saves observed mpv position and reanchors session`() = runBlocking {
        var now = 1_000L
        val session = DesktopPlaybackSession(
            episodeId = "episode-1",
            startPositionMs = 5_000L,
            nowMillis = { now },
        )
        var saved: SavedProgress? = null

        now = 3_000L
        val result = syncPlaybackProgressFromMpv(
            session = session,
            queryPositionMs = { Result.success(42_000L) },
            saveProgress = { episodeId, positionMs, lastWatched ->
                saved = SavedProgress(episodeId, positionMs, lastWatched)
                Result.success(Unit)
            },
            nowMillis = { 123_456L },
        )

        assertEquals(Result.success(42_000L), result)
        assertEquals(SavedProgress("episode-1", 42_000L, 123_456L), saved)
        assertEquals(42_000L, session.currentPositionMs())

        now = 4_500L

        assertEquals(43_500L, session.currentPositionMs())
    }

    @Test
    fun `syncPlaybackProgressFromMpv ignores null mpv position without saving`() = runBlocking {
        val session = DesktopPlaybackSession(episodeId = "episode-1", startPositionMs = 5_000L)
        var saveCount = 0

        val result = syncPlaybackProgressFromMpv(
            session = session,
            queryPositionMs = { Result.success(null) },
            saveProgress = { _, _, _ ->
                saveCount++
                Result.success(Unit)
            },
        )

        assertEquals(Result.success(null), result)
        assertEquals(0, saveCount)
    }

    @Test
    fun `syncPlaybackProgressFromMpv returns query error without saving`() = runBlocking {
        val session = DesktopPlaybackSession(episodeId = "episode-1", startPositionMs = 5_000L)
        var saveCount = 0

        val result = syncPlaybackProgressFromMpv(
            session = session,
            queryPositionMs = {
                Result.failure(AppError.PlaybackError.StreamError("IPC unavailable"))
            },
            saveProgress = { _, _, _ ->
                saveCount++
                Result.success(Unit)
            },
        )

        assertTrue(result is Result.Error)
        assertEquals(0, saveCount)
    }

    private data class SavedProgress(
        val episodeId: String,
        val positionMs: Long,
        val lastWatched: Long,
    )
}
