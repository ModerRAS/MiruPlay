package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackProgressSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressSyncTest {
    @Test
    fun `syncObservedPlaybackProgress saves observed position and reanchors session`() = runBlocking {
        var now = 1_000L
        val session = PlaybackProgressSession(
            episodeId = "episode-1",
            startPositionMs = 5_000L,
            nowMillis = { now },
        )
        var saved: SavedProgress? = null

        now = 3_000L
        val result = syncObservedPlaybackProgress(
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
    fun `syncObservedPlaybackProgress ignores null player position without saving`() = runBlocking {
        val session = PlaybackProgressSession(episodeId = "episode-1", startPositionMs = 5_000L)
        var saveCount = 0

        val result = syncObservedPlaybackProgress(
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
    fun `syncObservedPlaybackProgress returns query error without saving`() = runBlocking {
        val session = PlaybackProgressSession(episodeId = "episode-1", startPositionMs = 5_000L)
        var saveCount = 0

        val result = syncObservedPlaybackProgress(
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
