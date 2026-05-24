package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.player.mpv.MpvIpcController
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import com.miruplay.tv.player.mpv.MpvSeekMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files

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

    @Test
    fun `manual stop prefers observed mpv position over estimated session position`() = runBlocking {
        val mpv = Files.createTempFile("miruplay-mpv", ".exe")
        try {
            val player = MpvProcessPlayer(
                config = MpvRuntimeConfig(mpvExecutable = mpv, rife = null),
                processLauncher = { AliveProcess() },
                ipcClient = FakeMpvIpcController(positionSeconds = 42.5),
            )
            player.play(
                PlaybackSource(
                    uri = "D:/Anime/Episode 01.mkv",
                    mediaSourceId = "desktop-playback",
                    episodeId = "D:/Anime/Episode 01.mkv",
                ),
            )
            val session = PlaybackProgressSession(
                episodeId = "D:/Anime/Episode 01.mkv",
                startPositionMs = 30_000L,
                nowMillis = { 1_000L },
            )
            var saved: SavedProgress? = null

            val result = stopDesktopPlayback(
                player = player,
                session = session,
                saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                    saved = SavedProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                    Result.success(Unit)
                },
            )

            assertEquals(42_500L, result.savedPositionMs)
            assertTrue(result.stoppedPlayer)
            assertEquals(
                SavedProgress(
                    episodeId = "D:/Anime/Episode 01.mkv",
                    positionMs = 42_500L,
                    incrementPlayCount = false,
                ),
                saved?.copy(lastWatched = 0L),
            )
        } finally {
            Files.deleteIfExists(mpv)
        }
    }

    private data class SavedProgress(
        val episodeId: String,
        val positionMs: Long,
        val lastWatched: Long = 0L,
        val incrementPlayCount: Boolean,
    )

    private class AliveProcess : Process() {
        override fun getOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun exitValue(): Int =
            throw IllegalThreadStateException("still running")

        override fun destroy() = Unit

        override fun isAlive(): Boolean = true

        override fun pid(): Long = 77L
    }

    private class FakeMpvIpcController(
        private val positionSeconds: Double? = null,
    ) : MpvIpcController {
        override suspend fun cyclePause(): Result<Unit> =
            Result.success(Unit)

        override suspend fun setPaused(paused: Boolean): Result<Unit> =
            Result.success(Unit)

        override suspend fun setSpeed(speed: Double): Result<Unit> =
            Result.success(Unit)

        override suspend fun seekBy(seconds: Double, mode: MpvSeekMode): Result<Unit> =
            Result.success(Unit)

        override suspend fun quit(): Result<Unit> =
            Result.success(Unit)

        override suspend fun getTimePositionSeconds(): Result<Double?> =
            Result.success(positionSeconds)

        override suspend fun getDurationSeconds(): Result<Double?> =
            Result.success(null)

        override suspend fun getPaused(): Result<Boolean?> =
            Result.success(false)

        override suspend fun getEofReached(): Result<Boolean?> =
            Result.success(false)
    }

}
