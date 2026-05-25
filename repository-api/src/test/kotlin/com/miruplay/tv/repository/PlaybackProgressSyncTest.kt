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
    fun `savePlaybackProgressSnapshot normalizes position and saves play count intent`() = runBlocking {
        var saved: SavedProgress? = null

        val result = savePlaybackProgressSnapshot(
            episodeId = "episode-1",
            positionMs = -10L,
            incrementPlayCount = true,
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
            nowMillis = { 123L },
        )

        assertEquals(Result.success(Unit), result)
        assertEquals(SavedProgress("episode-1", 0L, 123L, incrementPlayCount = true), saved)
    }

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
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
            nowMillis = { 123_456L },
        )

        assertEquals(Result.success(42_000L), result)
        assertEquals(SavedProgress("episode-1", 42_000L, 123_456L, incrementPlayCount = false), saved)
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
            saveProgress = { _, _, _, _ ->
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
            saveProgress = { _, _, _, _ ->
                saveCount++
                Result.success(Unit)
            },
        )

        assertTrue(result is Result.Error)
        assertEquals(0, saveCount)
    }

    @Test
    fun `savePlaybackProgressOnStop saves queried position when mpv reports one`() = runBlocking {
        val session = PlaybackProgressSession(episodeId = "episode-1", startPositionMs = 5_000L)
        var saved: SavedProgress? = null

        val result = savePlaybackProgressOnStop(
            session = session,
            queryPositionMs = { Result.success(24_000L) },
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
            nowMillis = { 7_000L },
        )

        assertEquals(Result.success(24_000L), result)
        assertEquals(SavedProgress("episode-1", 24_000L, 7_000L, incrementPlayCount = false), saved)
    }

    @Test
    fun `savePlaybackProgressOnStop falls back to session position when player position is unavailable`() = runBlocking {
        var now = 1_000L
        val session = PlaybackProgressSession(
            episodeId = "episode-1",
            startPositionMs = 5_000L,
            nowMillis = { now },
        )
        now = 2_500L
        var saved: SavedProgress? = null

        val result = savePlaybackProgressOnStop(
            session = session,
            queryPositionMs = { Result.success(null) },
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
            nowMillis = { 9_000L },
        )

        assertEquals(Result.success(6_500L), result)
        assertEquals(SavedProgress("episode-1", 6_500L, 9_000L, incrementPlayCount = false), saved)
    }

    @Test
    fun `savePlaybackProgressOnStop falls back to session position when query fails`() = runBlocking {
        val session = PlaybackProgressSession(
            episodeId = "episode-1",
            startPositionMs = 5_000L,
            nowMillis = { 1_000L },
        )
        var saved: SavedProgress? = null

        val result = savePlaybackProgressOnStop(
            session = session,
            queryPositionMs = {
                Result.failure(AppError.PlaybackError.StreamError("IPC unavailable"))
            },
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
            nowMillis = { 9_000L },
        )

        assertEquals(Result.success(5_000L), result)
        assertEquals(SavedProgress("episode-1", 5_000L, 9_000L, incrementPlayCount = false), saved)
    }

    @Test
    fun `savePlaybackProgressOnStop can save without a player query`() = runBlocking {
        val session = PlaybackProgressSession(
            episodeId = "episode-1",
            startPositionMs = 5_000L,
            nowMillis = { 1_000L },
        )
        var saved: SavedProgress? = null

        val result = savePlaybackProgressOnStop(
            session = session,
            queryPositionMs = null,
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
            nowMillis = { 9_000L },
        )

        assertEquals(Result.success(5_000L), result)
        assertEquals(SavedProgress("episode-1", 5_000L, 9_000L, incrementPlayCount = false), saved)
    }

    private data class SavedProgress(
        val episodeId: String,
        val positionMs: Long,
        val lastWatched: Long,
        val incrementPlayCount: Boolean,
    )
}
