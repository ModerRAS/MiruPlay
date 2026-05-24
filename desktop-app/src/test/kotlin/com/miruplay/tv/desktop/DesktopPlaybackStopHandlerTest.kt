package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DesktopPlaybackStopHandlerTest {
    @Test
    fun `desktop next playback source uses shared episode ordering and resume rules`() = runBlocking {
        val episodes = listOf(
            episode(id = "ep2", number = 2, duration = 100_000L),
            episode(id = "ep1", number = 1, duration = 100_000L),
        )

        val target = desktopNextPlaybackSource(
            currentEpisodeId = "ep1",
            episodes = episodes,
            nextProgress = ProgressRecord(
                episodeId = "ep2",
                positionMs = 30_000L,
                lastWatched = 1L,
            ),
        )

        assertEquals(
            PlaybackSource(
                uri = "D:/Anime/ep2.mkv",
                mediaSourceId = "anime",
                startPosition = 30_000L,
                episodeId = "ep2",
            ),
            target,
        )
    }

    @Test
    fun `desktop next playback source clears start position for completed episode`() = runBlocking {
        val episodes = listOf(
            episode(id = "ep1", number = 1, duration = 100_000L),
            episode(id = "ep2", number = 2, duration = 100_000L),
        )

        val target = desktopNextPlaybackSource(
            currentEpisodeId = "ep1",
            episodes = episodes,
            nextProgress = ProgressRecord(
                episodeId = "ep2",
                positionMs = 95_000L,
                lastWatched = 1L,
                playCount = 1,
            ),
        )

        assertEquals(
            PlaybackSource(
                uri = "D:/Anime/ep2.mkv",
                mediaSourceId = "anime",
                startPosition = 0L,
                episodeId = "ep2",
            ),
            target,
        )
    }

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

    private fun episode(
        id: String,
        number: Int,
        duration: Long,
    ): Episode =
        Episode(
            id = id,
            animeId = "anime",
            episodeNumber = number,
            filePath = "D:/Anime/$id.mkv",
            fileName = "$id.mkv",
            duration = duration,
        )
}
