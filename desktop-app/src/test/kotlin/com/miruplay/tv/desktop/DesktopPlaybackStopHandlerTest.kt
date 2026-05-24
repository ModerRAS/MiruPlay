package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DesktopPlaybackStopHandlerTest {
    @Test
    fun `playback start saves resume position without incrementing play count`() = runBlocking {
        val session = PlaybackProgressSession(
            episodeId = "D:/Anime/Episode 01.mkv",
            startPositionMs = 45_000L,
            nowMillis = { 2_000L },
        )
        val source = PlaybackSource(
            uri = "D:/Anime/Episode 01.mkv",
            mediaSourceId = "desktop-playback",
            startPosition = 45_000L,
            episodeId = "D:/Anime/Episode 01.mkv",
        )
        var saved: SavedProgress? = null

        saveDesktopPlaybackStartProgress(
            session = session,
            source = source,
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
        )

        assertEquals(
            SavedProgress(
                episodeId = "D:/Anime/Episode 01.mkv",
                positionMs = 45_000L,
                incrementPlayCount = false,
            ),
            saved?.copy(lastWatched = 0L),
        )
    }

    @Test
    fun `playback completion saves completed position and increments play count`() = runBlocking {
        val session = PlaybackProgressSession(
            episodeId = "D:/Anime/Episode 01.mkv",
            startPositionMs = 45_000L,
            nowMillis = { 2_000L },
        )
        var saved: SavedProgress? = null

        val result = saveDesktopPlaybackCompletionProgress(
            session = session,
            queryDurationMs = { Result.success(1_500_000L) },
            queryPositionMs = { Result.success(1_499_000L) },
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
        )

        assertEquals(Result.success(1_500_000L), result)
        assertEquals(
            SavedProgress(
                episodeId = "D:/Anime/Episode 01.mkv",
                positionMs = 1_500_000L,
                incrementPlayCount = true,
            ),
            saved?.copy(lastWatched = 0L),
        )
    }

    @Test
    fun `playback completion falls back to observed position when duration is unavailable`() = runBlocking {
        val session = PlaybackProgressSession(
            episodeId = "D:/Anime/Episode 01.mkv",
            startPositionMs = 45_000L,
            nowMillis = { 2_000L },
        )
        var saved: SavedProgress? = null

        val result = saveDesktopPlaybackCompletionProgress(
            session = session,
            queryDurationMs = { Result.success(null) },
            queryPositionMs = { Result.success(1_499_000L) },
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
        )

        assertEquals(Result.success(1_499_000L), result)
        assertEquals(
            SavedProgress(
                episodeId = "D:/Anime/Episode 01.mkv",
                positionMs = 1_499_000L,
                incrementPlayCount = true,
            ),
            saved?.copy(lastWatched = 0L),
        )
    }

    @Test
    fun `manual stop saves current progress without incrementing play count`() = runBlocking {
        val session = PlaybackProgressSession(
            episodeId = "D:/Anime/Episode 01.mkv",
            startPositionMs = 30_000L,
            nowMillis = { 1_000L },
        )
        var saved: SavedProgress? = null

        val result = stopDesktopPlayback(
            player = null,
            session = session,
            saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                Result.success(Unit)
            },
        )

        assertEquals(30_000L, result.savedPositionMs)
        assertFalse(result.stoppedPlayer)
        assertEquals(
            SavedProgress(
                episodeId = "D:/Anime/Episode 01.mkv",
                positionMs = 30_000L,
                incrementPlayCount = false,
            ),
            saved?.copy(lastWatched = 0L),
        )
    }

    private data class SavedProgress(
        val episodeId: String,
        val positionMs: Long,
        val lastWatched: Long = 0L,
        val incrementPlayCount: Boolean,
    )

}
